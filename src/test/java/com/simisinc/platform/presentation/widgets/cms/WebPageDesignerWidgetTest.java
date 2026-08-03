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

package com.simisinc.platform.presentation.widgets.cms;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.SaveWebPageCommand;
import com.simisinc.platform.application.cms.WebPageXmlLayoutCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.cms.WebPageTemplate;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageTemplateRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.controller.XMLWebPageTemplateLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/8/2022 7:00 AM
 */
class WebPageDesignerWidgetTest extends WidgetBase {

  @Test
  void executeUseTemplates() {

    // Set request parameters
    addQueryParameter(widgetContext, "returnPage", "/web-page");
    addQueryParameter(widgetContext, "webPage", "web-page");

    // Shows the editor
    try (MockedStatic<WebPageRepository> webPageRepositoryMockedStatic = mockStatic(WebPageRepository.class)) {
      webPageRepositoryMockedStatic.when(() -> WebPageRepository.findByLink("web-page")).thenReturn(null);

      List<WebPageTemplate> webPageTemplateList = new ArrayList<>();
      WebPageTemplate webPageTemplate = new WebPageTemplate();
      webPageTemplate.setName("Template A");
      webPageTemplate.setCategory("CMS");
      webPageTemplate.setTemplateOrder(2);
      webPageTemplateList.add(webPageTemplate);

      List<WebPageTemplate> webPageTemplateList2 = new ArrayList<>();
      WebPageTemplate webPageTemplate2 = new WebPageTemplate();
      webPageTemplate2.setName("Template B");
      webPageTemplate2.setCategory("CMS");
      webPageTemplate2.setTemplateOrder(1);
      webPageTemplateList2.add(webPageTemplate2);

      try (MockedStatic<XMLWebPageTemplateLoader> xmlWebPageTemplateLoaderMockedStatic = mockStatic(XMLWebPageTemplateLoader.class)) {
        xmlWebPageTemplateLoaderMockedStatic.when(() -> XMLWebPageTemplateLoader.retrieveTemplateList(any())).thenReturn(webPageTemplateList);

        try (MockedStatic<WebPageTemplateRepository> webPageTemplateRepositoryMockedStatic = mockStatic(WebPageTemplateRepository.class)) {
          webPageTemplateRepositoryMockedStatic.when(WebPageTemplateRepository::findAll).thenReturn(webPageTemplateList2);

          WebPageDesignerWidget widget = new WebPageDesignerWidget();
          widget.execute(widgetContext);

          // Verify the output
          Assertions.assertEquals(WebPageDesignerWidget.TEMPLATES_JSP, widgetContext.getJsp());

          List<WebPageTemplate> webPageTemplateListRequest = (List) request.getAttribute("webPageTemplateList");
          Assertions.assertEquals(2, webPageTemplateListRequest.size());
          Assertions.assertEquals("Template B", webPageTemplateListRequest.get(0).getName());
          Assertions.assertEquals("Template A", webPageTemplateListRequest.get(1).getName());
        }
      }
    }
  }

  @Test
  void executeDisplayError() {
    // Set request parameters
    addQueryParameter(widgetContext, "returnPage", "/web-page");
    addQueryParameter(widgetContext, "webPage", "web-page");

    // Add the form data which has error
    WebPage webPage = new WebPage();
    webPage.setPageXml("<page><section><column><widget name=\"content\" /></column></section></page>");
    widgetContext.setRequestObject(webPage);

    // Show a form Error
    WebPageDesignerWidget widget = new WebPageDesignerWidget();
    widget.execute(widgetContext);
    Assertions.assertEquals(WebPageDesignerWidget.ACE_XML_EDITOR_JSP, widgetContext.getJsp());
    Assertions.assertNotNull(request.getAttribute("webPage"));
  }

  @Test
  void postEditorSuccess() {
    // Set POST parameters
    addQueryParameter(widgetContext, "widget", widgetContext.getUniqueId());
    addQueryParameter(widgetContext, "token", "12345");
    addQueryParameter(widgetContext, "webPage", "/web-page");
    addQueryParameter(widgetContext, "returnPage", "/web-page");
    addQueryParameter(widgetContext, "pageXmlValue", "<page><section><column><widget name=\"content\" /></column></section></page>");

    // Execute the widget
    try (MockedStatic<WebPageRepository> webPageRepositoryMockedStatic = mockStatic(WebPageRepository.class)) {
      webPageRepositoryMockedStatic.when(() -> WebPageRepository.findByLink("/web-page")).thenReturn(null);

      try (MockedStatic<SaveWebPageCommand> saveWebPageCommandMockedStatic = mockStatic(SaveWebPageCommand.class)) {
        saveWebPageCommandMockedStatic.when(() -> SaveWebPageCommand.saveWebPage(any())).thenReturn(new WebPage());

        // Execute
        WebPageDesignerWidget widget = new WebPageDesignerWidget();
        WidgetContext result = widget.post(widgetContext);

        // Verify
        Assertions.assertNotNull(result);
        Assertions.assertNull(widgetContext.getWarningMessage());
        Assertions.assertNull(widgetContext.getErrorMessage());
        Assertions.assertEquals("/web-page", widgetContext.getRedirect());
      }
    }
  }

