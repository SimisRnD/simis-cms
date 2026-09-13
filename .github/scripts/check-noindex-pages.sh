#!/usr/bin/env bash
#
# check-noindex-pages.sh
#
# Assert that a deployed instance keeps its account and utility pages out of search indexes
# (issue #2021).
#
# Before the fix, seven platform pages -- /login, /logout, /forgot-password, /register,
# /validation-sent, /confirm-subscription and /unsubscribe -- were served to an anonymous visitor
# with no X-Robots-Tag at all. A site audit of www.simisinc.com flagged /login and
# /forgot-password, the two it could reach by following links; /login carries an inbound link from
# the site-wide header, giving it more internal inlinks than any real content page. The rest were
# equally indexable and had simply not been linked or guessed yet.
#
# Why this runs against a booted app rather than in a unit test:
#   AccountPageNoindexTest covers the two halves that can be tested in isolation -- that
#   cms-layout.xml marks the right pages, and that XMLPageLoader parses the attribute the way it
#   is written. Neither proves the third link in the chain: that PageServlet actually turns a
#   noindex page into a response header. That needs a real request against a real deployment,
#   which is exactly what this job already has.
#
# Status is deliberately not part of the assertion, beyond skipping a 404. On a configured site
# these pages answer 200; on a fresh instance with no session or token, four of them redirect to
# "/" instead. The header is set either way and is what is being checked, so keying the assertion
# on 200 would have silently skipped four of the six here -- which is exactly what an earlier
# version of this script did.
#
# The controls matter as much as the assertions. A bug that sent X-Robots-Tag: noindex on EVERY
# response would satisfy every positive check here while quietly delisting the whole site, so an
# ordinary page is asserted NOT to carry it.
#
# Usage: check-noindex-pages.sh [base-url]   (default http://localhost)

set -uo pipefail

BASE="${1:-http://localhost}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

NOINDEX_PATHS=(/login /logout /forgot-password /validation-sent /confirm-subscription /unsubscribe)

FAILURES=0
CHECKED=0

fail() {
  echo "  FAIL: $*"
  FAILURES=$((FAILURES + 1))
}

robots_value() {
  # Last occurrence wins, trailing CR and spaces stripped, lowercased for comparison
  grep -i '^x-robots-tag:' "$1" | tail -1 | cut -d: -f2- | tr -d '\r' | tr -d ' ' | tr '[:upper:]' '[:lower:]'
}

# fetch <path> -> echoes the status code, writes headers to $TMP/hdr; non-zero on transport failure
fetch() {
  curl -sS --http1.1 --max-time 30 -o /dev/null -D "$TMP/hdr" -w '%{http_code}' "${BASE}$1"
}

expect_noindex() {
  local path="$1" code
  code=$(fetch "$path")
  if [ $? -ne 0 ]; then
    fail "$path: request failed"
    return
  fi
  if [ "$code" = "404" ]; then
    echo "  skip: $path (404, not served here)"
    return
  fi
  CHECKED=$((CHECKED + 1))
  local value
  value=$(robots_value "$TMP/hdr")
  if [ "$value" = "noindex" ]; then
    echo "  ok: $path ($code) sends X-Robots-Tag: noindex"
  else
    fail "$path ($code) must send X-Robots-Tag: noindex, got '${value:-(no header)}'"
  fi
}

expect_indexable() {
  local path="$1" why="$2" code
  code=$(fetch "$path")
  if [ $? -ne 0 ]; then
    fail "$path: request failed"
    return
  fi
  if [ "$code" = "404" ]; then
    echo "  skip: $path (404, not served here)"
    return
  fi
  local value
  value=$(robots_value "$TMP/hdr")
  if [ -z "$value" ]; then
    echo "  ok: $path ($code) sends no X-Robots-Tag ($why)"
  else
    fail "$path must stay indexable ($why), but sent X-Robots-Tag: $value"
  fi
}

echo "Search-index policy against ${BASE}"
echo
echo "Account and utility pages, which must never be indexed:"
for path in "${NOINDEX_PATHS[@]}"; do
  expect_noindex "$path"
done

echo
echo "Controls -- these must NOT pick up the header:"
expect_indexable "/" "an ordinary content page"
expect_indexable "/register" "a real destination a site may want discoverable (issue #2021)"

echo
# A deployment serving none of these would otherwise reach the end having asserted nothing and
# exit 0. Every listed page is expected to answer: if one starts 404ing, the list here and the
# layout have drifted apart and that is worth failing over, not skipping past.
if [ "$CHECKED" -ne "${#NOINDEX_PATHS[@]}" ]; then
  echo "FAILED: ${CHECKED} of ${#NOINDEX_PATHS[@]} noindex pages were actually checked."
  echo "        A path this script probes has moved or is no longer served; reconcile it with"
  echo "        cms-layout.xml and AccountPageNoindexTest."
  exit 1
fi

if [ "$FAILURES" -ne 0 ]; then
  echo "FAILED: $FAILURES search-index policy check(s)"
  exit 1
fi
echo "All search-index policy checks passed ($CHECKED noindex pages inspected)"
