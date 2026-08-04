/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.simisinc.platform.presentation.controller;

import com.simisinc.platform.application.cms.SaveContentCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.presentation.widgets.cms.ContentEditorWidget;
import com.simisinc.platform.presentation.widgets.cms.WebContainerContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author matt rajkowski
 * @created 7/14/2022 8:00 AM
 */
class WebContainerCommandTest {
  @Test
  void replaceVariable() {
    // User information
    User user = new User();
    user.setId(1L);
    user.setFirstName("Sam");
    user.setLastName("O'Dell");

    String content = "Hi ${user.fullName}, welcome to the site.";
    String term = "user.fullName";
    Object bean = user;
    String property = "fullName";

    String value = WebContainerCommand.replaceVariable(content, term, bean, property);

    Assertions.assertEquals("Hi Sam O'Dell, welcome to the site.", value);
  }

  @Test
  void replaceSqlVariable() {
    // User information
    User user = new User();
    user.setId(1L);
    user.setFirstName("Sam");
    user.setLastName("O'Dell");

    String content = "Hi ${user.fullName:sql}, welcome to the site.";
    String term = "user.fullName";
    Object bean = user;
    String property = "fullName";

    String value = WebContainerCommand.replaceVariable(content, term, bean, property);

    Assertions.assertEquals("Hi Sam O''Dell, welcome to the site.", value);
  }

  @Test
  void replaceHtmlVariable() {
    // User information
    User user = new User();
    user.setId(1L);
    user.setNickname("\"Goal\" of 75%");

    String content = "<p>${user.nickname:html}</p>";
    String term = "user.nickname";
    Object bean = user;
    String property = "nickname";

    String value = WebContainerCommand.replaceVariable(content, term, bean, property);

    Assertions.assertEquals("<p>&quot;Goal&quot; of 75%</p>", value);
  }

  @Test
  void replaceToHtmlVariable() {
    // User information
    User user = new User();
    user.setId(1L);
    user.setNickname("\"Goal\" of 75%");

    String content = "${user.nickname:toHtml}";
    String term = "user.nickname";
    Object bean = user;
    String property = "nickname";

    String value = WebContainerCommand.replaceVariable(content, term, bean, property);

    Assertions.assertEquals("<html><head></head><body>\"Goal\" of 75%</body></html>", value);
  }

  @Test
  void replaceJsonVariable() {
    // User information
    User user = new User();
    user.setId(1L);
    user.setNickname("\"Goal\" of 75%");

    String content = "{\"nickname\":\"${user.nickname:json}\"}";
    String term = "user.nickname";
    Object bean = user;
    String property = "nickname";

    String value = WebContainerCommand.replaceVariable(content, term, bean, property);

    Assertions.assertEquals("{\"nickname\":\"\\\"Goal\\\" of 75%\"}", value);
  }

  @Test
  void replaceVariableButBlank() {
    // User information
    User user = new User();
    user.setId(1L);

    String content = "Hi ${user.fullName:sql}, welcome to the site.";
    String term = "user.fullName";
    Object bean = user;
    String property = "fullName";

    String value = WebContainerCommand.replaceVariable(content, term, bean, property);

    Assertions.assertEquals("Hi , welcome to the site.", value);
  }

