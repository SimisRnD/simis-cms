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

import com.simisinc.platform.domain.model.cms.WebContainer;
import com.simisinc.platform.infrastructure.persistence.cms.WebContainerRepository;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.servlet.ServletContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/17/21 10:48 AM
 */
public class XMLFooterLoader implements Serializable {

  static final long serialVersionUID = 536435325324169646L;
  private static Log LOG = LogFactory.getLog(XMLFooterLoader.class);

  public XMLFooterLoader() {
  }

  public static Footer retrieveFooter(ServletContext context, Map<String, String> widgetLibrary, String layout) {
    // Load the footer from the database
    WebContainer container = WebContainerRepository.findByName(layout);
    try {
      // Not found, import it from XML
      if (container == null && context != null) {
        // @todo Save the header to the DB?
        return loadFooterFromFile(context, layout, "/WEB-INF/web-layouts/footer/footer-layout.xml", widgetLibrary);
      }
      return XMLFooterLoader.addFromXml(container, widgetLibrary);
    } catch (Exception e) {
      LOG.error("Could not parse container XML");
    }
    return null;
  }

  public static Footer loadFooterFromFile(ServletContext context, String layoutName, String fileName, Map<String, String> widgetLibrary) {
    try {
      Document document = readDocument(context, fileName);
      return parseDocument(document, layoutName, widgetLibrary);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  private static Document readDocument(ServletContext context, String file)
      throws FactoryConfigurationError, ParserConfigurationException, SAXException, IOException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);

    DocumentBuilder builder = factory.newDocumentBuilder();
    try (InputStream is = context.getResourceAsStream(file)) {
      return builder.parse(is);
    }
  }

  public static Footer addFromXml(WebContainer webContainer, Map<String, String> widgetLibrary)
      throws FactoryConfigurationError, ParserConfigurationException, SAXException, IOException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);

    DocumentBuilder builder = factory.newDocumentBuilder();
    Document document = null;
    try (InputStream is = IOUtils.toInputStream(webContainer.getContainerXml(), "UTF-8")) {
      document = builder.parse(is);
    }
    return parseDocument(document, webContainer.getName(), widgetLibrary);
  }

  private static Footer parseDocument(Document document, String layoutName, Map<String, String> widgetLibrary) {
    NodeList containerTags = document.getElementsByTagName("footer");
    int numberOfLayouts = containerTags.getLength();
    if (numberOfLayouts == 0) {
      return null;
    }
    Element container = null;
    if (numberOfLayouts == 1) {
      container = (Element) containerTags.item(0);
    } else {
      for (int i = 0; i < containerTags.getLength(); i++) {
        Element thisContainer = (Element) containerTags.item(i);
        String thisName = thisContainer.getAttribute("name");
        if (layoutName.equals(thisName)) {
          container = thisContainer;
          break;
        }
      }
    }
    if (container != null) {
      Footer footer = loadFooter(document, container, widgetLibrary);
      // @note set it back to the requested layout name regardless of what the document has
      footer.setName(layoutName);
      LOG.debug("Created footer: " + footer.getName());
      return footer;
    }
    return null;
  }

  private static Footer loadFooter(Document document, Element container, Map<String, String> widgetLibrary) {
    String layoutName = container.getAttribute("name");
    if (layoutName.contains("{")) {
      layoutName = layoutName.substring(0, layoutName.indexOf("{"));
    }
    Footer footer = new Footer(layoutName);
    if (container.hasAttribute("role")) {
      String aRoles = container.getAttribute("role");
      if (aRoles.length() > 0) {
        List<String> roles = Stream.of(aRoles.split(","))
            .map(String::trim)
            .collect(toList());
        footer.setRoles(roles);
      }
    }
    if (container.hasAttribute("group")) {
      String aGroups = container.getAttribute("group");
      if (aGroups.length() > 0) {
        List<String> groups = Stream.of(aGroups.split(","))
            .map(String::trim)
            .collect(toList());
        footer.setGroups(groups);
      }
    }
    if (container.hasAttribute("class")) {
      // Applies the html class to the footer
      footer.setCssClass(container.getAttribute("class"));
    }
    XMLContainerCommands.appendSections(document, footer.getSections(), container.getChildNodes(), widgetLibrary);
    return footer;
  }
}
