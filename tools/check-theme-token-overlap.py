#!/usr/bin/env python3
"""Report where the runtime theme and the token layer paint the same thing.

Background
----------
``check-css-token-adoption.py`` counts hex literals in first-party stylesheets
and ratchets that number down. That is one of three ways a component can miss
the token layer, and it was the only one with a gate (issue #1598).

The uncounted one this script covers: ``main.jsp`` emits the site's ``theme.*``
properties as CSS rules at runtime. Those rules can paint the same property, on
the same component, as a rule in a first-party stylesheet that uses a token.
Which one wins is then decided by specificity and source order rather than by
anyone's decision -- and neither file shows the collision, because the theme
rule lives in a JSP and the stylesheet rule lives in CSS.

That is not hypothetical. Twenty-two theme rules reached the admin console --
every button variant with its computed contrasting ink, both callout variants,
the top bar -- putting a token-driven background under theme-driven components
(#1587, then #1594). The adoption count did not move across three merged PRs
(170 before, 170 after) while the incoherence on screen got worse, because a
``var()`` pointing at a runtime property contains no hex literal to count.

Both were found by looking at a screen, after shipping. This finds them before.

What it does
------------
Extracts every colour-painting rule the theme block emits, and every first-party
CSS rule that paints the same properties from a ``--sc-*`` token. Reduces both
sides to a comparable target -- narrowing pseudos like ``:where()`` and
``:not()`` are stripped, since they restrict a rule without changing what it
aims at, while state pseudos like ``:hover`` are kept, because a rest rule and a
hover rule are different claims. Reports each target/property claimed by both.

An overlap is not automatically a bug. A theme rule scoped away from the admin
console and a console-only token rule never meet, and a theme value is often
*meant* to override a token default on the public site. So this reports, and
ACCEPTED carries the ones that have been looked at, each with the reason.

Exit status is 1 under --strict when an unreviewed overlap is found.
"""
from __future__ import annotations

import argparse
import io
import os
import re
import sys
import textwrap

MAIN_JSP = "src/main/webapp/WEB-INF/jsp/main.jsp"
CSS_DIR = "src/main/webapp/css"

# Properties worth comparing. Layout is excluded deliberately: a theme sets colour, and a
# stylesheet setting padding on the same selector is not competing with it.
COLOUR_PROPS = {"color", "background", "background-color", "border", "border-color",
                "border-top-color", "border-bottom-color", "border-left-color",
                "border-right-color", "fill", "stroke", "outline-color"}

# Narrowing pseudos restrict which elements a rule reaches without changing what it aims at, so
# two rules that differ only by these are still claiming the same component.
NARROWING = re.compile(r":(?:where|not|is|has)\([^()]*(?:\([^()]*\)[^()]*)*\)")

# State pseudos are kept: a rest rule and a hover rule are different claims on the same component.
STATE = re.compile(r"(:(?:hover|focus|focus-visible|focus-within|active|visited|disabled|checked))")

GUARD_OPEN = '<c:if test="${!isAdminConsole}">'

# Overlaps that have been reviewed. Each entry is (target, property, why).
ACCEPTED = [
    ("body", "color",
     "The theme's body ink is meant to win on the public site; the console is scoped away from it "
     "by the isAdminConsole guard, and check-theme-scope.py enforces that."),
    ("body", "background-color",
     "Same as body/color -- the site's own ground on public pages, guarded away from the console."),
    ("a", "color",
     "A site sets its own link colour; body.admin-console re-points links at the token layer for "
     "the console, which is the intended split."),
    (".callout", "background-color",
     "Both layers do claim this, and that is intended: the token is the default surface and the "
     "site's theme.callout.backgroundColor is meant to override it. It did not until issue 1650 -- "
     "the token rule carried six :not() exclusions, taking it to (0,7,0) and outranking the "
     "theme's (0,1,0), so a configured callout colour was silently discarded. The exclusions are "
     "gone from the light-mode rule, which now sits at (0,1,0) and loses to the theme emitted "
     "after it, as it should."),
    (".callout.header", "color",
     "The header callout is chrome rather than page content: platform.css gives it inverse ink on "
     "a fixed dark fill, and the theme's callout ink would be unreadable on it. Deliberate, and "
     "the theme rule is guarded away from the console."),
]

# Overlaps that are NOT accepted and NOT yet fixed, recorded so the gate can go green on the state
# it was written against without pretending the finding is resolved. Each needs an issue.
KNOWN_UNFIXED = [
]


def normalise(selector: str) -> str:
    """Reduce a selector to what it aims at, so two spellings of one target compare equal."""
    s = NARROWING.sub("", selector)
    states = "".join(sorted(set(m.group(1) for m in STATE.finditer(s))))
    s = STATE.sub("", s)
    s = re.sub(r"\s+", " ", s).strip().rstrip(",").strip()
    # Sort the classes within a simple compound so .button.primary == .primary.button
    parts = s.split()
    out = []
    for part in parts:
        classes = sorted(re.findall(r"\.[A-Za-z0-9_-]+", part))
        rest = re.sub(r"\.[A-Za-z0-9_-]+", "", part)
        out.append(rest + "".join(classes))
    return (" ".join(out) + states).strip()


