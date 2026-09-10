#!/usr/bin/env python3
"""Report <widget> blocks in the layout XML that define the same preference tag twice.

Background
----------
XMLContainerCommands.addWidgetPreferences() builds a widget's preference map by
walking its direct child elements in document order and calling
``preferenceMap.put(childName, value)`` for each one. ``Map.put`` has no duplicate-key
detection: if a ``<widget>`` block in a ``*-layout.xml`` file accidentally contains the
same tag twice (e.g. two ``<title>`` tags), the second occurrence silently wins and the
first is lost -- not at parse time, not at JSP-compile time, not at test time.

This happened for real: PR #578 added two new admin-dashboard tiles by adding their
icon/title/report tags into an EXISTING ``<widget>`` block instead of giving each tile
its own ``<column>``, so the new tags collided with the pre-existing ones of the same
name. Both tiles were live in the XML and never rendered -- caught only by a manual
browser check, not by CI (fixed narrowly in PR #650, which splits the block in two).

What it does
------------
Parses every ``src/main/webapp/WEB-INF/web-layouts/**/*-layout.xml`` file and, for each
``<widget>`` block, counts direct child tags that would actually reach a preference
``put`` -- i.e. a simple element with non-blank text content, or a container element
(like ``<fields>``, ``<links>``, ``<options>``, ``<tabs>``) whose sub-elements carry at
least one non-blank attribute, which is how ``addWidgetPreferences`` serializes it.
Any tag name that would do this more than once in the same block is reported.

This deliberately does NOT flag repeated elements one level deeper (e.g. many
``<field>`` entries inside one ``<fields>`` container) -- that nesting is the
intentional, separate mechanism this same method uses for genuinely repeatable
structured data. A full scan of every existing layout file (468 ``<widget>`` blocks
across 18 files, as of this writing) found exactly one pre-existing instance -- the
still-open #578/#562 bug this script was written to catch -- and confirmed no widget
relies on a repeated direct-child tag for a legitimate reason, so this check has no
known false positive to special-case around.

Modes
-----
Default is REPORT-ONLY: it prints findings and exits 0. Pass ``--strict`` (or set
``STRICT=1``) to exit 1 when any widget has a duplicate tag, or a layout file fails to
parse.

Exit codes: 0 = no duplicates (or report-only), 1 = a problem found under --strict,
2 = bad usage, or the layout directory is missing.

This is a read-only reporter. It changes no files.
"""
from __future__ import annotations

import argparse
import glob
import os
import sys
import xml.etree.ElementTree as ET

LAYOUT_ROOT = "src/main/webapp/WEB-INF/web-layouts"


def fail(message: str) -> "NoReturn":
    """Exit 2: this check could not find what it measures.

    Distinct from exit 1 (a real finding) on purpose -- a gate whose layout directory
    has been renamed out from under it must be visibly broken rather than read as a
    duplicate-tag finding (or, worse, as a clean run).
    """
    print("error: " + message, file=sys.stderr)
    sys.exit(2)


def widget_label(widget: ET.Element) -> str:
    return widget.get("name") or widget.get("id") or "(unnamed)"


def reaches_put(child: ET.Element) -> bool:
    """Mirror addWidgetPreferences: would this element, on its own, actually
    produce a preferenceMap.put() call?"""
    text = "".join(child.itertext()).strip()
    if text:
        return True
    if len(list(child)) == 0:
        return False
    for sub in child:
        if not isinstance(sub.tag, str):
            continue
        if any(value.strip() for value in sub.attrib.values()):
            return True
    return False


def duplicate_tags(widget: ET.Element) -> dict[str, int]:
    """Return {tag: count} restricted to direct-child tags of one <widget> that
    would each independently reach preferenceMap.put(), counted 2 or more times."""
    counts: dict[str, int] = {}
    for child in widget:
        if not isinstance(child.tag, str):
            continue
        if not reaches_put(child):
            continue
        counts[child.tag] = counts.get(child.tag, 0) + 1
    return {tag: n for tag, n in counts.items() if n > 1}


def scan(path: str) -> list[tuple[int, str, str, int]]:
    """Return (widget ordinal, widget label, tag, count) for every duplicate in
    one file. The ordinal (position among <widget> elements in document order)
    disambiguates widgets that share a name -- common in this XML, since many
    unrelated tiles are all named e.g. "siteStats" -- so two distinct widgets
    are never miscounted as one."""
    findings = []
    tree = ET.parse(path)
    for ordinal, widget in enumerate(tree.getroot().iter("widget")):
        for tag, count in sorted(duplicate_tags(widget).items()):
            findings.append((ordinal, widget_label(widget), tag, count))
    return findings


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                  formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("root", nargs="?", default=".")
    ap.add_argument("--strict", action="store_true",
                    default=os.environ.get("STRICT") == "1")
    args = ap.parse_args()

    base = os.path.join(args.root, LAYOUT_ROOT)
    if not os.path.isdir(base):
        fail("%s not found (run from the repository root)" % base)

    pattern = os.path.join(base, "**", "*-layout.xml")
    files = sorted(glob.glob(pattern, recursive=True))

    bad: list[tuple[str, int, str, str, int]] = []
    parse_errors: list[tuple[str, str]] = []
    for path in files:
        rel = os.path.relpath(path, args.root)
        try:
            for ordinal, label, tag, count in scan(path):
                bad.append((rel, ordinal, label, tag, count))
        except ET.ParseError as exc:
            parse_errors.append((rel, str(exc)))

    lines = ["Duplicate widget-preference tag check (%d layout files)" % len(files), ""]
    if parse_errors:
        for rel, err in parse_errors:
            lines.append("  PARSE ERROR  %s: %s" % (rel, err))
        lines.append("")
    if bad:
        for rel, _ordinal, label, tag, count in bad:
            lines.append("  DUPLICATE  %s  widget=%s  <%s> appears %d times" % (rel, label, tag, count))
        lines.append("")
        distinct_widgets = len({(rel, ordinal) for rel, ordinal, _, _, _ in bad})
        lines.append("Summary: %d duplicate tag(s) across %d widget(s)." % (len(bad), distinct_widgets))
    else:
        lines.append("Summary: no duplicate direct-child preference tags found.")
    report = "\n".join(lines)
    print(report)

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a") as fh:
            fh.write("## Duplicate widget-preference tags\n\n")
            if bad:
                fh.write("**%d duplicate tag(s) found.**\n\n" % len(bad))
                fh.write("| File | Widget | Tag | Count |\n|---|---|---|---|\n")
                for rel, _ordinal, label, tag, count in bad:
                    fh.write("| `%s` | %s | `%s` | %d |\n" % (rel, label, tag, count))
            else:
                fh.write("No duplicate direct-child preference tags found.\n")

    if args.strict and (bad or parse_errors):
        print()
        print("FAIL: a <widget> block defines the same preference tag more than once.")
        print("The second occurrence silently overwrites the first at render time --")
        print("give the new content its own <column>/<widget> block instead of adding")
        print("a same-named tag into an existing one.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
