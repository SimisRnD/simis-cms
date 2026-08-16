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
import org.mockito.ArgumentCaptor;
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
    try (MockedStatic<WebPageRepository> webPageRepositoryMockedStatic = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
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
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      WebPageDesignerWidget widget = new WebPageDesignerWidget();
      widget.execute(widgetContext);
      Assertions.assertEquals(WebPageDesignerWidget.ACE_XML_EDITOR_JSP, widgetContext.getJsp());
      Assertions.assertNotNull(request.getAttribute("webPage"));
    }
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
    try (MockedStatic<WebPageRepository> webPageRepositoryMockedStatic = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
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

  // Regression coverage for the template-picker onboarding gap: web-page-templates.jsp used to hide
  // its title field entirely for the home page ("/"), and never collected a description at all --
  // both are set from the same "title"/"description" request parameters post() already reads/sets, so
  // this proves the widget's own save logic never excluded the home page (the exclusion was JSP-only)
  // and that description is now actually persisted.

  @Test
  void postSetsTitleAndDescriptionForANewPageIncludingTheHomePage() {
    addQueryParameter(widgetContext, "widget", widgetContext.getUniqueId());
    addQueryParameter(widgetContext, "token", "12345");
    addQueryParameter(widgetContext, "webPage", "/");
    addQueryParameter(widgetContext, "returnPage", "/");
    addQueryParameter(widgetContext, "title", "My Home Page");
    addQueryParameter(widgetContext, "description", "A short description shown in search results");
    addQueryParameter(widgetContext, "pageXml", "<page><section><column><widget name=\"content\" /></column></section></page>");

    try (MockedStatic<WebPageRepository> webPageRepositoryMockedStatic = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      webPageRepositoryMockedStatic.when(() -> WebPageRepository.findByLink("/")).thenReturn(null);

      try (MockedStatic<SaveWebPageCommand> saveWebPageCommandMockedStatic = mockStatic(SaveWebPageCommand.class)) {
        ArgumentCaptor<WebPage> saved = ArgumentCaptor.forClass(WebPage.class);
        saveWebPageCommandMockedStatic.when(() -> SaveWebPageCommand.saveWebPage(saved.capture())).thenReturn(new WebPage());

        WebPageDesignerWidget widget = new WebPageDesignerWidget();
        WidgetContext result = widget.post(widgetContext);

        Assertions.assertNotNull(result);
        Assertions.assertNull(widgetContext.getErrorMessage());
        Assertions.assertEquals("My Home Page", saved.getValue().getTitle());
        Assertions.assertEquals("A short description shown in search results", saved.getValue().getDescription());
      }
    }
  }

  @Test
  void postLeavesTitleAndDescriptionUnchangedWhenNotSubmitted() {
    addQueryParameter(widgetContext, "widget", widgetContext.getUniqueId());
    addQueryParameter(widgetContext, "token", "12345");
    addQueryParameter(widgetContext, "webPage", "/existing-page");
    addQueryParameter(widgetContext, "returnPage", "/existing-page");
    addQueryParameter(widgetContext, "pageXml", "<page><section><column><widget name=\"content\" /></column></section></page>");

    WebPage existing = new WebPage();
    existing.setLink("/existing-page");
    existing.setTitle("Existing Title");
    existing.setDescription("Existing description");

    try (MockedStatic<WebPageRepository> webPageRepositoryMockedStatic = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      webPageRepositoryMockedStatic.when(() -> WebPageRepository.findByLink("/existing-page")).thenReturn(existing);

      try (MockedStatic<SaveWebPageCommand> saveWebPageCommandMockedStatic = mockStatic(SaveWebPageCommand.class)) {
        ArgumentCaptor<WebPage> saved = ArgumentCaptor.forClass(WebPage.class);
        saveWebPageCommandMockedStatic.when(() -> SaveWebPageCommand.saveWebPage(saved.capture())).thenReturn(new WebPage());

        WebPageDesignerWidget widget = new WebPageDesignerWidget();
        widget.post(widgetContext);

        Assertions.assertEquals("Existing Title", saved.getValue().getTitle());
        Assertions.assertEquals("Existing description", saved.getValue().getDescription());
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
    try (MockedStatic<WebPageRepository> webPageRepositoryMockedStatic = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
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
        // #1237: the submitted content must be preserved so the redisplayed form shows what
        // failed to save, instead of coming back blank.
        Assertions.assertNotNull(widgetContext.getRequestObject(),
            "the in-progress webPage must be set as the request object on a DataException save failure");
      }
    }
  }

  // Regression coverage for issue #1237: SaveWebPageCommand.saveWebPage() (and
  // WebPageRepository.save() on the governed-review branch) return null rather than throwing when
  // the underlying insert/update fails -- DB.insertInto()/DB.update() swallow the SQLException and
  // report failure by returning -1/false, not by propagating an exception. That's a genuinely
  // different failure path than postError() above (which covers the thrown-DataException case): the
  // webPage == null branch, not the catch block. Both must set the request object so the form
  // redisplays the user's in-progress content.
  @Test
  void postShowsAnErrorAndPreservesTheSubmittedContentWhenSaveReturnsNullWithoutThrowing() {
    addQueryParameter(widgetContext, "widget", widgetContext.getUniqueId());
    addQueryParameter(widgetContext, "token", "12345");
    addQueryParameter(widgetContext, "webPage", "/new-page");
    addQueryParameter(widgetContext, "returnPage", "/new-page");
    addQueryParameter(widgetContext, "pageXml", "<page><section><column><widget name=\"content\" /></column></section></page>");

    try (MockedStatic<WebPageRepository> webPageRepositoryMockedStatic = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      webPageRepositoryMockedStatic.when(() -> WebPageRepository.findByLink("/new-page")).thenReturn(null);

      try (MockedStatic<SaveWebPageCommand> saveWebPageCommandMockedStatic = mockStatic(SaveWebPageCommand.class)) {
        saveWebPageCommandMockedStatic.when(() -> SaveWebPageCommand.saveWebPage(any())).thenReturn(null);

        WebPageDesignerWidget widget = new WebPageDesignerWidget();
        WidgetContext result = widget.post(widgetContext);

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(widgetContext.getErrorMessage());
        Assertions.assertNull(widgetContext.getRedirect());
        Object requestObject = widgetContext.getRequestObject();
        Assertions.assertNotNull(requestObject,
            "the in-progress webPage must be set as the request object when save() returns null");
        Assertions.assertInstanceOf(WebPage.class, requestObject);
        Assertions.assertEquals("<page><section><column><widget name=\"content\" /></column></section></page>",
            ((WebPage) requestObject).getPageXml(),
            "the redisplayed page must show what the user actually submitted, not a blank editor");
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
        MockedStatic<WebPageXmlLayoutCommand> widgetLibrary = mockStatic(WebPageXmlLayoutCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
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
        MockedStatic<WebPageXmlLayoutCommand> widgetLibrary = mockStatic(WebPageXmlLayoutCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
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

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
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

  // Issue #957: this legacy editor writes page content directly via SaveWebPageCommand, with none of
  // the draftPageXml/submit/approve machinery the P4 layout builder (PageServlet) goes through --
  // completely bypassing governed review. These prove that when webPage.review.required is on, new
  // content lands in draftPageXml (leaving whatever is already live untouched) instead of publishing
  // straight to pageXml, and that legacy behavior is unchanged when review is not required.

  @Test
  void postRawXmlSaveRoutesToDraftPageXmlWhenReviewIsRequiredInsteadOfPublishingDirectly() {
    addQueryParameter(widgetContext, "widget", widgetContext.getUniqueId());
    addQueryParameter(widgetContext, "token", "12345");
    addQueryParameter(widgetContext, "webPage", "/existing-live-page");
    addQueryParameter(widgetContext, "returnPage", "/existing-live-page");
    addQueryParameter(widgetContext, "pageXml",
        "<page><section><column><widget name=\"content\"><uniqueId>injected</uniqueId></widget></column></section></page>");

    WebPage existing = new WebPage();
    existing.setId(42);
    existing.setLink("/existing-live-page");
    existing.setPageXml("<page><section><column><widget name=\"content\"><uniqueId>reviewed-and-live</uniqueId></widget></column></section></page>");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<SaveWebPageCommand> saveWebPageCommand = mockStatic(SaveWebPageCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      webPageRepository.when(() -> WebPageRepository.findByLink("/existing-live-page")).thenReturn(existing);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("webPage.review.required")).thenReturn(true);
      webPageRepository.when(() -> WebPageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      WebPageDesignerWidget widget = new WebPageDesignerWidget();
      WidgetContext result = widget.post(widgetContext);

      Assertions.assertNotNull(result);
      Assertions.assertNull(widgetContext.getErrorMessage());
      Assertions.assertNull(widgetContext.getWarningMessage());

      ArgumentCaptor<WebPage> saved = ArgumentCaptor.forClass(WebPage.class);
      webPageRepository.verify(() -> WebPageRepository.save(saved.capture()));
      Assertions.assertTrue(saved.getValue().getPageXml().contains("reviewed-and-live"),
          "the live page content must not change until the new content is reviewed and approved");
      Assertions.assertTrue(saved.getValue().getDraftPageXml().contains("injected"),
          "the new content must land in draftPageXml pending review");

      // The single save chokepoint that would publish straight to live must never be reached
      saveWebPageCommand.verifyNoInteractions();
    }
  }

  @Test
  void postRawXmlSavePublishesDirectlyWhenReviewIsNotRequired() {
    addQueryParameter(widgetContext, "widget", widgetContext.getUniqueId());
    addQueryParameter(widgetContext, "token", "12345");
    addQueryParameter(widgetContext, "webPage", "/existing-live-page");
    addQueryParameter(widgetContext, "returnPage", "/existing-live-page");
    addQueryParameter(widgetContext, "pageXml",
        "<page><section><column><widget name=\"content\"><uniqueId>new-content</uniqueId></widget></column></section></page>");

    WebPage existing = new WebPage();
    existing.setId(42);
    existing.setLink("/existing-live-page");
    existing.setPageXml("<page><section><column><widget name=\"content\"><uniqueId>old-content</uniqueId></widget></column></section></page>");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<SaveWebPageCommand> saveWebPageCommand = mockStatic(SaveWebPageCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      webPageRepository.when(() -> WebPageRepository.findByLink("/existing-live-page")).thenReturn(existing);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("webPage.review.required")).thenReturn(false);
      saveWebPageCommand.when(() -> SaveWebPageCommand.saveWebPage(any())).thenAnswer(invocation -> invocation.getArgument(0));

      WebPageDesignerWidget widget = new WebPageDesignerWidget();
      WidgetContext result = widget.post(widgetContext);

      Assertions.assertNotNull(result);
      Assertions.assertNull(widgetContext.getErrorMessage());

      ArgumentCaptor<WebPage> saved = ArgumentCaptor.forClass(WebPage.class);
      saveWebPageCommand.verify(() -> SaveWebPageCommand.saveWebPage(saved.capture()));
      Assertions.assertTrue(saved.getValue().getPageXml().contains("new-content"),
          "legacy behavior: publishes straight to live when review is not required");

      webPageRepository.verify(() -> WebPageRepository.save(any()), org.mockito.Mockito.never());
    }
  }

  @Test
  void postDesignerCanvasSaveRoutesToDraftPageXmlWhenReviewIsRequired() {
    addQueryParameter(widgetContext, "widget", widgetContext.getUniqueId());
    addQueryParameter(widgetContext, "token", "12345");
    addQueryParameter(widgetContext, "webPage", "/existing-live-page");
    addQueryParameter(widgetContext, "content",
        "<div class=\"row\"><div class=\"column col-sm-12 col-md-12 col-xs-12\">"
            + "<!--gm-editable-region--><h3 data-widget=\"map\">Map</h3><p>Write a description</p><!--/gm-editable-region-->"
            + "</div></div>");

    WebPage existing = new WebPage();
    existing.setId(42);
    existing.setLink("/existing-live-page");
    existing.setPageXml("<page><section><column><widget name=\"content\"><uniqueId>reviewed-and-live</uniqueId></widget></column></section></page>");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<SaveWebPageCommand> saveWebPageCommand = mockStatic(SaveWebPageCommand.class);
        MockedStatic<WebPageXmlLayoutCommand> widgetLibrary = mockStatic(WebPageXmlLayoutCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      webPageRepository.when(() -> WebPageRepository.findByLink("/existing-live-page")).thenReturn(existing);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("webPage.review.required")).thenReturn(true);
      widgetLibrary.when(WebPageXmlLayoutCommand::getWidgetLibrary).thenReturn(REGISTERED_WIDGETS);
      webPageRepository.when(() -> WebPageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      WebPageDesignerWidget widget = new WebPageDesignerWidget();
      widget.post(widgetContext);

      Assertions.assertEquals("[{\"status\":\"0\"}]", widgetContext.getJson());

      ArgumentCaptor<WebPage> saved = ArgumentCaptor.forClass(WebPage.class);
      webPageRepository.verify(() -> WebPageRepository.save(saved.capture()));
      Assertions.assertTrue(saved.getValue().getPageXml().contains("reviewed-and-live"),
          "the live page content must not change until the new content is reviewed and approved");
      Assertions.assertNotNull(saved.getValue().getDraftPageXml(),
          "the designer-canvas save must land in draftPageXml pending review");
      saveWebPageCommand.verifyNoInteractions();
    }
  }

  @Test
  void postDesignerCanvasSavePublishesDirectlyToPageXmlWhenReviewIsNotRequired() {
    addQueryParameter(widgetContext, "widget", widgetContext.getUniqueId());
    addQueryParameter(widgetContext, "token", "12345");
    addQueryParameter(widgetContext, "webPage", "/existing-live-page");
    addQueryParameter(widgetContext, "content",
        "<div class=\"row\"><div class=\"column col-sm-12 col-md-12 col-xs-12\">"
            + "<!--gm-editable-region--><h3 data-widget=\"map\">Map</h3><p>Write a description</p><!--/gm-editable-region-->"
            + "</div></div>");

    WebPage existing = new WebPage();
    existing.setId(42);
    existing.setLink("/existing-live-page");
    existing.setPageXml("<page><section><column><widget name=\"content\"><uniqueId>old-content</uniqueId></widget></column></section></page>");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<SaveWebPageCommand> saveWebPageCommand = mockStatic(SaveWebPageCommand.class);
        MockedStatic<WebPageXmlLayoutCommand> widgetLibrary = mockStatic(WebPageXmlLayoutCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      webPageRepository.when(() -> WebPageRepository.findByLink("/existing-live-page")).thenReturn(existing);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("webPage.review.required")).thenReturn(false);
      widgetLibrary.when(WebPageXmlLayoutCommand::getWidgetLibrary).thenReturn(REGISTERED_WIDGETS);
      saveWebPageCommand.when(() -> SaveWebPageCommand.saveWebPage(any())).thenAnswer(invocation -> invocation.getArgument(0));

      WebPageDesignerWidget widget = new WebPageDesignerWidget();
      widget.post(widgetContext);

      Assertions.assertEquals("[{\"status\":\"0\"}]", widgetContext.getJson());

      ArgumentCaptor<WebPage> saved = ArgumentCaptor.forClass(WebPage.class);
      saveWebPageCommand.verify(() -> SaveWebPageCommand.saveWebPage(saved.capture()));
      Assertions.assertFalse(saved.getValue().getPageXml().contains("old-content"),
          "legacy behavior: the designer-canvas save publishes straight to pageXml when review is not required");
      Assertions.assertNull(saved.getValue().getDraftPageXml());
      webPageRepository.verify(() -> WebPageRepository.save(any()), org.mockito.Mockito.never());
    }
  }

  @Test
  void postRemovingContentRoutesToDraftPageXmlWhenReviewIsRequiredLeavingLivePageUntouched() {
    addQueryParameter(widgetContext, "widget", widgetContext.getUniqueId());
    addQueryParameter(widgetContext, "token", "12345");
    addQueryParameter(widgetContext, "webPage", "/existing-live-page");
    addQueryParameter(widgetContext, "returnPage", "/existing-live-page");
    // No "pageXml" parameter at all -- the widget's own "content is being removed" branch.

    WebPage existing = new WebPage();
    existing.setId(42);
    existing.setLink("/existing-live-page");
    existing.setPageXml("<page><section><column><widget name=\"content\"><uniqueId>reviewed-and-live</uniqueId></widget></column></section></page>");
    existing.setDraftPageXml("<page><section><column><widget name=\"content\"><uniqueId>stale-pending-draft</uniqueId></widget></column></section></page>");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<SaveWebPageCommand> saveWebPageCommand = mockStatic(SaveWebPageCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      webPageRepository.when(() -> WebPageRepository.findByLink("/existing-live-page")).thenReturn(existing);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("webPage.review.required")).thenReturn(true);
      webPageRepository.when(() -> WebPageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      WebPageDesignerWidget widget = new WebPageDesignerWidget();
      widget.post(widgetContext);

      ArgumentCaptor<WebPage> saved = ArgumentCaptor.forClass(WebPage.class);
      webPageRepository.verify(() -> WebPageRepository.save(saved.capture()));
      Assertions.assertTrue(saved.getValue().getPageXml().contains("reviewed-and-live"),
          "removing draft content must never touch the live page");
      Assertions.assertNull(saved.getValue().getDraftPageXml(),
          "the pending draft is cleared, not silently republished");
      saveWebPageCommand.verifyNoInteractions();
    }
  }

  @Test
  void postDatabaseTemplateRoutesToDraftPageXmlWhenReviewIsRequired() {
    addQueryParameter(widgetContext, "widget", widgetContext.getUniqueId());
    addQueryParameter(widgetContext, "token", "12345");
    addQueryParameter(widgetContext, "webPage", "/existing-live-page");
    addQueryParameter(widgetContext, "returnPage", "/existing-live-page");
    // templateId is read via context.getRequest().getParameter(...), not context.getParameter(...) --
    // the test harness's mock backs the former with setAttribute(), not the query-parameter map.
    request.setAttribute("templateId", "5");

    WebPage existing = new WebPage();
    existing.setId(42);
    existing.setLink("/existing-live-page");
    existing.setPageXml("<page><section><column><widget name=\"content\"><uniqueId>reviewed-and-live</uniqueId></widget></column></section></page>");

    WebPageTemplate dbTemplate = new WebPageTemplate();
    dbTemplate.setId(5L);
    dbTemplate.setName("From The Database");
    dbTemplate.setPageXml("<page><section><column><widget name=\"content\"><uniqueId>${webPageName}-from-template</uniqueId></widget></column></section></page>");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<WebPageTemplateRepository> templateRepository = mockStatic(WebPageTemplateRepository.class);
        MockedStatic<SaveWebPageCommand> saveWebPageCommand = mockStatic(SaveWebPageCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      webPageRepository.when(() -> WebPageRepository.findByLink("/existing-live-page")).thenReturn(existing);
      templateRepository.when(() -> WebPageTemplateRepository.findById(5L)).thenReturn(dbTemplate);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("webPage.review.required")).thenReturn(true);
      webPageRepository.when(() -> WebPageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      WebPageDesignerWidget widget = new WebPageDesignerWidget();
      widget.post(widgetContext);

      ArgumentCaptor<WebPage> saved = ArgumentCaptor.forClass(WebPage.class);
      webPageRepository.verify(() -> WebPageRepository.save(saved.capture()));
      Assertions.assertTrue(saved.getValue().getPageXml().contains("reviewed-and-live"),
          "picking a template for an existing page must not change what's live until reviewed");
      Assertions.assertTrue(saved.getValue().getDraftPageXml().contains("from-template"),
          "the selected template's content must land in draftPageXml pending review");
      saveWebPageCommand.verifyNoInteractions();
    }
  }

  @Test
  void postDatabaseTemplatePublishesDirectlyWhenReviewIsNotRequired() {
    addQueryParameter(widgetContext, "widget", widgetContext.getUniqueId());
    addQueryParameter(widgetContext, "token", "12345");
    addQueryParameter(widgetContext, "webPage", "/existing-live-page");
    addQueryParameter(widgetContext, "returnPage", "/existing-live-page");
    // templateId is read via context.getRequest().getParameter(...), not context.getParameter(...) --
    // the test harness's mock backs the former with setAttribute(), not the query-parameter map.
    request.setAttribute("templateId", "5");

    WebPage existing = new WebPage();
    existing.setId(42);
    existing.setLink("/existing-live-page");
    existing.setPageXml("<page><section><column><widget name=\"content\"><uniqueId>old-content</uniqueId></widget></column></section></page>");

    WebPageTemplate dbTemplate = new WebPageTemplate();
    dbTemplate.setId(5L);
    dbTemplate.setName("From The Database");
    dbTemplate.setPageXml("<page><section><column><widget name=\"content\"><uniqueId>${webPageName}-from-template</uniqueId></widget></column></section></page>");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<WebPageTemplateRepository> templateRepository = mockStatic(WebPageTemplateRepository.class);
        MockedStatic<SaveWebPageCommand> saveWebPageCommand = mockStatic(SaveWebPageCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      webPageRepository.when(() -> WebPageRepository.findByLink("/existing-live-page")).thenReturn(existing);
      templateRepository.when(() -> WebPageTemplateRepository.findById(5L)).thenReturn(dbTemplate);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("webPage.review.required")).thenReturn(false);
      saveWebPageCommand.when(() -> SaveWebPageCommand.saveWebPage(any())).thenAnswer(invocation -> invocation.getArgument(0));

      WebPageDesignerWidget widget = new WebPageDesignerWidget();
      widget.post(widgetContext);

      ArgumentCaptor<WebPage> saved = ArgumentCaptor.forClass(WebPage.class);
      saveWebPageCommand.verify(() -> SaveWebPageCommand.saveWebPage(saved.capture()));
      Assertions.assertTrue(saved.getValue().getPageXml().contains("from-template"),
          "legacy behavior: a template pick publishes straight to pageXml when review is not required");
      Assertions.assertNull(saved.getValue().getDraftPageXml());
      webPageRepository.verify(() -> WebPageRepository.save(any()), org.mockito.Mockito.never());
    }
  }

  // Issue #957 review follow-up: execute()'s redisplay of a just-submitted save must not crash or
  // discard real (gated) content -- see effectiveContent()/mayPublishDirectly() in the production code.

  @Test
  void executeRedisplayingAGatedBrandNewDesignerPageDoesNotThrowAndKeepsTheRealDraftContent() {
    addQueryParameter(widgetContext, "webPage", "/brand-new-page");

    WebPage justSaved = new WebPage();
    justSaved.setLink("/brand-new-page");
    // Simulates exactly what post() leaves behind for a brand-new page created from a
    // designer-tagged template while governed review is required: pageXml is still null, the real
    // selected template's content is in draftPageXml.
    justSaved.setDraftPageXml("<page editor=\"designer\"><section><column class=\"small-12 cell\">"
        + "<widget name=\"content\"><uniqueId>hello</uniqueId></widget></column></section></page>");
    widgetContext.setRequestObject(justSaved);

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("webPage.review.required")).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("features.layout-editor")).thenReturn(true);

      WebPageDesignerWidget widget = new WebPageDesignerWidget();
      WidgetContext result = Assertions.assertDoesNotThrow(() -> widget.execute(widgetContext),
          "must not NPE reading pageXml on a page whose real content is gated into draftPageXml");

      Assertions.assertEquals(WebPageDesignerWidget.DESIGNER_JSP, result.getJsp());
      WebPage shown = (WebPage) request.getAttribute("webPage");
      Assertions.assertTrue(shown.getDraftPageXml().contains("hello"),
          "the real selected-template content must survive redisplay, not get overwritten by the generic blank scaffold");
    }
  }

  @Test
  void executeShowsTheRawXmlEditorForAnExistingLivePageWhenReviewIsRequiredInsteadOfTheTemplatePicker() {
    // Live-verification catch: an existing page with real live content, no pending draft, opened
    // normally (not a post-redisplay) while webPage.review.required is on. The blank-page check must
    // look at both pageXml and draftPageXml -- checking only whichever field a save would target
    // (draftPageXml, since review is required) would misread this page as blank and show the
    // template picker instead of the editor with its real content.
    addQueryParameter(widgetContext, "webPage", "/existing-live-page");

    WebPage existing = new WebPage();
    existing.setLink("/existing-live-page");
    existing.setPageXml("<page><section><column><widget name=\"content\"><uniqueId>reviewed-and-live</uniqueId></widget></column></section></page>");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      webPageRepository.when(() -> WebPageRepository.findByLink("/existing-live-page")).thenReturn(existing);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("webPage.review.required")).thenReturn(true);

      WebPageDesignerWidget widget = new WebPageDesignerWidget();
      WidgetContext result = widget.execute(widgetContext);

      Assertions.assertEquals(WebPageDesignerWidget.ACE_XML_EDITOR_JSP, result.getJsp(),
          "an existing page with real content must show the editor, not the blank-page template picker");
      WebPage shown = (WebPage) request.getAttribute("webPage");
      Assertions.assertTrue(shown.getPageXml().contains("reviewed-and-live"), "the live content must be untouched");
    }
  }
}