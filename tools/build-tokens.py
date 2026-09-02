#!/usr/bin/env python3
"""Generate the colour token values in platform-tokens.css from a small seed set.

A theme is defined by the HUE SEEDS below, not by hand-authoring ~89 hex values.
Lightness and chroma stay authored per token, because those carry the contrast
decisions check-token-contrast.py enforces; only hue derives. That split is what
issue 1803's prototype measured: hue snaps to a seed within a just-noticeable
difference, lightness and chroma do not want a mathematical ladder.

Output is flat sRGB hex, so browser support is unchanged and the contrast gate
keeps reading literal values.

    python3 tools/build-tokens.py --check   # CI: committed CSS matches the seeds
    python3 tools/build-tokens.py --write   # regenerate after editing a seed

The dark and auto blocks are emitted from the same rows, so they cannot drift
apart. That is why the dark/auto parity check no longer exists.
"""
import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _oklch import oklch_to_hex  # noqa: E402

TOKENS_CSS = "src/main/webapp/css/platform-tokens.css"

# --- the theme -------------------------------------------------------------
# Change these to change the palette. Everything else follows.

SEED_LIGHT = {
    "brand": 36.4,  # SimIS brand orange-red
    "chrome": 256.6,  # the admin chrome ladder
    "danger": 25.0,  # error state - kept clear of brand (issue 1803)
    "link": 248.4,  # link, focus ring, info
    "marker": 47.6,  # the active-nav accent
    "neutral": 81.7,  # warm grey: surfaces, borders, body text, tables
    "success": 150.8,
    "warning": 75.7,
}

SEED_DARK = {
    "brand": 37.8,  # SimIS brand orange-red
    "danger": 25.0,  # error state - kept clear of brand (issue 1803)
    "focus": 67.8,  # amber focus ring on dark
    "link": 188.0,  # teal on dark, not the light blue - a legibility call
    "neutral": 74.5,  # warm dark ground
}

# --- authored lightness and chroma -----------------------------------------
# (token, family, L, C). These are the design system; the seeds are the theme.

LIGHT = [
    ('--sc-brand',                      'brand',    0.561340, 0.170558),
    ('--sc-brand-hover',                'brand',    0.500400, 0.152538),
    ('--sc-brand-text',                 'brand',    0.500400, 0.152538),
    ('--sc-chrome',                     'chrome',   0.242083, 0.027640),
    ('--sc-chrome-accent',              'chrome',   0.467511, 0.163945),
    ('--sc-chrome-accent-deep',         'chrome',   0.402002, 0.144986),
    ('--sc-chrome-control',             'chrome',   0.305579, 0.033385),
    ('--sc-chrome-control-hover',       'chrome',   0.329364, 0.036550),
    ('--sc-chrome-ink',                 'chrome',   0.946889, 0.012557),
    ('--sc-chrome-ink-muted',           'chrome',   0.771232, 0.027081),
    ('--sc-chrome-ink-subtle',          'chrome',   0.687627, 0.030991),
    ('--sc-chrome-raised',              'chrome',   0.281860, 0.031768),
    ('--sc-chrome-selected',            'chrome',   0.344332, 0.040472),
    ('--sc-danger',                     'danger',   0.544065, 0.158406),
    ('--sc-fnd-alert',                  'danger',   0.579635, 0.170102),
    ('--sc-fnd-primary',                'link',     0.556031, 0.130957),
    ('--sc-focus-ring',                 'link',     0.519207, 0.119502),
    ('--sc-info',                       'link',     0.525435, 0.119731),
    ('--sc-info-light',                 'link',     0.957444, 0.013746),
    ('--sc-link',                       'link',     0.519207, 0.119502),
    ('--sc-link-hover',                 'link',     0.454582, 0.104443),
    ('--sc-chrome-active-marker',       'marker',   0.704871, 0.186721),
    ('--sc-border',                     'neutral',  0.883687, 0.013374),
    ('--sc-border-control',             'neutral',  0.616293, 0.006446),
    ('--sc-field-disabled-bg',          'neutral',  0.969953, 0.002872),
    ('--sc-field-placeholder',          'neutral',  0.563904, 0.012368),
    ('--sc-field-text',                 'neutral',  0.213502, 0.010320),
    ('--sc-fnd-dark-gray',              'neutral',  0.633031, 0.022553),
    ('--sc-fnd-light-gray',             'neutral',  0.924870, 0.004171),
    ('--sc-fnd-medium-gray',            'neutral',  0.837398, 0.009386),
    ('--sc-fnd-table-head',             'neutral',  0.982049, 0.004110),
    ('--sc-fnd-table-head-hover',       'neutral',  0.967578, 0.005732),
    ('--sc-fnd-table-row-hover',        'neutral',  0.982601, 0.001707),
    ('--sc-fnd-table-rule',             'neutral',  0.961577, 0.005740),
    ('--sc-fnd-table-stripe',           'neutral',  0.961577, 0.005740),
    ('--sc-fnd-table-stripe-hover',     'neutral',  0.947036, 0.007423),
    ('--sc-surface',                    'neutral',  0.964579, 0.005736),
    ('--sc-surface-sunken',             'neutral',  0.935469, 0.009133),
    ('--sc-text',                       'neutral',  0.282713, 0.008417),
    ('--sc-text-muted',                 'neutral',  0.519706, 0.012057),
    ('--sc-text-subtle',                'neutral',  0.691197, 0.013036),
    ('--sc-fnd-success',                'success',  0.787466, 0.192355),
    ('--sc-fnd-success-ink',            'success',  0.539792, 0.137915),
    ('--sc-fnd-warning',                'warning',  0.808785, 0.170358),
    ('--sc-fnd-warning-ink',            'warning',  0.559423, 0.117465),
]

