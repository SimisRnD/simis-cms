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

package com.simisinc.platform.presentation.controller.cms;

import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.cms.WebContainer;
import com.simisinc.platform.infrastructure.persistence.cms.WebContainerRepository;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/21/21 9:13 PM
 */
public class WebContainerDesignerWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  protected static Log LOG = LogFactory.getLog(WebContainerDesignerWidget.class);

  static String XML_EDITOR_JSP = "/cms/web-container-xml-editor.jsp";

  public WidgetContext execute(WidgetContext context) {

    // The default JSP
    context.setJsp(XML_EDITOR_JSP);

    // Specify the return page
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    context.getRequest().setAttribute("returnPage", returnPage);

    WebContainer webContainer = null;

    // The POST was triggered
    if (context.getRequestObject() != null) {
      // Determine the reason...
      webContainer = (WebContainer) context.getRequestObject();
      context.getRequest().setAttribute("webContainer", webContainer);
      // There was a post error so let the user make changes
      return context;
    }

    // Determine the container type
    String name = context.getParameter("name");
    if (StringUtils.isBlank(name)) {
      LOG.debug("Value is required");
      return context;
    }
    webContainer = WebContainerRepository.findByName(name);
    if (webContainer == null) {
      // Prepare for a new one?
//      webContainer = new WebContainer();
//      webContainer.setName(name);
//      webContainer.setContainerXml(XMLHeaderLoader.loadXmlFromFile());
      LOG.warn("Container not found for name: " + name);
      return context;
    }

    // Prepare for viewing
    context.getRequest().setAttribute("webContainer", webContainer);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    String nameValue = context.getParameter("name");
    if (StringUtils.isEmpty(nameValue) || !nameValue.contains(".")) {
      LOG.warn("Missing web container name value");
      return context;
    }
    String type = nameValue.substring(0, nameValue.indexOf("."));
    WebContainer webContainer = WebContainerRepository.findByName(nameValue);
    if (webContainer == null) {
      LOG.info("Could not find an existing container, ready to create a new one: " + nameValue);
      webContainer = new WebContainer();
      webContainer.setName(nameValue);
      String label = context.getParameter("label");
      if (StringUtils.isNotBlank(label)) {
        webContainer.setLabel(label);
      } else {
        webContainer.setLabel(nameValue);
      }
    }

    // Check for content
    String containerXml = context.getParameter("containerXml");
    if (StringUtils.isEmpty(containerXml)) {
      // Content is being removed
      webContainer.setContainerXml(null);
    } else {
      webContainer.setContainerXml(containerXml);
    }

    if (!StringUtils.isEmpty(webContainer.getContainerXml())) {
      // Validate the XML before saving and alert the user
      try {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputStream is = IOUtils.toInputStream(webContainer.getContainerXml(), "UTF-8");
        Document document = builder.parse(is);
        NodeList tags = document.getElementsByTagName(type);
        if (tags.getLength() != 1) {
          context.setRequestObject(webContainer);
          context.setWarningMessage("<" + type + "></" + type + "> element is required");
          return context;
        }
      } catch (Exception e) {
        LOG.error("User input: xml did not validate: " + e.getMessage());
        context.setRequestObject(webContainer);
        context.setErrorMessage("The XML could not be validated. Use <" + type + "><section><column><widget> notation. Error reported: " + e.getMessage());
        return context;
      }
    }

    // Save the page
    webContainer = WebContainerRepository.save(webContainer);
    if (webContainer == null) {
      LOG.warn("Web container record was not saved!");
      context.setErrorMessage("An error occurred");
      return context;
    }

    // Determine the page to return to
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    if (!StringUtils.isEmpty(returnPage)) {
      context.setRedirect(returnPage);
    } else {
      context.setRedirect("/");
    }
    return context;
  }
}
