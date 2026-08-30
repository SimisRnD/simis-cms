#!/usr/bin/env python3
"""Keep the admin console's heading outline intact.

``main.jsp`` emits ``<h1 class="show-for-sr">`` with the page title, but only inside
the admin-console layout branch and only when the page sets ``title=``. So on an
admin page every *authored* heading already sits under an h1, and authoring an h4
there is an h1 -> h4 jump (WCAG 1.3.1). Authoring an h1 there is a second h1 on the
page.

Both happened, and neither was detected. Issue 1622 catalogued the skips from a
grep and got the scope wrong twice over -- it counted 17 sites when there were 27,
and its worked examples had already been fixed by the time anyone read it. The
duplicate h1s (issue 1660) were never catalogued at all. The reason is the same in
both cases: nothing measures this, so the only record of it is a hand-written list
that goes stale the moment anything moves. That is issue 1598's complaint in a
different costume.

What this checks, over the layout XML files that define ``/admin`` pages:

1. **No skipped levels.** Each authored heading may be at most one level deeper
   than the one before it. The first authored heading on a page that receives the
   screen-reader h1 must therefore be an h2.
2. **No authored h1** on a page that already receives the screen-reader h1.
3. **No ``<h1>`` in a widget JSP under ``jsp/admin/``**, since those render inside
   the branch that already emitted one.

The fix in every case is the same, and it costs no CSS: move the tag for structure
and pin the appearance with the matching ``.hN`` utility class. ``platform.css`` and
Foundation both define heading sizes as paired ``hN, .hN`` selectors, so the class
carries declarations identical to the tag at higher specificity -- ``<h2 class="h4">``
renders exactly as the h4 did. That is the pattern issue 1511 established for widget
titles and PRs 1659/1661 used here.

Two things are deliberately NOT findings:

- **Heading specimens.** ``/admin/theme-properties`` renders one heading of each
  level on purpose, to show what the theme does to them. Retagging those would
  destroy the thing the page exists to demonstrate.
- **JSPs that render outside the admin branch.** ``image-browser.jsp`` lives under
  ``jsp/admin/`` but renders on ``/image-browser``, which is defined in
  ``cms-layout.xml``. No screen-reader h1 is emitted there, so its h1 is the page's
  only one and is correct. This exact file was wrongly reported as a defect on the
  first pass of issue 1660.

The routes excluded from the admin branch are read out of ``main.jsp`` rather than
hardcoded here, so the two cannot drift apart.

This is a read-only reporter. It changes no files.

Exit status is 1 under --strict when a finding is reported.
"""
from __future__ import annotations

import argparse
import io
import os
import re
import sys

MAIN_JSP = "src/main/webapp/WEB-INF/jsp/main.jsp"
LAYOUT_DIR = "src/main/webapp/WEB-INF/web-layouts/page"
ADMIN_JSP_DIR = "src/main/webapp/WEB-INF/jsp/admin"

# The line in main.jsp that selects the admin-console layout. Its route literals are the
# branch's definition; parsing them keeps this tool honest if that condition is edited.
ADMIN_BRANCH_MARKER = "fn:startsWith(pageRenderInfo.name, '/admin')"

HEADING = re.compile(r"<h([1-6])[^>]*>(.*?)</h([1-6])>", re.DOTALL)
PAGE = re.compile(r"<page\s+[^>]*name=\"([^\"]*)\"[^>]*>")
TITLE_ATTR = re.compile(r"\btitle=\"([^\"]*)\"")
TAGS = re.compile(r"<[^>]+>")

# A page that renders one heading of each level to demonstrate them. Matching on the text
# rather than the page name means a second specimen page is covered without an edit here.
SPECIMEN = re.compile(r"^H[1-6] Header$")

# Renders on /image-browser (cms-layout.xml), outside the admin branch, so no screen-reader
# h1 precedes it and its own h1 is correct. See the module docstring.
JSP_H1_ALLOWED = {
    "image-browser.jsp": "renders on /image-browser (cms-layout.xml), outside the admin branch",
    # A heading specimen in the CSS editor's preview pane, and /admin/css-editor is one of the
    # routes excluded from the admin branch anyway.
    "css-editor.jsp": "heading specimen in the CSS editor preview",
}

