#!/usr/bin/env python3
"""Evaluate the contrast of design-token *values*, not just count colour literals.

Background
----------
Every contrast ratio guaranteeing accessibility in this product is a
hand-computed number written into a CSS comment in ``platform-tokens.css``.
Nothing tied a number to the declaration it described, so the two could -- and
did -- come apart.

In 8da06161 ``--sc-surface-raised`` was flattened to ``#53575c``, the Anthracite
brand *accent*, while serving as the surface under ``.card``, ``.reveal``,
``table``, ``.tabs``, ``.accordion``, ``.callout``, ``.tooltip``,
``.dropdown-pane``, ``.platform-dialog`` and every form field. Muted text landed
at 2.64:1, links at 2.43:1 and the focus ring at 2.43:1. The comment sitting
directly above the declaration still read "muted 5.01:1 (vs raised), link
4.61:1" -- numbers that describe a surface near ``#292c32``. The value drifted;
the comment was never re-derived. That is issue #1489 / PR #1490.

Nothing in CI could have caught it, or its recurrence.
``check-css-token-adoption.py`` counts hardcoded colours and explicitly excludes
this file, since it is the one place colours are *supposed* to be literal.
``route-foundation-tokens.py --check`` byte-diffs a generated file. Neither
evaluates a colour value.

What this checks
----------------
1. **Pairings.** A declared table of (foreground token, background token, floor)
   is computed from the real token values, per theme block. 4.5:1 for text
   (SC 1.4.3), 3:1 for control borders and focus indicators (SC 1.4.11).

2. **Dark/auto parity.** The dark tokens are declared twice --
   ``:root[data-theme="dark"]`` and a duplicate ``:root[data-theme="auto"]``
   inside ``@media (prefers-color-scheme: dark)``. They are duplicates by
   design, and a token fixed in only one leaves every ``theme.ui.mode=auto``
   site on the broken value. ``--sc-shadow-*`` genuinely differs between the two
   and is exempted by name, split by reason: PARITY_PENDING is waiting on a fix
   already in flight and reports itself retirable the moment its blocks agree,
   while PARITY_SETTLED is divergent on purpose and never does.

3. **Comment claims.** Each ratio quoted in a comment is pinned to the token
   pairing it describes and must match the computed value at the precision the
   comment states. This is the check that would have caught #1489: the stale
   comment fails the build.

4. **No unregistered claims.** Every ``N:1`` in a comment must be covered by a
   CLAIM or by an entry in EXEMPT_RATIOS (WCAG floors, which are not measured
   values). A new hand-computed number cannot enter the file without being
   either verified here or explicitly waived with a reason -- which is what
   stops this drifting again after everyone has forgotten why the gate exists.

What this cannot check
----------------------
**A green run does not mean every colour in the product is accessible.** This
tool proves that a declared pair of token *values* clears its floor. It cannot
prove that pair is the one the browser actually resolves, and the difference is
not academic:

- A token can be sound and never applied. Nothing applies
  ``--sc-field-placeholder`` in light mode -- the only ``::placeholder`` rules in
  first-party CSS are dark-scoped -- so Foundation's own
  ``::placeholder{color:var(--sc-fnd-medium-gray,#cacaca)}`` wins and the rendered
  ratio is 1.64:1, not the 2.76:1 this file's tokens imply. Issue 1506. The
  failing declaration is in vendored CSS, so nothing here can see it.
- Stylesheets with no dark-mode rules at all (issue #1484) are a third mechanism
  again: light panels surviving into dark mode, with hardcoded rather than
  tokenised backgrounds.

Three sibling defects, three different root causes -- 1489 was a surface value,
1498 is a text-token value, 1506 is a rule that was never written. Only the first
two are the kind of thing a token-pair check can catch. Closing that last gap
needs a reachability pass over the cascade, which is a different tool.

Usage
-----
    python3 tools/check-token-contrast.py [root] [--verbose] [--self-test]

Exit 0 clean, 1 on any failure.
"""

import argparse
import re
import sys
from pathlib import Path

TOKENS_CSS = "src/main/webapp/css/platform-tokens.css"

# --------------------------------------------------------------------------
# Colour maths -- WCAG 2.x relative luminance and contrast ratio.
# --------------------------------------------------------------------------

