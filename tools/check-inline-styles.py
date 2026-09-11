#!/usr/bin/env python3
"""Fail when a JSP gains an inline ``style="..."`` attribute (issue #1999).

The enforced Content-Security-Policy carries ``style-src 'self' 'unsafe-inline'``, which
SecurityScorecard reports as "Content Security Policy Contains 'unsafe-*' Directive". It
cannot be removed while the page renders inline style attributes, and it cannot be worked
around: a CSP nonce applies to a ``<style>`` element, never to a ``style=`` attribute, and
the only other way to permit attributes is ``'unsafe-hashes'`` -- itself an unsafe-* keyword.
So clearing the finding means the page emits none.

351 existed across 122 templates when this was added. Migrating them is slow, and without a gate new ones
arrive faster than old ones leave. This records today's count per file as a BACKLOG that may
only shrink: a file that gains an inline style fails; a file that loses one is reported so the
number is lowered in the same PR. Same shape as ``check-icon-link-names.py``, whose JSP
neutraliser this reuses -- so commented-out markup is not counted, and line numbers stay true.

When replacing an attribute, keep its precedence. An inline style outranks every selector, so a
plain class carrying the same declaration can lose to an existing rule it used to beat -- e.g.
``style="margin-bottom:0"`` beats ``.callout p { margin-bottom: 1rem }``, and ``.u-mb-0`` does
not. Utilities that replace inline styles therefore declare ``!important``.

What this does not cover
------------------------
Templates are one of three sources, and CSP is page-wide, so this reaching zero is necessary
but not sufficient to drop ``'unsafe-inline'``:

  * editor content -- the HTML sanitizer permits ``style`` on span and h1-h4
    (``HtmlCommand``), and that content renders on the same page as the template;
  * script -- markup built as a string (``innerHTML``, ``$('<div style=...>')``) or
    ``setAttribute('style', ...)`` is refused the same way. ``<script>`` bodies are blanked
    before scanning, so none of it is counted here; a report-only trial of the stricter
    policy is what finds it. (``el.style.x = ...`` is CSSOM, not an attribute, and is allowed.)

Email templates (``WEB-INF/email-templates``) are out of scope on purpose: mail clients strip
``<style>`` and need inline styles, and no email is rendered under the page's CSP.

Exit codes: 0 = within the backlog (or report-only), 1 = a file over its backlog under
--strict, 2 = the template tree is missing or too small to be the real one. See issue #1999.

@author elizabeth houser
"""

import argparse
import importlib.util
import os
import re
import sys

JSP_ROOT = os.path.join("src", "main", "webapp", "WEB-INF", "jsp")
# Fewer than this means the path or the walk is wrong, not that the tree is clean.
MIN_FILES = 50
STYLE_ATTR_RE = re.compile(r"""\sstyle\s*=\s*["']""", re.I)
_ICON_GATE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "check-icon-link-names.py")

# May only go down. Recorded 2026-09-10 at 351 across 122 templates; lowered the same day to
# 228 across 89 when the spacing-only attributes became u-* classes, then to 135 across 61 when
# the sizing, position, overflow and text ones did, then to 98 across 54 when layout and category
# styles moved to nonced head rules, then to 67 across 35 when the rest of the computed values did.
# Paths are relative to src/main/webapp/WEB-INF/jsp.
BACKLOG = {
    "admin/audit-log-list.jsp": 1,
    "admin/blog-post-list.jsp": 1,
    "admin/calendar-event-list.jsp": 1,
    "admin/folder-files-list.jsp": 1,
    "admin/image-browser.jsp": 12,
    "admin/items-list.jsp": 1,
    "admin/newsletter-send.jsp": 1,
    "admin/site-properties-editor.jsp": 1,
    "admin/site-stats-alert-card.jsp": 1,
    "admin/site-stats-recent-actions.jsp": 1,
    "admin/sitemap.jsp": 4,
    "admin/user-form.jsp": 2,
    "admin/users-list.jsp": 2,
    "admin/web-page-list.jsp": 1,
    "admin/web-vitals.jsp": 1,
    "calendar/calendar-search-results.jsp": 1,
    "calendar/full-calendar.jsp": 3,
    "calendar/small-calendar.jsp": 1,
    "cms/form.jsp": 1,
    "cms/image-browser.jsp": 2,
    "cms/table-widget.jsp": 1,
    "cms/web-page-search-results.jsp": 1,
    "cms/wiki-search-results-list.jsp": 1,
    "ecommerce/cart.jsp": 2,
    "ecommerce/customer-payment-form.jsp": 1,
    "ecommerce/shipping-address-form.jsp": 2,
    "items/delete-item-button.jsp": 1,
    "items/item-full-form.jsp": 3,
    "items/item-job-form.jsp": 1,
    "items/items-integrated-search-results-list.jsp": 1,
    "layout-header-standard.jspf": 1,
    "main.jsp": 4,
    "userProfile/my-email-preferences.jsp": 2,
    "userProfile/my-profile-form.jsp": 1,
    "visual-editor/media-library-panel.jsp": 6,
}