  @Test
  void replaceWithParameterValue() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter("name")).thenReturn("Sam");

    String content = "Hi ${request.name}, welcome to the site.";
    String value = WebContainerCommand.replaceVariableWithParameterValue(request, content);

    Assertions.assertEquals("Hi Sam, welcome to the site.", value);
  }

  @Test
  void replaceWithParameterValueEncoding() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter("name")).thenReturn("Sam \"O'Dell\"");

    String content = "Hi ${request.name:html}, welcome to the site.";
    String value = WebContainerCommand.replaceVariableWithParameterValue(request, content);

    Assertions.assertEquals("Hi Sam &quot;O&apos;Dell&quot;, welcome to the site.", value);
  }

  @Test
  void loopReplaceWithParameterValueEncoding() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter("name")).thenReturn("Sam");
    String content = "Hi ${request.name:html}, welcome to the site. Your name is ${request.name:html}.";
    while (content.contains("${request.")) {
      content = WebContainerCommand.replaceVariableWithParameterValue(request, content);
    }
    Assertions.assertEquals("Hi Sam, welcome to the site. Your name is Sam.", content);
  }

  @Test
  void loopErrorReplaceWithParameterValueEncoding() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter("name")).thenReturn("Sam");
    String content = "Hi ${request.name, welcome to the site. Your name is ${request.name:html.";
    while (content.contains("${request.")) {
      content = WebContainerCommand.replaceVariableWithParameterValue(request, content);
    }
    Assertions.assertEquals("Hi ", content);
  }

  // Guard against including a widget JSP that does not exist -- such an include re-enters
  // the controller servlet and recurses until the stack overflows.

  private static final String JSP_PATH = "/WEB-INF/jsp/example-widget.jsp";

  @Test
  void existingWidgetJspMayBeIncluded() throws Exception {
    ServletContext servletContext = mock(ServletContext.class);
    URL resource = URI.create("file:/app/WEB-INF/jsp/example-widget.jsp").toURL();
    when(servletContext.getResource(JSP_PATH)).thenReturn(resource);

    Assertions.assertTrue(WebContainerCommand.widgetJspExists(servletContext, JSP_PATH));
  }

  @Test
  void missingWidgetJspIsNotIncluded() throws Exception {
    ServletContext servletContext = mock(ServletContext.class);
    // getResource returns null when the resource does not exist
    when(servletContext.getResource(JSP_PATH)).thenReturn(null);

    Assertions.assertFalse(WebContainerCommand.widgetJspExists(servletContext, JSP_PATH),
        "a missing JSP must not be included -- that is the recursion trigger");
  }

  @Test
  void malformedWidgetJspPathIsTreatedAsMissingNotThrown() throws Exception {
    ServletContext servletContext = mock(ServletContext.class);
    when(servletContext.getResource(JSP_PATH)).thenThrow(new MalformedURLException("bad path"));

    // Must be swallowed and reported as "does not exist", never propagated out of the render
    Assertions.assertFalse(WebContainerCommand.widgetJspExists(servletContext, JSP_PATH));
  }

  // A widget's title is combined with its WebPage's/Page's own title (e.g. a wildcard page like
  // /news/* titled "News") unless the widget already composed its title in full.

  @Test
  void uncomposedTitleGetsContainerTitleAppended() {
    String result = WebContainerCommand.composePageTitle("Contact Us", false, "Acme Corp");
    Assertions.assertEquals("Contact Us - Acme Corp", result);
  }

  @Test
  void uncomposedTitleIsUnchangedWhenContainerTitleIsBlank() {
    String result = WebContainerCommand.composePageTitle("Contact Us", false, "");
    Assertions.assertEquals("Contact Us", result);
  }

  @Test
  void composedTitleIsNotDoubledByContainerTitle() {
    // BlogPostWidget composes "<post title> - <blog name>" itself; a wildcard WebPage titled
    // "News" (matching the blog name) must not be appended a second time on top of that.
    String blogPostTitle = "Launch Announcement" + " - " + "News";
    String result = WebContainerCommand.composePageTitle(blogPostTitle, true, "News");
    Assertions.assertEquals("Launch Announcement - News", result);
  }

  @Test
  void composedTitleIsUnchangedWhenContainerTitleIsBlank() {
    String result = WebContainerCommand.composePageTitle("Launch Announcement - News", true, "");
    Assertions.assertEquals("Launch Announcement - News", result);
  }

  // Regression coverage for issue #259: the composition-canvas editor toolbar and
  // ItemsListWidget's edit-mode UI both read page-level request attributes that PageServlet sets
  // once, before WebContainerCommand.processWidgets()'s per-widget loop begins. That loop wipes
  // request attributes between each widget's turn so one widget's leftovers can't bleed into the
  // next widget's render -- but it must not wipe page-level attributes that need to survive for
  // the whole request, or a real page's own first widget kills them before anything ever reads
  // them back. See PageServlet.java's request.setAttribute("pageEditMode"/"pageLayoutMode"/
  // "hasDraft"/"widgetLibraryJson", ...) calls, and ItemsListWidget.java:84-85.

  @Test
  void pageLevelAttributesSurviveThePerWidgetReset() {
    Assertions.assertTrue(WebContainerCommand.isPreservedAcrossWidgetReset("pageEditMode"));
    Assertions.assertTrue(WebContainerCommand.isPreservedAcrossWidgetReset("pageLayoutMode"));
    Assertions.assertTrue(WebContainerCommand.isPreservedAcrossWidgetReset("hasDraft"));
    Assertions.assertTrue(WebContainerCommand.isPreservedAcrossWidgetReset("widgetLibraryJson"));
  }

  @Test
  void cspNonceSurvivesThePerWidgetReset() {
    // Regression test for issue #944: PageServlet.java computes cspNonce once, before the
    // section/column/widget walk begins, exactly like the other PAGE_LEVEL_ATTRIBUTE_NAMES entries
    // -- but it was never added to this set when the nonce-based CSP feature shipped (PR #386).
    // On any page with more than zero widgets, the very first widget's reset wiped it before
    // main.jsp's EL (or any later widget's own nonce="${cspNonce}" markup) ever got to read it
    // back, so every nonce="${cspNonce}" in every JSP silently rendered as nonce="" -- never
    // matching the real per-request nonce actually sent in the Content-Security-Policy header,
    // regardless of how many widgets a page had after the first.
    Assertions.assertTrue(WebContainerCommand.isPreservedAcrossWidgetReset("cspNonce"));
  }

  @Test
  void sitePropertyMapsSurviveThePerWidgetReset() {
    // Regression test: PageServlet.java now publishes systemPropertyMap/sitePropertyMap/
    // themePropertyMap/socialPropertyMap/socialMediaLinkList/analyticsPropertyMap/
    // ecommercePropertyMap before the section/column/widget walk begins (moved there so a
    // widget JSP like ActivityListWidget's activity-list.jsp -- which reads
    // systemPropertyMap['system.www.context'] directly -- can see them during its own JSP turn,
    // not just once main.jsp renders afterward). Without this exemption the very first widget's
    // reset would wipe them right back out before any later widget, or even main.jsp, read them.
    Assertions.assertTrue(WebContainerCommand.isPreservedAcrossWidgetReset("systemPropertyMap"));
    Assertions.assertTrue(WebContainerCommand.isPreservedAcrossWidgetReset("sitePropertyMap"));
    Assertions.assertTrue(WebContainerCommand.isPreservedAcrossWidgetReset("themePropertyMap"));
    Assertions.assertTrue(WebContainerCommand.isPreservedAcrossWidgetReset("socialPropertyMap"));
    Assertions.assertTrue(WebContainerCommand.isPreservedAcrossWidgetReset("socialMediaLinkList"));
    Assertions.assertTrue(WebContainerCommand.isPreservedAcrossWidgetReset("analyticsPropertyMap"));
    Assertions.assertTrue(WebContainerCommand.isPreservedAcrossWidgetReset("ecommercePropertyMap"));
  }

  @Test
  void existingControllerMasterAndRequestPrefixedAttributesStillSurvive() {
    // Unchanged pre-existing behavior -- must not regress with the new exemption added alongside it.
    Assertions.assertTrue(WebContainerCommand.isPreservedAcrossWidgetReset("controllerShowMainMenu"));
    Assertions.assertTrue(WebContainerCommand.isPreservedAcrossWidgetReset("masterWebPage"));
    Assertions.assertTrue(WebContainerCommand.isPreservedAcrossWidgetReset("requestObject"));
  }

  @Test
  void ordinaryPerWidgetAttributesAreStillWiped() {
    // Unchanged pre-existing behavior -- an ordinary widget-scoped attribute (e.g. a widget's own
    // rendering data) must still be reset between widgets so it can't leak into the next widget's
    // JSP, even though its name happens to be page-related in spirit.
    Assertions.assertFalse(WebContainerCommand.isPreservedAcrossWidgetReset("contentHtml"));
    Assertions.assertFalse(WebContainerCommand.isPreservedAcrossWidgetReset("collection"));
    Assertions.assertFalse(WebContainerCommand.isPreservedAcrossWidgetReset("isEditMode"));
  }

  // Regression coverage for a further composition-canvas bug found while verifying #259's fix
  // set: a column (or section) with zero widgets -- e.g. one just created by the "+Column"/
  // "+Section" mutate controls, before anything has been added to it -- was omitted from render
  // info entirely, in BOTH normal public rendering and pageLayoutMode. That's correct for public
  // rendering (no empty grid cell on a live page), but it meant a freshly added empty column/
  // section never got a rendered [data-editor-column]/[data-editor-section] element, so it had no
  // "+Widget"/"✕ Column" trigger to populate or remove it -- invisible and stuck until the
  // whole draft was discarded. processWidgets() now adds it anyway when pageLayoutMode is true.

  private static WebContainerContext newGetContext() {
    return newGetContext(new HashMap<>());
  }

  private static WebContainerContext newGetContext(Map<String, Object> widgetInstances) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    // Only reached once processWidgets() actually iterates a widget -- stubbed unconditionally
    // since which tests hit it depends on whether their column is empty.
    when(request.getAttributeNames()).thenReturn(Collections.emptyEnumeration());
    HttpServletResponse response = mock(HttpServletResponse.class);
    return new WebContainerContext(request, response, new ControllerSession(), widgetInstances, null, null);
  }

  private static Section sectionWith(Column... columns) {
    Section section = new Section();
    section.setColumns(List.of(columns));
    return section;
  }

  // WebContainerCommand.processWidgets() looks up an "execute" method by reflection on whatever's
  // registered in widgetInstances, matching how a real widget class is invoked.
  public static class StubWidget {
    public WidgetContext execute(WidgetContext context) {
      context.setHtml("<p>stub</p>");
      return context;
    }
  }

  // Mirrors ContentWidget.execute() when it has no "uniqueId" preference yet (e.g. immediately
  // after being added via the composition canvas's "+Widget" control, before any preferences have
  // been set): it returns null rather than setting a JSP or any HTML.
  public static class StubWidgetWithNoContent {
    public WidgetContext execute(WidgetContext context) {
      return null;
    }
  }

  @Test
  void emptyColumnOmittedFromRenderInfoOutsideLayoutMode() throws Exception {
    Section section = sectionWith(new Column());
    PageRenderInfo pageRenderInfo = new PageRenderInfo();

    WebContainerCommand.processWidgets(newGetContext(), List.of(section), pageRenderInfo,
        new HashMap<>(), "", "/test", mock(UserSession.class), new HashMap<>(), false);

    Assertions.assertTrue(pageRenderInfo.getSectionRenderInfoList().isEmpty(),
        "an empty column must not appear on a real page outside layout mode");
  }

  @Test
  void emptyColumnAndItsSectionAreKeptVisibleInLayoutMode() throws Exception {
    Section section = sectionWith(new Column());
    PageRenderInfo pageRenderInfo = new PageRenderInfo();

    WebContainerCommand.processWidgets(newGetContext(), List.of(section), pageRenderInfo,
        new HashMap<>(), "", "/test", mock(UserSession.class), new HashMap<>(), true);

    Assertions.assertEquals(1, pageRenderInfo.getSectionRenderInfoList().size(),
        "a freshly-added empty section must still render in layout mode, or its own "
            + "\"+Column\"/\"✕ Section\" controls are unreachable");
    SectionRenderInfo sectionRenderInfo = pageRenderInfo.getSectionRenderInfoList().get(0);
    Assertions.assertEquals(1, sectionRenderInfo.getColumnRenderInfoList().size(),
        "the empty column itself must still render, or its own \"+Widget\"/\"✕ Column\" controls are unreachable");
    Assertions.assertTrue(sectionRenderInfo.getColumnRenderInfoList().get(0).getWidgetRenderInfoList().isEmpty());
  }

  @Test
  void emptyColumnAlongsideAPopulatedOneIsStillKeptInLayoutMode() throws Exception {
    // The reported repro: an admin adds a second, empty column next to an existing column that
    // already has content -- both must survive the reload, not just the one with widgets.
    Column populated = new Column();
    populated.setWidgets(List.of(new Widget("exampleWidget")));
    Section section = sectionWith(populated, new Column());

    Map<String, Object> widgetInstances = new HashMap<>();
    widgetInstances.put("exampleWidget", new StubWidget());

    PageRenderInfo pageRenderInfo = new PageRenderInfo();

    WebContainerCommand.processWidgets(newGetContext(widgetInstances), List.of(section), pageRenderInfo,
        new HashMap<>(), "", "/test", mock(UserSession.class), new HashMap<>(), true);

    Assertions.assertEquals(1, pageRenderInfo.getSectionRenderInfoList().size());
    List<ColumnRenderInfo> columns = pageRenderInfo.getSectionRenderInfoList().get(0).getColumnRenderInfoList();
    Assertions.assertEquals(2, columns.size(), "both the populated and the empty column must render");
    Assertions.assertEquals(1, columns.get(0).getWidgetRenderInfoList().size());
    Assertions.assertTrue(columns.get(1).getWidgetRenderInfoList().isEmpty());
  }

  @Test
  void columnWithContentRendersRegardlessOfLayoutMode() throws Exception {
    Column populated = new Column();
    populated.setWidgets(List.of(new Widget("exampleWidget")));
    Section section = sectionWith(populated);

    Map<String, Object> widgetInstances = new HashMap<>();
    widgetInstances.put("exampleWidget", new StubWidget());

    PageRenderInfo pageRenderInfo = new PageRenderInfo();

    WebContainerCommand.processWidgets(newGetContext(widgetInstances), List.of(section), pageRenderInfo,
        new HashMap<>(), "", "/test", mock(UserSession.class), new HashMap<>(), false);

    Assertions.assertEquals(1, pageRenderInfo.getSectionRenderInfoList().size(),
        "unchanged pre-existing behavior -- a column with real content renders with or without layout mode");
    Assertions.assertEquals(1, pageRenderInfo.getSectionRenderInfoList().get(0).getColumnRenderInfoList().size());
  }

  // Regression coverage for a widget-granularity sibling of the column/section bug above, found
  // live-verifying #745: a widget that's been added to the layout but produces no output yet (e.g.
  // a "content" widget added via "+Widget", which has no uniqueId preference until the admin sets
  // one) was omitted from its column's render info entirely, in both public rendering and
  // pageLayoutMode. Correct for public rendering, but it meant the widget never got a rendered
  // [data-editor-widget] element, so it had no "+Widget after"/"✕ Widget"/"⚙ Prefs" trigger to
  // configure or remove it -- invisible and stuck (and blocking "✕ Column", which refuses to
  // remove a column that still contains a widget) even though it was really in draft_page_xml.

  @Test
  void widgetWithNoContentOmittedFromRenderInfoOutsideLayoutMode() throws Exception {
    Column column = new Column();
    column.setWidgets(List.of(new Widget("exampleWidget")));
    Section section = sectionWith(column);

    Map<String, Object> widgetInstances = new HashMap<>();
    widgetInstances.put("exampleWidget", new StubWidgetWithNoContent());

    PageRenderInfo pageRenderInfo = new PageRenderInfo();

    WebContainerCommand.processWidgets(newGetContext(widgetInstances), List.of(section), pageRenderInfo,
        new HashMap<>(), "", "/test", mock(UserSession.class), new HashMap<>(), false);

    Assertions.assertTrue(pageRenderInfo.getSectionRenderInfoList().isEmpty(),
        "a widget with no content must not appear on a real page outside layout mode");
  }

  @Test
  void widgetWithNoContentGetsPlaceholderInLayoutMode() throws Exception {
    Column column = new Column();
    column.setWidgets(List.of(new Widget("exampleWidget")));
    Section section = sectionWith(column);

    Map<String, Object> widgetInstances = new HashMap<>();
    widgetInstances.put("exampleWidget", new StubWidgetWithNoContent());

    PageRenderInfo pageRenderInfo = new PageRenderInfo();

    WebContainerCommand.processWidgets(newGetContext(widgetInstances), List.of(section), pageRenderInfo,
        new HashMap<>(), "", "/test", mock(UserSession.class), new HashMap<>(), true);

    Assertions.assertEquals(1, pageRenderInfo.getSectionRenderInfoList().size(),
        "a freshly-added widget's section must still render in layout mode, or the column/section "
            + "controls around it are unreachable");
    SectionRenderInfo sectionRenderInfo = pageRenderInfo.getSectionRenderInfoList().get(0);
    Assertions.assertEquals(1, sectionRenderInfo.getColumnRenderInfoList().size());
    List<WidgetRenderInfo> widgets = sectionRenderInfo.getColumnRenderInfoList().get(0).getWidgetRenderInfoList();
    Assertions.assertEquals(1, widgets.size(),
        "the widget itself must still render, or its own \"⚙ Prefs\"/\"✕ Widget\" controls are unreachable");
    Assertions.assertEquals("", widgets.get(0).getContent());
  }

  @Test
  void widgetWithNoContentAlongsideAPopulatedOneBothRenderInLayoutModeInOrder() throws Exception {
    // The reported repro's shape: a column with a real widget and a freshly-added, not-yet-configured
    // one -- both must survive the reload, in document order (platform-editor.js's "+Widget" trigger
    // counts existing [data-editor-widget] DOM elements to compute where the next one goes, so a
    // silently-dropped or reordered placeholder would misplace the next insert).
    Column column = new Column();
    column.setWidgets(List.of(new Widget("exampleWidget"), new Widget("blankWidget")));
    Section section = sectionWith(column);

    Map<String, Object> widgetInstances = new HashMap<>();
    widgetInstances.put("exampleWidget", new StubWidget());
    widgetInstances.put("blankWidget", new StubWidgetWithNoContent());

    PageRenderInfo pageRenderInfo = new PageRenderInfo();

    WebContainerCommand.processWidgets(newGetContext(widgetInstances), List.of(section), pageRenderInfo,
        new HashMap<>(), "", "/test", mock(UserSession.class), new HashMap<>(), true);

    List<WidgetRenderInfo> widgets = pageRenderInfo.getSectionRenderInfoList().get(0)
        .getColumnRenderInfoList().get(0).getWidgetRenderInfoList();
    Assertions.assertEquals(2, widgets.size(), "both the populated and the blank widget must render");
    Assertions.assertEquals("<p>stub</p>", widgets.get(0).getContent());
    Assertions.assertEquals("", widgets.get(1).getContent());
  }

  // Regression coverage for #799: a delete control that submits via a real HTTP POST (confirmPostAction()/
  // postAction() in main.jsp, or a literal <form method="post">) must still resolve to METHOD_DELETE, not
  // METHOD_POST -- checking isPost() before command=delete silently routed every such click to a widget's
  // post(WidgetContext) instead of delete(WidgetContext), which broke Delete on every widget that defines
  // delete() but not a matching post() (see #796/#798 for the first instance found, #799 for the full list).
  // These tests exercise WebContainerContext's constructor directly, the exact logic that made that
  // decision, so they fail if the precedence regresses.

  private static WebContainerContext contextWith(String httpMethod, String command) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn(httpMethod);
    when(request.getParameter("command")).thenReturn(command);
    HttpServletResponse response = mock(HttpServletResponse.class);
    return new WebContainerContext(request, response, new ControllerSession(), new HashMap<>(), null, null);
  }

  @Test
  void postWithDeleteCommandResolvesToDeleteNotPost() {
    WebContainerContext context = contextWith("POST", "delete");

    Assertions.assertTrue(context.isDelete(), "a POST carrying command=delete must resolve to delete()");
    Assertions.assertFalse(context.isPost(), "must not also resolve as a plain post()");
  }

  @Test
  void getWithDeleteCommandStillResolvesToDelete() {
    // Unchanged pre-existing behavior -- a plain GET delete link (no confirmPostAction) already worked.
    WebContainerContext context = contextWith("GET", "delete");

    Assertions.assertTrue(context.isDelete());
    Assertions.assertFalse(context.isPost());
  }

  @Test
  void plainPostWithoutDeleteCommandStillResolvesToPost() {
    // The common case -- an ordinary form save POST, no command parameter at all -- must be unaffected.
    WebContainerContext context = contextWith("POST", null);

    Assertions.assertTrue(context.isPost());
    Assertions.assertFalse(context.isDelete());
  }

  @Test
  void postWithNonDeleteCommandStillResolvesToPost() {
    // A POST whose command is something other than "delete" (e.g. BlockedIPListWidget's
    // downloadCSVFile/uploadCSVFile) must still reach post(), which does its own command dispatch.
    WebContainerContext context = contextWith("POST", "uploadCSVFile");

    Assertions.assertTrue(context.isPost());
    Assertions.assertFalse(context.isDelete());
  }

  // Regression coverage for #826: a violation-triggering /content-editor save's warning message
  // must actually survive to the redirect target's render, not merely be set on the WidgetContext
  // (that part already worked -- #258/PR #822). The flash mechanism exercised above (widgetWarning
  // Message / WARNING_MESSAGE, read back per-widget at the top of this method) only re-surfaces a
  // message when a widget with the SAME computed per-page uniqueId (widget.getWidgetName() + its
  // position on the page) renders on the very next page. returnPage is the live page the content
  // lives on and never contains the content-editor widget, so a warning sent there was silently
  // dropped. ContentEditorWidget.post() now redirects back to /content-editor itself -- where this
  // same widget instance renders again -- whenever there's a warning to show. Proven end-to-end here
  // through the real processWidgets() flash mechanism, across two separate simulated requests
  // sharing one ControllerSession (as a redirect + follow-up GET would).

  @Test
  void a11yWarningSurvivesARedirectBackToTheContentEditor() throws Exception {
    UserSession userSession = new UserSession();
    String formToken = userSession.getFormToken();

    ControllerSession controllerSession = new ControllerSession();

    // Mirrors cms-layout.xml's /content-editor page: exactly one widget on the page, so its
    // computed per-page uniqueId is deterministically "contentEditor1" on both requests below.
    Column column = new Column();
    column.setWidgets(List.of(new Widget("contentEditor")));
    Section section = sectionWith(column);

    Map<String, String> coreData = new HashMap<>();
    coreData.put("userId", "1");

    Content savedContent = new Content();
    savedContent.setId(42L);
    savedContent.setUniqueId("hello-content");
    savedContent.setDraftContent("<p>Text</p><img src=\"/assets/foo.jpg\">");

    // Phase 1: the save POST that finds an accessibility violation.
    HttpServletRequest postRequest = mock(HttpServletRequest.class);
    when(postRequest.getMethod()).thenReturn("POST");
    when(postRequest.getAttributeNames()).thenReturn(Collections.emptyEnumeration());
    Map<String, String[]> postParams = new HashMap<>();
    postParams.put("uniqueId", new String[] { "hello-content" });
    postParams.put("content", new String[] { "<p>Text</p><img src=\"/assets/foo.jpg\">" });
    postParams.put("save", new String[] { "Save as Draft" });
    postParams.put("returnPage", new String[] { "/about-us" });
    when(postRequest.getParameterMap()).thenReturn(postParams);
    when(postRequest.getParameter("token")).thenReturn(formToken);
    HttpServletResponse postResponse = mock(HttpServletResponse.class);

    Map<String, Object> postWidgetInstances = new HashMap<>();
    postWidgetInstances.put("contentEditor", new ContentEditorWidget());

    WebContainerContext postContext = new WebContainerContext(postRequest, postResponse, controllerSession,
        postWidgetInstances, null, null);
    PageRenderInfo postRenderInfo = new PageRenderInfo();
    postRenderInfo.setTargetWidget("contentEditor1");

    try (MockedStatic<SaveContentCommand> saveContent = mockStatic(SaveContentCommand.class)) {
      saveContent
          .when(() -> SaveContentCommand.saveSafeContent(eq("hello-content"),
              eq("<p>Text</p><img src=\"/assets/foo.jpg\">"), anyLong(), eq(false)))
          .thenReturn(savedContent);

      boolean handled = WebContainerCommand.processWidgets(postContext, List.of(section), postRenderInfo, coreData,
          "", "/content-editor", userSession, new HashMap<>(), false);
      Assertions.assertTrue(handled, "a targeted POST that issues a redirect must short-circuit further rendering");
    }

    // The redirect goes back to the editor itself, carrying the saved content's uniqueId and the
    // original returnPage, instead of straight out to returnPage -- that is the fix.
    verify(postResponse).sendRedirect("/content-editor?uniqueId=hello-content&returnPage=%2Fabout-us");

    // Phase 2: the browser follows that redirect with a plain GET to the same page. A stub stands in
    // for ContentEditorWidget here since its own execute() hits the database -- the mechanism under
    // test is WebContainerCommand's flash-message pickup, which runs before any widget method is
    // invoked, so which class actually implements the widget does not matter.
    HttpServletRequest getRequest = mock(HttpServletRequest.class);
    when(getRequest.getMethod()).thenReturn("GET");
    when(getRequest.getAttributeNames()).thenReturn(Collections.emptyEnumeration());
    when(getRequest.getParameterMap()).thenReturn(Map.of("uniqueId", new String[] { "hello-content" }));
    HttpServletResponse getResponse = mock(HttpServletResponse.class);

    Map<String, Object> getWidgetInstances = new HashMap<>();
    getWidgetInstances.put("contentEditor", new StubWidget());

    WebContainerContext getContext = new WebContainerContext(getRequest, getResponse, controllerSession,
        getWidgetInstances, null, null);
    PageRenderInfo getRenderInfo = new PageRenderInfo();

    WebContainerCommand.processWidgets(getContext, List.of(section), getRenderInfo, coreData, "", "/content-editor",
        userSession, new HashMap<>(), false);

    // This is the exact request attribute page_messages.jspf reads (${warningMessage}) -- genuine
    // end-to-end proof the warning reaches the redirect target's render, not just that
    // setWarningMessage() was called on the WidgetContext during the save itself.
    ArgumentCaptor<String> warningCaptor = ArgumentCaptor.forClass(String.class);
    verify(getRequest).setAttribute(eq(RequestConstants.WARNING_MESSAGE_TEXT), warningCaptor.capture());
    Assertions.assertTrue(warningCaptor.getValue().contains("accessibility"), warningCaptor.getValue());
    Assertions.assertTrue(warningCaptor.getValue().contains("missing alt text"), warningCaptor.getValue());
  }

}
