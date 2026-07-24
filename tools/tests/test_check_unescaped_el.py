"""check-unescaped-el.py: bare JSP EL vs escaped output."""

from conftest import run_tool, write

TOOL = "check-unescaped-el.py"

ESCAPED = '<%@ taglib prefix="c" uri="jakarta.tags.core" %>\n<c:out value="${user.name}"/>\n'
BARE = '<%@ taglib prefix="c" uri="jakarta.tags.core" %>\n<p>${user.name}</p>\n'


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
