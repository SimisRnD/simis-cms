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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.MakeContentUniqueIdCommand;
import com.simisinc.platform.application.cms.SaveWebPageCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.cms.WebPageDesignerToXmlCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.cms.WebPageTemplate;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageTemplateRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.controller.XMLWebPageTemplateLoader;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/6/18 1:12 PM
 */
public class WebPageDesignerWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  protected static Log LOG = LogFactory.getLog(WebPageDesignerWidget.class);

  static String TEMPLATES_JSP = "/cms/web-page-templates.jsp";
  static String DESIGNER_JSP = "/cms/web-page-designer.jsp";
  static String RAW_XML_JSP = "/cms/web-page-editor.jsp";
  static String ACE_XML_EDITOR_JSP = "/cms/web-page-xml-editor.jsp";
  static String CODE_MIRROR_XML_EDITOR_JSP = "/cms/web-page-code-mirror-xml-editor.jsp";

  public WidgetContext execute(WidgetContext context) {

    // The default JSP
    // @note the ACE editor is unstable for XML, CPU gets out of control; might be fixed
    context.setJsp(ACE_XML_EDITOR_JSP);
//    context.setJsp(CODE_MIRROR_XML_EDITOR_JSP);

    // See if an editor is specified
    String editor = context.getParameter("editor");
    if ("designer".equals(editor)) {
      context.setJsp(DESIGNER_JSP);
    }

    // Specify the return page
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    context.getRequest().setAttribute("returnPage", returnPage);

    WebPage webPage = null;

    // The POST was triggered
    if (context.getRequestObject() != null) {
      // Determine the reason...
      webPage = (WebPage) context.getRequestObject();
      context.getRequest().setAttribute("webPage", webPage);
      if (webPage.getPageXml().contains("editor=\"designer\"")) {
        // An editor was specified, so use it
        context.setJsp(DESIGNER_JSP);
      } else {
        // There was a post error
        return context;
      }
    }

    // webPage must be specified, even if it doesn't exist
    String webPageLinkValue = context.getParameter("webPage");
    if (webPage == null) {
      // Determine the page being edited
      if (StringUtils.isEmpty(webPageLinkValue)) {
        LOG.warn("Missing webPage value");
        return context;
      }
      webPage = WebPageRepository.findByLink(webPageLinkValue);
//      webPage = LoadWebPageCommand.loadByLink(webPageLinkValue);
    }
    if (webPage == null) {
      LOG.debug("Could not find an existing page, ready to create a new one");
      webPage = new WebPage();
      webPage.setLink(webPageLinkValue);
    }
    // Show some templates
    if (StringUtils.isBlank(webPage.getPageXml())) {

      if ("designer".equals(editor)) {
        // @todo use WebPageDesignerCommand.convertFromPageLayoutToBootstrap();
        // Default to a single column template
        webPage.setPageXml("<page>\n" +
            "  <section>\n" +
            "    <column class=\"small-12 cell\">\n" +
            "      <widget name=\"content\">\n" +
            "        <uniqueId>" + MakeContentUniqueIdCommand.parseToValidValue(webPageLinkValue.substring(1)) + "-hello</uniqueId>\n" +
            "      </widget>\n" +
            "    </column>\n" +
            "  </section>\n" +
            "</page>");
        // Which will be... MakeContentUniqueIdCommand.getId(webPageLinkValue.substring(1)) + "-hello
        // <div class="row">
        //    <div class="column col-sm-12" data-unique-id="">
        //      <p>Write your content</p>
        //    </div>
        //  </div>
      } else {
        // Load web templates from the filesystem and database
        List<WebPageTemplate> webPageTemplateList = XMLWebPageTemplateLoader.retrieveTemplateList(context.getRequest().getServletContext());
        if (!webPageTemplateList.isEmpty()) {
          LOG.debug("Found templates...");

          // Look for additional database templates
          List<WebPageTemplate> webPageTemplateList2 = WebPageTemplateRepository.findAll();
          if (!webPageTemplateList2.isEmpty()) {
            webPageTemplateList.addAll(webPageTemplateList2);
          }

          // Sort the list
          webPageTemplateList.sort(Comparator.comparing(WebPageTemplate::getName));
          webPageTemplateList.sort(Comparator.comparing(WebPageTemplate::getTemplateOrder));
          webPageTemplateList.sort(Comparator.comparing(WebPageTemplate::getCategory));

          context.getRequest().setAttribute("webPageTemplateList", webPageTemplateList);
          context.setJsp(TEMPLATES_JSP);
        }
      }
    }
    context.getRequest().setAttribute("webPage", webPage);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    // Determine the web page being edited
    String webPageLinkValue = context.getParameter("webPage");
    if (StringUtils.isEmpty(webPageLinkValue)) {
      LOG.warn("Missing webPage value");
      return context;
    }
    String contentUniqueIdValue = webPageLinkValue.substring(1);

    WebPage webPage = WebPageRepository.findByLink(webPageLinkValue);
    if (webPage == null) {
      LOG.info("Could not find an existing page, ready to create a new one: " + webPageLinkValue);
      webPage = new WebPage();
      webPage.setLink(webPageLinkValue);
    }
    String title = context.getParameter("title");
    if (StringUtils.isNotBlank(title)) {
      webPage.setTitle(title);
    }
    webPage.setCreatedBy(context.getUserId());
    webPage.setModifiedBy(context.getUserId());

    // Determine the source of the page content
    // Database Template
    String templateIdValue = context.getRequest().getParameter("templateId");

    // Filesystem Template
    String templateUniqueIdValue = context.getRequest().getParameter("templateUniqueId");
    // Designer Content
    String pageDesignHtml = context.getParameter("content");
    // Text Field
    String pageXmlValue = context.getParameter("pageXml");

    // Determine the source of the page XML
    WebPageTemplate webPageTemplate = null;
    if (StringUtils.isNumeric(templateIdValue)) {
      // Database
      webPageTemplate = WebPageTemplateRepository.findById(Long.parseLong(templateIdValue));
    } else if (StringUtils.isNumeric(templateUniqueIdValue)) {
      // Filesystem template
      List<WebPageTemplate> webPageTemplateList = XMLWebPageTemplateLoader.retrieveTemplateList(context.getRequest().getServletContext());
      webPageTemplate = webPageTemplateList.stream()
          .filter(template -> Long.parseLong(templateUniqueIdValue) == template.getUniqueId())
          .findFirst()
          .orElse(null);
    }
    if (webPageTemplate != null) {
      // A template exists
      String template = webPageTemplate.getPageXml();
      String webPageName = MakeContentUniqueIdCommand.parseToValidValue(contentUniqueIdValue);
      if (StringUtils.isBlank(webPageName)) {
        webPageName = "home";
      }
      template = StringUtils.replace(template, "${webPageName}", webPageName);
      webPage.setPageXml(template);
      webPage.setTemplate(webPageTemplate.getName());
    } else if (pageDesignHtml != null) {
      // Page designer
      if (LOG.isDebugEnabled()) {
        LOG.debug("Found designer content...");
        LOG.debug("Content found: " + pageDesignHtml);
      }
      String pageXml = WebPageDesignerToXmlCommand.convertFromBootstrapHtml(webPage, pageDesignHtml);
      LOG.debug("Converted to: " + pageXml);
      webPage.setPageXml(pageXml);
      try {
        SaveWebPageCommand.saveWebPage(webPage);
        context.setJson("[{\"status\":\"0\"}]");
      } catch (Exception e) {
        context.setJson("[{\"message\":\"The web page could not be saved: " + e.getMessage() + "\"}]");
      }
      return context;
    } else {
      // Check for raw content
      if (StringUtils.isEmpty(pageXmlValue)) {
        // Content is being removed
        webPage.setPageXml(null);
      } else {
        // Content is being updated
        String webPageName = MakeContentUniqueIdCommand.parseToValidValue(contentUniqueIdValue);
        pageXmlValue = StringUtils.replace(pageXmlValue, "${webPageName}", webPageName);
        webPage.setPageXml(pageXmlValue);
      }
    }

    // Validate the XML before saving and alert the user
    if (!StringUtils.isEmpty(webPage.getPageXml())) {
      try {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = null;
        try (InputStream is = IOUtils.toInputStream(webPage.getPageXml(), "UTF-8")) {
          document = builder.parse(is);
        }
        NodeList pageTags = document.getElementsByTagName("page");
        if (pageTags.getLength() != 1) {
          context.setRequestObject(webPage);
          context.setWarningMessage("<page></page> element is required");
          return context;
        }
      } catch (Exception e) {
        LOG.error("User input: pageXml did not validate: " + e.getMessage());
        context.setRequestObject(webPage);
        context.setErrorMessage("The XML could not be validated. Use <page><section><column><widget> notation. Error reported: " + e.getMessage());
        return context;
      }

      // If the designer is specified in the template, no need to save...
      if (webPage.getPageXml().contains("editor=\"designer\"")) {
        context.setRequestObject(webPage);
        context.setRedirect("/admin/web-page-designer?editor=designer&webPage=" + webPage.getLink());
        return context;
      }
    }

    // Save the page
    webPage.setSearchable(true);
    try {
      webPage = SaveWebPageCommand.saveWebPage(webPage);
    } catch (DataException de) {
      LOG.warn("Web page record was not saved!");
      context.setErrorMessage("An error occurred");
      return context;
    }

    // Check for final errors
    if (webPage == null) {
      LOG.warn("Web page record was not saved!");
      context.setErrorMessage("An error occurred");
      return context;
    }

    // Determine the page to return to
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    if (StringUtils.isEmpty(returnPage)) {
      returnPage = webPageLinkValue;
    }
    context.setRedirect(returnPage);
    return context;
  }
}
