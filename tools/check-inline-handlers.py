#!/usr/bin/env python3
"""Report inline HTML event-handler attributes (on*=) in served JSPs.

Background
----------
``PageServlet`` sends ``Content-Security-Policy: script-src 'self' 'nonce-<random>'``
with no ``'unsafe-inline'`` and no ``'unsafe-hashes'``. Under that policy the
browser refuses to run an inline event-handler attribute -- ``onclick=``,
``onchange=``, ``onsubmit=``, ... -- because those fall under ``script-src-attr``,
which a nonce does not cover. The handler simply never fires and nothing surfaces
in the UI: a logo upload did nothing (issue #1190), a Media Library sort dropdown
did nothing (issue #1194), an admin "Delete" link did nothing. See issue #1188.

The fix is to bind the behaviour with ``addEventListener`` inside a nonce'd
``<script>`` block, or -- for the shared confirm-and-POST action links -- via the
global ``data-confirm-post`` delegation in ``main.jsp``. This gate keeps the
pattern from coming back: it finds every inline ``on*=`` attribute in the JSP tree
and, in ``--strict`` mode, fails on any that is not recorded in ``ALLOWLIST`` as
known, still-to-be-converted debt. A NEW inline handler cannot merge without
either converting it or writing its file into the allowlist, and the allowlist
only shrinks as the sweep proceeds.

What counts
-----------
Only inline event-handler ATTRIBUTES on HTML tags are reported. Comments (JSP
``<%-- --%>``, HTML ``<!-- -->``) and the contents of ``<script>`` blocks are
blanked out first -- preserving line numbers -- so that:

  * prose in a comment that merely mentions ``onclick=`` is not a finding
    (several already-converted files carry such comments), and
  * JavaScript property assignment (``el.onclick = fn``) inside a ``<script>`` is
    not a finding -- that runs fine; it is script the nonce authorises.

Handlers injected as HTML strings from inside JavaScript (``'<a onclick=...>'``)
are out of scope for this reporter; none exist in the baseline and they are a
distinct DOM-injection concern.

Modes
-----
Report-only by default. ``--strict`` (or ``STRICT=1``) exits 1 on any file whose
live inline-handler count exceeds its allowlisted number, or on any un-allowlisted
file with handlers. CI runs it with ``--strict``.

This is a read-only reporter. It changes no files.
"""
from __future__ import annotations

import argparse
import os
import re
import sys

JSP_ROOT = "src/main/webapp/WEB-INF/jsp"

# HTML event-handler attribute names. Kept explicit (rather than a bare ``on\w+``)
# so a stray word like "onboarding" or an attribute like ``data-online`` can never
# match. Any on* attribute is equally dead under the CSP; this is simply the set
# that actually occurs plus the common remainder.
EVENTS = (
    "click", "dblclick", "change", "input", "submit", "reset", "select",
    "focus", "focusin", "focusout", "blur", "keydown", "keyup", "keypress",
    "mousedown", "mouseup", "mouseover", "mouseout", "mouseenter", "mouseleave", "mousemove",
    "load", "unload", "beforeunload", "error", "scroll", "resize", "contextmenu",
    "drag", "dragstart", "dragend", "dragover", "dragenter", "dragleave", "drop",
    "touchstart", "touchend", "touchmove", "paste", "copy", "cut", "wheel", "toggle",
    "play", "pause", "ended", "invalid", "search",
)
HANDLER = re.compile(r"""\son(?:%s)\s*=\s*["']""" % "|".join(EVENTS), re.IGNORECASE)

# file (relative to JSP_ROOT) -> number of known inline handlers still to convert.
# Each is dead under the CSP (issue #1188). The count only goes down as the sweep
# replaces them with addEventListener / the data-confirm-post contract; when a
# file reaches zero, delete its line. New handlers (a new file, or a bump above
# the recorded number) fail --strict.
ALLOWLIST: dict[str, int] = {
    # Baseline captured 2026-08-15 (issue #1188). Each of these files still binds
    # behaviour with an inline on*= attribute that the CSP makes dead; they are the
    # remaining sweep. Run `python3 tools/check-inline-handlers.py .` for the live
    # list with file:line. Decrement/remove an entry when you convert its handler(s);
    # never raise a number. Keep alphabetised.
    "admin/app-form.jsp": 1,
    "admin/audit-log-list.jsp": 1,
    "admin/blog-tags-list.jsp": 1,
    "admin/cancel-order.jsp": 1,
    "admin/capability-grants.jsp": 1,
    "admin/collection-categories-list.jsp": 1,
    "admin/collection-details.jsp": 1,
    "admin/collection-relationships-list.jsp": 1,
    "admin/collection-tags-list.jsp": 1,
    "admin/content-list.jsp": 1,
    "admin/database-maintenance.jsp": 1,
    "admin/dataset-schema.jsp": 1,
    "admin/datasets-list.jsp": 1,
    "admin/file-versions-list.jsp": 1,
    "admin/folder-details.jsp": 1,
    "admin/folder-files-list.jsp": 1,
    "admin/folder-list.jsp": 1,
    "admin/folder-sub-folders-list.jsp": 1,
    "admin/forms.jsp": 1,
    "admin/groups-list.jsp": 1,
    "admin/health-dashboard.jsp": 1,
    "admin/integration-registry.jsp": 1,
    "admin/items-list.jsp": 1,
    "admin/mailing-list-members.jsp": 1,
    "admin/mailing-lists.jsp": 1,
    "admin/newsletter-send.jsp": 1,
    "admin/product-categories-list.jsp": 1,
    "admin/product-form.jsp": 1,
    "admin/product-list.jsp": 1,
    "admin/sales-tax-nexus-list.jsp": 1,
    "admin/ship-order.jsp": 1,
    "admin/shipping-rates-list.jsp": 1,
    "admin/sub-folder-details.jsp": 1,
    "admin/theme-editor.jsp": 1,
    "admin/web-page-list-editor.jsp": 1,
    "admin/webhook-subscription-form.jsp": 1,
    "cms/album-gallery.jsp": 1,
    "cms/blog-editor.jsp": 2,
    "cms/form.jsp": 1,
    "cms/web-page-editor.jsp": 1,
    "ecommerce/add-product-sku-to-cart.jsp": 1,
    "ecommerce/add-product-to-cart.jsp": 1,
    "ecommerce/cart.jsp": 1,
    "ecommerce/customer-shipping-method-form.jsp": 1,
    "ecommerce/order-updates-form.jsp": 1,
    "ecommerce/shipping-address-form.jsp": 2,
    "items/approve-item-button.jsp": 1,
    "items/hide-item-button.jsp": 1,
    "items/item-full-form.jsp": 1,
    "items/item-member-form.jsp": 1,
    "items/item-relationship-form.jsp": 1,
    "userProfile/my-email-preferences.jsp": 1,
    "userProfile/my-profile-form.jsp": 1,
}


