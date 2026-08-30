"""check-heading-levels.py: the admin console's heading outline.

main.jsp emits a screen-reader h1 on admin-console pages, so an authored h4 there is
an h1 -> h4 jump and an authored h1 is a second h1. Issue 1622 catalogued the skips by
hand and got the count wrong twice; the duplicate h1s (issue 1660) were never
catalogued at all. Nothing measured either, which is why this tool exists.
"""

from conftest import run_tool, write

TOOL = "check-heading-levels.py"
MAIN_JSP = "src/main/webapp/WEB-INF/jsp/main.jsp"
LAYOUT = "src/main/webapp/WEB-INF/web-layouts/page/admin-layout.xml"
ADMIN_JSP = "src/main/webapp/WEB-INF/jsp/admin/some-list.jsp"

# The real branch condition, trimmed. The tool reads its route literals from this line
# rather than hardcoding them, so the two cannot drift apart.
MAIN = """<c:choose>
    <c:when test="${fn:startsWith(pageRenderInfo.name, '/admin') && pageRenderInfo.name ne '/admin/web-page' && pageRenderInfo.name ne '/admin/css-editor'}">
      <div class="web-content admin-web-content">
        <c:if test="${!empty pageRenderInfo.title}">
          <h1 class="show-for-sr"><c:out value="${pageRenderInfo.title}"/></h1>
        </c:if>
      </div>
    </c:when>
  </c:choose>
"""


def layout(*pages: str) -> str:
    return "<?xml version=\"1.0\" ?>\n<pages>\n" + "\n".join(pages) + "\n</pages>\n"


def page(name: str, headings: str, title: str | None = "Admin") -> str:
    attr = ' title="%s"' % title if title else ""
    return ('  <page name="%s" role="admin"%s>\n'
            '    <section><column><widget name="content">\n'
            '      <html><![CDATA[%s]]></html>\n'
            '    </widget></column></section>\n'
            '  </page>' % (name, attr, headings))


def setup(repo, layout_xml: str, jsp: str | None = None, main: str = MAIN):
    write(repo, MAIN_JSP, main)
    write(repo, LAYOUT, layout_xml)
    if jsp is not None:
        write(repo, ADMIN_JSP, jsp)


def test_headings_starting_at_h2_pass(repo):
    setup(repo, layout(page("/admin/users", "<h2>Section</h2><h3>Detail</h3>")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "skip no levels" in r.stdout


def test_an_authored_h4_under_the_screen_reader_h1_fails(repo):
    # The state issue 1622 describes: the page h1 is invisible, so an h4 looks like a
    # reasonable first heading and is actually a two-level jump.
    setup(repo, layout(page("/admin/users", "<h4>Section</h4>")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "jumps h1 -> h4" in r.stdout


def test_an_authored_h1_is_a_duplicate(repo):
    # Issue 1660: admin-ecommerce-layout.xml authored an h1 on a titled admin page.
    setup(repo, layout(page("/admin/orders", "<h1>Order No. 1</h1>", title="Orders")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "authors an h1" in r.stdout
    assert "Orders" in r.stdout


def test_a_skip_deeper_in_the_page_fails(repo):
    setup(repo, layout(page("/admin/users", "<h2>Section</h2><h4>Detail</h4>")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "jumps h2 -> h4" in r.stdout


def test_returning_to_a_shallower_level_is_not_a_skip(repo):
    # h2, h3, h2 is a correct outline -- only going deeper by more than one is a skip.
    setup(repo, layout(page("/admin/users", "<h2>A</h2><h3>A.1</h3><h2>B</h2><h3>B.1</h3>")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_heading_specimens_are_not_findings(repo):
    # /admin/theme-properties renders one heading of each level to show what the theme does
    # to them. Retagging those destroys the thing the page exists to demonstrate.
    setup(repo, layout(page(
        "/admin/theme-properties",
        "<h2>Typography</h2><h1>H1 Header</h1><h2>H2 Header</h2><h3>H3 Header</h3>"
        "<h4>H4 Header</h4><h5>H5 Header</h5>")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_a_page_on_an_excluded_route_is_not_checked(repo):
    # /admin/css-editor is excluded from the admin branch in main.jsp, so it gets no
    # screen-reader h1 and its own h1 is correct.
    setup(repo, layout(page("/admin/css-editor", "<h1>H1 Header</h1>", title="CSS Editor")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_a_page_without_a_title_is_not_checked(repo):
    # main.jsp guards the screen-reader h1 on ${!empty pageRenderInfo.title}. With no title
    # there is no h1, so the page's own h1 is the only one -- and demoting it would leave the
    # page with none at all.
    setup(repo, layout(page("/admin/thing", "<h1>Thing</h1>", title=None)))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_a_route_query_template_still_matches(repo):
    # Real names carry them: /admin/order-details{?order-number}
    setup(repo, layout(page("/admin/order-details{?order-number}", "<h4>Detail</h4>")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "jumps h1 -> h4" in r.stdout


def test_a_non_admin_page_is_not_checked(repo):
    # Public pages get no h1 from main.jsp, which is why they need authored ones.
    setup(repo, layout(page("/about-us", "<h1>About</h1>", title="About")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_an_h1_in_an_admin_widget_jsp_fails(repo):
    setup(repo, layout(page("/admin/users", "<h2>Section</h2>")),
          jsp='<c:if test="${!empty title}">\n  <h1><c:out value="${title}"/></h1>\n</c:if>\n')
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "some-list.jsp:2" in r.stdout


def test_an_h2_in_an_admin_widget_jsp_passes(repo):
    setup(repo, layout(page("/admin/users", "<h2>Section</h2>")),
          jsp='  <h2 class="h1"><c:out value="${title}"/></h2>\n')
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_image_browser_may_keep_its_h1(repo):
    # It lives under jsp/admin/ but renders on /image-browser, defined in cms-layout.xml and
    # outside the admin branch. Reported as a defect on the first pass of issue 1660; it is not.
    write(repo, MAIN_JSP, MAIN)
    write(repo, LAYOUT, layout(page("/admin/users", "<h2>Section</h2>")))
    write(repo, "src/main/webapp/WEB-INF/jsp/admin/image-browser.jsp", "  <h1>Images</h1>\n")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_reports_without_strict_but_does_not_fail(repo):
    setup(repo, layout(page("/admin/users", "<h4>Section</h4>")))
    r = run_tool(TOOL, repo)
    assert r.returncode == 0
    assert "jumps h1 -> h4" in r.stdout


def test_a_missing_branch_condition_is_reported_not_ignored(repo):
    # The excluded routes are read out of main.jsp. If that condition is rewritten, this tool
    # cannot report accurately -- it must say so rather than silently passing everything.
    setup(repo, layout(page("/admin/users", "<h4>Section</h4>")),
          main="<div>no admin branch here</div>\n")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "admin-console branch condition" in r.stdout
