#!/usr/bin/env python3
"""A themed button colour must never reach a .hollow or .clear button.

``main.jsp`` emits the site's ``theme.button.*`` properties as CSS twice over: a
fill rule that paints the background, and an ink rule that picks a caption colour
the fill can actually carry. Both halves have to agree about which buttons they
are for, because a hollow button is *defined* by having no fill -- Foundation
gives it a caption that contrasts with the page behind it, not with a button
colour.

They did not agree. Every ink rule carried ``:not(.clear):not(.hollow)`` and no
fill rule did, so a hollow button took a background it was never meant to have
and was then skipped by the rule that would have given it a readable caption. It
kept Foundation's hollow ink, which is the secondary colour itself -- the same
value now sitting behind it. On the pilot the page editor's Media Library button
rendered as a blank grey slab at 1.000:1, with its label present in the DOM and
painted in exactly its own background colour (issue 1608).

The mismatch was invisible to every existing gate. ``check-token-contrast.py``
reads token *values*, and these colours arrive from runtime theme properties with
no literal to read. ``check-theme-scope.py`` asks whether a theme rule sits inside
the admin-console guard, which these all did. The half-scoped feature sat in the
file from issue 1537 until issue 1608 without moving a single number.

So: every themed button rule that paints a colour must exclude both variants on
every one of its selectors. ``:not(.clear):not(.hollow)`` and
``:where(:not(.clear):not(.hollow))`` both satisfy this -- the wrapper changes the
rule's specificity, not its scope, and which one is correct depends on the rule
(see the comment in main.jsp). Additional exclusions such as ``:not(.box)`` are
free to be there.

This is a read-only reporter. It changes no files.

Exit status is 1 under --strict when a themed button colour can reach a variant.
"""
from __future__ import annotations

import argparse
import io
import os
import re
import sys

MAIN_JSP = "src/main/webapp/WEB-INF/jsp/main.jsp"

# A <c:if> gated on a button theme property, or on one of the derived ink variables the
# block computes from them (inkPrimary, inkSuccessHover, ...). Both families paint from
# the site's configured button colours and both are in scope.
THEMED_BUTTON = re.compile(r"theme\.button\.|empty\s+ink[A-Z]")

# selector-list { property: ... } emitted inside that <c:if>
RULE = re.compile(r'">([^<{]*?)\{([^}]*)\}')

# Only rules that actually paint. A rule setting, say, border-radius from a theme property
# would be no business of this check.
PAINTS = re.compile(r"\b(color|background|background-color|border-color)\s*:")

REQUIRED = (".clear", ".hollow")


def split_selectors(selector_list: str) -> list[str]:
    """Split on top-level commas only -- a comma inside :where(...) or :not(...) is not a separator."""
    out, depth, current = [], 0, ""
    for ch in selector_list:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(current.strip())
            current = ""
        else:
            current += ch
    if current.strip():
        out.append(current.strip())
    return out


def missing_exclusions(selector: str) -> list[str]:
    """Which of .clear / .hollow this selector fails to exclude."""
    return [v for v in REQUIRED if (":not(%s)" % v) not in selector.replace(" ", "")]


def offenders(path: str) -> list[tuple[int, str, list[str]]]:
    """(line number, selector, missing exclusions) for every themed button rule that can reach a variant."""
    with io.open(path, encoding="utf-8") as handle:
        lines = handle.read().split("\n")

    found = []
    for i, line in enumerate(lines):
        if not THEMED_BUTTON.search(line):
            continue
        match = RULE.search(line)
        if not match:
            continue
        selector_list, declarations = match.group(1), match.group(2)
        if not PAINTS.search(declarations):
            continue
        for selector in split_selectors(selector_list):
            if ".button" not in selector:
                continue
            missing = missing_exclusions(selector)
            if missing:
                found.append((i + 1, selector, missing))
    return found


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--strict", action="store_true",
                        help="exit 1 on any finding (CI runs with this)")
    args = parser.parse_args()

    path = os.path.join(args.root, MAIN_JSP)
    if not os.path.exists(path):
        print("MISSING  %s" % MAIN_JSP, file=sys.stderr)
        return 1

    found = offenders(path)

    if found:
        print("FAIL  %d themed button rule selector(s) can reach a .hollow or .clear button in %s"
              % (len(found), MAIN_JSP))
        for line_no, selector, missing in found:
            print("  L%-5d %s" % (line_no, selector[:96]))
            print("         missing %s" % ", ".join(":not(%s)" % v for v in missing))
        print()
        print("A hollow or clear button draws on the page surface, not on a button fill, so a")
        print("themed colour reaching one gives it a background it has no caption for -- the")
        print("label ends up painted in its own background colour (issue 1608). Add the missing")
        print("exclusion; wrap it in :where() if the rule must keep its current specificity.")
        return 1 if args.strict else 0

    print("OK  every themed button colour is scoped away from .hollow and .clear")
    return 0


if __name__ == "__main__":
    sys.exit(main())