def _blank(match: "re.Match[str]") -> str:
    """Replace a matched region with spaces, preserving newlines so that blanking
    comments and scripts never shifts the line numbers of later code."""
    return re.sub(r"[^\n]", " ", match.group(0))


JSP_COMMENT = re.compile(r"<%--.*?--%>", re.DOTALL)
HTML_COMMENT = re.compile(r"<!--.*?-->", re.DOTALL)
SCRIPT_BLOCK = re.compile(r"<script\b.*?</script\s*>", re.DOTALL | re.IGNORECASE)


def strip_noise(text: str) -> str:
    text = JSP_COMMENT.sub(_blank, text)
    text = HTML_COMMENT.sub(_blank, text)
    text = SCRIPT_BLOCK.sub(_blank, text)
    return text


def scan(path: str) -> "list[tuple[int, str]]":
    with open(path, encoding="utf-8", errors="replace") as fh:
        text = fh.read()
    hits: "list[tuple[int, str]]" = []
    for lineno, line in enumerate(strip_noise(text).splitlines(), start=1):
        for m in HANDLER.finditer(line):
            hits.append((lineno, m.group(0).strip().rstrip("=\"' ")))
    return hits


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("root", nargs="?", default=".")
    ap.add_argument("--strict", action="store_true", default=os.environ.get("STRICT") == "1")
    args = ap.parse_args()

    base = os.path.join(args.root, JSP_ROOT)
    if not os.path.isdir(base):
        sys.exit("error: %s not found (run from the repository root)" % base)

    counts: "dict[str, list[tuple[int, str]]]" = {}
    scanned = 0
    for dirpath, _, files in os.walk(base):
        for name in sorted(files):
            if not (name.endswith(".jsp") or name.endswith(".jspf")):
                continue
            scanned += 1
            path = os.path.join(dirpath, name)
            rel = os.path.relpath(path, base).replace(os.sep, "/")
            hits = scan(path)
            if hits:
                counts[rel] = hits

    # Guard against a path/regex change silently turning this into a no-op.
    if scanned < 50:
        sys.exit("error: scanned only %d JSP files under %s -- check the path" % (scanned, JSP_ROOT))

    total = sum(len(h) for h in counts.values())
    print("Inline event-handler (on*=) report  (%d files under %s)" % (scanned, JSP_ROOT))
    print("=" * 72)
    print("Live inline handlers: %d across %d files.  Allowlisted debt: %d files.\n"
          % (total, len(counts), len(ALLOWLIST)))

    violations = []   # (rel, count, allowed, hits)
    for rel in sorted(counts):
        hits = counts[rel]
        allowed = ALLOWLIST.get(rel, 0)
        if len(hits) > allowed:
            violations.append((rel, len(hits), allowed, hits))

    # Allowlist entries whose file has fewer (or zero) handlers than recorded --
    # someone converted without trimming the allowlist. Not a failure; a nudge.
    stale = [(rel, n, len(counts.get(rel, [])))
             for rel, n in sorted(ALLOWLIST.items()) if len(counts.get(rel, [])) < n]

    if violations:
        print("NEW / OVER-BUDGET inline handlers -- dead under the CSP (issue #1188):")
        print("Convert to addEventListener in a nonce'd <script>, or (for confirm-and-POST")
        print("action links) use data-confirm-post + data-post-url bound in main.jsp.\n")
        for rel, n, allowed, hits in violations:
            note = "not allowlisted" if allowed == 0 else ("allowlisted for %d" % allowed)
            print("  %-56s %d handler(s), %s:" % (rel, n, note))
            for lineno, attr in hits:
                print("      :%-5d %s=" % (lineno, attr))
        print()
    else:
        print("No new or over-budget inline handlers.\n")

    if stale:
        print("Allowlist entries to trim (file now has fewer handlers than recorded):")
        for rel, recorded, actual in stale:
            print("  %-56s recorded %d, now %d" % (rel, recorded, actual))
        print()

    if args.strict and violations:
        print("FAIL: %d file(s) carry inline event handlers that are not accounted for."
              % len(violations))
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