DARK = [
    ('--sc-brand-text',                 'brand',    0.685384, 0.153973),
    ('--sc-danger',                     'danger',   0.724428, 0.114798),
    ('--sc-focus-ring',                 'focus',    0.742192, 0.126945),
    ('--sc-info',                       'link',     0.719473, 0.069999),
    ('--sc-info-light',                 'link',     0.297198, 0.023612),
    ('--sc-link',                       'link',     0.719473, 0.069999),
    ('--sc-link-hover',                 'link',     0.789305, 0.058592),
    ('--sc-border-control',             'neutral',  0.800902, 0.040441),
    ('--sc-field-bg',                   'neutral',  0.278790, 0.019834),
    ('--sc-field-disabled-bg',          'neutral',  0.278790, 0.019834),
    ('--sc-field-placeholder',          'neutral',  0.735389, 0.030735),
    ('--sc-field-text',                 'neutral',  0.964579, 0.005736),
    ('--sc-fnd-dark-gray',              'neutral',  0.667475, 0.013718),
    ('--sc-fnd-ink-surface',            'neutral',  0.268090, 0.011992),
    ('--sc-fnd-light-gray',             'neutral',  0.304122, 0.013836),
    ('--sc-fnd-medium-gray',            'neutral',  0.426006, 0.014751),
    ('--sc-fnd-table-head',             'neutral',  0.208190, 0.015404),
    ('--sc-fnd-table-head-hover',       'neutral',  0.258250, 0.020162),
    ('--sc-fnd-table-row-hover',        'neutral',  0.356135, 0.026424),
    ('--sc-fnd-table-stripe',           'neutral',  0.208190, 0.015404),
    ('--sc-fnd-table-stripe-hover',     'neutral',  0.258250, 0.020162),
    ('--sc-surface',                    'neutral',  0.239582, 0.015836),
    ('--sc-surface-overlay',            'neutral',  0.316672, 0.023658),
    ('--sc-surface-raised',             'neutral',  0.316672, 0.023658),
    ('--sc-surface-sunken',             'neutral',  0.208190, 0.015404),
    ('--sc-text',                       'neutral',  0.964579, 0.005736),
    ('--sc-text-inverse',               'neutral',  0.239582, 0.015836),
    ('--sc-text-muted',                 'neutral',  0.705583, 0.033860),
]

# Achromatic by intent -- pure white/black/grey, where a hue would be noise.
LIGHT_LITERAL = [
    ('--sc-chrome-ink-selected',        '#ffffff'),
    ('--sc-field-bg',                   '#ffffff'),
    ('--sc-fnd-ink-on-accent',          '#0a0a0a'),
    ('--sc-fnd-ink-surface',            '#0a0a0a'),
    ('--sc-fnd-on-accent',              '#fefefe'),
    ('--sc-fnd-secondary',              '#767676'),
    ('--sc-fnd-surface',                '#fefefe'),
    ('--sc-surface-overlay',            '#ffffff'),
    ('--sc-surface-raised',             '#ffffff'),
    ('--sc-text-inverse',               '#ffffff'),
]

DARK_LITERAL = [
    ('--sc-fnd-on-accent',              '#fefefe'),
]


# Dark-mode declarations that are not colours the seeds can generate -- translucent
# borders, shadow stacks, and var() aliases. They are emitted verbatim into BOTH the
# dark and auto blocks, which is what makes the old dark/auto parity check redundant.
# This is the set that actually drifted in practice (the shadow stacks, issues 1492
# and 1503), so it is the set most worth generating rather than hand-maintaining.
DARK_SHARED = [
    ('--sc-border',                     'rgba(245, 243, 239, 0.13)'),
    ('--sc-fnd-table-rule',             'rgba(245, 243, 239, 0.13)'),
    ('--sc-fnd-ink',                    'var(--sc-text)'),
    ('--sc-fnd-surface',                'var(--sc-surface-raised)'),
    ('--sc-fnd-success-ink',            'var(--sc-link)'),
    ('--sc-fnd-warning-ink',            'var(--sc-link)'),
    ('--sc-shadow-md',                  '0 1px 0 rgba(255, 255, 255, 0.05), 0 1px 3px rgba(0, 0, 0, 0.45)'),
    ('--sc-shadow-lg',                  '0 2px 6px rgba(0, 0, 0, 0.5)'),
    ('--sc-shadow-raised',              '0 1px 2px rgba(0, 0, 0, 0.45), 0 6px 16px -8px rgba(0, 0, 0, 0.6)'),
]

