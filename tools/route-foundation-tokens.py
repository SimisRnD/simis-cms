#!/usr/bin/env python3
"""Generate a token-routed copy of Foundation's stylesheet from the vendored original.

Background
----------
Foundation is vendored here as compiled CSS only -- there is no Sass source and
no ``_settings.scss`` -- so its palette is baked into the shipped file. That is
why the admin still renders in Foundation's factory blue (``#1779ba``): nothing
in those rules asks what the site's theme is. Measured on 6.8.1, that file
carries 351 hardcoded colour declarations.

Recompiling from Sass would fix the palette but not the problem: Sass variables
resolve at build time, so it yields exactly one baked palette. The product needs
runtime switching (light/dark today, named themes later), which is what CSS
custom properties give. So instead of recompiling, this rewrites the compiled
file's palette to ``var(--sc-fnd-*)`` references.

Only the ten *base* colours are routed -- Foundation's ``$white``, ``$black``,
``$primary``, ``$secondary``, ``$success``, ``$warning``, ``$alert`` and its
three greys. Those cover 285 of the 351 declarations (81%). The remaining 66 are
Sass-computed darkenings used for hover and active states; they are deliberately
left alone for now rather than approximated, so this transform stays provably
faithful. Deriving them at runtime with ``color-mix()`` is a follow-up.

Faithfulness
------------
Each replacement keeps the original colour as the ``var()`` fallback, and the
tokens ship initialised to Foundation's own values. The generated stylesheet
therefore renders **identically** to the original until someone deliberately
changes a token. That is the point: this change moves no pixels, it only makes
the pixels addressable.

A hex inside a ``url()`` value is never replaced -- an inline SVG data URI would
be corrupted by it. 6.8.1 happens to contain none, but a future upgrade might.

Modes
-----
Default regenerates the output file. ``--check`` verifies the committed output
matches what this script produces from the current vendored input and exits 1 on
any difference, so CI catches a hand-edited generated file or a re-vendored
Foundation that was not regenerated.
"""

import argparse
import re
import sys
from pathlib import Path

VENDOR_DIR = "src/main/webapp/css/foundation-6.8.1"
SOURCE = f"{VENDOR_DIR}/foundation.min.css"
GENERATED = f"{VENDOR_DIR}/foundation.tokens.min.css"

# Foundation's base palette -> token name. Foundation uses one Sass variable per
# colour across every role it plays (its $white is both a surface and inverse
# text), so a 1:1 colour-to-token mapping reproduces its own semantics exactly.
# Foundation's base palette -> token.
#
# Most colours keep a single token: Foundation uses one Sass variable per colour
# across every role it plays, and for an accent or a status colour that is right --
# a themed alert is one colour whether it is painting text, a fill or a border.
#
# Two colours are different, and the difference only shows up in dark mode. $white
# is used 21 times as a background and 32 times as text sitting ON a coloured fill;
# $black likewise. In a dark theme those move in OPPOSITE directions -- surfaces go
# dark while text on a coloured button must stay light -- so a single token cannot
# express it. Giving --sc-fnd-white a dark value would turn 32 button captions
# dark-on-dark. Those two are therefore split by role.
PALETTE = {
    "#1779ba": "--sc-fnd-primary",
    "#767676": "--sc-fnd-secondary",
    "#3adb76": "--sc-fnd-success",
    "#ffae00": "--sc-fnd-warning",
    "#cc4b37": "--sc-fnd-alert",
    "#e6e6e6": "--sc-fnd-light-gray",
    "#cacaca": "--sc-fnd-medium-gray",
    "#8a8a8a": "--sc-fnd-dark-gray",
}

# (colour, role) -> token, for the two that invert. Role is derived from the
# declaration's property; anything that is not text is treated as a surface, which
# keeps borders and shadows moving with the surface they sit against.
SPLIT = {
    ("#fefefe", "text"): "--sc-fnd-on-accent",
    ("#fefefe", "surface"): "--sc-fnd-surface",
    ("#0a0a0a", "text"): "--sc-fnd-ink",
    ("#0a0a0a", "surface"): "--sc-fnd-ink-surface",
    ("#0a0a0a", "on-light-accent"): "--sc-fnd-ink-on-accent",
}

HEADER = (
    "/* GENERATED FILE -- DO NOT EDIT.\n"
    "   Produced by tools/route-foundation-tokens.py from foundation.min.css.\n"
    "   Foundation's base palette is routed to var(--sc-fnd-*) so the theme can\n"
    "   reach it at runtime. Each reference keeps the original colour as its\n"
    "   fallback, and the tokens ship with Foundation's own values, so this file\n"
    "   renders identically to the original until a token is deliberately changed.\n"
    "   Re-run the script after re-vendoring Foundation; CI verifies it matches. */\n"
)

URL_VALUE = re.compile(r"url\([^)]*\)", re.IGNORECASE)
HEX = re.compile(r"#[0-9a-fA-F]{3,8}\b")


def _expand(hex_value: str) -> str:
    """#abc -> #aabbcc, lowercased, so shorthand matches the palette too."""
    h = hex_value.lower()
    if len(h) == 4:
        h = "#" + "".join(c * 2 for c in h[1:])
    return h