def props_of(declarations: str) -> set:
    """Only properties whose OWN value comes from a token.

    Matching any colour property in a rule that merely contains a token somewhere was wrong: a
    rule setting ``background-color: #353535`` beside ``color: var(--sc-text-inverse)`` is making
    a token claim on the ink and a hardcoded one on the fill, and reporting the fill as a token
    overlap is a false positive. A gate that cries wolf gets muted, and then it catches nothing.
    """
    found = set()
    for declaration in declarations.split(";"):
        if ":" not in declaration:
            continue
        name, _, value = declaration.partition(":")
        name = name.strip()
        if name in COLOUR_PROPS and "var(--sc-" in value:
            found.add(name)
    return found


def theme_claims(root: str):
    """(target, property, inside_admin_guard) for every colour rule the theme block emits."""
    path = os.path.join(root, MAIN_JSP)
    if not os.path.exists(path):
        return None
    text = io.open(path, encoding="utf-8").read()
    guard_at = text.find(GUARD_OPEN)
    claims = {}
    for match in re.finditer(r'\}">([^<]*\{[^}]*\})', text):
        rule = match.group(1)
        if "var(--sc-" not in rule:
            continue
        selector, declarations = rule.split("{", 1)
        guarded = guard_at != -1 and match.start() > guard_at
        for one in selector.split(","):
            target = normalise(one)
            if not target:
                continue
            for prop in props_of(declarations):
                claims.setdefault((target, prop), guarded)
    return claims


def token_claims(root: str):
    """(target, property) -> file, for first-party CSS rules painting from a --sc-* token."""
    css_dir = os.path.join(root, CSS_DIR)
    claims = {}
    if not os.path.isdir(css_dir):
        return claims
    for name in sorted(os.listdir(css_dir)):
        if not name.endswith(".css") or "foundation" in name or name.endswith(".min.css"):
            continue
        text = io.open(os.path.join(css_dir, name), encoding="utf-8", errors="replace").read()
        text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
        for selector, declarations in re.findall(r"([^{}]+)\{([^}]*)\}", text):
            if "var(--sc-" not in declarations:
                continue
            for one in selector.split(","):
                target = normalise(one)
                if not target or target.startswith("@") or target.startswith(":root"):
                    continue
                for prop in props_of(declarations):
                    claims.setdefault((target, prop), name)
    return claims


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--strict", action="store_true", help="exit 1 on an unreviewed overlap")
    parser.add_argument("--verbose", action="store_true", help="list accepted overlaps too")
    args = parser.parse_args()

    theme = theme_claims(args.root)
    if theme is None:
        print("MISSING  %s" % MAIN_JSP, file=sys.stderr)
        return 1
    tokens = token_claims(args.root)

    accepted = {(t, p): why for t, p, why in ACCEPTED}
    known = {(t, p): why for t, p, why in KNOWN_UNFIXED}
    findings, reviewed, carried = [], [], []
    for (target, prop), guarded in sorted(theme.items()):
        if (target, prop) not in tokens:
            continue
        entry = (target, prop, tokens[(target, prop)], guarded)
        if (target, prop) in accepted:
            reviewed.append(entry)
        elif (target, prop) in known:
            carried.append(entry)
        else:
            findings.append(entry)

    print("theme colour claims: %d   token colour claims: %d" % (len(theme), len(tokens)))

    if args.verbose and reviewed:
        print("\nreviewed overlaps (in ACCEPTED):")
        for target, prop, where, guarded in reviewed:
            print("  %-42s %-18s %s" % (target[:42], prop, where))

    if carried:
        print("\nCARRIED  %d known overlap(s) awaiting a fix, not failing the build:" % len(carried))
        for target, prop, where, guarded in carried:
            print("  %-40s %-18s %s" % (target[:40], prop, where))
            reason = known[(target, prop)]
            for line in textwrap.wrap(reason, 92):
                print("      %s" % line)

    if not findings:
        print("\nOK  no NEW overlap between the runtime theme and the token layer")
        return 0

    print("\nFAIL  %d selector/property claimed by BOTH the runtime theme and a token rule\n"
          % len(findings))
    for target, prop, where, guarded in findings:
        scope = "guarded from console" if guarded else "REACHES THE CONSOLE"
        print("  %-40s %-18s %-26s %s" % (target[:40], prop, where, scope))
    print()
    print("Which one wins is decided by specificity and source order, not by a decision, and")
    print("neither file shows the collision. Either scope one of them away, or add it to")
    print("ACCEPTED in this script with the reason it is intended.")
    return 1 if args.strict else 0


if __name__ == "__main__":
    sys.exit(main())
