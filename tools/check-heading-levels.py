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

    findings = check_layouts(args.root, prefix, excluded) + check_admin_jsps(args.root)

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
