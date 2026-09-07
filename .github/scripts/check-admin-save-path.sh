#!/usr/bin/env bash
#
# check-admin-save-path.sh
#
# Assert that an administrator can still SAVE something after a deploy.
#
# The rest of the deploy smoke test is anonymous. It packages the WAR, brings the container up,
# waits for the app's own healthcheck, and reads bytes off a handful of public URLs. Every
# assertion it makes is made as a logged-out visitor, so nothing in CI has ever exercised an
# authenticated write. A regression in the session, the form token, the CSP, or a migration that
# runs but leaves a settings write failing is invisible to all of it: the pages still return a
# perfectly healthy 200 with a correct Content-Length.
#
# That is not a hypothetical shape of bug here. Issue #1188 was exactly it -- PageServlet sends
# script-src 'self' 'nonce-...' with no 'unsafe-inline', so an inline on*= handler never ran and the
# control it belonged to silently did nothing. A logo upload did nothing. A Media Library sort did
# nothing. An admin Delete link did nothing. None of it surfaced in the UI and none of it failed a
# check, because a page whose buttons are dead still serves flawlessly.
#
# tools/check-inline-handlers.py now guards that one regression statically. This covers the general
# case a static scan cannot see: the authenticated write path, end to end, against a real deploy.
#
# THE ASSERTION THAT MATTERS IS THE READ-BACK.
#
# A POST returning 200, or a 302 to the form, proves nothing on its own -- that is precisely how a
# silently-failing save looks, and it is how every bug above presented. So the check is not "did the
# POST succeed"; it is "does the value come back changed on a subsequent GET". Do not relax this to
# a status-code assertion.
#
# What is exercised, in order:
#
#   1. GET /login                  -- session cookie issued, form token and widget id read from the form
#   2. POST /login                 -- credentials submitted
#   3. GET  the settings form      -- 200 AND the field present. This is the authentication
#                                     assertion: anonymously this URL answers 302 with an empty body
#                                     and no field, so reaching the form at all proves the session
#                                     authenticated. A login that silently failed cannot get here.
#   4. POST a changed value        -- every field on the form is resubmitted, the way a browser does,
#                                     with one changed. Posting the target field alone would blank
#                                     its neighbours, including the enabled checkbox.
#   5. GET  the settings form      -- the changed value must read back. This is the real check.
#   6. POST the original value     -- restore, so the script leaves nothing behind and is safe to
#                                     point at an instance that is not thrown away afterwards.
#
# Demonstrated to fail, not just to pass. A gate nobody has watched fail is decoration, so both
# failure modes were reproduced against this container before the check was committed:
#
#   * Wrong credentials -- run with a bad password, the script stops at step 3 with
#     "GET /admin/security-txt-properties returned 302, expected 200". Anonymous and
#     failed-sign-in are the same response here, which is what makes step 3 a usable auth assertion.
#
#   * A silently-discarded write -- a BEFORE UPDATE trigger returning NULL was installed on
#     site_properties, which makes every UPDATE affect zero rows and raise no error:
#
#         CREATE FUNCTION swallow_update() RETURNS trigger AS $$ BEGIN RETURN NULL; END; $$
#           LANGUAGE plpgsql;
#         CREATE TRIGGER swallow_site_property_updates BEFORE UPDATE ON site_properties
#           FOR EACH ROW EXECUTE FUNCTION swallow_update();
#
#     The application noticed nothing. The POST still answered 302, exactly as it does on success.
#     The read-back caught it ("the save did not persist"), the script exited 1, and dropping the
#     trigger returned it to green. That is the precise shape of failure this exists for.
#
# Note that REVOKE UPDATE does not reproduce it: the app's database role owns site_properties and an
# owner keeps its privileges through a REVOKE, so the write succeeds and the check stays green. The
# trigger is the reproduction that works.
#
# If this fails at step 3 and the credentials are definitely right, suspect the login rate limiter
# before suspecting the deploy. RateLimitCommand allows 5 attempts per username and 10 per IP in a
# rolling 30-minute window by default (security.rateLimit.usernameMaxAttempts /
# usernameWindowMinutes override them). It is cache-backed, not a column on the user -- a locked-out
# account still shows failed_attempt_count 0 and a null locked_until, so the user table looks
# perfectly healthy while every sign-in is refused with "Too many attempts. Please try again later."
# in the app log.
#
# One CI run spends one attempt against a container that was created moments earlier, so this does
# not arise in the pipeline. It arises when the same container is driven repeatedly -- iterating on
# this script locally, or re-running the job by hand against a still-running stack. Restart the app
# container to clear the buckets.
#
# Why securitytxt.acknowledgments:
#   It is a plain text property, empty by default, that feeds one optional line of
#   /.well-known/security.txt and nothing else. Nothing renders it, no code branches on it, and it
#   needs no step-up re-authentication the way a role or group change does. The point is to cover
#   the authenticated write path at all -- one save on one inert property. Resist widening this into
#   a tour of the admin: a broad script is a flaky one, and a flaky gate gets ignored.
#
# Usage: check-admin-save-path.sh [base-url]
#   Credentials come from CMS_ADMIN_USERNAME / CMS_ADMIN_PASSWORD, matching the values the workflow
#   already writes into the compose .env. The app auto-provisions that admin on first boot
#   (V71120__create_admin.java), so there is no fixture to load.

