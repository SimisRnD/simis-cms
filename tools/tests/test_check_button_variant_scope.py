"""check-button-variant-scope.py: a themed button colour must not reach .hollow or .clear.

main.jsp emits each theme.button.* property as a fill rule and an ink rule. A
hollow button has no fill by definition, so Foundation gives it a caption that
contrasts with the page rather than with a button colour. Filling it without also
recolouring the caption leaves the label painted in its own background -- which is
what shipped: every ink rule excluded the variants, no fill rule did, and the page
editor's Media Library button rendered as a blank grey slab at 1.000:1 (issue 1608).
"""

from conftest import run_tool, write

TOOL = "check-button-variant-scope.py"
MAIN_JSP = "src/main/webapp/WEB-INF/jsp/main.jsp"


def style(*rules: str) -> str:
    return "      <style>\n" + "\n".join("        " + r for r in rules) + "\n      </style>\n"


FILL = ('<c:if test="${!empty themePropertyMap[\'theme.button.secondary.backgroundColor\']}">'
        '%s{background-color:var(--sc-button-secondary-background-color)}</c:if>')
INK = ('<c:if test="${!empty inkSecondary}">'
       '%s{color:#fff !important}</c:if>')


def test_where_wrapped_exclusion_passes(repo):
    write(repo, MAIN_JSP, style(FILL % ".button.secondary:where(:not(.clear):not(.hollow))",
                                INK % ".button.secondary:not(.clear):not(.hollow)"))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "scoped away" in r.stdout


def test_bare_not_exclusion_also_passes(repo):
    # :where() is about specificity, not scope. Either spelling keeps the colour off a
    # variant, and which one a rule should use is a per-rule judgement.
    write(repo, MAIN_JSP, style(FILL % ".button.secondary:not(.clear):not(.hollow)"))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_unscoped_fill_fails(repo):
    # The shipped state: the ink rule excludes the variants and the fill beside it does not.
    write(repo, MAIN_JSP, style(FILL % ".button.secondary",
                                INK % ".button.secondary:not(.clear):not(.hollow)"))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert ".button.secondary" in r.stdout
    assert ":not(.hollow)" in r.stdout


def test_half_an_exclusion_still_fails(repo):
    # .clear has the same problem as .hollow and is easy to drop while adding the other.
    write(repo, MAIN_JSP, style(FILL % ".button.secondary:not(.hollow)"))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    finding = [l for l in r.stdout.split("\n") if l.strip().startswith("missing")][0]
    assert ":not(.clear)" in finding
    assert ":not(.hollow)" not in finding


def test_every_selector_in_a_list_is_checked(repo):
    # The hover rules carry three selectors; scoping the first two and missing the third
    # leaves the colour reaching a variant on exactly one state.
    rule = (".button.secondary:hover:where(:not(.clear):not(.hollow)), "
            ".button.secondary:focus:where(:not(.clear):not(.hollow)), "
            "#platform-menu ul.menu li a.button.secondary:hover")
    write(repo, MAIN_JSP, style(FILL % rule))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "#platform-menu" in r.stdout
    # The two scoped selectors beside it are not findings.
    assert r.stdout.startswith("FAIL  1 themed button rule selector(s)")


def test_a_comma_inside_where_is_not_a_separator(repo):
    # Splitting the selector list naively on commas would tear this rule into fragments
    # and report the tail as an unscoped selector.
    write(repo, MAIN_JSP, style(FILL % ".button.secondary:where(:not(.clear), :not(.x)):not(.hollow)"))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_a_themed_rule_that_paints_nothing_is_not_a_finding(repo):
    # Scope matters for colour. A theme property driving geometry has no caption to strand.
    rule = ('<c:if test="${!empty themePropertyMap[\'theme.button.radius\']}">'
            '.button{border-radius:var(--sc-button-radius)}</c:if>')
    write(repo, MAIN_JSP, style(rule))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_a_non_button_theme_rule_is_out_of_scope(repo):
    rule = ('<c:if test="${!empty themePropertyMap[\'theme.body.text.color\']}">'
            'body{color:var(--sc-body-text-color)}</c:if>')
    write(repo, MAIN_JSP, style(rule))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_reports_without_strict_but_does_not_fail(repo):
    write(repo, MAIN_JSP, style(FILL % ".button.secondary"))
    r = run_tool(TOOL, repo)
    assert r.returncode == 0
    assert "can reach" in r.stdout