# Recorded level skips per admin widget JSP -- a RATCHET, not a target.
#
# Every admin page already carries the screen-reader h1, so a widget's first heading should be an
# h2 and nothing below it should jump a level. 60 sites do, in 58 files, and they are not one
# problem: the largest family by far is a help block ("What this page shows", "Common problems and
# how to fix them") authored at h5 or h6 directly under an h2, and the rest are first headings that
# start at h3 or h4.
#
# Fixing all 60 at once is exactly the sweep issue 1622 warns against, so they are recorded here
# instead and the count may only shrink. A file whose count goes UP fails; a file absent from this
# map may have no skips at all. Same shape as check-inline-handlers.py's ALLOWLIST.
#
# Counts BELOW the recorded number are reported, not failed -- PRs 1663 and 1664 lower several of
# these, and a gate that failed on an improvement would make merge order load-bearing. Lower the
# number here when that happens; the tool prints the exact line to change.
JSP_SKIP_BASELINE = {
    "add-tracking-number.jsp": 1, "allowed-ip-list.jsp": 1, "analytics-retention.jsp": 1,
    "apis-list.jsp": 1, "apps-list.jsp": 1, "audit-log-list.jsp": 1, "blocked-ip-list.jsp": 1,
    "blog-form.jsp": 1, "blog-list.jsp": 1, "bot-list.jsp": 1, "cache-management.jsp": 1,
    "calendar-form.jsp": 1, "calendar-list.jsp": 1, "capability-grants.jsp": 1,
    "collection-form.jsp": 1, "content-list.jsp": 1, "content-versions-list.jsp": 1,
    "custom-fields-form-json.jsp": 1, "database-maintenance.jsp": 2, "dataset-schema.jsp": 1,
    "editorial-calendar.jsp": 1, "folder-file-drop-zone.jsp": 1, "folder-file-form.jsp": 1,
    "folder-form.jsp": 1, "form-field-form.jsp": 1, "forms.jsp": 1, "groups-list.jsp": 1,
    "health-dashboard.jsp": 1, "image-browser.jsp": 1, "integration-registry.jsp": 1,
    "job-queue-dashboard.jsp": 1, "mailing-list-members.jsp": 1, "mfa-enrolled-roles.jsp": 1,
    "newsletter-send.jsp": 1, "page-template-gallery.jsp": 1, "pricing-rule-form.jsp": 1,
    "product-category-form.jsp": 1, "product-form.jsp": 1, "role-capabilities-form.jsp": 1,
    "sales-tax-nexus-address-form.jsp": 1, "seo-overview.jsp": 1, "seo-sitemap.jsp": 1,
    "shipping-rate-form.jsp": 1, "site-properties-editor.jsp": 1, "sitemap-editor.jsp": 1,
    "sitemap.jsp": 1, "sub-folder-form.jsp": 1, "theme-editor.jsp": 1, "user-details.jsp": 2,
    "user-form.jsp": 1, "users-list.jsp": 1, "web-page-list.jsp": 1, "web-redirect-form.jsp": 1,
    "web-redirects-list.jsp": 1, "webhook-deliveries-list.jsp": 1,
    "webhook-subscription-form.jsp": 1, "wiki-form.jsp": 1, "wiki-page-list.jsp": 1,
}

# Blanked before headings are counted, all preserving line numbers. Without the script and HTML
# comment passes, a JS string that builds markup reads as a heading: photo-gallery.jsp does
# innerHTML = '<h4>' + data.title + '</h4>', and web-page-designer.jsp assembles the designer's
# widget prototype the same way. Both looked like real findings until they were read.
JSP_COMMENT = re.compile(r"<%--.*?--%>", re.S)
SCRIPT_BLOCK = re.compile(r"<script\b.*?</script>", re.S | re.I)
HTML_COMMENT = re.compile(r"<!--.*?-->", re.S)
HEADING_PAIR = re.compile(r"<(h[1-6])\b([^>]*)>(.*?)</\1>", re.S)
STRIP_TAGS = re.compile(r"<[^>]+>")
REVEAL_CLASS = re.compile(r"class=\"[^\"]*\breveal\b[^\"]*\"")