HEX_RE = re.compile(r"^#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
RGB_RE = re.compile(
    r"^rgba?\(\s*([\d.]+)\s*[,\s]\s*([\d.]+)\s*[,\s]\s*([\d.]+)\s*(?:[,/]\s*([\d.%]+)\s*)?\)$",
    re.IGNORECASE,
)


def parse_colour(value: str):
    """A CSS colour as (r, g, b, alpha) with channels 0-255 and alpha 0-1.

    Returns None for anything that is not a literal colour -- ``var()`` chains
    are resolved before this is reached, and non-colour tokens (radii, spacing,
    shadows) are never passed here.
    """
    v = value.strip()
    if HEX_RE.match(v):
        h = v[1:]
        if len(h) in (3, 4):
            h = "".join(c * 2 for c in h)
        r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
        a = int(h[6:8], 16) / 255 if len(h) == 8 else 1.0
        return r, g, b, a
    m = RGB_RE.match(v)
    if m:
        r, g, b = (float(m.group(i)) for i in (1, 2, 3))
        raw = m.group(4)
        a = 1.0 if raw is None else (float(raw[:-1]) / 100 if raw.endswith("%") else float(raw))
        return r, g, b, a
    return None


def composite(fg, bg):
    """Flatten a translucent foreground onto an opaque background (simple alpha over).

    Contrast is undefined for a translucent colour on its own. --sc-border is
    rgba() in dark mode; if a translucent value is ever promoted to a role that
    1.4.11 covers, this makes the answer right instead of silently wrong.
    """
    fr, fg_, fb, fa = fg
    br, bg_, bb, _ = bg
    return (
        fr * fa + br * (1 - fa),
        fg_ * fa + bg_ * (1 - fa),
        fb * fa + bb * (1 - fa),
        1.0,
    )


def luminance(colour) -> float:
    """WCAG relative luminance. Alpha is ignored; composite() first if it matters."""
    def linearise(c: float) -> float:
        c = c / 255
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

    r, g, b = (linearise(c) for c in colour[:3])
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(fg, bg) -> float:
    """Contrast ratio of a foreground over an opaque background, 1.0 to 21.0."""
    if fg[3] < 1.0:
        fg = composite(fg, bg)
    lighter, darker = sorted((luminance(fg), luminance(bg)), reverse=True)
    return (lighter + 0.05) / (darker + 0.05)


def ratio_of(a: str, b: str) -> float:
    """Contrast between two literal colour strings."""
    ca, cb = parse_colour(a), parse_colour(b)
    if ca is None:
        raise ValueError(f"not a colour: {a!r}")
    if cb is None:
        raise ValueError(f"not a colour: {b!r}")
    return contrast(ca, cb)


# --------------------------------------------------------------------------
# CSS parsing
# --------------------------------------------------------------------------

COMMENT_RE = re.compile(r"/\*.*?\*/", re.DOTALL)
DECL_RE = re.compile(r"(--[a-z0-9-]+)\s*:\s*([^;{}]+);")
VAR_RE = re.compile(r"^var\(\s*(--[a-z0-9-]+)\s*(?:,\s*(.*))?\)$", re.DOTALL)

# Selectors that open each theme block. The light block appears twice -- Layer 1
# (semantic tokens) and Layer 1b (Foundation's palette) -- and both are merged.
BLOCK_SELECTORS = {
    "light": r":root\s*\{",
    "dark": r':root\[data-theme="dark"\]\s*\{',
    "auto": r':root\[data-theme="auto"\]\s*\{',
}


def blank_comments(text: str):
    """Replace comments with same-length filler so offsets survive.

    Declarations must be parsed with comments removed -- this file's comments
    contain text like ``--sc-surface: text 16.12:1, ...`` which a naive
    declaration regex reads as a real declaration and reports as a phantom
    out-of-sync token. Keeping the length identical means comment spans found in
    the original text still line up with block spans found in the blanked text.
    """
    spans = []
    out = list(text)
    for m in COMMENT_RE.finditer(text):
        spans.append((m.start(), m.end()))
        for i in range(m.start(), m.end()):
            if out[i] != "\n":
                out[i] = " "
    return "".join(out), spans


def match_brace(text: str, open_idx: int) -> int:
    """Index just past the ``}`` matching the ``{`` at open_idx."""
    depth = 0
    for i in range(open_idx, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return i + 1
    raise ValueError(f"unbalanced braces from offset {open_idx}")


def find_blocks(blanked: str) -> dict:
    """Theme name -> list of (start, end) body spans, comments already blanked."""
    blocks = {}
    for name, selector in BLOCK_SELECTORS.items():
        spans = []
        for m in re.finditer(selector, blanked):
            open_idx = m.end() - 1
            spans.append((open_idx + 1, match_brace(blanked, open_idx) - 1))
        blocks[name] = spans
    return blocks


def declarations(blanked: str, spans) -> dict:
    """Custom-property declarations across a set of spans, later wins."""
    decls = {}
    for start, end in spans:
        for m in DECL_RE.finditer(blanked, start, end):
            decls[m.group(1)] = " ".join(m.group(2).split())
    return decls


def resolve(name: str, block: str, tokens: dict, _seen=None) -> str:
    """A token's literal value in a theme block, following var() indirection.

    Dark and auto blocks override only some tokens; the rest cascade from
    ``:root``. Indirection is real here -- ``--sc-fnd-surface: var(--sc-surface-raised)``
    in the dark blocks, and ``--sc-fnd-white: var(--sc-fnd-surface)`` /
    ``--sc-fnd-black: var(--sc-fnd-ink)`` in the light block -- and it must be
    followed *from the requesting block*, which is how the cascade resolves it:
    --sc-fnd-white is only declared in :root, but in dark mode it lands on the
    dark --sc-fnd-surface.
    """
    _seen = _seen or set()
    if name in _seen:
        raise ValueError(f"circular var() reference at {name} ({block})")
    _seen = _seen | {name}

    value = tokens[block].get(name, tokens["light"].get(name))
    if value is None:
        raise KeyError(f"{name} is not declared in {block} or :root")

    m = VAR_RE.match(value)
    if m:
        target, fallback = m.group(1), m.group(2)
        # Only an undeclared *immediate* target falls back. A KeyError from deeper
        # in the chain is a broken chain, and swallowing it here would substitute a
        # plausible value for a real defect -- the failure mode this tool exists for.
        if target not in tokens[block] and target not in tokens["light"]:
            if fallback is None:
                raise KeyError(f"{target} is not declared in {block} or :root")
            return fallback.strip()
        return resolve(target, block, tokens, _seen)
    return value


def colour_of(spec: str, block: str, tokens: dict) -> str:
    """Resolve a table entry -- a token name or a literal colour -- to a literal."""
    return resolve(spec, block, tokens) if spec.startswith("--") else spec


# --------------------------------------------------------------------------
# 1. Declared pairings
# --------------------------------------------------------------------------
#
# Floors: 4.5:1 for text (SC 1.4.3 Contrast (Minimum)); 3:1 for control borders
# and focus indicators (SC 1.4.11 Non-text Contrast). --sc-border is decorative
# and 1.4.11-exempt on purpose, so it is deliberately absent from this table.
#
# Backgrounds are the surfaces each foreground is actually painted on by
# Layer 3b, not every theoretical combination.

TEXT, NONTEXT = 4.5, 3.0

BLOCKS = ("light", "dark", "auto")
SURFACES = ("--sc-surface", "--sc-surface-raised", "--sc-surface-sunken", "--sc-surface-overlay")

# Pairings that hold in every theme.
COMMON = [
    ("--sc-field-text", "--sc-field-bg", TEXT),
    ("--sc-field-placeholder", "--sc-field-bg", TEXT),
    ("--sc-border-control", "--sc-field-bg", NONTEXT),
    ("--sc-focus-ring", "--sc-field-bg", NONTEXT),
    # Disabled and read-only fields keep muted text (Layer 3b).
    ("--sc-text-muted", "--sc-field-disabled-bg", TEXT),
    # Painted on the theme accent, which this file deliberately does not own.
    # Checked against the surface it inverts, so a mis-set value is still caught.
    ("--sc-text-inverse", "--sc-text", TEXT),
    # Foundation, as Foundation itself pairs these.
    ("--sc-fnd-ink", "--sc-fnd-surface", TEXT),
    ("--sc-fnd-ink", "--sc-surface", TEXT),
    ("--sc-fnd-on-accent", "--sc-fnd-ink-surface", TEXT),   # .tooltip
    ("--sc-fnd-on-accent", "--sc-fnd-primary", TEXT),
    ("--sc-fnd-on-accent", "--sc-fnd-alert", TEXT),
    # The token that exists so warning/success captions stay dark: those two
    # fills keep their light values in dark mode, so their text must not follow.
    ("--sc-fnd-ink-on-accent", "--sc-fnd-warning", TEXT),
    ("--sc-fnd-ink-on-accent", "--sc-fnd-success", TEXT),
    # The deprecated role-blind aliases, still used by stylesheets routed
    # before the split. They resolve through two levels of var().
    ("--sc-fnd-black", "--sc-fnd-white", TEXT),
]

# Pairings that only exist in dark mode. --sc-fnd-on-accent is white text for a
# coloured fill; in dark it also paints menu bars sitting straight on the page,
# which is a real pairing there and a nonsensical one in light (white on white).
# --sc-fnd-dark-gray is Foundation's muted grey, only legible on the dark page.
DARK_ONLY = [
    ("--sc-fnd-on-accent", "--sc-surface", TEXT),
    ("--sc-fnd-dark-gray", "--sc-surface", TEXT),
]

PAIRINGS = []
for _block in BLOCKS:
    for _bg in SURFACES:
        PAIRINGS += [
            (_block, "--sc-text", _bg, TEXT),
            (_block, "--sc-text-muted", _bg, TEXT),
            (_block, "--sc-link", _bg, TEXT),
            (_block, "--sc-link-hover", _bg, TEXT),
            (_block, "--sc-focus-ring", _bg, NONTEXT),
            (_block, "--sc-border-control", _bg, NONTEXT),
        ]
    PAIRINGS += [(_block, fg, bg, floor) for fg, bg, floor in COMMON]
    if _block != "light":
        PAIRINGS += [(_block, fg, bg, floor) for fg, bg, floor in DARK_ONLY]

# Pairings that miss their floor today. Held open here, with the measured value
# and a reason, rather than dropped from the table: a waiver prints on every run
# and cannot go stale, which is more than the CSS comments it replaces managed.
# None of these is introduced by this change -- the check found them on its
# first run against main.
WAIVED = {
    # Issue 1498. --sc-surface-sunken is hex f4f5f7 in light, only 1.09:1 off the
    # white page. --sc-text-muted and --sc-link were both picked for 4.58:1 on
    # white and never re-derived against frost. No live failure today -- the one
    # light-mode consumer that pairs text with it renders a decorative icon, not
    # text -- so the trap is the next author who puts .help-text on a sunken panel.
    # Unlike issue 1489 there is no surface-side fix: in light mode the surface is
    # the lighter of the pair, so it would have to reach L 0.9827 (about hex
    # fefefe) to rescue muted, which is no longer a surface.
    ("light", "--sc-text-muted", "--sc-surface-sunken"): "4.19:1 -- muted text on frost, issue 1498",
    ("light", "--sc-link", "--sc-surface-sunken"): "4.20:1 -- links on frost, issue 1498",
    ("light", "--sc-border-control", "--sc-surface-sunken"):
        "2.97:1 -- a control border against a frost ground, just under 1.4.11, issue 1498",
    ("light", "--sc-text-muted", "--sc-field-disabled-bg"):
        "4.19:1 -- the same frost value as the sunken surface above, issue 1498. No "
        "light-mode consumer today; both Layer 3b rules that paint it are dark-scoped.",
    # Issue 1506, and read the reason before trusting the number. 2.76:1 is what
    # THIS PAIRING computes, but it is not what ships: no light-mode rule applies
    # --sc-field-placeholder at all (the only ::placeholder rules in first-party CSS
    # are the two dark-scoped ones), so Foundation's own
    # `::placeholder{color:var(--sc-fnd-medium-gray,#cacaca)}` wins and the rendered
    # light placeholder is 1.64:1. This gate cannot see that: the failing declaration
    # lives in vendored Foundation, not here. See "What this cannot check" above.
    # The trap for whoever fixes 1506: adding the missing light rule without also
    # changing the value swaps 1.64:1 for 2.76:1 and looks resolved.
    ("light", "--sc-field-placeholder", "--sc-field-bg"):
        "2.76:1 as declared -- but nothing applies this token in light mode; what "
        "renders is Foundation's #cacaca at 1.64:1. Issue 1506. The light-mode note "
        "calls placeholder text 'not AA-required', which SC 1.4.3 does not exempt.",
    # None means every theme: Foundation's five accent fills are deliberately
    # not redefined for dark, so this one misses in all three blocks.
    (None, "--sc-fnd-on-accent", "--sc-fnd-alert"):
        "4.4981:1 -- Foundation's own $white on its own $alert, marginally under. "
        "Vendored palette, shipped unmodified on purpose (see the Layer 1b note); "
        "repainting it is a brand decision, not a CI one.",
}

# --------------------------------------------------------------------------
# 2. Dark/auto parity
# --------------------------------------------------------------------------

# Exempt because the divergence is settled and intended. Never reported as
# retirable: a future reader should not re-open a decision someone already made.
PARITY_SETTLED = {
    # All three blocks are already at 1px 2px here; only the alpha differs
    # (0.4 dark vs 0.45 auto). PR 1492's geometry argument does not reach it, and
    # it was deliberately dropped from that PR's scope.
    "--sc-shadow-sm",
}

# Exempt pending a fix that is already in flight. Each entry is reported (never
# failed) as soon as its two blocks agree, so an exemption that has stopped
# protecting anything says so instead of sitting here silently.
#
# Empty as of PR 1492, which converged --sc-shadow-md and --sc-shadow-lg onto the
# geometry light and dark already shared. They were listed here to decouple this
# check's merge from that one's; that ordering resolved when 1492 landed, and the
# check itself flagged them as retirable on the first run against the new main.
# --sc-shadow-sm stays in PARITY_SETTLED above -- its divergence is alpha-only and
# deliberate, not pending anything.
PARITY_PENDING: set[str] = set()

PARITY_EXEMPT = PARITY_SETTLED | PARITY_PENDING

# --------------------------------------------------------------------------
# 3. Comment claims
# --------------------------------------------------------------------------
#
# Each entry is (pattern, block, groups). The pattern must capture one number
# per group. `block` names the theme the quoted number was computed in, or None
# to use whichever block the comment lexically sits inside -- the Foundation
# summary table is duplicated verbatim in the dark and auto blocks, and each
# copy is checked against its own block's tokens.
#
# A group is (mode, [(fg, bg), ...]). "exact" requires the computed ratio to
# round to the quoted number at the precision the comment states; "floor"
# requires it to be at least the quoted number. Listing several pairings under
# one group is for numbers the comment asserts about a set -- the three tokens
# sharing a surface value, or the three that must all clear one bound.

CLAIMS = [
    # -- Layer 1, light -----------------------------------------------------
    (r"Text - contrast vs --sc-surface: text ([\d.]+):1, muted ([\d.]+):1, link ([\d.]+):1",
     "light",
     [("exact", [("--sc-text", "--sc-surface")]),
      ("exact", [("--sc-text-muted", "--sc-surface")]),
      ("exact", [("--sc-link", "--sc-surface")])]),

    # Raw palette colours the file rejected. Quoted as literals because the
    # point of the sentence is that the *unmodified* palette value fails.
    (r"Pewter \(#979CA4, ([\d.]+):1 on white / ([\d.]+):1 on frost as text\)",
     "light",
     [("exact", [("#979ca4", "#ffffff")]),
      ("exact", [("#979ca4", "#f4f5f7")])]),

    (r"Platinum \(#D9DCE1, ([\d.]+):1 as a control border\)",
     "light", [("exact", [("#d9dce1", "#ffffff")])]),

    (r"--sc-border-control outlines real controls: ([\d.]+):1 on white",
     "light", [("exact", [("--sc-border-control", "--sc-surface")])]),

    (r"Focus ring - Fixed link blue, ([\d.]+):1 on white",
     "light", [("exact", [("--sc-focus-ring", "--sc-surface")])]),

    # The counterfactual this token exists to prevent: if --sc-fnd-ink-on-accent
    # followed --sc-fnd-ink into dark it would take var(--sc-text), landing light
    # text on Foundation's light warning/success fills, which keep light values.
    (r"light-on-light \(([\d.]+):1 on warning, ([\d.]+):1 on success\)",
     "dark",
     [("exact", [("--sc-fnd-ink", "--sc-fnd-warning")]),
      ("exact", [("--sc-fnd-ink", "--sc-fnd-success")])]),

    # -- Layer 2 header: the fresh-install accent, on light page and dark ----
    # "white text" is read as pure #ffffff (7.2775 -> 7.3), not Foundation's
    # --sc-fnd-on-accent #fefefe, which measures 7.2158 -> 7.2. The prose is
    # ambiguous between the two; pinning the reading here is what makes the
    # sentence checkable at all. If the intended subject is ever the Foundation
    # token, change this pair and the comment together -- the gate will insist.
    (r"Anthracite \(#53575c\): white text on it is ([\d.]+):1",
     "light", [("exact", [("#ffffff", "#53575c")])]),

    (r"only reaches\s+([\d.]+):1 there",
     "dark", [("exact", [("#53575c", "--sc-surface")])]),

    (r"just less severe\s+\(([\d.]+):1\)",
     "dark", [("exact", [("#1779ba", "--sc-surface")])]),

    # -- Layer 2, dark ------------------------------------------------------
    # Why Anthracite is not a surface: the three failing ratios on #53575c.
    (r"all land under 4\.5:1 on it \(([\d.]+) / ([\d.]+) / ([\d.]+)\)",
     "dark",
     [("exact", [("--sc-text-muted", "#53575c")]),
      ("exact", [("--sc-link", "#53575c")]),
      ("exact", [("--sc-link-hover", "#53575c")])]),

    (r"keeps all\s+three past ([\d.]+):1",
     "dark",
     [("floor", [("--sc-text-muted", "--sc-surface-raised"),
                 ("--sc-link", "--sc-surface-raised"),
                 ("--sc-link-hover", "--sc-surface-raised")])]),

    (r"Cards read as lifted by ([\d.]+):1",
     "dark", [("exact", [("--sc-surface-raised", "--sc-surface")])]),

    (r"Contrast vs --sc-surface: text ([\d.]+):1, muted ([\d.]+):1, link ([\d.]+):1,\s+"
     r"link-hover ([\d.]+):1",
     "dark",
     [("exact", [("--sc-text", "--sc-surface")]),
      ("exact", [("--sc-text-muted", "--sc-surface")]),
      ("exact", [("--sc-link", "--sc-surface")]),
      ("exact", [("--sc-link-hover", "--sc-surface")])]),

    # The comment asserts overlay and field-bg carry the raised value, so each
    # number is checked against all three backgrounds rather than just raised.
    (r"Vs --sc-surface-raised \(the value --sc-surface-overlay\s+and --sc-field-bg also "
     r"carry\): text ([\d.]+):1, muted ([\d.]+):1, link ([\d.]+):1,\s+link-hover ([\d.]+):1",
     "dark",
     [("exact", [("--sc-text", "--sc-surface-raised"),
                 ("--sc-text", "--sc-surface-overlay"),
                 ("--sc-text", "--sc-field-bg")]),
      ("exact", [("--sc-text-muted", "--sc-surface-raised"),
                 ("--sc-text-muted", "--sc-surface-overlay"),
                 ("--sc-text-muted", "--sc-field-bg")]),
      ("exact", [("--sc-link", "--sc-surface-raised"),
                 ("--sc-link", "--sc-surface-overlay"),
                 ("--sc-link", "--sc-field-bg")]),
      ("exact", [("--sc-link-hover", "--sc-surface-raised"),
                 ("--sc-link-hover", "--sc-surface-overlay"),
                 ("--sc-link-hover", "--sc-field-bg")])]),

    (r"true Platinum -- ([\d.]+):1 on raised",
     "dark", [("exact", [("--sc-border-control", "--sc-surface-raised")])]),

    # -- Foundation summary table, duplicated in the dark and auto blocks ----
    (r"ink on surface \.+\s+([\d.]+):1\s+on-accent on primary \.+\s+([\d.]+):1",
     None,
     [("exact", [("--sc-fnd-ink", "--sc-fnd-surface")]),
      ("exact", [("--sc-fnd-on-accent", "--sc-fnd-primary")])]),

    (r"ink on the page \.+\s+([\d.]+):1\s+menu bars on dark bar \.+\s+([\d.]+):1",
     None,
     [("exact", [("--sc-fnd-ink", "--sc-surface")]),
      ("exact", [("--sc-fnd-on-accent", "--sc-surface")])]),

    (r"on-accent on ink-surface \.+\s+([\d.]+):1\s+dark-gray on the page \.+\s+([\d.]+):1",
     None,
     [("exact", [("--sc-fnd-on-accent", "--sc-fnd-ink-surface")]),
      ("exact", [("--sc-fnd-dark-gray", "--sc-surface")])]),
]

# --------------------------------------------------------------------------
# 4. Numbers that are not measurements
# --------------------------------------------------------------------------
#
# Every N:1 in a comment must be covered by a CLAIM or by one of these. The
# alternative -- checking only what happens to be registered -- is how the
# stale comment survived in the first place.

EXEMPT_RATIOS = [
    (r"Contrast \(Minimum\) at (4\.5):1", "SC 1.4.3 floor, not a measurement"),
    (r"Non-text Contrast at (3):1", "SC 1.4.11 floor, not a measurement"),
    (r"well past the (3):1 that", "SC 1.4.11 floor, not a measurement"),
    (r"comfortably past the\s+(4\.5):1 floor", "SC 1.4.3 floor, not a measurement"),
    (r"short of the (3):1 non-text floor", "SC 1.4.11 floor, not a measurement"),
    (r"all land under (4\.5):1 on it", "SC 1.4.3 floor, not a measurement"),
    (r"luminance to clear (4\.5):1", "SC 1.4.3 floor, not a measurement"),
]

RATIO_IN_COMMENT = re.compile(r"\d+(?:\.\d+)?:1")


# --------------------------------------------------------------------------
# Checks
# --------------------------------------------------------------------------

def load(root: Path):
    text = (root / TOKENS_CSS).read_text(encoding="utf-8")
    blanked, comment_spans = blank_comments(text)
    blocks = find_blocks(blanked)
    tokens = {name: declarations(blanked, spans) for name, spans in blocks.items()}
    return text, blanked, comment_spans, blocks, tokens


def line_of(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def block_at(offset: int, blocks: dict):
    """Innermost theme block containing an offset, or None."""
    best, best_len = None, None
    for name, spans in blocks.items():
        for start, end in spans:
            if start <= offset < end and (best_len is None or end - start < best_len):
                best, best_len = name, end - start
    return best


def neutral_at(target: float, at_least: bool) -> str:
    """The nearest neutral grey on the correct side of a luminance bound.

    Surfaces in this file are all near-neutral, so a grey is a fair illustration of
    what a bound actually costs. Returns "" when no sRGB value satisfies it.
    """
    values = range(256) if at_least else range(255, -1, -1)
    for v in values:
        if (luminance((v, v, v)) >= target) if at_least else (luminance((v, v, v)) <= target):
            return "#%02x%02x%02x" % (v, v, v)
    return ""


def how_to_clear(fg_val: str, bg_val: str, floor: float) -> str:
    """What each side of a failing pair would have to reach to meet the floor.

    A pairing can fail for two quite different reasons, and the numbers say which.
    Issue 1489 was a *surface* problem: the background was the darker side with room
    to move away from the text, so darkening it fixed everything. Issue 1498 looks
    identical but is a *text-token* problem: in light mode the background is the
    lighter side and already near white, so the surface bound lands at hex fefefe --
    no longer a surface. Printing both bounds stops the next reader reaching for the
    1489 playbook on a pairing it does not fit.
    """
    lf, lb = luminance(parse_colour(fg_val)), luminance(parse_colour(bg_val))
    if lb < lf:                      # background is the darker side
        bg_need, bg_dir = (lf + 0.05) / floor - 0.05, "darker"
        fg_need, fg_dir = floor * (lb + 0.05) - 0.05, "lighter"
    else:                            # background is the lighter side
        bg_need, bg_dir = floor * (lf + 0.05) - 0.05, "lighter"
        fg_need, fg_dir = (lb + 0.05) / floor - 0.05, "darker"

    def phrase(need, direction, label, now, illustrate):
        op = ">=" if direction == "lighter" else "<="
        if not 0.0 <= need <= 1.0:
            return f"{label} cannot reach L {op} {need:.4f} at any colour"
        # A neutral grey is a fair illustration for a surface, which in this file is
        # always near-neutral. Deliberately not offered for the foreground, where it
        # would read as a proposed text colour rather than a bound.
        grey = f", ~{neutral_at(need, direction == 'lighter')}" if illustrate else ""
        return f"{label} L {op} {need:.4f} ({direction}, now {now:.4f}{grey})"

    return ("to clear it: "
            + phrase(bg_need, bg_dir, "background", lb, True)
            + ", or " + phrase(fg_need, fg_dir, "foreground", lf, False))


def check_pairings(tokens, errors, notes, verbose):
    checked = 0
    seen_waivers = set()
    for block, fg, bg, floor in PAIRINGS:
        try:
            fg_val, bg_val = colour_of(fg, block, tokens), colour_of(bg, block, tokens)
        except KeyError as exc:
            errors.append(f"{TOKENS_CSS}: pairing {fg} on {bg} ({block}): {exc}")
            continue
        r = ratio_of(fg_val, bg_val)
        checked += 1
        # Two decimals normally, but never let rounding print a failing ratio as
        # though it met its floor -- 4.4981:1 must not read as "4.50:1, floor 4.5:1".
        shown = f"{r:.4f}" if r < floor <= float(f"{r:.2f}") else f"{r:.2f}"
        label = f"{block:5s} {fg} ({fg_val}) on {bg} ({bg_val}) = {shown}:1, floor {floor}:1"
        if r < floor:
            waiver = WAIVED.get((block, fg, bg)) or WAIVED.get((None, fg, bg))
            hint = how_to_clear(fg_val, bg_val, floor) if r < floor else ""
            if waiver:
                # The reason is printed once per distinct waiver, not once per
                # block, so a token waived in all three does not triple the noise.
                if waiver in seen_waivers:
                    notes.append(f"WAIVED   {label}")
                else:
                    seen_waivers.add(waiver)
                    detail = f"\n           {hint}" if verbose else ""
                    notes.append(f"WAIVED   {label}\n           {waiver}{detail}")
            else:
                errors.append(f"CONTRAST {label}\n           {hint}")
        elif verbose:
            notes.append(f"ok       {label}")
    return checked


def check_parity(tokens, errors, verbose, notes):
    dark, auto = tokens["dark"], tokens["auto"]
    names = (set(dark) | set(auto)) - PARITY_EXEMPT
    for name in sorted(names):
        d, a = dark.get(name), auto.get(name)
        if d is None:
            errors.append(f"PARITY   {name} is declared in [data-theme=\"auto\"] but not "
                          f"[data-theme=\"dark\"]")
        elif a is None:
            errors.append(f"PARITY   {name} is declared in [data-theme=\"dark\"] but not "
                          f"[data-theme=\"auto\"] -- every theme.ui.mode=auto site misses it")
        elif d != a:
            errors.append(f"PARITY   {name}: dark is {d!r}, auto is {a!r} -- the two blocks are "
                          f"duplicates by design; a value fixed in only one leaves every "
                          f"theme.ui.mode=auto site on the old value")
    for name in sorted(PARITY_PENDING):
        d, a = dark.get(name), auto.get(name)
        if d is not None and d == a:
            notes.append(f"retired  {name} is exempt from the dark/auto parity check but the two "
                         f"blocks now agree -- drop it from PARITY_EXEMPT")
    if verbose:
        notes.append(f"ok       dark/auto parity across {len(names)} tokens "
                     f"({len(PARITY_EXEMPT)} exempt)")
    return len(names)


def decimals(literal: str) -> int:
    return len(literal.split(".", 1)[1]) if "." in literal else 0


def check_claims(text, blocks, tokens, errors, notes, verbose):
    """Verify every quoted ratio, and return the spans they account for."""
    covered, checked = [], 0
    for pattern, declared_block, groups in CLAIMS:
        matches = list(re.finditer(pattern, text))
        if not matches:
            errors.append(f"STALE    no comment in {TOKENS_CSS} matches the registered claim "
                          f"{pattern!r} -- the CLAIMS table in this script is out of date")
            continue
        for m in matches:
            block = declared_block or block_at(m.start(), blocks)
            if block is None:
                errors.append(f"{TOKENS_CSS}:{line_of(text, m.start())}: claim {pattern!r} is "
                              f"outside every theme block and declares no block")
                continue
            for idx, (mode, pairs) in enumerate(groups, start=1):
                literal = m.group(idx)
                covered.append(m.span(idx))
                claimed, dp = float(literal), decimals(literal)
                tolerance = 0.5 * 10 ** (-dp) + 1e-9
                for fg, bg in pairs:
                    try:
                        fg_val, bg_val = colour_of(fg, block, tokens), colour_of(bg, block, tokens)
                    except KeyError as exc:
                        errors.append(f"{TOKENS_CSS}:{line_of(text, m.start(idx))}: {exc}")
                        continue
                    r = ratio_of(fg_val, bg_val)
                    checked += 1
                    where = f"{TOKENS_CSS}:{line_of(text, m.start(idx))}"
                    if mode == "exact" and abs(r - claimed) > tolerance:
                        errors.append(
                            f"COMMENT  {where}: comment says {literal}:1 for {fg} on {bg} "
                            f"({block}), computed {r:.2f}:1 "
                            f"[{fg_val} on {bg_val}] -- re-derive the number or fix the value")
                    elif mode == "floor" and r + tolerance < claimed:
                        errors.append(
                            f"COMMENT  {where}: comment claims at least {literal}:1 for {fg} on "
                            f"{bg} ({block}), computed {r:.2f}:1 [{fg_val} on {bg_val}]")
                    elif verbose:
                        notes.append(f"ok       {where}: {literal}:1 {fg} on {bg} ({block}) "
                                     f"= {r:.2f}:1")
    return covered, checked


def check_registration(text, comment_spans, covered, errors):
    """Every N:1 inside a comment must be a verified claim or a declared exemption."""
    for pattern, reason in EXEMPT_RATIOS:
        matches = list(re.finditer(pattern, text))
        if not matches:
            # Held to the same standard as CLAIMS: an exemption nobody can see the
            # subject of is dead config, and dead config is how tables stop being read.
            errors.append(f"STALE    no comment in {TOKENS_CSS} matches the exemption "
                          f"{pattern!r} ({reason}) -- drop it from EXEMPT_RATIOS")
        for m in matches:
            covered.append(m.span(1))

    for cstart, cend in comment_spans:
        for m in RATIO_IN_COMMENT.finditer(text, cstart, cend):
            num_end = m.end() - 2  # the number, without the ":1"
            if any(s <= m.start() and num_end <= e for s, e in covered):
                continue
            snippet = " ".join(text[max(cstart, m.start() - 60):m.end() + 20].split())
            errors.append(
                f"UNCLAIMED {TOKENS_CSS}:{line_of(text, m.start())}: {m.group(0)} is not tied to "
                f"a token pairing\n           ...{snippet}...\n"
                f"           Register it in CLAIMS (so it is verified) or in EXEMPT_RATIOS (if "
                f"it is a WCAG floor rather than a measurement).")


# --------------------------------------------------------------------------
# Self-test
# --------------------------------------------------------------------------

SELF_TEST = [
    ("#000000", "#ffffff", 21.00),
    ("#767676", "#ffffff", 4.54),   # the canonical AA-passing grey
    ("#777777", "#ffffff", 4.48),   # one step lighter, and failing -- proves the
                                    # calculator resolves the boundary rather than
                                    # rounding past it
]


def self_test(errors, notes):
    for fg, bg, want in SELF_TEST:
        got = round(ratio_of(fg, bg), 2)
        if abs(got - want) > 1e-9:
            errors.append(f"SELFTEST {fg} on {bg}: expected {want:.2f}:1, got {got:.2f}:1")
        else:
            notes.append(f"ok       self-test {fg} on {bg} = {got:.2f}:1")


# --------------------------------------------------------------------------

def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("root", nargs="?", default=".")
    ap.add_argument("--verbose", action="store_true", help="print every passing pairing")
    ap.add_argument("--self-test", action="store_true",
                    help="only exercise the contrast calculator against known values")
    args = ap.parse_args()

    errors, notes = [], []

    if args.self_test:
        self_test(errors, notes)
        print("\n".join(notes))
        for e in errors:
            print(e, file=sys.stderr)
        return 1 if errors else 0

    root = Path(args.root)
    path = root / TOKENS_CSS
    if not path.is_file():
        print(f"MISSING  {TOKENS_CSS}", file=sys.stderr)
        return 1

    self_test(errors, notes if args.verbose else [])
    if errors:  # a broken calculator makes every other verdict meaningless
        for e in errors:
            print(e, file=sys.stderr)
        return 1

    try:
        text, _blanked, comment_spans, blocks, tokens = load(root)
    except ValueError as exc:
        print(f"PARSE    {TOKENS_CSS}: {exc}", file=sys.stderr)
        return 1

    for name, spans in blocks.items():
        if not spans:
            errors.append(f"PARSE    {TOKENS_CSS}: no {name} token block found "
                          f"(selector {BLOCK_SELECTORS[name]!r})")
    if errors:
        for e in errors:
            print(e, file=sys.stderr)
        return 1

    pairs = check_pairings(tokens, errors, notes, args.verbose)
    parity = check_parity(tokens, errors, args.verbose, notes)
    covered, claims = check_claims(text, blocks, tokens, errors, notes, args.verbose)
    check_registration(text, comment_spans, covered, errors)

    for n in notes:
        print(n)
    for e in errors:
        print(e, file=sys.stderr)

    if errors:
        print(f"\n{len(errors)} problem(s) in {TOKENS_CSS}. A contrast ratio in a comment is a "
              f"promise about a declaration; this gate keeps the two together.", file=sys.stderr)
        return 1

    print(f"OK  {pairs} token pairings above their floor, {claims} comment ratios re-derived, "
          f"{parity} tokens identical across the dark and auto blocks")
    return 0


if __name__ == "__main__":
    sys.exit(main())