  @Test
  void postError() {
    // Set POST parameters
    addQueryParameter(widgetContext, "widget", widgetContext.getUniqueId());
    addQueryParameter(widgetContext, "token", "12345");
    addQueryParameter(widgetContext, "webPage", "/web-page");
    addQueryParameter(widgetContext, "returnPage", "/web-page");
    addQueryParameter(widgetContext, "pageXmlValue", "<page><section><column><widget name=\"content\" /></column></section></page>");

    // Execute the widget
    try (MockedStatic<WebPageRepository> webPageRepositoryMockedStatic = mockStatic(WebPageRepository.class)) {
      webPageRepositoryMockedStatic.when(() -> WebPageRepository.findByLink("/web-page")).thenReturn(null);

      try (MockedStatic<SaveWebPageCommand> saveWebPageCommandMockedStatic = mockStatic(SaveWebPageCommand.class)) {
        saveWebPageCommandMockedStatic.when(() -> SaveWebPageCommand.saveWebPage(any())).thenThrow(new DataException("Error"));

        // Execute
        WebPageDesignerWidget widget = new WebPageDesignerWidget();
        WidgetContext result = widget.post(widgetContext);

        // Verify
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(widgetContext.getErrorMessage());
        Assertions.assertNull(widgetContext.getRedirect());
      }
    }
  }

  // Regression coverage for issue #532: post()'s gridmanager-designer branch (triggered by the "content"
  // parameter, distinct from the raw-XML "pageXmlValue" branch the tests above exercise) used to call
  // WebPageDesignerToXmlCommand.convertFromBootstrapHtml() OUTSIDE the try/catch that wraps the save --
  // an exception from conversion itself (which #532's fix makes possible, via the new widget-name
  // validation) would have propagated up uncaught instead of producing the same clean error response a
  // save failure gets. These two tests exercise that branch directly, the one no existing test above did.

  private static final Map<String, String> REGISTERED_WIDGETS = Map.of(
      "content", "com.simisinc.platform.presentation.widgets.cms.ContentWidget",
      "map", "com.simisinc.platform.presentation.widgets.maps.MapWidget");

