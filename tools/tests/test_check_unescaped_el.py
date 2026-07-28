"""check-unescaped-el.py: bare JSP EL vs escaped output."""

from conftest import run_tool, write

TOOL = "check-unescaped-el.py"

ESCAPED = '<%@ taglib prefix="c" uri="jakarta.tags.core" %>\n<c:out value="${user.name}"/>\n'
BARE = '<%@ taglib prefix="c" uri="jakarta.tags.core" %>\n<p>${user.name}</p>\n'
ATTR_BARE = '<%@ taglib prefix="c" uri="jakarta.tags.core" %>\n<div class="${user.name}"></div>\n'


def test_escaped_el_passes_strict(repo):
    write(repo, "src/main/webapp/WEB-INF/jsp/ok.jsp", ESCAPED)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_bare_el_fails_strict(repo):
    write(repo, "src/main/webapp/WEB-INF/jsp/bad.jsp", BARE)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "bad.jsp" in (r.stdout + r.stderr)


def test_bare_el_reported_but_exit_zero_without_strict(repo):
    write(repo, "src/main/webapp/WEB-INF/jsp/bad.jsp", BARE)
    r = run_tool(TOOL, repo)
    assert r.returncode == 0


def test_attr_context_bare_el_fails_strict(repo):
    # Regression test: --strict once carved ATTR context out of its exit code
    # ("report-only pending #319... once #319 merges, drop this exclusion"),
    # so a raw HTML-attribute injection point like this could sit unallowlisted
    # on main with the gate still green. #319 merged and the carve-out was
    # removed -- this pins ATTR context to the same enforcement as HTML/JS.
    write(repo, "src/main/webapp/WEB-INF/jsp/bad-attr.jsp", ATTR_BARE)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "bad-attr.jsp" in (r.stdout + r.stderr)
