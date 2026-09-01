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

Issue #1765 then moved sixteen Settings rows onto /admin/settings, a page of cards. The gate
kept passing while quietly checking sixteen fewer links, which is the failure mode it was
written to prevent, one level up: not a wrong answer, an unasked question. The hub tests
below pin both halves -- that a card is judged against the page the hub sits on, and that a
hub whose destinations stop being readable fails instead of shrinking.
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

HUB_SOURCE = ("src/main/java/com/simisinc/platform/presentation/widgets/admin/"
              "SettingsHubWidget.java")
HUB_JSP = "src/main/webapp/WEB-INF/jsp/admin/settings-hub.jsp"


def hub_page(name: str = "/admin/settings", role: str = "admin", capability: str = None) -> str:
    """A <page> that renders the settings-hub widget -- how the tool finds the hub at all."""
    attrs = ' role="%s"' % role if role is not None else ""
    if capability is not None:
        attrs += ' capability="%s"' % capability
    return ('  <page name="%s"%s title="Settings">\n'
            "    <section><column>\n"
            '      <widget name="settingsHub"><title>Settings</title></widget>\n'
            "    </column></section>\n"
            "  </page>" % (name, attrs))


def hub_source(*entries: str, jsp: str = "/admin/settings-hub.jsp") -> str:
    """A stand-in for SettingsHubWidget.java carrying the entry calls the tool reads."""
    return ("package com.simisinc.platform.presentation.widgets.admin;\n"
            "public class SettingsHubWidget extends GenericWidget {\n"
            '  static String JSP = "%s";\n'
            "  static final List<SettingsGroup> SETTINGS_GROUPS = List.of(\n"
            '      new SettingsGroup("Group", "desc", Arrays.asList(\n'
            "%s)));\n"
            "}\n" % (jsp, "\n".join(entries)))


def hub_entry(label: str, link: str, module: str = None) -> str:
    if module is None:
        return ('          entry("%s", "%s", "fa-thing",\n'
                '              "What it holds."),' % (label, link))
    return ('          moduleEntry("%s", "%s", "fa-thing",\n'
            '              "What it holds.", "%s"),' % (label, link, module))


WIDGET_LIBRARY = "src/main/webapp/WEB-INF/widgets/widget-library.xml"


def widget_library(name: str = "settingsHub") -> str:
    return ('<?xml version="1.0" ?>\n<widgets>\n'
            '  <widget name="%s" class="com.simisinc.platform.presentation.widgets.admin.'
            'SettingsHubWidget" />\n</widgets>\n' % name)


HUB_MARKUP = ('<c:forEach items="${settingsGroupList}" var="settingsGroup">\n'
              '  <c:forEach items="${settingsGroup.entryList}" var="settingsEntry">\n'
              '    <a href="${ctx}<c:out value="${settingsEntry.link}"/>">'
              '<c:out value="${settingsEntry.label}"/></a>\n'
              "  </c:forEach>\n"
              "</c:forEach>\n")


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


# ------------------------------------------------------------------------ settings hub

def test_hub_destinations_are_checked_like_menu_rows(repo):
    """The coverage issue #1765 dropped. A card is a nav link with a different gate."""
    write(repo, MAIN_JSP, menu(section(ADMIN_ONLY, row("/admin/settings"))))
    write(repo, LAYOUT, pages(hub_page(),
                              page("/admin/theme-properties", "admin"),
                              page("/admin/feature-flags", "admin")))
    write(repo, HUB_SOURCE, hub_source(hub_entry("Theme", "/admin/theme-properties"),
                                       hub_entry("Feature Flags", "/admin/feature-flags")))
    write(repo, HUB_JSP, HUB_MARKUP)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "2 settings-hub card(s) checked on /admin/settings" in r.stdout, r.stdout


def test_hub_card_wider_than_its_destination_is_a_dead_link(repo):
    """The #1764 hazard, arriving through the hub instead of the menu.

    Nothing else catches this. SettingsHubWidgetTest pins each destination to role="admin",
    but the leak is opened from the other side -- by widening the page the hub sits on --
    and every destination is still exactly what that test demands.
    """
    write(repo, MAIN_JSP, menu(section(ADMIN_OR_MANAGE, row("/admin/settings"))))
    write(repo, LAYOUT, pages(hub_page(capability="admin:manage"),
                              page("/admin/theme-properties", "admin"),
                              page("/admin/sso-properties", "admin", "admin:manage")))
    write(repo, HUB_SOURCE, hub_source(hub_entry("Theme", "/admin/theme-properties"),
                                       hub_entry("Single Sign-On", "/admin/sso-properties")))
    write(repo, HUB_JSP, HUB_MARKUP)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "DEAD LINK" in r.stdout
    assert "/admin/theme-properties" in r.stdout
    assert "capability admin:manage" in r.stdout
    # Only the card whose destination is narrower; the one that admits the capability is not.
    assert r.stdout.count("DEAD LINK") == 1, r.stdout


