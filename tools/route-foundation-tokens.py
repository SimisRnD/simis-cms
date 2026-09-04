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

The source is consumed whole
----------------------------
``foundation.min.css`` is read verbatim and everything in it reaches the output,
so it cannot carry a note about itself. A comment added there is copied into the
served stylesheet, where it is at best noise and at worst a claim about the wrong
file; and because the rewrite scans the whole text rather than just declarations,
any hex inside that comment is routed to a ``var()`` and counted as a routed
declaration. Both were verified, not assumed.

That matters because the unserved vendor originals invite exactly such a note:
their colours are stock Foundation, they are greppable, and reading a contrast
ratio off them has already produced one false accessibility defect. The warning
lives where it does no damage instead -- a banner in ``foundation.css`` (which
nothing reads) and ``README.md`` in the vendored directory. Put it there, not here.

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
SOURCE = f"{VENDOR_DIR}/foundation.min.css"  # consumed verbatim -- see the docstring before editing it
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
    # #cc4b37 is NOT here -- $alert plays a fill role and a foreground role that
    # part company in dark mode, so it is split by role below.
    "#e6e6e6": "--sc-fnd-light-gray",
    "#cacaca": "--sc-fnd-medium-gray",
    "#8a8a8a": "--sc-fnd-dark-gray",
    # Table chrome. Sass-computed neutrals rather than base palette entries, added because
    # the admin is mostly tables and these were the last thing in it still reading cool
    # against a warm surface -- thead, tfoot, the stripe, the rules between rows and the
    # three hover shades. Seeded with Foundation's own values, so routing them changes
    # nothing until someone decides what they should be.
    # #f1f1f1 is NOT here -- it does two jobs and is split by role below.
    "#f8f8f8": "--sc-fnd-table-head",
    "#f3f3f3": "--sc-fnd-table-head-hover",
    "#f9f9f9": "--sc-fnd-table-row-hover",
    "#ececec": "--sc-fnd-table-stripe-hover",
}

# (colour, role) -> token, for the colours whose roles part company. Role is derived from the
# declaration's property; anything that is not text is treated as a surface, which
# keeps borders and shadows moving with the surface they sit against.
SPLIT = {
    ("#fefefe", "text"): "--sc-fnd-on-accent",
    ("#fefefe", "surface"): "--sc-fnd-surface",
    ("#0a0a0a", "text"): "--sc-fnd-ink",
    ("#0a0a0a", "surface"): "--sc-fnd-ink-surface",
    ("#0a0a0a", "on-light-accent"): "--sc-fnd-ink-on-accent",
    # #f1f1f1 is the third colour that inverts, and it inverts the same way $white does.
    # Foundation spends it on two unrelated jobs: the RULE around tbody/tfoot/thead
    # (`border:1px solid #f1f1f1`) and the FILL behind tfoot and every even row. In light
    # mode one pale grey serves both. In dark they part company -- the fill wants to go
    # darker than the surface it stripes, while the rule has to stay light enough to be
    # seen against that surface. Left as one token, whichever job you satisfy breaks the
    # other: the fill was darkened and the rule was not, so `thead` shipped a near-white
    # #f1f1f1 border in dark mode, which is what led here.
    ("#f1f1f1", "surface"): "--sc-fnd-table-stripe",
    ("#f1f1f1", "border"): "--sc-fnd-table-rule",
    # $alert is the third colour to need this, and it splits along a different seam than
    # #f1f1f1: not fill-vs-rule, but fill-vs-foreground. Foundation spends #cc4b37 both as
    # a FILL under light ink (.button.alert, .button-group.alert, .label.alert, .badge.alert,
    # .progress.alert) and as INK or a BORDER drawn straight on the page ground (.form-error,
    # .is-invalid-label, .is-invalid-input, the hollow/clear alert variants and their
    # dropdown caret).
    #
    # Held as one token the two roles are strictly opposed in dark mode. The fill has to stay
    # dark enough for #fefefe ink to clear 4.5:1 on top of it; the ink has to be light enough
    # to clear 4.5:1 against a dark page. A red that satisfies one fails the other. Issue 1527
    # lifted the shared token to #cb4834 for the fill and disclosed that the ink role kept
    # failing on the dark page; that is issue 1851, and this split is what closes it.
    #
    # Border follows the ink rather than the surface: every #cc4b37 border in Foundation is
    # drawn on the page ground -- a hollow button's outline, an invalid field's outline --
    # never around a filled alert element. The dropdown caret reaches the same token by a
    # different path: .button.dropdown is in MARK_SELECTORS, so its border-colour is already
    # classified as a foreground mark before the border role is considered.
    ("#cc4b37", "surface"): "--sc-fnd-alert",
    ("#cc4b37", "text"): "--sc-fnd-alert-ink",
    ("#cc4b37", "border"): "--sc-fnd-alert-ink",
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
    """'text', 'border' or 'surface', from the property this colour belongs to.

    Scans back to the start of the declaration and reads its property name. Anything
    that is not `color` counts as a surface, so shadows follow the surface they sit
    against rather than the text drawn on it -- except for the handful of selectors in
    MARK_SELECTORS, where a background paints an icon rather than a panel.

    A `border*` property gets its own role because a rule and a fill can need opposite
    treatment in dark mode (see the #f1f1f1 note on SPLIT). This is additive: a colour
    with no ('border') entry falls back to its ('surface') one in ``route``, so every
    colour split before this existed keeps the token it had.
    """
    start = max(css.rfind(";", 0, pos), css.rfind("{", 0, pos), css.rfind("}", 0, pos))
    decl = css[start + 1:pos]
    name, _, _ = decl.partition(":")
    lowered = name.strip().lower()
    if lowered == "color":
        return "text"
    selector = _selector_for(css, pos)
    if any(mark in selector for mark in MARK_SELECTORS):
        return "text"
    if lowered.startswith("border"):
        return "border"
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
            if token is None and role == "border":
                # Only #f1f1f1 distinguishes a rule from a fill. Every other split colour
                # predates the 'border' role and recorded its borders as surfaces, so fall
                # back rather than dropping them out of the routing entirely.
                token = SPLIT.get((value, "surface"))
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
