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

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.presentation.widgets.cms.WebContainerContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

import static org.mockito.Mockito.mock;
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

}
