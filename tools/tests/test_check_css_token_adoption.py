"""check-css-token-adoption.py: hardcoded colour counts may fall, never rise."""

from conftest import run_tool, write

TOOL = "check-css-token-adoption.py"
CSS = "src/main/webapp/css/platform-thing.css"
BASELINE = "tools/css-colour-baseline.txt"

TWO = ".a{color:#ffffff}.b{border:1px solid #cacaca}"


def seed(repo, css=TWO):
    write(repo, CSS, css)


def test_write_creates_a_baseline(repo):
    seed(repo)
    r = run_tool(TOOL, repo, "--write")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "2 " + CSS in (repo / BASELINE).read_text()


def test_passes_when_unchanged(repo):
    seed(repo)
    run_tool(TOOL, repo, "--write")
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr


def test_fails_when_a_colour_is_added(repo):
    seed(repo)
    run_tool(TOOL, repo, "--write")
    write(repo, CSS, TWO + ".c{background:#123456}")
    r = run_tool(TOOL, repo)
    assert r.returncode == 1
    assert "REGRESSED" in r.stdout + r.stderr


def test_passes_and_reports_when_a_colour_is_routed_away(repo):
    """Going below baseline is an improvement, not a failure -- an in-progress branch
    must never be blocked by a baseline that is merely out of date."""
    seed(repo)
    run_tool(TOOL, repo, "--write")
    write(repo, CSS, ".a{color:var(--sc-text-inverse, #ffffff)}.b{border:1px solid #cacaca}")
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr
    assert "improved" in r.stdout


def test_a_var_fallback_is_not_a_hardcoded_colour(repo):
    """Penalising fallbacks would push authors to drop them, which is worse than the literal."""
    seed(repo, ".a{color:var(--sc-text, #17191e)}")
    run_tool(TOOL, repo, "--write")
    assert "0 " + CSS in (repo / BASELINE).read_text()


def test_shorthand_hex_counts(repo):
    seed(repo, ".a{color:#fff}.b{color:#abc}")
    run_tool(TOOL, repo, "--write")
    assert "2 " + CSS in (repo / BASELINE).read_text()


def test_a_new_untracked_stylesheet_fails(repo):
    """Otherwise a whole new file of literals slips past the ratchet entirely."""
    seed(repo)
    run_tool(TOOL, repo, "--write")
    write(repo, "src/main/webapp/css/platform-new.css", ".x{color:#ff0000}")
    r = run_tool(TOOL, repo)
    assert r.returncode == 1
    assert "UNTRACKED" in r.stdout + r.stderr


def test_the_tokens_file_is_excluded(repo):
    """platform-tokens.css is where colours are supposed to be literal."""
    seed(repo)
    write(repo, "src/main/webapp/css/platform-tokens.css", ":root{--sc-text:#17191e;--sc-surface:#fff}")
    run_tool(TOOL, repo, "--write")
    assert "platform-tokens.css" not in (repo / BASELINE).read_text()


def test_missing_baseline_fails(repo):
    seed(repo)
    r = run_tool(TOOL, repo)
    assert r.returncode == 1
    assert "MISSING" in r.stdout + r.stderr
