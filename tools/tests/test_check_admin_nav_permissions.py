"""check-admin-nav-permissions.py: a nav row must not outrun the page it links to.

The admin menu in main.jsp decides who sees a row; the layout XML decides who may open
the page. Nothing connects them, so a row can be shown to someone the page denies -- they
see the link and are refused when they click it.

Issue #1764 moves the CSP Violations row into the Access section, where every other page
is role="admin" capability="admin:manage" and the section gate matches. /admin/csp-violations
is role="admin" only, so writing the row like its neighbours would have shown a dead link to
every admin:manage holder. PR #1768 caught that by hand. These tests pin the shape that
catches it automatically -- and the shapes that must NOT be reported, since a gate that
cries wolf gets switched off.
"""

import re
from conftest import run_tool, write

TOOL = "check-admin-nav-permissions.py"
MAIN_JSP = "src/main/webapp/WEB-INF/jsp/main.jsp"
LAYOUT = "src/main/webapp/WEB-INF/web-layouts/page/admin-layout.xml"


def menu(*blocks: str) -> str:
    """Wrap menu markup in the landmarks the tool looks for."""
    return ('<div class="off-canvas" id="offCanvas" data-off-canvas>\n'
            "  <nav aria-label=\"Admin navigation\">\n"
            + "\n".join(blocks)
            + "\n  </nav>\n</div>\n")


def row(href: str, label: str = "Thing") -> str:
    """A row written the way every row in main.jsp is: an inline <c:if> inside the <li>
    start tag toggling is-active, which is NOT a visibility gate."""
    return ('              <li<c:if test="${fn:startsWith(pageRenderInfo.name, \'%s\')}">'
            ' class="is-active"</c:if>><a href="${ctx}%s">'
            "<span>%s</span></a></li>" % (href, href, label))


def section(test: str, *rows: str, title: str = "Access") -> str:
    return ('            <c:if test="%s">\n'
            '              <ul class="vertical menu">\n'
            '                <li class="section-title">%s</li>\n'
            "%s\n"
            "              </ul>\n"
            "            </c:if>" % (test, title, "\n".join(rows)))


def pages(*decls: str) -> str:
    return "<?xml version=\"1.0\" ?>\n<pages>\n" + "\n".join(decls) + "\n</pages>\n"


def page(name: str, role: str = None, capability: str = None) -> str:
    attrs = ""
    if role is not None:
        attrs += ' role="%s"' % role
    if capability is not None:
        attrs += ' capability="%s"' % capability
    return '  <page name="%s"%s title="T"/>' % (name, attrs)


ADMIN_OR_MANAGE = "${userSession.hasRole('admin') || userSession.hasPermission('admin:manage')}"
ADMIN_ONLY = "${userSession.hasRole('admin')}"


# --------------------------------------------------------------------- the #1764 shape

