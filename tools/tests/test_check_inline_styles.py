"""check-inline-styles.py: a JSP may not gain an inline style attribute.

The page's CSP has to carry style-src 'unsafe-inline' for as long as any template
renders a style= attribute -- a nonce covers <style> elements, never attributes --
and SecurityScorecard reports that keyword as a finding. 351 attributes exist today,
recorded per file as a backlog that may only shrink (issue #1999).
"""

import os
import subprocess
import sys

from conftest import TOOLS_DIR, run_tool, write

TOOL = "check-inline-styles.py"
JSP_ROOT = "src/main/webapp/WEB-INF/jsp"
# A real BACKLOG entry, recorded at 1.
BACKLOGGED = "admin/allowed-ip-list-form.jsp"

CLEAN = '<p class="callout">Nothing to see</p>\n'


def fill(repo, count=60):
    """The tool refuses to run against fewer than 50 templates, so a path or walk
    change cannot quietly turn it into a no-op. Give it a big enough tree."""
    for i in range(count):
        write(repo, f"{JSP_ROOT}/filler{i}.jsp", CLEAN)


def check(repo, *args):
    return run_tool(TOOL, repo, *args)


def test_clean_tree_passes_strict(repo):
    fill(repo)
    result = check(repo, "--strict")
    assert result.returncode == 0, result.stdout + result.stderr
    assert "OK" in result.stdout


def test_new_inline_style_fails_strict(repo):
    fill(repo)
    write(repo, f"{JSP_ROOT}/cms/new.jsp", '<p>a</p>\n<p style="margin-bottom:0">b</p>\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "jsp/cms/new.jsp: line 2" in result.stdout
    assert "!important" in result.stdout


def test_non_strict_reports_but_exits_zero(repo):
    fill(repo)
    write(repo, f"{JSP_ROOT}/cms/new.jsp", '<p style="margin-bottom:0">b</p>\n')
    result = check(repo)
    assert result.returncode == 0
    assert "FAIL" in result.stdout


def test_strict_from_the_environment(repo):
    fill(repo)
    write(repo, f"{JSP_ROOT}/cms/new.jsp", '<p style="margin-bottom:0">b</p>\n')
    result = subprocess.run([sys.executable, str(TOOLS_DIR / TOOL), str(repo)],
                            capture_output=True, text=True, env={**os.environ, "STRICT": "1"})
    assert result.returncode == 1


def test_single_quoted_attribute_counts(repo):
    fill(repo)
    write(repo, f"{JSP_ROOT}/cms/new.jsp", "<p style='color:red'>b</p>\n")
    assert check(repo, "--strict").returncode == 1


def test_computed_attribute_counts(repo):
    # 32 of today's 351 are built from EL -- still an attribute, still refused.
    fill(repo)
    write(repo, f"{JSP_ROOT}/cms/new.jsp", '<img style="object-position: <c:out value="${pos}"/>">\n')
    assert check(repo, "--strict").returncode == 1


def test_backlogged_file_within_its_count_passes(repo):
    fill(repo)
    write(repo, f"{JSP_ROOT}/{BACKLOGGED}", '<div style="display:none">x</div>\n')
    assert check(repo, "--strict").returncode == 0


def test_backlogged_file_over_its_count_fails(repo):
    fill(repo)
    write(repo, f"{JSP_ROOT}/{BACKLOGGED}",
          '<div style="display:none">x</div>\n<div style="display:none">y</div>\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "backlog allows 1" in result.stdout


def test_a_backlog_ahead_of_its_counts_is_noted_not_failed(repo):
    # Fixing one must never be blocked -- it is reported so the number comes down.
    fill(repo)
    result = check(repo, "--strict")
    assert result.returncode == 0
    assert "NOTE" in result.stdout
    assert f"jsp/{BACKLOGGED}: 1 -> 0" in result.stdout


def test_similar_attribute_names_are_not_findings(repo):
    fill(repo)
    write(repo, f"{JSP_ROOT}/cms/new.jsp",
          '<div data-style="x" class="styled" data-font-style="italic">x</div>\n')
    assert check(repo, "--strict").returncode == 0


def test_commented_out_markup_is_ignored(repo):
    fill(repo)
    write(repo, f"{JSP_ROOT}/cms/new.jsp",
          '<%-- <p style="color:red">old</p> --%>\n<!-- <p style="color:red"> -->\n')
    assert check(repo, "--strict").returncode == 0


def test_script_bodies_are_ignored(repo):
    # Out of scope here, by design: script-built markup is found by the report-only trial.
    fill(repo)
    write(repo, f"{JSP_ROOT}/cms/new.jsp",
          '<script nonce="x">\n  el.innerHTML = \'<b style="color:red">\';\n</script>\n')
    assert check(repo, "--strict").returncode == 0


def test_line_numbers_survive_a_multi_line_comment(repo):
    fill(repo)
    write(repo, f"{JSP_ROOT}/cms/new.jsp",
          '<%--\n  one\n  two\n--%>\n<p style="color:red">x</p>\n')
    result = check(repo, "--strict")
    assert "jsp/cms/new.jsp: line 5" in result.stdout


def test_email_templates_are_out_of_scope(repo):
    # Mail clients need inline styles, and no email renders under the page CSP.
    fill(repo)
    write(repo, "src/main/webapp/WEB-INF/email-templates/cms/site-sign-up.html",
          '<td style="padding:0">x</td>\n')
    assert check(repo, "--strict").returncode == 0


def test_refuses_to_run_against_too_few_templates(repo):
    fill(repo, count=3)
    result = check(repo, "--strict")
    assert result.returncode == 2
    assert "scanned only 3" in result.stderr


def test_missing_jsp_tree_exits_two(repo):
    result = check(repo)
    assert result.returncode == 2
    assert "MISSING" in result.stderr
