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

import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.presentation.controller.cms.Column;
import com.simisinc.platform.presentation.controller.cms.Page;
import com.simisinc.platform.presentation.controller.cms.Section;
import com.simisinc.platform.presentation.controller.cms.Widget;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
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
import java.net.URL;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/6/18 8:00 PM
 */
public class XMLPageLoader implements Serializable {

  static final long serialVersionUID = 536435325324169646L;
  private static Log LOG = LogFactory.getLog(XMLPageLoader.class);
  // The available pages (name = page)
  private Map<String, Page> pages;
  // The files to process
  private List<XMLPageLoaderFiles> files = new ArrayList<>();
  // The available widgets for pages
  private Map<String, String> widgetLibrary = new HashMap<>();

  public XMLPageLoader(Map<String, Page> pages) {
    this.pages = pages;
  }

  public Page get(String name) {
    return pages.get(name);
  }

  public boolean containsKey(String name) {
    return pages.containsKey(name);
  }

  public void remove(String name) {
    pages.remove(name);
  }

  public Map<String, String> getWidgetLibrary() {
    return widgetLibrary;
  }

  public void setWidgetLibrary(Map<String, String> widgetLibrary) {
    this.widgetLibrary = widgetLibrary;
  }

  public void loadWidgetLibrary(ServletContext context, String fileName) {
    try {
      Document document = parseDocument(fileName, context);
      loadWidgetLibrary(document);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void loadWidgetLibrary(Document document) {
    NodeList objectTags = document.getElementsByTagName("widget");
    for (int i = 0; i < objectTags.getLength(); i++) {
      Element objectTag = (Element) objectTags.item(i);
      String aName = objectTag.getAttribute("name");
      String cName = objectTag.getAttribute("class");
      widgetLibrary.put(aName, cName);
      LOG.debug("Found widget object: " + cName);
    }
  }

  public synchronized void addDirectory(ServletContext context, String directory) {
    Set<String> files = context.getResourcePaths("/WEB-INF/" + directory);
    for (String file : files) {
      LOG.debug("Directory: " + directory + " found file: " + file);
      addFile(file);
    }
  }

  public synchronized void addFile(String fileName) {
    files.add(new XMLPageLoaderFiles(fileName));
  }

  public synchronized void load(ServletContext context) {
    try {
      for (XMLPageLoaderFiles file : files) {
        URL url = context.getResource(file.getFile());
        long lastModified = url.openConnection().getLastModified();
        if (file.getLastModified() == -1 || (lastModified > 0 && lastModified > file.getLastModified())) {
          LOG.info("Loading page layout: " + file.getFile());
          Document document = parseDocument(file.getFile(), context);
          loadAllPages(document);
          file.setLastModified(lastModified);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private Document parseDocument(String file, ServletContext context)
      throws FactoryConfigurationError, ParserConfigurationException, SAXException, IOException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    InputStream is = context.getResourceAsStream(file);
    return builder.parse(is);
  }

  public Page addFromXml(String pageName, WebPage webPage)
      throws FactoryConfigurationError, ParserConfigurationException, SAXException, IOException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    InputStream is = IOUtils.toInputStream(webPage.getPageXml(), "UTF-8");
    Document document = builder.parse(is);
    NodeList pageTags = document.getElementsByTagName("page");
    if (pageTags == null) {
      pageTags = document.getElementsByTagName("service");
    }
    if (pageTags.getLength() > 0) {
      Element pageTag = (Element) pageTags.item(0);
      Page page = loadPage(document, pageTag);
      page.setName(pageName);
      if (StringUtils.isNotBlank(webPage.getTitle())) {
        page.setTitle(webPage.getTitle());
      }
      if (StringUtils.isNotBlank(webPage.getKeywords())) {
        page.setKeywords(webPage.getKeywords());
      }
      if (StringUtils.isNotBlank(webPage.getDescription())) {
        page.setDescription(webPage.getDescription());
      }
      pages.put(pageName, page);
      LOG.debug("Created page: " + page.getName());
      return page;
    }
    return null;
  }

  private void loadAllPages(Document document) {
    NodeList pageTags = document.getElementsByTagName("page");
    if (pageTags == null || pageTags.getLength() == 0) {
      pageTags = document.getElementsByTagName("service");
    }
    for (int i = 0; i < pageTags.getLength(); i++) {
      Element pageTag = (Element) pageTags.item(i);
      Page p = loadPage(document, pageTag);
      pages.put(p.getName(), p);
      LOG.debug("Found page: " + p.getName());
    }
  }

  private Page loadPage(Document document, Element e) {
    String aName = e.getAttribute("name");
    if (e.hasAttribute("endpoint")) {
      aName = e.getAttribute("endpoint");
    }
    if (aName.contains("{")) {
      aName = aName.substring(0, aName.indexOf("{"));
    }
    String collectionUniqueId = e.getAttribute("collectionUniqueId");
    String itemUniqueId = e.getAttribute("itemUniqueId");
    Page page = new Page(aName, collectionUniqueId, itemUniqueId);
    if (e.hasAttribute("title")) {
      page.setTitle(e.getAttribute("title"));
    }
    if (e.hasAttribute("keywords")) {
      page.setKeywords(e.getAttribute("keywords"));
    }
    if (e.hasAttribute("description")) {
      page.setDescription(e.getAttribute("description"));
    }
    if (e.hasAttribute("role")) {
      String aRoles = e.getAttribute("role");
      if (aRoles.length() > 0) {
        List<String> roles = Stream.of(aRoles.split(","))
            .map(String::trim)
            .collect(toList());
        page.setRoles(roles);
      }
    }
    if (e.hasAttribute("group")) {
      String aGroups = e.getAttribute("group");
      if (aGroups.length() > 0) {
        List<String> groups = Stream.of(aGroups.split(","))
            .map(String::trim)
            .collect(toList());
        page.setGroups(groups);
      }
    }
    if (e.hasAttribute("class")) {
      if (e.hasAttribute("endpoint")) {
        /* @deprecated */
        // An inline widget/service (to be moved to own loader)
        String className = e.getAttribute("class");
        Section section = new Section();
        Column indirectColumn = new Column();
        section.getColumns().add(indirectColumn);
        page.getSections().add(section);
        Widget widget = new Widget(aName);
        widget.setWidgetClassName(className);
        indirectColumn.getWidgets().add(widget);
        widgetLibrary.put(aName, className);
      } else {
        // Applies the html class to the page
        page.setCssClass(e.getAttribute("class"));
      }
    }
    XMLContainerCommands.appendSections(document, page.getSections(), e.getChildNodes(), widgetLibrary);
    return page;
  }
}