def _blank_out():
    spec = importlib.util.spec_from_file_location("_icon_gate", _ICON_GATE)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.blank_out


def scan_tree(root):
    """Return (files scanned, {path relative to WEB-INF/jsp: [line numbers]})."""
    blank_out = _blank_out()
    base = os.path.join(root, JSP_ROOT)
    found = {}
    scanned = 0
    for dirpath, _dirs, files in os.walk(base):
        for name in sorted(files):
            if not name.endswith((".jsp", ".jspf")):
                continue
            scanned += 1
            path = os.path.join(dirpath, name)
            with open(path, encoding="utf-8", errors="replace") as handle:
                text = blank_out(handle.read())
            lines = [text[:m.start()].count("\n") + 1 for m in STYLE_ATTR_RE.finditer(text)]
            if lines:
                found[os.path.relpath(path, base).replace(os.sep, "/")] = lines
    return scanned, found


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--strict", action="store_true", default=os.environ.get("STRICT") == "1",
                        help="exit 1 on any finding (CI runs with this)")
    args = parser.parse_args(argv)

    if not os.path.isdir(os.path.join(args.root, JSP_ROOT)):
        print("MISSING  %s (run from the repository root)" % JSP_ROOT, file=sys.stderr)
        return 2

    scanned, found = scan_tree(args.root)
    if scanned < MIN_FILES:
        print("MISSING  scanned only %d templates under %s -- check the path" % (scanned, JSP_ROOT),
              file=sys.stderr)
        return 2
    over = {rel: hits for rel, hits in found.items() if len(hits) > BACKLOG.get(rel, 0)}
    under = [(rel, was, len(found.get(rel, [])))
             for rel, was in sorted(BACKLOG.items()) if len(found.get(rel, [])) < was]

    if over:
        total = sum(len(h) - BACKLOG.get(r, 0) for r, h in over.items())
        print("FAIL  %d new inline style attribute(s), in %d file(s)" % (total, len(over)))
        for rel, hits in sorted(over.items()):
            allowed = BACKLOG.get(rel, 0)
            suffix = "  (backlog allows %d)" % allowed if allowed else ""
            print("  jsp/%s: line%s %s%s" % (rel, "" if len(hits) == 1 else "s",
                                         ", ".join(str(n) for n in hits), suffix))
        print()
        print("Move the declaration into a stylesheet. If it replaces an inline style, keep its")
        print("precedence with !important -- a plain class can lose to a rule the attribute beat.")
        print("A value computed at render time can move into a rule in a nonced <style> element,")
        print("validated first as LogoWidget.cssLength() does for logo.jsp. See issue #1999.")
        return 1 if args.strict else 0

    if under:
        print("NOTE  the backlog is now ahead of the recorded counts -- lower BACKLOG in %s"
              % os.path.basename(__file__))
        for rel, was, now in under:
            print("  jsp/%s: %d -> %d" % (rel, was, now))

    remaining = sum(len(h) for h in found.values())
    print("OK  no new inline style attributes (%d remain in the backlog, across %d files)"
          % (remaining, len(found)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