def test_row_wider_than_its_page_is_a_dead_link(repo):
    """The hazard: an admin:manage holder sees the row and cannot open the page."""
    write(repo, MAIN_JSP, menu(section(ADMIN_OR_MANAGE,
                                       row("/admin/apis"),
                                       row("/admin/csp-violations"))))
    write(repo, LAYOUT, pages(page("/admin/apis", "admin", "admin:manage"),
                              page("/admin/csp-violations", "admin")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "DEAD LINK" in r.stdout
    assert "/admin/csp-violations" in r.stdout
    assert "capability admin:manage" in r.stdout
    # Only the mismatched row is reported; the neighbour whose page matches is not.
    assert r.stdout.count("DEAD LINK") == 1
    assert "1 dead link(s)" in r.stdout


def test_same_row_with_its_own_narrower_test_passes(repo):
    """PR #1768's fix: the row carries its own hasRole('admin') gate."""
    guarded = ('            <c:if test="%s">\n%s\n            </c:if>'
               % (ADMIN_ONLY, row("/admin/csp-violations")))
    write(repo, MAIN_JSP, menu(section(ADMIN_OR_MANAGE, row("/admin/apis"), guarded)))
    write(repo, LAYOUT, pages(page("/admin/apis", "admin", "admin:manage"),
                              page("/admin/csp-violations", "admin")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "DEAD LINK" not in r.stdout
    assert "2 menu row(s) checked" in r.stdout


def test_row_narrower_than_its_page_is_not_a_finding(repo):
    """Hidden-but-reachable is a discoverability question, not a broken link."""
    guarded = ('            <c:if test="%s">\n%s\n            </c:if>'
               % (ADMIN_ONLY, row("/admin/thing")))
    write(repo, MAIN_JSP, menu(section(ADMIN_OR_MANAGE, guarded)))
    write(repo, LAYOUT, pages(page("/admin/thing", "admin", "admin:manage")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


# ------------------------------------------------------------------ EL shapes it must read

def test_nested_c_if_tests_are_anded(repo):
    """A row's effective gate is every enclosing <c:if>, not just the innermost."""
    inner = ('            <c:if test="${userSession.hasRole(\'community-manager\')}">\n'
             "%s\n            </c:if>" % row("/admin/thing"))
    write(repo, MAIN_JSP, menu(section(ADMIN_OR_MANAGE, inner)))
    # Only someone holding BOTH is shown the row, and the page admits community-manager,
    # so there is no leak even though the outer gate alone would be wider than the page.
    write(repo, LAYOUT, pages(page("/admin/thing", "community-manager")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_negation_is_evaluated_not_approximated(repo):
    """main.jsp's Editorial Calendar row uses !hasRole to avoid showing a link twice.

    Comparing token sets would read the `!hasRole('admin')` as if admin were admitted.
    """
    gate = ("${(userSession.hasRole('admin') || userSession.hasRole('community-manager'))"
            " && !userSession.hasRole('admin')}")
    inner = '            <c:if test="%s">\n%s\n            </c:if>' % (gate, row("/admin/thing"))
    write(repo, MAIN_JSP, menu(section(ADMIN_OR_MANAGE, inner)))
    write(repo, LAYOUT, pages(page("/admin/thing", "community-manager")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_site_property_condition_does_not_hide_a_leak(repo):
    """A section switched off by a site property is still a dead link once switched on."""
    gate = ("${!empty ecommercePropertyMap['ecommerce.enabled']"
            " && ecommercePropertyMap['ecommerce.enabled'] eq 'true'}")
    inner = ('            <c:if test="%s">\n%s\n            </c:if>'
             % (ADMIN_OR_MANAGE, row("/admin/orders")))
    write(repo, MAIN_JSP, menu('            <c:if test="%s">' % gate, inner,
                               "            </c:if>"))
    write(repo, LAYOUT, pages(page("/admin/orders", "admin"),
                              page("/admin/apis", "admin", "admin:manage")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "/admin/orders" in r.stdout
    assert "capability admin:manage" in r.stdout


def test_inline_is_active_c_if_is_not_a_visibility_gate(repo):
    """Every row carries an inline <c:if> toggling class="is-active". Reading it as a
    visibility test would make every row look gated on the current page name."""
    write(repo, MAIN_JSP, menu(section(ADMIN_ONLY, row("/admin/thing"))))
    write(repo, LAYOUT, pages(page("/admin/thing", "admin")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "1 menu row(s) checked" in r.stdout


def test_href_resolves_through_shorter_prefixes(repo):
    """locatePage() falls back to shorter path prefixes: the wiki link is the real case."""
    write(repo, MAIN_JSP, menu(section(ADMIN_OR_MANAGE,
                                       row("/admin/docs/wiki/Home"))))
    write(repo, LAYOUT, pages(page("/admin/docs/wiki", "admin"),
                              page("/admin/apis", "admin", "admin:manage")))
    r = run_tool(TOOL, repo, "--strict")
    # Resolves to the /admin/docs/wiki declaration (role="admin"), which does not
    # admit admin:manage -- so it is a finding, not an unresolvable href.
    assert r.returncode == 1, r.stdout + r.stderr
    assert "DEAD LINK" in r.stdout
    assert "UNDETERMINED" not in r.stdout


def test_page_declaring_role_and_capability_admits_either(repo):
    """WebComponentCommand ORs the two: a capability alone is enough."""
    write(repo, MAIN_JSP, menu(section(ADMIN_OR_MANAGE, row("/admin/audit-log"))))
    write(repo, LAYOUT, pages(page("/admin/audit-log", "admin", "admin:manage")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


# ------------------------------------------------------ loud about what it cannot read

def test_unmodelled_user_predicate_is_undetermined_not_skipped(repo):
    """A new userSession call must fail the build, not silently pass the row."""
    gate = "${userSession.hasSomethingNew('x')}"
    write(repo, MAIN_JSP, menu(section(gate, row("/admin/thing")),
                               section(ADMIN_ONLY, row("/admin/other"), title="Settings")))
    write(repo, LAYOUT, pages(page("/admin/thing", "admin"), page("/admin/other", "admin")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "UNDETERMINED" in r.stdout
    assert "unmodelled userSession predicate" in r.stdout


def test_href_with_no_page_declaration_is_undetermined(repo):
    write(repo, MAIN_JSP, menu(section(ADMIN_ONLY, row("/admin/nowhere"))))
    write(repo, LAYOUT, pages(page("/admin/thing", "admin")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "UNDETERMINED" in r.stdout
    assert "resolves to no <page> declaration" in r.stdout


def test_unbalanced_c_if_nesting_is_reported_loudly(repo):
    """A <c:if> shape the parser cannot track fails rather than mis-nesting rows."""
    write(repo, MAIN_JSP, menu('            <c:if test="%s"><ul>' % ADMIN_ONLY,
                               row("/admin/thing"),
                               "            </ul></c:if>"))
    write(repo, LAYOUT, pages(page("/admin/thing", "admin")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "COULD NOT DETERMINE" in r.stdout
    assert "0 rows checked" in r.stdout


def test_zero_rows_checked_is_a_failure_not_a_pass(repo):
    """tools/check-column-length-limits.py reported clean while its regex matched nothing.

    A parser that has stopped matching must not look like a passing gate.
    """
    write(repo, MAIN_JSP, menu('            <ul class="vertical menu">',
                               '              <li class="section-title">Access</li>',
                               "            </ul>"))
    write(repo, LAYOUT, pages(page("/admin/thing", "admin")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "0 rows checked" in r.stdout or "0 menu row(s) checked" in r.stdout
    assert "not a pass" in r.stdout


def test_missing_menu_region_is_a_failure(repo):
    write(repo, MAIN_JSP, "<html><body>no admin menu here</body></html>\n")
    write(repo, LAYOUT, pages(page("/admin/thing", "admin")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "COULD NOT DETERMINE" in r.stdout


# ----------------------------------------------------------------------------- modes

def test_report_only_by_default(repo):
    write(repo, MAIN_JSP, menu(section(ADMIN_OR_MANAGE, row("/admin/apis"),
                                       row("/admin/csp-violations"))))
    write(repo, LAYOUT, pages(page("/admin/apis", "admin", "admin:manage"),
                              page("/admin/csp-violations", "admin")))
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr
    assert "DEAD LINK" in r.stdout


def test_summary_reports_the_row_count(repo):
    write(repo, MAIN_JSP, menu(section(ADMIN_ONLY, row("/admin/a"), row("/admin/b"),
                                       row("/admin/c"))))
    write(repo, LAYOUT, pages(page("/admin/a", "admin"), page("/admin/b", "admin"),
                              page("/admin/c", "admin")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "Summary: 3 menu row(s) checked" in r.stdout


def test_real_repository_tree_is_clean(repo):
    """The gate must pass on the tree it ships with, or it cannot be enforcing."""
    import pathlib
    root = pathlib.Path(__file__).resolve().parents[2]
    r = run_tool(TOOL, root, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "0 dead link(s), 0 undetermined" in r.stdout
    # Guard the same thing the pinned count guarded -- a parser that has stopped matching
    # while the exit code stays 0 -- without pinning a number that any legitimate menu edit
    # invalidates. This was "Summary: 7", which failed the moment issue #1765 moved sixteen
    # settings rows onto their own page and took the total from 72 to 57: a real change, a
    # green gate, and a red test. A floor still catches the failure mode (a broken parser
    # reports 0, not 40) and does not train people to bump a magic number.
    match = re.search(r"Summary: (\d+) menu row\(s\) checked", r.stdout)
    assert match, r.stdout
    assert int(match.group(1)) >= 40, f"only {match.group(1)} rows matched; has the parser broken?"