BLOCK_SELECTORS = {
    "light": r":root\s*\{",
    "dark": r':root\[data-theme="dark"\]\s*\{',
    "auto": r':root\[data-theme="auto"\]\s*\{',
}

DECL_RE = re.compile(r"(--sc-[a-z0-9-]+)(\s*:\s*)(#[0-9a-fA-F]{6})(\s*;)")
ANY_DECL_RE = re.compile(r"(--sc-[a-z0-9-]+)(\s*:\s*)([^;{}]+?)(\s*;)")


def generate(rows, literals, seeds):
    """token -> hex, for one block."""
    out = {}
    for token, family, lightness, chroma in rows:
        if family not in seeds:
            raise SystemExit("no seed for family %r (token %s)" % (family, token))
        out[token] = oklch_to_hex(lightness, chroma, seeds[family])
    for token, value in literals:
        out[token] = value
    return out


def blank_comments(text):
    """Replace comment bodies with spaces, preserving offsets and line count."""
    def repl(m):
        return "".join("\n" if ch == "\n" else " " for ch in m.group(0))
    return re.sub(r"/\*.*?\*/", repl, text, flags=re.DOTALL)


def block_spans(text):
    """Brace-matched span of every theme block, keyed by name."""
    blanked = blank_comments(text)
    spans = []
    for name, pattern in BLOCK_SELECTORS.items():
        for m in re.finditer(pattern, blanked):
            if name == "light" and blanked[: m.start()].rstrip().endswith(("]", ")")):
                continue
            open_idx = blanked.index("{", m.start())
            depth = 0
            for i in range(open_idx, len(blanked)):
                if blanked[i] == "{":
                    depth += 1
                elif blanked[i] == "}":
                    depth -= 1
                    if depth == 0:
                        spans.append((name, open_idx, i))
                        break
    return spans


def expected():
    light = generate(LIGHT, LIGHT_LITERAL, SEED_LIGHT)
    dark = generate(DARK, DARK_LITERAL, SEED_DARK)
    return {"light": light, "dark": dark, "auto": dark}


def expected_shared():
    """Non-colour declarations emitted identically into dark and auto."""
    table = dict(DARK_SHARED)
    return {"light": {}, "dark": table, "auto": table}


def apply(text, want, shared_want, write):
    """Rewrite (or check) every generated declaration in place."""
    blanked = blank_comments(text)
    edits = []
    problems = []
    for name, open_idx, close_idx in block_spans(text):
        table = want[name]
        shared = shared_want[name]
        for m in ANY_DECL_RE.finditer(blanked, open_idx, close_idx):
            token = m.group(1)
            if token not in shared:
                continue
            have = " ".join(text[m.start(3):m.end(3)].split())
            need = shared[token]
            if have != need:
                problems.append((name, token, have, need))
                edits.append((m.start(3), m.end(3), need))
        for m in DECL_RE.finditer(blanked, open_idx, close_idx):
            token = m.group(1)
            if token not in table:
                continue
            have = text[m.start(3):m.end(3)]
            need = table[token]
            if have.lower() != need.lower():
                problems.append((name, token, have, need))
                edits.append((m.start(3), m.end(3), need))
    if write:
        for start, end, need in sorted(edits, reverse=True):
            text = text[:start] + need + text[end:]
    return text, problems


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--check", action="store_true", help="fail if the CSS is stale")
    g.add_argument("--write", action="store_true", help="regenerate the CSS in place")
    ap.add_argument("root", nargs="?", default=".", help="repository root")
    args = ap.parse_args()

    path = Path(args.root) / TOKENS_CSS
    text = path.read_text()
    want = expected()
    new_text, problems = apply(text, want, expected_shared(), args.write)

    covered = sum(len(v) for v in (want["light"], want["dark"], want["auto"])) + 2 * len(DARK_SHARED)
    if args.write:
        if problems:
            path.write_text(new_text)
            print("build-tokens: updated %d declaration(s)" % len(problems))
            for name, token, have, need in problems:
                print("  %-6s %-32s %s -> %s" % (name, token, have, need))
        else:
            print("build-tokens: already current (%d declarations)" % covered)
        return 0

    if problems:
        print("build-tokens: %d declaration(s) do not match the seeds" % len(problems))
        for name, token, have, need in problems:
            print("  %-6s %-32s committed %s, seeds give %s" % (name, token, have, need))
        print("\nRun: python3 tools/build-tokens.py --write")
        return 1
    print("build-tokens: OK, %d generated declarations match the seeds" % covered)
    return 0


if __name__ == "__main__":
    sys.exit(main())
