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
import com.simisinc.platform.application.cms.SaveWebPageCommand;
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
}