def admin_branch_routes(root: str) -> tuple[str, list[str]]:
    """('/admin', [routes excluded from the admin-console branch]) read from main.jsp."""
    path = os.path.join(root, MAIN_JSP)
    with io.open(path, encoding="utf-8") as handle:
        for line in handle:
            if ADMIN_BRANCH_MARKER in line:
                routes = re.findall(r"'(/[^']*)'", line)
                if routes:
                    return routes[0], routes[1:]
    raise LookupError(
        "could not find the admin-console branch condition in %s -- this tool reads the "
        "excluded routes from it, so it cannot report accurately until that is fixed" % MAIN_JSP)


def line_of(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def gets_screen_reader_h1(name: str, title: str | None, prefix: str, excluded: list[str]) -> bool:
    """Whether main.jsp emits its sr-only h1 for this page."""
    # Route names may carry a query template, e.g. /admin/order-details{?order-number}
    base = name.split("{", 1)[0]
    if not base.startswith(prefix):
        return False
    if base in excluded:
        return False
    return bool(title)


def headings_by_page(text: str):
    """[(page_name, title, [(level, text, line)])] in document order."""
    pages = [(m.start(), m.group(1), (TITLE_ATTR.search(m.group(0)) or [None, None])[1])
             for m in PAGE.finditer(text)]
    if not pages:
        return []
    starts = [p[0] for p in pages]
    buckets: list[list] = [[] for _ in pages]
    for m in HEADING.finditer(text):
        if m.group(1) != m.group(3):
            continue  # mismatched tags are a different problem; check-xml-well-formed owns it
        idx = -1
        for i, s in enumerate(starts):
            if s < m.start():
                idx = i
            else:
                break
        if idx < 0:
            continue
        label = TAGS.sub("", m.group(2)).strip()
        buckets[idx].append((int(m.group(1)), label, line_of(text, m.start())))
    return [(pages[i][1], pages[i][2], buckets[i]) for i in range(len(pages))]


def check_layouts(root: str, prefix: str, excluded: list[str]) -> list[str]:
    findings = []
    layout_dir = os.path.join(root, LAYOUT_DIR)
    if not os.path.isdir(layout_dir):
        return findings
    for fname in sorted(os.listdir(layout_dir)):
        if not fname.endswith(".xml"):
            continue
        rel = os.path.join(LAYOUT_DIR, fname)
        with io.open(os.path.join(layout_dir, fname), encoding="utf-8") as handle:
            text = handle.read()
        for name, title, headings in headings_by_page(text):
            if not headings:
                continue
            if not gets_screen_reader_h1(name, title, prefix, excluded):
                continue
            previous = 1  # the screen-reader h1
            for level, label, line in headings:
                if SPECIMEN.match(label):
                    continue
                if level == 1:
                    findings.append(
                        "%s:%d  %s authors an h1, but already receives the screen-reader h1 "
                        "(title=\"%s\") -- \"%s\"" % (rel, line, name, title, label[:48]))
                elif level > previous + 1:
                    findings.append(
                        "%s:%d  %s jumps h%d -> h%d -- \"%s\""
                        % (rel, line, name, previous, level, label[:48]))
                previous = level
    return findings


def check_admin_jsps(root: str) -> list[str]:
    findings = []
    jsp_dir = os.path.join(root, ADMIN_JSP_DIR)
    if not os.path.isdir(jsp_dir):
        return findings
    for fname in sorted(os.listdir(jsp_dir)):
        if not fname.endswith(".jsp") or fname in JSP_H1_ALLOWED:
            continue
        rel = os.path.join(ADMIN_JSP_DIR, fname)
        with io.open(os.path.join(jsp_dir, fname), encoding="utf-8") as handle:
            for n, line in enumerate(handle, 1):
                if re.search(r"<h1[ >]", line):
                    findings.append(
                        "%s:%d  widget JSP emits an h1, but it renders inside the admin branch "
                        "which already emitted one" % (rel, n))
    return findings


def blank_preserving_lines(match) -> str:
    return re.sub(r"[^\n]", " ", match.group(0))


def jsp_skips(path: str) -> list[tuple[int, int, int, str]]:
    """Level skips in one JSP as (line, from_level, to_level, label).

    A widget renders under the page's h1, so the running level starts at 1 and each heading may
    be at most one deeper than the last. Headings inside a wired dialog are excluded (a closed
    Reveal is display:none and an open one is scoped by aria-modal), as are the H1..H5 Header
    specimens.
    """
    with io.open(path, encoding="utf-8", errors="replace") as handle:
        text = handle.read()
    for pattern in (JSP_COMMENT, SCRIPT_BLOCK, HTML_COMMENT):
        text = pattern.sub(blank_preserving_lines, text)

    found = []
    previous = 1
    for m in HEADING_PAIR.finditer(text):
        before = text[max(0, m.start() - 500):m.start()]
        if REVEAL_CLASS.search(before) and 'role="dialog"' in before:
            continue
        label = STRIP_TAGS.sub("", m.group(3)).strip()
        if SPECIMEN.match(label):
            continue
        level = int(m.group(1)[1])
        if level > previous + 1:
            found.append((text.count("\n", 0, m.start()) + 1, previous, level, label))
        previous = level
    return found


def check_admin_jsp_levels(root: str) -> tuple[list[str], list[str]]:
    """(failures, notes) -- a count above its baseline fails; below it is only a note."""
    failures, notes = [], []
    jsp_dir = os.path.join(root, ADMIN_JSP_DIR)
    if not os.path.isdir(jsp_dir):
        return failures, notes
    for fname in sorted(os.listdir(jsp_dir)):
        if not fname.endswith(".jsp"):
            continue
        skips = jsp_skips(os.path.join(jsp_dir, fname))
        allowed = JSP_SKIP_BASELINE.get(fname, 0)
        if len(skips) > allowed:
            rel = os.path.join(ADMIN_JSP_DIR, fname)
            for line, frm, to, label in skips[allowed:]:
                failures.append("%s:%d  jumps h%d -> h%d -- \"%s\"" % (rel, line, frm, to, label[:44]))
        elif len(skips) < allowed:
            notes.append("%s is down to %d (baseline says %d) -- lower it: \"%s\": %d,"
                         % (fname, len(skips), allowed, fname, len(skips)))
    return failures, notes


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--strict", action="store_true",
                        help="exit 1 on any finding (CI runs with this)")
    args = parser.parse_args()

    if not os.path.exists(os.path.join(args.root, MAIN_JSP)):
        print("MISSING  %s" % MAIN_JSP, file=sys.stderr)
        return 1

    try:
        prefix, excluded = admin_branch_routes(args.root)
    except LookupError as exc:
        print("FAIL  %s" % exc)
        return 1 if args.strict else 0

    level_failures, level_notes = check_admin_jsp_levels(args.root)
    findings = (check_layouts(args.root, prefix, excluded) + check_admin_jsps(args.root)
                + level_failures)

    for note in level_notes:
        print("NOTE  " + note)
    if level_notes:
        print()

    if findings:
        print("FAIL  %d admin heading problem(s)" % len(findings))
        for f in findings:
            print("  " + f)
        print()
        print("Move the tag for structure and pin the appearance with the matching .hN utility")
        print("class -- <h2 class=\"h4\"> renders exactly as the h4 did, because platform.css and")
        print("Foundation both define heading sizes as paired 'hN, .hN' selectors. No CSS change.")
        return 1 if args.strict else 0

    print("OK  admin pages start at h2, skip no levels, and author no second h1")
    return 0


if __name__ == "__main__":
    sys.exit(main())
