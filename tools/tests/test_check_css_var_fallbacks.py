"""check-css-var-fallbacks.py: a var() fallback must still equal its token's value."""

from conftest import run_tool, write

TOOL = "check-css-var-fallbacks.py"
CSS = "src/main/webapp/css/platform-thing.css"
TOKENS = "src/main/webapp/css/platform-tokens.css"
MAIN_JSP = "src/main/webapp/WEB-INF/jsp/main.jsp"

LIGHT = """:root {
  --sc-text-muted: #71767d;
  --sc-surface: #ffffff;
  --sc-radius-md: 4px;
}
:root[data-theme="dark"] {
  --sc-text-muted: #9aa0a6;
  --sc-surface: #17191e;
}
"""


def seed(repo, css, tokens=LIGHT):
    write(repo, TOKENS, tokens)
    write(repo, CSS, css)


def test_a_matching_fallback_passes(repo):
    seed(repo, ".a{color:var(--sc-text-muted, #71767d)}")
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr


def test_a_stale_fallback_fails(repo):
    """The defect this exists for: the token was repainted, the literal was not."""
    seed(repo, ".a{color:var(--sc-text-muted, #5b6470)}")
    r = run_tool(TOOL, repo)
    assert r.returncode == 1
    out = r.stdout + r.stderr
    assert "STALE" in out and "--sc-text-muted" in out and "#71767d" in out


def test_drift_is_not_only_about_colour(repo):
    """--sc-radius-md's fallback went stale the same way; a hex-only check misses it."""
    seed(repo, ".a{border-radius:var(--sc-radius-md, 6px)}")
    r = run_tool(TOOL, repo)
    assert r.returncode == 1
    assert "STALE" in r.stdout + r.stderr


def test_shorthand_hex_matches_its_long_form(repo):
    seed(repo, ".a{background:var(--sc-surface, #fff)}")
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr


def test_hex_case_is_not_drift(repo):
    seed(repo, ".a{color:var(--sc-text-muted, #71767D)}")
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr


def test_only_the_light_value_is_compared(repo):
    """A fallback is one literal and cannot track a token that repaints per theme;
    matching the dark value is drift from the value a default site actually gets."""
    seed(repo, ".a{color:var(--sc-text-muted, #9aa0a6)}")
    r = run_tool(TOOL, repo)
    assert r.returncode == 1
    assert "STALE" in r.stdout + r.stderr


def test_light_values_split_across_two_root_blocks_are_all_found(repo):
    """The real token file declares them in more than one :root; reading only the
    first would treat the rest as undefined."""
    seed(repo, ".a{color:var(--sc-late, #abcdef)}",
         tokens=':root{--sc-text-muted:#71767d}\n:root{--sc-late:#abcdef}\n'
                ':root[data-theme="dark"]{--sc-late:#000000}\n')
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr


def test_token_to_token_indirection_is_resolved(repo):
    seed(repo, ".a{color:var(--sc-fnd-black, #0a0a0a)}",
         tokens=':root{--sc-fnd-ink:#0a0a0a;--sc-fnd-black:var(--sc-fnd-ink)}\n'
                ':root[data-theme="dark"]{--sc-fnd-ink:#ffffff}\n')
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr


def test_indirection_through_a_cycle_does_not_hang(repo):
    seed(repo, ".a{color:var(--sc-a, #123456)}",
         tokens=':root{--sc-a:var(--sc-b);--sc-b:var(--sc-a)}\n')
    r = run_tool(TOOL, repo)
    assert r.returncode == 1
    assert "UNDEFINED" in r.stdout + r.stderr


def test_a_commented_out_token_does_not_count_as_a_declaration(repo):
    seed(repo, ".a{color:var(--sc-text-muted, #71767d)}",
         tokens=':root{/* --sc-text-muted: #5b6470; */ --sc-text-muted:#71767d}\n')
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr


def test_a_site_theme_token_has_no_static_value_to_compare(repo):
    """These are emitted inline per request from the site's own theme settings."""
    seed(repo, ".a{background:var(--sc-button-primary-background-color, #2c79be)}")
    write(repo, MAIN_JSP, "<style>--sc-button-primary-background-color:${c};</style>")
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr
    assert "1 site-theme" in r.stdout


def test_a_typo_is_not_excused_as_a_site_theme_token(repo):
    """The reason the site-theme names are read from the JSPs rather than waved
    through by prefix: a token that nothing declares or emits renders nothing."""
    seed(repo, ".a{color:var(--sc-txet-muted, #71767d)}")
    write(repo, MAIN_JSP, "<style>--sc-button-primary-background-color:${c};</style>")
    r = run_tool(TOOL, repo)
    assert r.returncode == 1
    out = r.stdout + r.stderr
    assert "UNDEFINED" in out and "--sc-txet-muted" in out


def test_a_fallback_containing_parens_is_compared_whole(repo):
    """Stopping at the first ')' would truncate rgba(...) and report false drift."""
    seed(repo, ".a{box-shadow:var(--sc-shadow, 0 1px 2px rgba(23, 25, 30, 0.06))}",
         tokens=":root{--sc-shadow:0 1px 2px rgba(23, 25, 30, 0.06)}\n")
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr


def test_drift_inside_a_parenthesised_fallback_is_still_caught(repo):
    seed(repo, ".a{box-shadow:var(--sc-shadow, 0 1px 2px rgba(0, 0, 0, 0.06))}",
         tokens=":root{--sc-shadow:0 1px 2px rgba(23, 25, 30, 0.06)}\n")
    r = run_tool(TOOL, repo)
    assert r.returncode == 1
    assert "STALE" in r.stdout + r.stderr


def test_an_allowlisted_approximation_passes(repo):
    """--sc-motion-ease was never literally 'ease'; it is a stand-in, not a stale copy."""
    seed(repo, ".a{transition:opacity 200ms var(--sc-motion-ease, ease)}",
         tokens=":root{--sc-motion-ease:cubic-bezier(0.2, 0, 0.2, 1)}\n")
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr
    assert "allowlisted" in r.stdout


def test_the_tokens_file_itself_is_not_scanned(repo):
    """It is the source of truth, not a consumer."""
    write(repo, TOKENS, LIGHT + ".x{color:var(--sc-text-muted, #5b6470)}")
    write(repo, CSS, ".a{color:var(--sc-text-muted, #71767d)}")
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr


def test_the_generated_foundation_stylesheet_is_not_scanned(repo):
    """It cannot drift -- its generator writes each fallback from the token value --
    and route-foundation-tokens.py --check already guards it byte-for-byte."""
    seed(repo, ".a{color:var(--sc-text-muted, #71767d)}")
    write(repo, "src/main/webapp/css/foundation-6.8.1/foundation.tokens.min.css",
          ".b{color:var(--sc-text-muted,#5b6470)}")
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr


def test_a_var_with_no_fallback_is_not_a_finding(repo):
    seed(repo, ".a{color:var(--sc-text-muted)}")
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr


def test_a_missing_token_stylesheet_fails(repo):
    """Silently passing would turn a deleted or moved token layer into a green build."""
    write(repo, CSS, ".a{color:var(--sc-text-muted, #71767d)}")
    r = run_tool(TOOL, repo)
    assert r.returncode == 1
    assert "MISSING" in r.stdout + r.stderr


def test_non_sc_custom_properties_are_ignored(repo):
    """Foundation and vendor properties are not ours to keep in sync."""
    seed(repo, ".a{color:var(--global-color, #1779ba)}")
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr
