"""check-page-level-attributes.py: a page-level attribute the per-widget reset wipes."""

from conftest import run_tool, write

TOOL = "check-page-level-attributes.py"

CONTROLLER = "src/main/java/com/simisinc/platform/presentation/controller"

CONSTANTS = """package com.simisinc.platform.presentation.controller;

public class RequestConstants {
  public static final String MASTER_WEB_PAGE = "masterWebPage";
  public static final String RENDER_TIME = "totalRenderTime";
}
"""

CONTAINER = """package com.simisinc.platform.presentation.controller;

public class WebContainerCommand {

  private static final Set<String> PAGE_LEVEL_ATTRIBUTE_NAMES = Set.of(
      %s);

  public static boolean processWidgets(WebContainerContext ctx) {
    return false;
  }

  protected static boolean isPreservedAcrossWidgetReset(String name) {
    return %s
        || PAGE_LEVEL_ATTRIBUTE_NAMES.contains(name);
  }
}
"""

SERVLET = """package com.simisinc.platform.presentation.controller;

public class PageServlet extends HttpServlet {

  public void service(HttpServletRequest request, HttpServletResponse response) {
%s
    WebContainerCommand.processWidgets(webContainerContext);
%s
  }
}
"""

DEFAULT_PREFIXES = ('name.startsWith("controller") || name.startsWith("master") '
                    '|| name.startsWith("request")')


def make_repo(repo, before="", after="", names=(), prefixes=DEFAULT_PREFIXES):
    write(repo, f"{CONTROLLER}/RequestConstants.java", CONSTANTS)
    write(repo, f"{CONTROLLER}/WebContainerCommand.java",
          CONTAINER % (", ".join('"%s"' % n for n in names), prefixes))
    write(repo, f"{CONTROLLER}/PageServlet.java", SERVLET % (before, after))
    return repo


def publish(*names):
    return "\n".join('    request.setAttribute("%s", value);' % n for n in names)


def test_published_and_exempted_passes_strict(repo):
    make_repo(repo, before=publish("cspNonce", "sitePropertyMap"),
              names=("cspNonce", "sitePropertyMap"))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "Summary: 0 unpreserved" in r.stdout


def test_published_but_not_exempted_fails_strict(repo):
    # Issue #944: cspNonce was published and never added to the set, so the first
    # widget's reset removed it and every nonce="${cspNonce}" rendered empty.
    make_repo(repo, before=publish("cspNonce", "sitePropertyMap"),
              names=("sitePropertyMap",))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "UNPRESERVED (1)" in r.stdout
    assert "PageServlet.java:6  cspNonce" in r.stdout
    assert "Summary: 1 unpreserved" in r.stdout


def test_unpreserved_reported_but_exit_zero_without_strict(repo):
    make_repo(repo, before=publish("cspNonce"), names=())
    r = run_tool(TOOL, repo)
    assert r.returncode == 0
    assert "UNPRESERVED (1)" in r.stdout


def test_prefixed_names_need_no_set_entry(repo):
    # isPreservedAcrossWidgetReset covers these three prefixes separately.
    make_repo(repo, before=publish("controllerShowMainMenu", "masterMenuTabList",
                                   "requestPricingRule"),
              names=())
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "PRESERVED by name prefix (3)" in r.stdout


def test_prefixes_are_read_from_the_source_not_hardcoded(repo):
    # Drop "master" from isPreservedAcrossWidgetReset and masterMenuTabList is no
    # longer exempt -- the check follows the source instead of its own copy.
    make_repo(repo, before=publish("masterMenuTabList"), names=(),
              prefixes='name.startsWith("controller") || name.startsWith("request")')
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "masterMenuTabList" in r.stdout


def test_attributes_set_after_the_walk_are_not_required(repo):
    # The reset only runs inside processWidgets, so anything published afterwards
    # reaches layout.jsp intact and needs no exemption.
    make_repo(repo, before=publish("cspNonce"), after=publish("includeStylesheet"),
              names=("cspNonce",))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "includeStylesheet" not in r.stdout


def test_identifier_name_resolved_through_request_constants(repo):
    make_repo(repo, before="    request.setAttribute(MASTER_WEB_PAGE, webPage);", names=())
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "masterWebPage" in r.stdout


def test_unresolvable_identifier_fails_strict(repo):
    # A constant this check cannot resolve is reported, not skipped: silently
    # ignoring it is how a gate turns into a placebo.
    make_repo(repo, before="    request.setAttribute(SOME_OTHER_CONSTANT, value);", names=())
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "UNRESOLVED (1)" in r.stdout
    assert "SOME_OTHER_CONSTANT" in r.stdout


def test_session_attributes_are_ignored(repo):
    # Session attributes are not touched by the per-widget request attribute reset.
    make_repo(repo, before='    request.getSession().setAttribute("pageEditMode", "true");',
              names=())
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "publishes 0 attribute(s)" in r.stdout


def test_a_comment_mentioning_set_attribute_is_not_a_finding(repo):
    before = ('    // keep in sync with request.setAttribute("notReal", x) below\n'
              '    /* request.setAttribute("alsoNotReal", x); */\n'
              + publish("cspNonce"))
    make_repo(repo, before=before, names=("cspNonce",))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "notReal" not in r.stdout
    assert "alsoNotReal" not in r.stdout


def test_double_slash_inside_a_string_does_not_start_a_comment(repo):
    before = ('    String url = "https://example.com";\n' + publish("cspNonce"))
    make_repo(repo, before=before, names=())
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "cspNonce" in r.stdout


def test_stale_set_entry_is_noted_but_does_not_fail(repo):
    make_repo(repo, before=publish("cspNonce"), names=("cspNonce", "goneFromPageServlet"))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "goneFromPageServlet" in r.stdout
    assert "the set has drifted" in r.stdout


def test_missing_widget_walk_anchor_exits_two(repo):
    make_repo(repo, before=publish("cspNonce"), names=("cspNonce",))
    path = repo / CONTROLLER / "PageServlet.java"
    path.write_text(path.read_text().replace(
        "WebContainerCommand.processWidgets(webContainerContext);", "renderWidgets();"))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 2
    assert "cannot locate the widget walk" in r.stderr


def test_missing_attribute_set_exits_two(repo):
    make_repo(repo, before=publish("cspNonce"), names=("cspNonce",))
    path = repo / CONTROLLER / "WebContainerCommand.java"
    path.write_text(path.read_text().replace(
        "Set<String> PAGE_LEVEL_ATTRIBUTE_NAMES", "Set<String> PRESERVED_NAMES"))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 2
    assert "PAGE_LEVEL_ATTRIBUTE_NAMES declaration was not found" in r.stderr


def test_missing_preserve_method_exits_two(repo):
    make_repo(repo, before=publish("cspNonce"), names=("cspNonce",))
    path = repo / CONTROLLER / "WebContainerCommand.java"
    path.write_text(path.read_text().replace(
        "isPreservedAcrossWidgetReset(String name)", "isPreserved(String name)"))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 2
    assert "isPreservedAcrossWidgetReset(String ...) not found" in r.stderr


def test_missing_source_file_exits_two(repo):
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 2
    assert "PageServlet.java not found" in r.stderr
