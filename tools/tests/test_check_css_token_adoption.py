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


def test_a_hex_in_a_comment_is_not_a_hardcoded_colour(repo):
    """Nothing inside /* */ renders, so nothing inside it is a colour the theme misses.

    Both shapes get counted by a naive hex scan: an issue reference in prose is
    hex-shaped (#1483 is four hex digits), and a real value can be quoted in prose or
    left behind in a commented-out rule. Charging a file for either inflates its
    baseline, and since the baseline is a floor, that inflation is permanent slack.
    """
    seed(repo, "\n".join([
        "/* Routed to var(--sc-link) in issue #1483; was #1779ba. */",
        "/* .old { color: #56C4CE; }  disabled, see #772 */",
        ".a{color:#ffffff}",
    ]))
    run_tool(TOOL, repo, "--write")
    assert "1 " + CSS in (repo / BASELINE).read_text()


def test_a_multiline_comment_is_stripped_whole(repo):
    """A block comment is the usual place a rationale -- and its hexes -- gets written."""
    seed(repo, "/*\n * Was #1779ba, see issue #1364.\n * #609ACE stayed for now.\n */\n.a{color:#ffffff}")
    run_tool(TOOL, repo, "--write")
    assert "1 " + CSS in (repo / BASELINE).read_text()


def test_a_fallback_and_a_comment_in_one_file_each_stay_excluded(repo):
    """Adding the comment rule must not cost the fallback rule, or the reverse."""
    seed(repo, "\n".join([
        "/* Routed to var(--sc-text) in issue #1483. */",
        ".a{color:var(--sc-surface, #ffffff)}",
        ".b{background:#cacaca}",
    ]))
    run_tool(TOOL, repo, "--write")
    assert "1 " + CSS in (repo / BASELINE).read_text()


def test_prose_naming_a_fallback_does_not_swallow_the_rules_after_it(repo):
    """Comments are stripped first, and the order is load-bearing.

    The fallback pattern runs to the next ``)``, which in a comment may be a paren
    belonging to live CSS further down. Stripping fallbacks first would let an
    unclosed ``var(`` in prose consume everything up to that paren -- here the whole
    ``.a`` rule -- so real literals would vanish and the file would be handed even
    more slack than counting comment hexes gave it.
    """
    seed(repo, "\n".join([
        "/* Written as a var(--sc-link, fallback pair; see the docstring. */",
        ".a{color:#ffffff}",
        ".b{box-shadow:0 0 0 1px rgba(0,0,0,.2)}",
    ]))
    run_tool(TOOL, repo, "--write")
    assert "1 " + CSS in (repo / BASELINE).read_text()


def test_commenting_out_a_rule_lowers_the_count(repo):
    """A disabled rule ships no colour, so the ratchet must see the file improve."""
    seed(repo)
    run_tool(TOOL, repo, "--write")
    write(repo, CSS, ".a{color:#ffffff}/* .b{border:1px solid #cacaca} */")
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr
    assert "improved" in r.stdout


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
