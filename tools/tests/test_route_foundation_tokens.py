"""route-foundation-tokens.py: routes Foundation's palette without changing how it renders."""

import re
from pathlib import Path

from conftest import run_tool, write

REPO_ROOT = Path(__file__).resolve().parents[2]

TOOL = "route-foundation-tokens.py"

SOURCE = "src/main/webapp/css/foundation-6.8.1/foundation.min.css"
GENERATED = "src/main/webapp/css/foundation-6.8.1/foundation.tokens.min.css"

# One declaration per base colour, plus a colour the palette does not contain and
# a hex inside a url() -- both of which must survive untouched.
SAMPLE = (
    ".a{color:#fefefe;background:#0a0a0a}"
    ".b{border-color:#1779ba;background-color:#767676}"
    ".c{color:#3adb76;border:1px solid #ffae00}"
    ".d{background:#cc4b37;color:#e6e6e6}"
    ".e{border:1px solid #cacaca;color:#8a8a8a}"
    ".f{color:#0c3d5d}"                                   # derived shade, deliberately not routed
    ".g{background:url(data:image/svg+xml;utf8,<svg fill='#1779ba'/>)}"  # must not be rewritten
)


def seed(repo, css=SAMPLE):
    write(repo, SOURCE, css)


def test_generates_and_routes_the_base_palette(repo):
    seed(repo)
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr
    out = (repo / GENERATED).read_text()
    # $white and $black are role-split (see test_splits_white_by_role); the rest are 1:1.
    assert "var(--sc-fnd-on-accent,#fefefe)" in out
    assert "var(--sc-fnd-ink-surface,#0a0a0a)" in out
    assert "var(--sc-fnd-primary,#1779ba)" in out
    assert "var(--sc-fnd-alert,#cc4b37)" in out


def test_keeps_the_original_colour_as_the_fallback(repo):
    """The fallback is what makes this safe: no token stylesheet still renders stock Foundation."""
    seed(repo)
    run_tool(TOOL, repo)
    out = (repo / GENERATED).read_text()
    for token, colour in re.findall(r"var\((--sc-fnd-[a-z-]+),(#[0-9a-fA-F]{3,8})\)", out):
        assert colour  # every reference carries one
    assert re.search(r"var\(--sc-fnd-[a-z-]+\)", out) is None, "a var() without a fallback"


def test_reverse_substitution_reproduces_the_input_exactly(repo):
    """The strongest guarantee available: substituting every var() back yields the pristine file,
    so the transform changed colours and nothing else -- no whitespace, no selectors, no rules."""
    seed(repo)
    run_tool(TOOL, repo)
    out = (repo / GENERATED).read_text()
    body = out[out.index("*/") + 3:]
    restored = re.sub(r"var\(--sc-fnd-[a-z-]+,(#[0-9a-fA-F]{3,8})\)", r"\1", body)
    assert restored == SAMPLE


def test_leaves_derived_shades_alone(repo):
    """Only the ten base colours are routed; Sass-computed hover shades stay literal for now."""
    seed(repo)
    run_tool(TOOL, repo)
    out = (repo / GENERATED).read_text()
    assert "#0c3d5d" in out
    assert "var(--sc-fnd" not in out[out.index("#0c3d5d") - 40:out.index("#0c3d5d")]


def test_never_rewrites_a_hex_inside_a_url(repo):
    """An inline SVG data URI would be corrupted by a var() reference."""
    seed(repo)
    run_tool(TOOL, repo)
    out = (repo / GENERATED).read_text()
    assert "url(data:image/svg+xml;utf8,<svg fill='#1779ba'/>)" in out


def test_check_mode_passes_when_generated_file_is_current(repo):
    seed(repo)
    run_tool(TOOL, repo)
    r = run_tool(TOOL, repo, "--check")
    assert r.returncode == 0, r.stdout + r.stderr


def test_check_mode_fails_when_generated_file_is_stale(repo):
    """Catches a re-vendored Foundation that nobody regenerated."""
    seed(repo)
    run_tool(TOOL, repo)
    write(repo, SOURCE, SAMPLE + ".h{color:#1779ba}")
    r = run_tool(TOOL, repo, "--check")
    assert r.returncode == 1
    assert "STALE" in r.stdout + r.stderr


def test_check_mode_fails_when_generated_file_is_hand_edited(repo):
    seed(repo)
    run_tool(TOOL, repo)
    write(repo, GENERATED, "/* someone edited the generated file */")
    r = run_tool(TOOL, repo, "--check")
    assert r.returncode == 1


def test_check_mode_fails_when_generated_file_is_missing(repo):
    seed(repo)
    r = run_tool(TOOL, repo, "--check")
    assert r.returncode == 1
    assert "MISSING" in r.stdout + r.stderr