def test_hub_card_narrower_than_its_destination_is_not_a_finding(repo):
    """Same direction rule as a menu row: hidden-but-reachable is not a broken link."""
    write(repo, MAIN_JSP, menu(section(ADMIN_ONLY, row("/admin/settings"))))
    write(repo, LAYOUT, pages(hub_page(),
                              page("/admin/theme-properties", "admin", "admin:manage")))
    write(repo, HUB_SOURCE, hub_source(hub_entry("Theme", "/admin/theme-properties")))
    write(repo, HUB_JSP, HUB_MARKUP)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_a_switched_off_module_card_is_still_checked(repo):
    """The hub marks a disabled module rather than hiding it, so its card is still a link.

    Reading moduleEntry as if it were gated would let a dead link through exactly where the
    menu used to gate one.
    """
    write(repo, MAIN_JSP, menu(section(ADMIN_OR_MANAGE, row("/admin/settings"))))
    write(repo, LAYOUT, pages(hub_page(capability="admin:manage"),
                              page("/admin/ecommerce-properties", "admin")))
    write(repo, HUB_SOURCE,
          hub_source(hub_entry("E-commerce Settings", "/admin/ecommerce-properties",
                               module="ecommerce.enabled")))
    write(repo, HUB_JSP, HUB_MARKUP)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "/admin/ecommerce-properties" in r.stdout
    assert "1 settings-hub card(s) checked" in r.stdout


def test_a_hub_destination_cannot_escape_checking(repo):
    """The regression guard. A destination declared some other way must fail, not vanish.

    This is the shape of the original loss: the links were still there, still shown to
    admins, and the gate went on reporting a clean run over a smaller set.
    """
    escaped = ('          new SettingsEntry("Feature Flags", "/admin/feature-flags",\n'
               '              "fa-flag", "What it holds.", null),')
    write(repo, MAIN_JSP, menu(section(ADMIN_ONLY, row("/admin/settings"))))
    write(repo, LAYOUT, pages(hub_page(),
                              page("/admin/theme-properties", "admin"),
                              page("/admin/feature-flags", "admin")))
    write(repo, HUB_SOURCE, hub_source(hub_entry("Theme", "/admin/theme-properties"), escaped))
    write(repo, HUB_JSP, HUB_MARKUP)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "UNDETERMINED" in r.stdout
    assert "/admin/feature-flags is named here" in r.stdout, r.stdout


def test_a_hub_that_yields_no_destinations_is_a_failure_not_a_pass(repo):
    """Zero cards on a hub that exists is the silent-skip this gate refuses to perform."""
    write(repo, MAIN_JSP, menu(section(ADMIN_ONLY, row("/admin/settings"))))
    write(repo, LAYOUT, pages(hub_page(), page("/admin/theme-properties", "admin")))
    write(repo, HUB_SOURCE, hub_source())
    write(repo, HUB_JSP, HUB_MARKUP)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "0 settings-hub card(s) checked" in r.stdout
    assert "not a pass" in r.stdout


