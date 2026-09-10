"""check-theme-token-overlap.py: the runtime theme and the token layer must not both paint a thing.

main.jsp emits the site's theme.* properties as CSS rules at runtime. Those rules can claim the
same property, on the same component, as a first-party stylesheet rule using a --sc-* token.
Whichever wins is then decided by specificity and source order rather than by a decision, and
neither file shows the collision -- one lives in a JSP, the other in CSS.

check-css-token-adoption.py cannot see this: a var() pointing at a runtime property contains no
hex literal to count. That is issue 1598, and it is why the adoption number sat unchanged across
three merged PRs while the incoherence on screen got worse.
"""

from conftest import run_tool, write

TOOL = "check-theme-token-overlap.py"
MAIN_JSP = "src/main/webapp/WEB-INF/jsp/main.jsp"
CSS = "src/main/webapp/css/platform.css"

GUARDED_THEME = """      <style>
        <c:set var="isAdminConsole" value="${fn:startsWith(pageRenderInfo.name, '/admin')}"/>
        <c:if test="${!isAdminConsole}">
        <c:if test="${!empty themePropertyMap['theme.widget.backgroundColor']}">.widget{background-color:var(--sc-widget-background-color)}</c:if>
        </c:if>
      </style>
"""

UNGUARDED_THEME = """      <style>
        <c:set var="isAdminConsole" value="${fn:startsWith(pageRenderInfo.name, '/admin')}"/>
        <c:if test="${!empty themePropertyMap['theme.widget.backgroundColor']}">.widget{background-color:var(--sc-widget-background-color)}</c:if>
      </style>
"""


def test_a_property_claimed_by_both_is_reported(repo):
    # The shape issue 1598 exists for: the theme paints .widget's background from a runtime
    # property, and a stylesheet paints the same property from a token. Nothing in either file
    # shows the other one.
    write(repo, MAIN_JSP, UNGUARDED_THEME)
    write(repo, CSS, ".widget { background-color: var(--sc-surface-raised); }")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert ".widget" in r.stdout
    assert "background-color" in r.stdout


def test_an_unguarded_theme_rule_is_flagged_as_reaching_the_console(repo):
    # The 22-rule leak of issue 1594. A guarded rule and an unguarded one are both overlaps, but
    # only the unguarded one also lands in the admin console, so the report says which.
    write(repo, MAIN_JSP, UNGUARDED_THEME)
    write(repo, CSS, ".widget { background-color: var(--sc-surface-raised); }")
    r = run_tool(TOOL, repo, "--strict")
    assert "REACHES THE CONSOLE" in r.stdout


def test_a_guarded_theme_rule_is_still_an_overlap_but_named_as_guarded(repo):
    # Guarding scopes a rule away from the console; it does not stop the two layers competing on
    # the public site, so it is still reported -- with the scope stated.
    write(repo, MAIN_JSP, GUARDED_THEME)
    write(repo, CSS, ".widget { background-color: var(--sc-surface-raised); }")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "guarded from console" in r.stdout


def test_different_properties_on_the_same_selector_do_not_collide(repo):
    # The theme sets the fill, the stylesheet sets the ink. Both claim .widget, neither claims the
    # same property, and reporting it would be noise.
    write(repo, MAIN_JSP, UNGUARDED_THEME)
    write(repo, CSS, ".widget { color: var(--sc-text); }")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout


def test_a_hardcoded_property_beside_a_token_one_is_not_a_token_claim(repo):
    # The false positive found on this script's first run. A rule setting background-color to a
    # literal beside color: var(--sc-...) is making a token claim on the ink only; counting every
    # colour property in any rule that merely contains a token inflated the token side by 66.
    write(repo, MAIN_JSP, UNGUARDED_THEME)
    write(repo, CSS, ".widget { background-color: #353535; color: var(--sc-text-inverse); }")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout


def test_narrowing_pseudos_do_not_hide_an_overlap(repo):
    # .button:where(:not(.clear)) and .button aim at the same component; a comparison that treated
    # them as different selectors would miss every real theme rule, which all carry these.
    theme = """      <style>
        <c:if test="${!empty themePropertyMap['theme.button.backgroundColor']}">.button:where(:not(.clear):not(.hollow)){background-color:var(--sc-button-background-color)}</c:if>
      </style>
"""
    write(repo, MAIN_JSP, theme)
    write(repo, CSS, ".button { background-color: var(--sc-fnd-primary); }")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout
    assert ".button" in r.stdout


def test_a_state_pseudo_is_a_different_claim(repo):
    # A rest rule and a hover rule are not competing; collapsing :hover away would invent an
    # overlap that does not exist.
    theme = """      <style>
        <c:if test="${!empty themePropertyMap['theme.button.hoverBackgroundColor']}">.button:hover{background-color:var(--sc-button-hover-background-color)}</c:if>
      </style>
"""
    write(repo, MAIN_JSP, theme)
    write(repo, CSS, ".button { background-color: var(--sc-fnd-primary); }")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout


def test_reports_without_strict_but_does_not_fail(repo):
    write(repo, MAIN_JSP, UNGUARDED_THEME)
    write(repo, CSS, ".widget { background-color: var(--sc-surface-raised); }")
    r = run_tool(TOOL, repo)
    assert r.returncode == 0
    assert ".widget" in r.stdout