set -uo pipefail

BASE="${1:-http://localhost}"
USERNAME="${CMS_ADMIN_USERNAME:-}"
PASSWORD="${CMS_ADMIN_PASSWORD:-}"
SETTINGS_PATH="/admin/security-txt-properties"
TARGET_FIELD="securitytxt.acknowledgments"

if [ -z "$USERNAME" ] || [ -z "$PASSWORD" ]; then
  echo "FAIL: CMS_ADMIN_USERNAME and CMS_ADMIN_PASSWORD must be set"
  exit 1
fi

TMP="$(mktemp -d)"
JAR="$TMP/cookies"
trap 'rm -rf "$TMP"' EXIT

FAILURES=0
fail() { echo "  FAIL: $*"; FAILURES=$((FAILURES + 1)); }
pass() { echo "  ok: $*"; }

# curl, always HTTP/1.1 and always through the one cookie jar. HTTP/1.1 for the same reason
# check-response-integrity.sh forces it: it keeps this comparable to what that script sees.
req() { curl -sS --http1.1 --max-time 30 -b "$JAR" -c "$JAR" "$@"; }

# Print the single <form> block that contains a given string, flattened onto one line. The admin
# page carries more than one form (the header search is another) and each has its own widget id, so
# the fields have to be read out of the right one rather than by first-match across the page.
#
# The flattening is not cosmetic. site-properties-editor.jsp emits its <input> tags across several
# lines, and grep is line-oriented -- matching tags line by line silently finds nothing, builds an
# empty POST body, and blanks every field on the form. Keep the newlines collapsed before any
# tag-matching runs.
form_block() {
  awk -v needle="$2" '
    /<form/  { inform = 1; buf = "" }
    inform   { buf = buf $0 " " }
    /<\/form>/ { if (inform && index(buf, needle)) printf "%s", buf; inform = 0; buf = "" }
  ' "$1"
}

# Read one attribute value out of an <input>/<select> by its name attribute.
field_value() {
  grep -oE "name=\"$2\"[^>]*value=\"[^\"]*\"" <<<"$1" | head -1 | sed 's/.*value="//; s/"$//'
}

echo "Checking the authenticated admin save path at ${BASE}"

# --- 1. GET the login form -------------------------------------------------------------------
if ! req -o "$TMP/login.html" -w '' "${BASE}/login"; then
  fail "could not fetch ${BASE}/login"
  exit 1
fi
LOGIN_FORM="$(form_block "$TMP/login.html" 'name="password"')"
LOGIN_WIDGET="$(field_value "$LOGIN_FORM" 'widget')"
LOGIN_TOKEN="$(field_value "$LOGIN_FORM" 'token')"

if [ -z "$LOGIN_WIDGET" ] || [ -z "$LOGIN_TOKEN" ]; then
  fail "login form carried no widget id and/or form token (widget='$LOGIN_WIDGET' token='$LOGIN_TOKEN')"
  echo "The login page rendered but its hidden fields are missing; a browser could not sign in either."
  exit 1
fi
pass "login form served with a form token"

# --- 2. POST the credentials -----------------------------------------------------------------
req -o "$TMP/login-post.html" -w '' \
  --data-urlencode "widget=${LOGIN_WIDGET}" \
  --data-urlencode "token=${LOGIN_TOKEN}" \
  --data-urlencode "email=${USERNAME}" \
  --data-urlencode "password=${PASSWORD}" \
  "${BASE}/login" >/dev/null

