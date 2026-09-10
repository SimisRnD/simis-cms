"""check-theme-scope.py: the site's theme colours must not reach the admin console.

main.jsp emits the site's theme.* properties as CSS. Those rules belong on the
public site and compete with the design tokens in the admin console, so they are
wrapped in a guard. Issue 1587 wrapped three of them and left nineteen, which put
a token-driven background under theme-driven components; issue 1594 wrapped the
rest. Nothing detected either state, which is issue 1598 and why this tool exists.
"""

from conftest import run_tool, write

TOOL = "check-theme-scope.py"
MAIN_JSP = "src/main/webapp/WEB-INF/jsp/main.jsp"

GUARDED = """      <style>
        <c:set var="isAdminConsole" value="${fn:startsWith(pageRenderInfo.name, '/admin')}"/>
        <c:if test="${!isAdminConsole}">
        <c:if test="${!empty themePropertyMap['theme.body.text.color']}">body{color:var(--sc-body-text-color)}</c:if>
        <c:if test="${!empty themePropertyMap['theme.callout.backgroundColor']}">.callout{background-color:var(--sc-callout-background-color)}</c:if>
        </c:if>
      </style>
"""

UNGUARDED = """      <style>
        <c:if test="${!empty themePropertyMap['theme.body.text.color']}">body{color:var(--sc-body-text-color)}</c:if>
        <c:if test="${!empty themePropertyMap['theme.callout.backgroundColor']}">.callout{background-color:var(--sc-callout-background-color)}</c:if>
      </style>
"""

# The state issue 1587 left behind: a guard exists, but a rule sits outside it.
PARTIALLY_GUARDED = """      <style>
        <c:set var="isAdminConsole" value="${fn:startsWith(pageRenderInfo.name, '/admin')}"/>
        <c:if test="${!isAdminConsole}">
        <c:if test="${!empty themePropertyMap['theme.body.text.color']}">body{color:var(--sc-body-text-color)}</c:if>
        </c:if>
        <c:if test="${!empty themePropertyMap['theme.button.primary.backgroundColor']}">.button.primary{background-color:var(--sc-button-primary-background-color)}</c:if>
      </style>
"""

# Declares the custom properties without painting anything -- harmless outside the guard.
DECLARATIONS_ONLY = """      <style>
        :root {
        <c:if test="${!empty themePropertyMap['theme.body.text.color']}">--sc-body-text-color:<c:out value="${themePropertyMap['theme.body.text.color']}"/>;</c:if>
        <c:if test="${!empty themePropertyMap['theme.callout.backgroundColor']}">--sc-callout-background-color:<c:out value="${themePropertyMap['theme.callout.backgroundColor']}"/>;</c:if>
        }
        <c:set var="isAdminConsole" value="${fn:startsWith(pageRenderInfo.name, '/admin')}"/>
        <c:if test="${!isAdminConsole}">
        <c:if test="${!empty themePropertyMap['theme.body.text.color']}">body{color:var(--sc-body-text-color)}</c:if>
        </c:if>
      </style>
"""

# Typography is deliberately out of scope: whether the console inherits the site's
# face is a separate decision that has not been made.
FONT_OUTSIDE_GUARD = """      <style>
        <c:if test="${!empty themePropertyMap['theme.fonts.body']}">body{font-family:'Inter',sans-serif}</c:if>
        <c:set var="isAdminConsole" value="${fn:startsWith(pageRenderInfo.name, '/admin')}"/>
        <c:if test="${!isAdminConsole}">
        <c:if test="${!empty themePropertyMap['theme.body.text.color']}">body{color:var(--sc-body-text-color)}</c:if>
        </c:if>
      </style>
"""


def test_fully_guarded_passes(repo):
    write(repo, MAIN_JSP, GUARDED)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "scoped away" in r.stdout


def test_no_guard_at_all_fails(repo):
    # The state before issue 1587: every theme colour rule reaches the console.
    write(repo, MAIN_JSP, UNGUARDED)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "no <c:if" in r.stdout


def test_a_rule_left_outside_the_guard_fails(repo):
    # The state issue 1587 left behind, and the one this tool exists to catch: the
    # guard is present, so a reader assumes the block is handled, and it is not.
    write(repo, MAIN_JSP, PARTIALLY_GUARDED)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "outside the admin-console guard" in r.stdout
    assert "theme.button.primary.backgroundColor" in r.stdout


def test_custom_property_declarations_are_not_findings(repo):
    # A declaration defines a value without painting anything. Counting them made
    # 44 harmless lines look like failures on the first run of this tool.
    write(repo, MAIN_JSP, DECLARATIONS_ONLY)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_a_font_rule_outside_the_guard_is_allowed(repo):
    write(repo, MAIN_JSP, FONT_OUTSIDE_GUARD)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_reports_without_strict_but_does_not_fail(repo):
    write(repo, MAIN_JSP, PARTIALLY_GUARDED)
    r = run_tool(TOOL, repo)
    assert r.returncode == 0
    assert "outside the admin-console guard" in r.stdout

# The site header renders on admin pages too and has no token-layer equivalent, so guarding its
# rules removed a theme without replacing it -- see the tool's docstring.
HEADER_OUTSIDE_GUARD = """      <style>
        <c:set var="isAdminConsole" value="${fn:startsWith(pageRenderInfo.name, '/admin')}"/>
        <c:if test="${!isAdminConsole}">
        <c:if test="${!empty themePropertyMap['theme.body.text.color']}">body{color:var(--sc-body-text-color)}</c:if>
        </c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.backgroundColor']}">#platform-menu,#platform-small-menu{background-color:var(--sc-topbar-background-color)}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.menu.text.color']}">#platform-menu ul.menu li a{color:var(--sc-topbar-menu-text-color)}</c:if>
      </style>
"""

# One selector in the header, one not: the second half still reaches the console.
MIXED_RULE_OUTSIDE_GUARD = """      <style>
        <c:set var="isAdminConsole" value="${fn:startsWith(pageRenderInfo.name, '/admin')}"/>
        <c:if test="${!isAdminConsole}">
        <c:if test="${!empty themePropertyMap['theme.body.text.color']}">body{color:var(--sc-body-text-color)}</c:if>
        </c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.menu.text.color']}">.callout.header, #platform-menu button.button i.fa{color:var(--sc-topbar-menu-text-color)}</c:if>
      </style>
"""


def test_a_header_scoped_rule_may_sit_outside_the_guard(repo):
    write(repo, MAIN_JSP, HEADER_OUTSIDE_GUARD)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_a_rule_mixing_header_and_non_header_selectors_still_fails(repo):
    # The exemption is per-selector, not per-rule: ".callout.header" is page content, and letting
    # the whole rule through because it also names #platform-menu would put a theme colour on a
    # console callout. This exact rule existed and had to be split.
    write(repo, MAIN_JSP, MIXED_RULE_OUTSIDE_GUARD)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert ".callout.header" in r.stdout


def test_a_selector_merely_containing_a_header_id_is_not_exempt(repo):
    # Prefix match, not substring: a rule that reaches #platform-menu from an outer selector is
    # not confined to the header.
    rule = ('<c:if test="${!empty themePropertyMap[\'theme.topbar.backgroundColor\']}">'
            'body .platform-body #platform-menu{background-color:var(--sc-topbar-background-color)}</c:if>')
    write(repo, MAIN_JSP, HEADER_OUTSIDE_GUARD.replace(
        "      </style>", "        " + rule + "\n      </style>"))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