def test_a_missing_hub_source_is_a_failure(repo):
    """The layout places the widget; if its destinations cannot be read, say so."""
    write(repo, MAIN_JSP, menu(section(ADMIN_ONLY, row("/admin/settings"))))
    write(repo, LAYOUT, pages(hub_page(), page("/admin/theme-properties", "admin")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "UNDETERMINED" in r.stdout
    assert "does not exist" in r.stdout


def test_a_hard_coded_link_in_the_hub_markup_is_reported(repo):
    """The JSP renders settingsEntry.link and nothing else. A link added beside it would be
    a destination read from neither side, so it is reported rather than assumed harmless."""
    write(repo, MAIN_JSP, menu(section(ADMIN_ONLY, row("/admin/settings"))))
    write(repo, LAYOUT, pages(hub_page(), page("/admin/theme-properties", "admin"),
                              page("/admin/apis", "admin")))
    write(repo, HUB_SOURCE, hub_source(hub_entry("Theme", "/admin/theme-properties")))
    write(repo, HUB_JSP, HUB_MARKUP + '<a href="${ctx}/admin/apis">APIs</a>\n')
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "hard-coded link /admin/apis" in r.stdout, r.stdout
    # And the card the widget does declare is still checked -- one bad link does not
    # silently take the rest of the hub out of the gate.
    assert "1 settings-hub card(s) checked" in r.stdout


def test_the_hub_widget_name_is_read_from_the_widget_library(repo):
    """Renaming the widget must move this gate with it, not leave the hub unchecked.

    Discovery is by class, the way the application binds the name, so a rename that updates
    widget-library.xml and the layout together keeps every card in the gate.
    """
    write(repo, MAIN_JSP, menu(section(ADMIN_ONLY, row("/admin/settings"))))
    write(repo, WIDGET_LIBRARY, widget_library("adminSettingsHub"))
    write(repo, LAYOUT, pages(hub_page().replace('name="settingsHub"',
                                                 'name="adminSettingsHub"'),
                              page("/admin/theme-properties", "admin")))
    write(repo, HUB_SOURCE, hub_source(hub_entry("Theme", "/admin/theme-properties")))
    write(repo, HUB_JSP, HUB_MARKUP)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "1 settings-hub card(s) checked" in r.stdout, r.stdout


def test_commented_out_java_is_not_a_destination(repo):
    """A path in prose is not a link, and a commented-out entry is not rendered.

    The broad literal sweep would otherwise fail the build over the widget's own javadoc,
    and a gate that cries wolf gets switched off.
    """
    noise = ('  /* See tools/check-admin-nav-permissions.py; it reads "/admin/mentioned". */\n'
             '  // entry("Retired", "/admin/retired", "fa-x", "Gone."),')
    write(repo, MAIN_JSP, menu(section(ADMIN_ONLY, row("/admin/settings"))))
    write(repo, LAYOUT, pages(hub_page(), page("/admin/theme-properties", "admin")))
    write(repo, HUB_SOURCE, hub_source(noise, hub_entry("Theme", "/admin/theme-properties")))
    write(repo, HUB_JSP, HUB_MARKUP)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "1 settings-hub card(s) checked" in r.stdout, r.stdout


def test_no_hub_in_the_layout_means_no_hub_clause(repo):
    """The hub is found through the layout XML, so a tree without one is simply menu-only."""
    write(repo, MAIN_JSP, menu(section(ADMIN_ONLY, row("/admin/thing"))))
    write(repo, LAYOUT, pages(page("/admin/thing", "admin")))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "settings-hub card(s)" not in r.stdout


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


def test_every_real_hub_destination_is_checked(repo):
    """The regression guard against the shipped tree, not a synthetic one.

    A floor on the row count would not have caught issue #1765's loss: sixteen links left the
    gate's reach and the count stayed comfortably above any floor anyone would have written.
    So this scrapes the widget source a second time, by a different route -- every
    "/admin/..." literal in the file, not the entry(...) calls the tool understands -- and
    demands that each one shows up in what the tool says it checked. The two reads can only
    agree if the tool is actually reading the destinations.
    """
    import pathlib
    root = pathlib.Path(__file__).resolve().parents[2]
    source = root / HUB_SOURCE
    assert source.is_file(), f"{HUB_SOURCE} is gone; this test and the gate both need updating"

    # Comments are dropped with a plain regex rather than the tool's own stripper, so the two
    # reads stay genuinely separate. It is cruder than the tool's -- a "//" inside a string
    # would fool it -- and the direction of that error is safe here: it can only drop a
    # destination from this side, and dropping one weakens this cross-check without ever
    # inventing a link that the tool must then explain.
    text = re.sub(r"/\*.*?\*/|//[^\n]*", "", source.read_text(), flags=re.S)
    jsp_field = re.search(r'\bJSP\s*=\s*"(/[^"]*)"', text)
    assert jsp_field, "no JSP field in the hub widget"
    destinations = {link for link in re.findall(r'"(/admin/[^"]*)"', text)
                    if link != jsp_field.group(1)}
    assert destinations, "no /admin/... destination found in the hub widget source"

    r = run_tool(TOOL, root, "--strict", "--list")
    assert r.returncode == 0, r.stdout + r.stderr
    listed = set(re.findall(r"^  checked  \S*SettingsHubWidget\.java:\d+  (\S+)",
                            r.stdout, re.M))
    missing = sorted(destinations - listed)
    assert not missing, (
        "these hub destinations are shown to admins but are not being checked: %s\n%s"
        % (missing, r.stdout))
    assert ("Summary: %d settings-hub card(s) checked" % len(destinations)) in r.stdout, r.stdout
