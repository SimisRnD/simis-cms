#!/usr/bin/env bash
#
# check-response-integrity.sh
#
# Assert that a deployed instance returns well-formed HTTP bodies. `ant package` proves the WAR
# builds and the existing smoke test proves it deploys and turns healthy; neither looks at what the
# responses actually contain. Two production incidents got through that gap:
#
#   #1832 -- connector compression rewrote bodies without updating Content-Length, so every
#            stylesheet went out with `Content-Encoding: gzip` beside the *uncompressed* byte count.
#            HTTP/1.1 clients hung waiting for bytes that were never coming and the site lost its
#            theme. deploy-smoke-test went green: the app was healthy, its answers were not.
#
#   #1878 -- a response-wrapping compression filter had the tail of its gzip stream discarded by
#            Tomcat (the response is suspended once a RequestDispatcher.forward() returns), so pages
#            arrived truncated. 22 unit tests went green: nothing in a mocked response suspends.
#
# Both are invisible to a health check and to unit tests.
#
# What each check is actually proven against, because "it would have caught X" is easy to assert
# and harder to demonstrate:
#
#   * The truncation check was run against PR #1878's real filter with its container fix reverted.
#     It failed on both forwarded pages and passed everything else. That is a real bug, reproduced.
#
#   * The length check was run against a stub server emitting the #1832 shape by hand (gzip body,
#     Content-Length for the unencoded body) and failed with the same client-side symptom the issue
#     reported -- the transfer stalling part-way. It has NOT been demonstrated against a genuine
#     reproduction of #1832, because there does not appear to be one: re-enabling connector
#     compression on tomcat:11.0-jdk21 compresses /css/platform.css to 26,172 bytes (the exact
#     figure in #1834) and sends NO Content-Length at all, so the mismatch never arises. The root
#     cause of #1832 remains unknown; this gate checks the invariant it violated, not a theory of
#     how it came to violate it.
#
# The checks, each an invariant rather than a snapshot:
#
#   1. A declared Content-Length must equal the bytes actually delivered -- the invariant #1832 broke.
#   2. A body served with `Content-Encoding: gzip` must be a COMPLETE gzip stream.
#   3. A request must not be answered with both `Content-Encoding` and a Content-Length describing
#      the unencoded body.
#
# Why the decode is done by hand rather than with `curl --compressed`:
#   curl exits 0 on a truncated gzip stream, having inflated as much as it could. During #1878's
#   truncation `curl --compressed` reported success on a body missing 8 KB. Only piping the raw
#   bytes through `gunzip` and checking ITS exit status detects the missing trailer. Do not
#   "simplify" this back to --compressed.
#
# Why HTTP/1.1 is forced:
#   HTTP/2 frames the body, so the end of the message is unambiguous and a wrong Content-Length is
#   masked. #1832 was invisible over h2 and obvious over 1.1.
#
# Compression is not assumed. If a response is not encoded, checks 2 and 3 do not apply to it and
# check 1 still does -- so this script is meaningful both before and after compression is enabled.
#
# Usage: check-response-integrity.sh [base-url]   (default http://localhost)

set -uo pipefail

BASE="${1:-http://localhost}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

FAILURES=0
CHECKED=0

fail() {
  echo "  FAIL: $*"
  FAILURES=$((FAILURES + 1))
}

pass() {
  echo "  ok: $*"
}

header_value() {
  # Last occurrence wins, trailing CR stripped, value lowercased for comparison
  grep -i "^$2:" "$1" | tail -1 | cut -d: -f2- | tr -d '\r' | tr -d ' ' | tr '[:upper:]' '[:lower:]'
}

check_path() {
  local path="$1" accept_encoding="$2" label="$3"
  local body="$TMP/body" hdr="$TMP/hdr"

  local code
  code=$(curl -sS --http1.1 --max-time 30 \
           -H "Accept-Encoding: ${accept_encoding}" \
           -o "$body" -D "$hdr" -w '%{http_code}' "${BASE}${path}" 2>"$TMP/curl.err")
  local curl_rc=$?

  if [ $curl_rc -ne 0 ]; then
    # 18 is CURLE_PARTIAL_FILE -- the transfer ended before Content-Length was satisfied, which is
    # the #1832 signature seen from the client side
    fail "$label: curl exited $curl_rc ($(tr -d '\n' < "$TMP/curl.err"))"
    return
  fi
  if [ "$code" != "200" ]; then
    echo "  skip: $label (HTTP $code)"
    return
  fi
  CHECKED=$((CHECKED + 1))

  local actual declared encoding
  actual=$(wc -c < "$body" | tr -d ' ')
  declared=$(header_value "$hdr" 'content-length')
  encoding=$(header_value "$hdr" 'content-encoding')

  # 1. Declared length must describe what was actually sent
  if [ -n "$declared" ]; then
    if [ "$declared" != "$actual" ]; then
      fail "$label: Content-Length says $declared, $actual bytes delivered"
    else
      pass "$label: Content-Length $declared matches the body"
    fi
  fi

  if [ -z "$encoding" ]; then
    pass "$label: not encoded"
    return
  fi

  # 3. An encoded body must not also carry a length for the unencoded one. Check 1 already compares
  #    declared against delivered, so reaching here with a declared length that matched is fine;
  #    what is fatal is a declared length that does not.
  if [ -n "$declared" ] && [ "$declared" != "$actual" ]; then
    fail "$label: Content-Encoding: $encoding sent with a Content-Length for the unencoded body"
  fi

  # 2. An encoded body must decode completely
  case "$encoding" in
    gzip)
      if gunzip -c "$body" > "$TMP/decoded" 2>"$TMP/gunzip.err"; then
        pass "$label: gzip stream is complete ($actual on the wire, $(wc -c < "$TMP/decoded" | tr -d ' ') decoded)"
      else
        # gunzip writes two lines and repeats the temp path; fold to one readable line
        fail "$label: gzip stream is incomplete -- $(sed "s|$TMP/body|the body|g" "$TMP/gunzip.err" | tr '\n' ' ')"
      fi
      ;;
    *)
      echo "  skip: $label (content-encoding $encoding is not checked)"
      ;;
  esac
}

echo "Response integrity against ${BASE}"
echo
echo "A generated page, rendered through a forward -- the #1878 shape:"
check_path "/"      "gzip, deflate" "GET / (gzip offered)"
check_path "/"      "identity"      "GET / (identity)"
check_path "/login" "gzip, deflate" "GET /login (gzip offered)"
echo
echo "A static asset served by the default servlet -- the #1832 shape:"
check_path "/css/platform.css" "gzip, deflate" "GET /css/platform.css (gzip offered)"
check_path "/css/platform.css" "identity"      "GET /css/platform.css (identity)"
echo
echo "A servlet that writes directly rather than forwarding:"
check_path "/robots.txt"  "gzip, deflate" "GET /robots.txt (gzip offered)"

echo
# A deployment where every path returns 404 or 302 would otherwise reach the end having asserted
# nothing at all and exit 0. Paths move; a silently empty gate is worse than a failing one.
MIN_CHECKED=4
if [ "$CHECKED" -lt "$MIN_CHECKED" ]; then
  echo "FAILED: only $CHECKED response(s) were actually checked, expected at least $MIN_CHECKED."
  echo "        The paths this script probes have moved, or the deployment is not serving them."
  exit 1
fi

if [ "$FAILURES" -ne 0 ]; then
  echo "FAILED: $FAILURES response integrity check(s)"
  exit 1
fi
echo "All response integrity checks passed ($CHECKED responses inspected)"