# --- 3. Reach the settings form, which proves the session authenticated -----------------------
SETTINGS_CODE="$(req -o "$TMP/form.html" -w '%{http_code}' "${BASE}${SETTINGS_PATH}")"
if [ "$SETTINGS_CODE" != "200" ]; then
  fail "GET ${SETTINGS_PATH} returned ${SETTINGS_CODE}, expected 200"
  echo "  Anonymously this path answers 302, so this is what a failed sign-in looks like."
  echo "  Check the credentials, the session cookie, and the login widget."
  exit 1
fi
if ! grep -q "$TARGET_FIELD" "$TMP/form.html"; then
  fail "signed in, but ${SETTINGS_PATH} did not render ${TARGET_FIELD}"
  exit 1
fi
pass "signed in and reached ${SETTINGS_PATH}"

SETTINGS_FORM="$(form_block "$TMP/form.html" "$TARGET_FIELD")"
WIDGET="$(field_value "$SETTINGS_FORM" 'widget')"
TOKEN="$(field_value "$SETTINGS_FORM" 'token')"
ORIGINAL="$(field_value "$SETTINGS_FORM" "$TARGET_FIELD")"

if [ -z "$WIDGET" ] || [ -z "$TOKEN" ]; then
  fail "settings form carried no widget id and/or form token"
  exit 1
fi

# Resubmit every field the form renders, with one changed -- a browser posts the whole form, and
# posting only the target would blank its neighbours (including the enabled checkbox).
build_post_args() {
  local new_value="$1"
  POST_ARGS=(--data-urlencode "widget=${WIDGET}" --data-urlencode "token=${TOKEN}")
  local line name value
  while IFS= read -r line; do
    name="${line#*name=\"}"
    name="${name%%\"*}"
    case "$name" in
      widget|token|'') continue ;;
    esac
    if [ "$name" = "$TARGET_FIELD" ]; then
      value="$new_value"
    else
      value="$(field_value "$line" "$name")"
    fi
    POST_ARGS+=(--data-urlencode "${name}=${value}")
  done < <(grep -oE '<(input|select)\b[^>]*name="[^"]*"[^>]*>' <<<"$SETTINGS_FORM")
}

# --- 4. Save a changed value -----------------------------------------------------------------
MARKER="https://example.invalid/ci-smoke-$$-$(date +%s)"
build_post_args "$MARKER"
req -o "$TMP/save.html" -w '' "${POST_ARGS[@]}" "${BASE}${SETTINGS_PATH}" >/dev/null

# --- 5. The check: it has to read back --------------------------------------------------------
req -o "$TMP/after.html" -w '' "${BASE}${SETTINGS_PATH}" >/dev/null
AFTER="$(field_value "$(form_block "$TMP/after.html" "$TARGET_FIELD")" "$TARGET_FIELD")"

if [ "$AFTER" = "$MARKER" ]; then
  pass "saved value read back from a fresh GET (${TARGET_FIELD})"
else
  fail "the save did not persist"
  echo "    wrote: ${MARKER}"
  echo "    read:  ${AFTER:-<empty>}"
  echo "  The POST was accepted and the value did not change. This is the silent-save failure this"
  echo "  check exists for -- a CSP, form-token, session or migration regression on the write path."
fi

# --- 6. Restore -------------------------------------------------------------------------------
build_post_args "$ORIGINAL"
req -o "$TMP/restore.html" -w '' "${POST_ARGS[@]}" "${BASE}${SETTINGS_PATH}" >/dev/null
req -o "$TMP/restored.html" -w '' "${BASE}${SETTINGS_PATH}" >/dev/null
RESTORED="$(field_value "$(form_block "$TMP/restored.html" "$TARGET_FIELD")" "$TARGET_FIELD")"
if [ "$RESTORED" = "$ORIGINAL" ]; then
  pass "original value restored"
else
  fail "could not restore ${TARGET_FIELD} (expected '${ORIGINAL:-<empty>}', found '${RESTORED:-<empty>}')"
fi

echo
if [ "$FAILURES" -gt 0 ]; then
  echo "Admin save path: ${FAILURES} failure(s)"
  exit 1
fi
echo "Admin save path: OK -- an administrator can sign in and persist a settings change"