def test_splits_white_by_role(repo):
    """$white is a surface in one place and text-on-a-coloured-fill in another; in a dark
    theme those move in opposite directions, so they cannot share a token."""
    seed(repo, ".a{background:#fefefe}.b{color:#fefefe}")
    run_tool(TOOL, repo)
    out = (repo / GENERATED).read_text()
    assert "background:var(--sc-fnd-surface,#fefefe)" in out
    assert "color:var(--sc-fnd-on-accent,#fefefe)" in out


def test_splits_black_by_role(repo):
    seed(repo, ".a{background:#0a0a0a}.b{color:#0a0a0a}")
    run_tool(TOOL, repo)
    out = (repo / GENERATED).read_text()
    assert "background:var(--sc-fnd-ink-surface,#0a0a0a)" in out
    assert "color:var(--sc-fnd-ink,#0a0a0a)" in out


def test_non_text_properties_follow_the_surface(repo):
    """Borders and shadows sit against the surface, not the text drawn on it."""
    seed(repo, ".a{border:1px solid #fefefe}.b{box-shadow:0 0 2px #0a0a0a}")
    run_tool(TOOL, repo)
    out = (repo / GENERATED).read_text()
    assert "--sc-fnd-surface" in out
    assert "--sc-fnd-ink-surface" in out


def test_accent_and_status_colours_are_not_split(repo):
    """A themed alert is one colour whether it paints text, a fill or a border."""
    seed(repo, ".a{color:#cc4b37}.b{background:#cc4b37}.c{border:1px solid #cc4b37}")
    run_tool(TOOL, repo)
    out = (repo / GENERATED).read_text()
    assert out.count("var(--sc-fnd-alert,#cc4b37)") == 3


def test_text_on_a_light_accent_fill_keeps_a_token_that_never_darkens(repo):
    """$black is text in both rules, but only one of them is text on the PAGE.

    Foundation's success and warning fills keep their light values in dark mode, so text
    drawn on them has to stay dark while text on the page follows the theme. Routing both
    to --sc-fnd-ink is what put labels and badges at 1.66:1 and 1.70:1 (issue 1515).
    """
    seed(repo, ".label.success{background:#3adb76;color:#0a0a0a}.help-text{color:#0a0a0a}")
    run_tool(TOOL, repo)
    out = (repo / GENERATED).read_text()
    assert ".label.success{background:var(--sc-fnd-success,#3adb76);color:var(--sc-fnd-ink-on-accent,#0a0a0a)}" in out
    assert ".help-text{color:var(--sc-fnd-ink,#0a0a0a)}" in out


def test_the_light_accent_list_covers_every_such_rule_in_the_vendored_stylesheet():
    """The guard that fails by omission, against the REAL file rather than a sample.

    LIGHT_ACCENT_TEXT_SELECTORS is a hand-maintained list, so it cannot be wrong -- only
    incomplete, silently, until someone measures. Every rule in the vendored stylesheet
    that draws dark text on a light accent fill must come out of the generator carrying
    --sc-fnd-ink-on-accent. Re-vendoring Foundation, or adding a component, fails here
    rather than in a dark-mode screenshot nobody takes.
    """
    # $success and $warning, plus the darker shades Foundation derives for their
    # hover/focus states -- all of them keep their light values in dark mode.
    light_accent_fills = ("#3adb76", "#ffae00", "#22bb5b", "#cc8b00")
    source = (REPO_ROOT / SOURCE).read_text(encoding="utf-8")
    generated = (REPO_ROOT / GENERATED).read_text(encoding="utf-8")

    checked = 0
    for rule in re.finditer(r"([^{}]+)\{([^{}]*)\}", source):
        selector, body = rule.group(1).strip(), rule.group(2)
        fills = " ".join(re.findall(r"background(?:-color)?\s*:\s*([^;]*)", body, re.I)).lower()
        if not any(f in fills for f in light_accent_fills):
            continue
        if not re.search(r"(^|;)\s*color\s*:\s*#0a0a0a", body, re.I):
            continue
        checked += 1
        routed = re.search(re.escape(selector) + r"\{[^{}]*\}", generated)
        assert routed, f"{selector} is missing from the generated stylesheet"
        assert "color:var(--sc-fnd-ink-on-accent,#0a0a0a)" in routed.group(0), (
            f"{selector} draws dark text on a light accent fill but is not in "
            f"LIGHT_ACCENT_TEXT_SELECTORS -- it will turn light-on-light in dark mode"
        )

    assert checked >= 8, f"expected the buttons, labels and badges; matched {checked} rules"
