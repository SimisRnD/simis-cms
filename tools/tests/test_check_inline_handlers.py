"""check-inline-handlers.py: inline on*= handlers and javascript: URLs.

Both are script under the CSP the app sends (script-src 'self' + nonce, no
'unsafe-inline'), so both silently do nothing when clicked. The tool reports the
first against an allowlist of recorded debt; the second has no allowlist,
because every occurrence was converted in issue #1383.
"""

from conftest import run_tool, write

TOOL = "check-inline-handlers.py"
JSP_ROOT = "src/main/webapp/WEB-INF/jsp"

CLEAN = '<p>Nothing to see</p>\n'
JS_URL = '<a href="javascript:deleteThing(1)">Delete</a>\n'
JS_URL_IN_JSP_COMMENT = '<%--<a href="javascript:deleteThing(1)">Delete</a>--%>\n'
JS_URL_IN_SCRIPT = '<script nonce="x">var s = \'href="javascript:x()"\';</script>\n'
CONVERTED = '<a href="#" data-js-call="deleteThing" data-js-arg1="1">Delete</a>\n'


def fill(repo, count=60):
    """The tool refuses to run against fewer than 50 JSPs, so a regex or path
    change cannot quietly turn it into a no-op. Give it a big enough tree."""
    for i in range(count):
        write(repo, f"{JSP_ROOT}/filler{i}.jsp", CLEAN)


def test_clean_tree_passes_strict(repo):
    fill(repo)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "No javascript: URLs" in r.stdout


def test_javascript_url_fails_strict(repo):
    fill(repo)
    write(repo, f"{JSP_ROOT}/bad.jsp", JS_URL)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "bad.jsp" in r.stdout


def test_javascript_url_reported_but_exit_zero_without_strict(repo):
    fill(repo)
    write(repo, f"{JSP_ROOT}/bad.jsp", JS_URL)
    r = run_tool(TOOL, repo)
    assert r.returncode == 0
    assert "bad.jsp" in r.stdout


def test_converted_link_passes_strict(repo):
    # The replacement form must not trip the gate it was introduced to satisfy.
    fill(repo)
    write(repo, f"{JSP_ROOT}/converted.jsp", CONVERTED)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_javascript_url_inside_a_jsp_comment_is_ignored(repo):
    # Commented-out markup is not a live control; flagging it would train people
    # to work around the gate rather than fix anything.
    fill(repo)
    write(repo, f"{JSP_ROOT}/commented.jsp", JS_URL_IN_JSP_COMMENT)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_javascript_url_inside_a_script_block_is_ignored(repo):
    # A string containing the text inside a nonce'd script is not an href.
    fill(repo)
    write(repo, f"{JSP_ROOT}/scripted.jsp", JS_URL_IN_SCRIPT)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_refuses_to_run_against_too_few_jsps(repo):
    # The no-op guard itself, since everything above depends on it.
    write(repo, f"{JSP_ROOT}/only.jsp", CLEAN)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode != 0
    assert "scanned only" in (r.stdout + r.stderr)
