"""check-platform-class-rules.py: a platform-* class a JSP emits must have a rule.

This exists because the failure it catches is invisible everywhere else. On
2026-09-10 two features shipped without their stylesheets, hours apart, both lost
when a "Merge branch 'main' into <branch>" commit resolved platform.css by taking
main's side. The JSP compiled, the CSS parsed, every other gate passed, and the page
rendered wrong -- found by eye, after deployment, twice. See issue #1991.
"""

from conftest import run_tool, write

TOOL = "check-platform-class-rules.py"
JSP = "src/main/webapp/WEB-INF/jsp/cms/widget.jsp"
CSS = "src/main/webapp/css/platform.css"
TOKENS = "src/main/webapp/css/platform-tokens.css"


def check(repo, *args):
    return run_tool(TOOL, repo, *args)


def setup(repo, jsp="", css="", tokens=""):
    write(repo, JSP, jsp)
    write(repo, CSS, css)
    write(repo, TOKENS, tokens)


def test_missing_jsp_root_is_an_error(repo):
    result = check(repo)
    assert result.returncode == 1
    assert "MISSING" in result.stderr


def test_missing_stylesheet_is_an_error(repo):
    write(repo, JSP, "<div></div>\n")
    result = check(repo)
    assert result.returncode == 1
    assert "MISSING" in result.stderr
    assert "platform.css" in result.stderr


def test_styled_class_passes(repo):
    setup(repo, '<div class="platform-thing">x</div>\n', ".platform-thing { color: red; }\n")
    assert check(repo, "--strict").returncode == 0


def test_unstyled_class_is_a_finding(repo):
    setup(repo, '<div class="platform-thing">x</div>\n', "/* nothing */\n")
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "platform-thing" in result.stdout
    assert "cms/widget.jsp:1" in result.stdout


def test_non_strict_reports_but_exits_zero(repo):
    setup(repo, '<div class="platform-thing">x</div>\n', "")
    result = check(repo)
    assert result.returncode == 0
    assert "FAIL" in result.stdout


def test_a_rule_in_the_token_layer_counts(repo):
    # A class may legitimately be styled in platform-tokens.css rather than
    # platform.css; treating only the latter as "styled" would fail honest markup.
    setup(repo, '<div class="platform-thing">x</div>\n', "", ".platform-thing { color: red; }\n")
    assert check(repo, "--strict").returncode == 0


def test_the_regression_shape_is_caught(repo):
    # Exactly what happened to #1968 and #1974: the class survives, the rule does not.
    setup(repo,
          '<a class="platform-blog-overview-thumbnail" href="/x"><img src="/i.png"></a>\n',
          "/* the merge took main's side and the rules went with it */\n")
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "platform-blog-overview-thumbnail" in result.stdout


def test_allowlisted_class_may_be_unstyled(repo):
    setup(repo, '<a class="platform-skip-link" href="#main">Skip</a>\n', "")
    assert check(repo, "--strict").returncode == 0


def test_allowlisted_class_that_became_styled_is_reported(repo):
    # The list is checked in both directions so it shrinks as things are fixed
    # instead of quietly accumulating.
    setup(repo, '<a class="platform-skip-link" href="#main">Skip</a>\n',
          ".platform-skip-link { position: absolute; }\n")
    result = check(repo, "--strict")
    assert result.returncode == 0
    assert "now styled" in result.stdout
    assert "platform-skip-link" in result.stdout


def test_a_class_built_from_a_jsp_expression_is_skipped(repo):
    # The name is not knowable here. Stripping the expression would invent a class
    # that is never emitted and then fail on it.
    setup(repo, '<div class="platform-<c:out value="${x}"/>-thing">x</div>\n', "")
    result = check(repo, "--strict")
    assert result.returncode == 0 or "platform-" not in result.stdout.split("FAIL")[-1]


def test_commented_out_markup_is_not_counted(repo):
    setup(repo, '<%-- <div class="platform-ghost">x</div> --%>\n', "")
    result = check(repo, "--strict")
    assert result.returncode == 0
    assert "platform-ghost" not in result.stdout


def test_script_bodies_are_not_counted(repo):
    setup(repo, '<script>var s = \'<div class="platform-ghost">\';</script>\n', "")
    result = check(repo, "--strict")
    assert result.returncode == 0
    assert "platform-ghost" not in result.stdout


def test_non_platform_classes_are_ignored(repo):
    setup(repo, '<div class="callout box grid-x">x</div>\n', "")
    assert check(repo, "--strict").returncode == 0


def test_jspf_fragments_are_scanned(repo):
    write(repo, "src/main/webapp/WEB-INF/jsp/layout.jspf",
          '<div class="platform-fragment">x</div>\n')
    write(repo, CSS, "")
    write(repo, TOKENS, "")
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "platform-fragment" in result.stdout


def test_single_quoted_class_attributes_are_scanned(repo):
    setup(repo, "<div class='platform-thing'>x</div>\n", "")
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "platform-thing" in result.stdout