  @Test
  void postDesignerContentWithARealWidgetTypeSaves() {
    addQueryParameter(widgetContext, "widget", widgetContext.getUniqueId());
    addQueryParameter(widgetContext, "token", "12345");
    addQueryParameter(widgetContext, "webPage", "/web-page");
    addQueryParameter(widgetContext, "content",
        "<div class=\"row\"><div class=\"column col-sm-12 col-md-12 col-xs-12\">"
            + "<!--gm-editable-region--><h3 data-widget=\"map\">Map</h3><p>Write a description</p><!--/gm-editable-region-->"
            + "</div></div>");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<SaveWebPageCommand> saveWebPageCommand = mockStatic(SaveWebPageCommand.class);
        MockedStatic<WebPageXmlLayoutCommand> widgetLibrary = mockStatic(WebPageXmlLayoutCommand.class)) {
      webPageRepository.when(() -> WebPageRepository.findByLink("/web-page")).thenReturn(null);
      saveWebPageCommand.when(() -> SaveWebPageCommand.saveWebPage(any())).thenReturn(new WebPage());
      widgetLibrary.when(WebPageXmlLayoutCommand::getWidgetLibrary).thenReturn(REGISTERED_WIDGETS);

      WebPageDesignerWidget widget = new WebPageDesignerWidget();
      widget.post(widgetContext);

      Assertions.assertEquals("[{\"status\":\"0\"}]", widgetContext.getJson());
      saveWebPageCommand.verify(() -> SaveWebPageCommand.saveWebPage(any()));
    }
  }

  @Test
  void postDesignerContentWithAnUnregisteredWidgetTypeFailsCleanly() {
    addQueryParameter(widgetContext, "widget", widgetContext.getUniqueId());
    addQueryParameter(widgetContext, "token", "12345");
    addQueryParameter(widgetContext, "webPage", "/web-page");
    addQueryParameter(widgetContext, "content",
        "<div class=\"row\"><div class=\"column col-sm-12 col-md-12 col-xs-12\">"
            + "<!--gm-editable-region--><h3 data-widget=\"not-a-real-widget\">Headline</h3><p>Write a description</p><!--/gm-editable-region-->"
            + "</div></div>");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<SaveWebPageCommand> saveWebPageCommand = mockStatic(SaveWebPageCommand.class);
        MockedStatic<WebPageXmlLayoutCommand> widgetLibrary = mockStatic(WebPageXmlLayoutCommand.class)) {
      webPageRepository.when(() -> WebPageRepository.findByLink("/web-page")).thenReturn(null);
      widgetLibrary.when(WebPageXmlLayoutCommand::getWidgetLibrary).thenReturn(REGISTERED_WIDGETS);

      WebPageDesignerWidget widget = new WebPageDesignerWidget();
      widget.post(widgetContext);

      // Must fail the same clean way a save failure does -- not throw uncaught, not silently succeed.
      Assertions.assertTrue(widgetContext.getJson().contains("could not be saved"), widgetContext.getJson());
      saveWebPageCommand.verifyNoInteractions();
    }
  }

  // Issue #410: the P4 composition-canvas gate. These prove (a) the flag suppresses all three of
  // WebPageDesignerWidget's "editor=designer" branch points -- the GET ?editor=designer param, the
  // template picker offering, and the save-time redirect -- and (b) a page that already has
  // editor="designer" in its persisted XML keeps rendering through its normal (non-canvas) path,
  // untouched, whether the flag is on or off.

  private WebPageTemplate template(String name, String pageXml) {
    WebPageTemplate template = new WebPageTemplate();
    template.setName(name);
    template.setCategory("CMS");
    template.setTemplateOrder(1);
    template.setPageXml(pageXml);
    return template;
  }

  @Test
  void executeFallsBackToTemplatePickerWhenDesignerRequestedButFlagIsOff() {
    addQueryParameter(widgetContext, "editor", "designer");
    addQueryParameter(widgetContext, "webPage", "/new-page");

    List<WebPageTemplate> webPageTemplateList = new ArrayList<>();
    webPageTemplateList.add(template("Blank", "<page><section><column class=\"small-12 cell\"></column></section></page>"));
    webPageTemplateList.add(template("Webpage Designer", "<page editor=\"designer\" />"));

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<XMLWebPageTemplateLoader> loader = mockStatic(XMLWebPageTemplateLoader.class);
        MockedStatic<WebPageTemplateRepository> repository = mockStatic(WebPageTemplateRepository.class)) {
      webPageRepository.when(() -> WebPageRepository.findByLink("/new-page")).thenReturn(null);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("features.layout-editor")).thenReturn(false);
      loader.when(() -> XMLWebPageTemplateLoader.retrieveTemplateList(any())).thenReturn(webPageTemplateList);
      repository.when(WebPageTemplateRepository::findAll).thenReturn(new ArrayList<>());

      WebPageDesignerWidget widget = new WebPageDesignerWidget();
      widget.execute(widgetContext);

      // Not the canvas -- the ?editor=designer param is ignored, so the request falls back to the
      // normal template-picker JSP instead of WebPageDesignerWidget.DESIGNER_JSP
      Assertions.assertEquals(WebPageDesignerWidget.TEMPLATES_JSP, widgetContext.getJsp());

      // And the designer template itself is no longer offered from that picker
      List<WebPageTemplate> shown = (List) request.getAttribute("webPageTemplateList");
      Assertions.assertEquals(1, shown.size());
      Assertions.assertEquals("Blank", shown.get(0).getName());
    }
  }

  @Test
  void executeStillOffersTheDesignerWhenTheFlagIsOn() {
    addQueryParameter(widgetContext, "webPage", "/new-page");

    List<WebPageTemplate> webPageTemplateList = new ArrayList<>();
    webPageTemplateList.add(template("Blank", "<page><section><column class=\"small-12 cell\"></column></section></page>"));
    webPageTemplateList.add(template("Webpage Designer", "<page editor=\"designer\" />"));

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<XMLWebPageTemplateLoader> loader = mockStatic(XMLWebPageTemplateLoader.class);
        MockedStatic<WebPageTemplateRepository> repository = mockStatic(WebPageTemplateRepository.class)) {
      webPageRepository.when(() -> WebPageRepository.findByLink("/new-page")).thenReturn(null);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("features.layout-editor")).thenReturn(true);
      loader.when(() -> XMLWebPageTemplateLoader.retrieveTemplateList(any())).thenReturn(webPageTemplateList);
      repository.when(WebPageTemplateRepository::findAll).thenReturn(new ArrayList<>());

      WebPageDesignerWidget widget = new WebPageDesignerWidget();
      widget.execute(widgetContext);

      Assertions.assertEquals(WebPageDesignerWidget.TEMPLATES_JSP, widgetContext.getJsp());
      List<WebPageTemplate> shown = (List) request.getAttribute("webPageTemplateList");
      Assertions.assertEquals(2, shown.size());
    }
  }

  @Test
  void executeOpensTheCanvasDirectlyForANewBlankPageWhenFlagIsOn() {
    addQueryParameter(widgetContext, "editor", "designer");
    addQueryParameter(widgetContext, "webPage", "/new-page");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      webPageRepository.when(() -> WebPageRepository.findByLink("/new-page")).thenReturn(null);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("features.layout-editor")).thenReturn(true);

      WebPageDesignerWidget widget = new WebPageDesignerWidget();
      widget.execute(widgetContext);

      Assertions.assertEquals(WebPageDesignerWidget.DESIGNER_JSP, widgetContext.getJsp());
      WebPage webPage = (WebPage) request.getAttribute("webPage");
      Assertions.assertNotNull(webPage);
      Assertions.assertTrue(webPage.getPageXml().contains("widget name=\"content\""));
    }
  }

  @Test
  void executeRendersAnExistingDesignerTaggedPageThroughItsNormalPathRegardlessOfTheFlag() {
    // No "editor" query param -- this is what every real link to this widget sends
    // (layout-header-standard.jspf's "Edit Page Layout", admin/web-page-list.jsp's XML icon, etc.);
    // ?editor=designer is only ever set by this same widget's own save-time redirect.
    addQueryParameter(widgetContext, "webPage", "/existing-designer-page");

    WebPage existing = new WebPage();
    existing.setLink("/existing-designer-page");
    existing.setPageXml("<page editor=\"designer\"><section><column class=\"small-12 cell\">"
        + "<widget name=\"content\"><uniqueId>hello</uniqueId></widget></column></section></page>");

    // Deliberately not mocking LoadSitePropertyCommand -- this path never consults the flag at all
    // (no "editor" param means the designer branch short-circuits before any flag lookup), so this
    // also proves that by construction rather than by stubbing a particular return value.
    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      webPageRepository.when(() -> WebPageRepository.findByLink("/existing-designer-page")).thenReturn(existing);

      WebPageDesignerWidget widget = new WebPageDesignerWidget();
      widget.execute(widgetContext);

      // The raw XML editor, not the canvas -- and the stored XML is exactly what it was
      Assertions.assertEquals(WebPageDesignerWidget.ACE_XML_EDITOR_JSP, widgetContext.getJsp());
      WebPage shown = (WebPage) request.getAttribute("webPage");
      Assertions.assertSame(existing, shown);
      Assertions.assertTrue(shown.getPageXml().contains("editor=\"designer\""), "stored XML must be untouched");
    }
  }

  @Test
  void postFallsBackToNormalSaveInsteadOfRedirectingToTheDesignerWhenFlagIsOff() {
    addQueryParameter(widgetContext, "widget", widgetContext.getUniqueId());
    addQueryParameter(widgetContext, "token", "12345");
    addQueryParameter(widgetContext, "webPage", "/new-designer-page");
    addQueryParameter(widgetContext, "pageXml",
        "<page editor=\"designer\"><section><column class=\"small-12 cell\"></column></section></page>");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<SaveWebPageCommand> saveWebPageCommand = mockStatic(SaveWebPageCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      webPageRepository.when(() -> WebPageRepository.findByLink("/new-designer-page")).thenReturn(null);
      saveWebPageCommand.when(() -> SaveWebPageCommand.saveWebPage(any())).thenAnswer(invocation -> invocation.getArgument(0));
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("features.layout-editor")).thenReturn(false);

      WebPageDesignerWidget widget = new WebPageDesignerWidget();
      WidgetContext result = widget.post(widgetContext);

      Assertions.assertNotNull(result);
      Assertions.assertNull(widgetContext.getWarningMessage());
      Assertions.assertNull(widgetContext.getErrorMessage());
      // Saved and sent back to the page itself -- not bounced to /admin/web-page-designer?editor=designer
      Assertions.assertEquals("/new-designer-page", widgetContext.getRedirect());
      saveWebPageCommand.verify(() -> SaveWebPageCommand.saveWebPage(any()));
    }
  }

  @Test
  void postRedirectsToTheDesignerWhenFlagIsOn() {
    addQueryParameter(widgetContext, "widget", widgetContext.getUniqueId());
    addQueryParameter(widgetContext, "token", "12345");
    addQueryParameter(widgetContext, "webPage", "/new-designer-page");
    addQueryParameter(widgetContext, "pageXml",
        "<page editor=\"designer\"><section><column class=\"small-12 cell\"></column></section></page>");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      webPageRepository.when(() -> WebPageRepository.findByLink("/new-designer-page")).thenReturn(null);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("features.layout-editor")).thenReturn(true);

      WebPageDesignerWidget widget = new WebPageDesignerWidget();
      WidgetContext result = widget.post(widgetContext);

      Assertions.assertNotNull(result);
      Assertions.assertEquals("/admin/web-page-designer?editor=designer&webPage=/new-designer-page", widgetContext.getRedirect());
    }
  }
}