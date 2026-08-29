#!/usr/bin/env python3
"""Keep the site's own theme colours out of the admin console.

``main.jsp`` emits the site's ``theme.*`` properties as CSS. Those rules paint the
public site, which is the point -- but they also reach the admin console unless
they are scoped away from it, and there they compete with the design tokens the
console is meant to use. The result is not a broken page: it is a console whose
background comes from one system and whose buttons and callouts come from
another, which reads as "the colours look wrong" and takes a screen to notice.

That is what happened. Issue 1587 scoped away three rules -- body text, body
background, links -- and left nineteen more applying to the console, so the
ground moved to the token layer and the components stayed on the site's theme.
Issue 1594 wrapped the rest.

Nothing detects this. ``check-css-token-adoption.py`` counts hex literals in
first-party stylesheets; a rule that reads a runtime theme property has no
literal in it and is invisible to that count. The adoption number was unchanged
across the whole episode (see issue 1598).

So: every rule in that block which paints a colour must sit inside the
``<c:if test="${!isAdminConsole}">`` guard. Font-family rules may sit outside --
they are typography, not palette, and the console inheriting the site's face is
a separate decision that has not been made.

The site header is the one palette exception, and it is allowed by selector
rather than by exemption comment. ``#platform-menu`` is the SITE's header and it
renders on admin pages too, so guarding its rules did not hand it to the token
layer -- there is no ``--sc-chrome-*`` rule covering those selectors. It removed a
theme and put nothing in its place: a transparent bar with Foundation's stock
blue links over the warm admin surface, beside a dark rail. A rule whose every
selector is scoped to ``#platform-menu``, ``#platform-small-menu`` or
``#platform-small-toggle-menu`` cannot reach the console's own chrome or content,
so it is safe outside the guard and is where the header's colours belong.

That is checked, not assumed: a rule listing a header selector *and* something
else -- ``.callout.header, #platform-menu button.button i.fa`` was one -- is still
a finding, because the non-header half would reach the console.

This is a read-only reporter. It changes no files.

Exit status is 1 when a colour-emitting theme rule is found outside the guard.
"""
from __future__ import annotations

import argparse
import io
import os
import re
import sys

MAIN_JSP = "src/main/webapp/WEB-INF/jsp/main.jsp"

GUARD_OPEN = '<c:if test="${!isAdminConsole}">'

# A rule reading one of the site's own theme properties
THEME_RULE = re.compile(r"themePropertyMap\[")

# ...and painting something. Two exclusions matter. font-family is deliberately absent: it is
# typography, not palette, and whether the console inherits the site's face is a separate
# decision. And a rule has to have a body -- the block also declares the custom properties
# themselves (--sc-body-text-color: #fff), which define a value without painting anything and
# are harmless outside the guard. Requiring a brace separates a rule from a declaration, and
# without it every one of those 44 declarations reads as a false positive.
PAINTS_COLOUR = re.compile(r"\{[^}]*\b(color|background|background-color|border|border-color)\s*:")

# Lines that mention a theme property without emitting a rule -- the c:set statements that
# compute a contrasting ink, for example, are inputs to a rule rather than a rule themselves.
IS_SET = re.compile(r"<c:set\b")

# The site header, which renders on admin pages as well as public ones and has no token-layer
# equivalent. A rule is exempt only when EVERY selector it lists is scoped to one of these -- a
# mixed rule still reaches the console through its other half.
HEADER_PREFIXES = ("#platform-menu", "#platform-small-menu", "#platform-small-toggle-menu")


def is_header_scoped(line: str) -> bool:
    """True when every selector in the rule is confined to the site header."""
    if '">' not in line or "{" not in line:
        return False
    selectors = line.split('">', 1)[1].split("{")[0]
    parts = [p.strip() for p in selectors.split(",") if p.strip()]
    if not parts:
        return False
    return all(any(part.startswith(prefix) for prefix in HEADER_PREFIXES) for part in parts)


def find_guard_region(lines: list[str]) -> tuple[int, int] | None:
    """The 0-based [start, end) line range inside the admin-console guard, or None."""
    start = None
    for i, line in enumerate(lines):
        if GUARD_OPEN in line:
            start = i + 1
            break
    if start is None:
        return None
    # The guard wraps a flat run of sibling <c:if> rules, so its close is the first
    # </c:if> at the guard's own indentation -- matching on depth would count every
    # nested rule's close as well.
    indent = len(lines[start - 1]) - len(lines[start - 1].lstrip())
    for j in range(start, len(lines)):
        stripped = lines[j].strip()
        if stripped == "</c:if>" and (len(lines[j]) - len(lines[j].lstrip())) == indent:
            return start, j
    return None


def offenders(path: str) -> tuple[list[tuple[int, str]], bool]:
    """Colour-emitting theme rules outside the guard, and whether a guard was found."""
    with io.open(path, encoding="utf-8") as handle:
        lines = handle.read().split("\n")

    region = find_guard_region(lines)
    if region is None:
        # No guard at all: every colour rule is unscoped.
        found = []
        for i, line in enumerate(lines):
            if (THEME_RULE.search(line) and PAINTS_COLOUR.search(line) and not IS_SET.match(line.strip())
                    and not is_header_scoped(line)):
                found.append((i + 1, line.strip()))
        return found, False

    start, end = region
    found = []
    for i, line in enumerate(lines):
        if start <= i < end:
            continue
        if (THEME_RULE.search(line) and PAINTS_COLOUR.search(line) and not IS_SET.match(line.strip())
                and not is_header_scoped(line)):
            found.append((i + 1, line.strip()))
    return found, True


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

    found, guarded = offenders(path)

    if not guarded:
        print("FAIL  no %s guard in %s -- every theme colour rule reaches the admin console"
              % (GUARD_OPEN, MAIN_JSP))
        for line_no, text in found[:10]:
            print("  L%-5d %s" % (line_no, text[:110]))
        if len(found) > 10:
            print("  ... and %d more" % (len(found) - 10))
        return 1 if args.strict else 0

    if found:
        print("FAIL  %d theme colour rule(s) outside the admin-console guard in %s"
              % (len(found), MAIN_JSP))
        for line_no, text in found:
            print("  L%-5d %s" % (line_no, text[:110]))
        print()
        print("These paint the admin console as well as the public site, where they compete")
        print("with the design tokens. Move them inside the guard, or -- if a rule genuinely")
        print("should reach the console -- say why here rather than leaving it to be rediscovered.")
        return 1 if args.strict else 0

    print("OK  every theme colour rule is scoped away from the admin console")
    return 0


if __name__ == "__main__":
    sys.exit(main())