# Selectors where a non-`color` property paints a foreground mark rather than a
# surface. The hamburger bars are drawn on a dark top bar and the dropdown caret is
# drawn on a coloured button fill: both belong with the text sitting on that fill,
# not with the page behind it. Routed as surfaces they would darken along with every
# background and disappear entirely in dark mode -- two controls silently erased.
# Matched on the enclosing selector, because the property alone cannot distinguish
# "background of a panel" from "background of a 2px bar used as an icon".
MARK_SELECTORS = (".menu-icon", ".button.dropdown")

# Selectors where `color:` is text drawn on a LIGHT accent fill rather than on the page.
# Foundation puts dark text on its light semantic fills (warning #ffae00, success #3adb76),
# and those fills keep their light values in dark mode because they are brand-adjacent. So
# this text has to stay dark too: routed to --sc-fnd-ink it would follow the theme, turn
# light, and land light-on-light -- 1.70:1 on warning, 1.66:1 on success, down from ~10.7:1.
# The mirror image of MARK_SELECTORS above: there a background was really an icon, here
# `color:` is really text-on-a-fill.
#
# This is a list, so it fails by omission rather than by being wrong. The buttons were
# added with the token; the labels and badges have the identical shape and were not, and
# shipped at exactly the ratios predicted above until they were measured (issue 1515).
# Anything added to Foundation that paints text on `success` or `warning` belongs here --
# the test that goes with this list asserts on the generated pairings for that reason.
LIGHT_ACCENT_TEXT_SELECTORS = (
    ".button.success",
    ".button.warning",
    ".button-group.success",
    ".button-group.warning",
    ".label.success",
    ".label.warning",
    ".badge.success",
    ".badge.warning",
)


def _selector_for(css: str, pos: int) -> str:
    """The selector of the rule containing `pos`."""
    brace = css.rfind("{", 0, pos)
    if brace == -1:
        return ""
    start = max(css.rfind("}", 0, brace), css.rfind("{", 0, brace), css.rfind(";", 0, brace))
    return css[start + 1:brace]


def _role_for(css: str, pos: int) -> str:
    """'text' or 'surface', from the property this colour belongs to.

    Scans back to the start of the declaration and reads its property name. Anything
    that is not `color` counts as a surface, so borders and shadows follow the surface
    they sit against rather than the text drawn on it -- except for the handful of
    selectors in MARK_SELECTORS, where a background paints an icon rather than a panel.
    """
    start = max(css.rfind(";", 0, pos), css.rfind("{", 0, pos), css.rfind("}", 0, pos))
    decl = css[start + 1:pos]
    name, _, _ = decl.partition(":")
    if name.strip().lower() == "color":
        return "text"
    selector = _selector_for(css, pos)
    if any(mark in selector for mark in MARK_SELECTORS):
        return "text"
    return "surface"


def _text_role_for(css: str, pos: int) -> str:
    """Which flavour of text this is: on the page, or on a light accent fill."""
    selector = _selector_for(css, pos)
    if any(s in selector for s in LIGHT_ACCENT_TEXT_SELECTORS):
        return "on-light-accent"
    return "text"


def route(css: str) -> tuple[str, int]:
    """Return the routed CSS and how many declarations were rewritten."""
    spans = [m.span() for m in URL_VALUE.finditer(css)]

    def inside_url(pos: int) -> bool:
        return any(start <= pos < end for start, end in spans)

    count = 0

    def replace(match: re.Match) -> str:
        nonlocal count
        if inside_url(match.start()):
            return match.group(0)
        value = _expand(match.group(0))
        token = PALETTE.get(value)
        if token is None:
            role = _role_for(css, match.start())
            if role == "text":
                role = _text_role_for(css, match.start())
            token = SPLIT.get((value, role))
        if token is None:
            return match.group(0)
        count += 1
        # The original value is retained as the fallback, so a missing token
        # stylesheet degrades to stock Foundation rather than to nothing.
        return f"var({token},{match.group(0)})"

    return HEX.sub(replace, css), count


def generate(root: Path) -> tuple[str, int]:
    css = (root / SOURCE).read_text(encoding="utf-8")
    routed, count = route(css)
    return HEADER + routed, count


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", default=".", help="repository root")
    parser.add_argument("--check", action="store_true",
                        help="verify the committed output matches; exit 1 if not")
    args = parser.parse_args()
    root = Path(args.root)

    source = root / SOURCE
    if not source.exists():
        print(f"ERROR: vendored stylesheet not found: {SOURCE}", file=sys.stderr)
        return 1

    content, count = generate(root)
    target = root / GENERATED

    if args.check:
        if not target.exists():
            print(f"MISSING  {GENERATED} has not been generated", file=sys.stderr)
            return 1
        if target.read_text(encoding="utf-8") != content:
            print(f"STALE    {GENERATED} does not match tools/route-foundation-tokens.py\n"
                  f"         Re-run: python3 tools/route-foundation-tokens.py",
                  file=sys.stderr)
            return 1
        print(f"OK       {GENERATED} matches ({count} declarations routed)")
        return 0

    target.write_text(content, encoding="utf-8")
    print(f"wrote {GENERATED} ({count} declarations routed to {len(PALETTE)} tokens)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
