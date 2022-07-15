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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.*;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 2/7/2021 12:53 PM
 */
public class XMLContainerCommands implements Serializable {

  static final long serialVersionUID = 536435325324169646L;
  private static Log LOG = LogFactory.getLog(XMLContainerCommands.class);

  public static void appendSections(Document document, List<Section> sections, NodeList children, Map<String, String> widgetLibrary) {
    // Process the sections
    boolean isWellFormed = false;
    int len = children.getLength();
    for (int i = 0; i < len; i++) {
      if (children.item(i).getNodeType() != Element.ELEMENT_NODE) {
        continue;
      }
      Element child = (Element) children.item(i);
      String childName = child.getTagName();
      if (!"section".equals(childName)) {
        continue;
      }
      if (!isWellFormed) {
        isWellFormed = true;
      }
      // Check for sections
      Section section = new Section();
      String htmlId = child.getAttribute("id");
      if (StringUtils.isNotBlank(htmlId)) {
        section.setHtmlId(htmlId);
      }
      String cssClass = child.getAttribute("class");
      if (StringUtils.isNotBlank(cssClass)) {
        section.setCssClass(cssClass);
      }
      String cssStyle = child.getAttribute("style");
      if (StringUtils.isNotBlank(cssStyle)) {
        section.setCssStyle(cssStyle);
      }
      if (child.hasAttribute("role")) {
        String aRoles = child.getAttribute("role");
        if (aRoles.length() > 0) {
          List<String> roles = Stream.of(aRoles.split(","))
              .map(String::trim)
              .collect(toList());
          section.setRoles(roles);
        }
      }
      if (child.hasAttribute("group")) {
        String aGroups = child.getAttribute("group");
        if (aGroups.length() > 0) {
          List<String> groups = Stream.of(aGroups.split(","))
              .map(String::trim)
              .collect(toList());
          section.setGroups(groups);
        }
      }
      if (child.hasAttribute("hr") && child.getAttribute("hr").equals("true")) {
        section.setHr(true);
      }
      String videoBackgroundUrl = child.getAttribute("videoBackgroundUrl");
      if (StringUtils.isNotBlank(videoBackgroundUrl)) {
        section.setVideoBackgroundUrl(videoBackgroundUrl);
      }
      sections.add(section);
      LOG.trace("Adding section");
      appendColumns(document, section, section.getColumns(), child.getChildNodes(), widgetLibrary);
    }

    // Perform a 2nd pass on the children to see if there are widgets here (short form)
    if (!isWellFormed) {
      Column indirectColumn = new Column();
      appendWidgets(document, indirectColumn, indirectColumn.getWidgets(), children, widgetLibrary);
      if (!indirectColumn.getWidgets().isEmpty()) {
        LOG.trace("Adding indirect section");
        Section section = new Section();
        section.getColumns().add(indirectColumn);
        sections.add(section);
      } else {
        // Perform a 3rd pass on the children to see if there are columns here (short form)
        Section indirectSection = new Section();
        appendColumns(document, indirectSection, indirectSection.getColumns(), children, widgetLibrary);
        if (!indirectSection.getColumns().isEmpty()) {
          sections.add(indirectSection);
        }
      }
    }
  }

  private static void appendColumns(Document document, Section sectionInfo, List<Column> columns, NodeList children, Map<String, String> widgetLibrary) {
    // Process the columns
    int len = children.getLength();
    for (int i = 0; i < len; i++) {
      if (children.item(i).getNodeType() != Element.ELEMENT_NODE) {
        continue;
      }
      Element child = (Element) children.item(i);
      String childName = child.getTagName();
      if (childName.equals("column")) {
        if (child.hasAttribute("use")) {
          // Look for the shared column
          String xmlId = child.getAttribute("use");
          LOG.trace("Looking for shared column: " + xmlId);
          try {
            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();
            child = (Element) xpath.evaluate("//*[@id='" + xmlId + "']", document, XPathConstants.NODE);
          } catch (Exception e) {
            LOG.error("Could not parse", e);
            continue;
          }
        }
        Column column = new Column();
        String htmlId = child.getAttribute("id");
        if (StringUtils.isNotBlank(htmlId)) {
          column.setHtmlId(htmlId);
        }
        String cssClass = child.getAttribute("class");
        if (StringUtils.isNotBlank(cssClass)) {
          column.setCssClass(cssClass);
        }
        String cssStyle = child.getAttribute("style");
        if (StringUtils.isNotBlank(cssStyle)) {
          column.setCssStyle(cssStyle);
        }
        String sticky = child.getAttribute("sticky");
        if ("true".equals(sticky)) {
          sectionInfo.setSticky(true);
          column.setSticky(true);
        }
        if (child.hasAttribute("role")) {
          String aRoles = child.getAttribute("role");
          if (aRoles.length() > 0) {
            List<String> roles = Stream.of(aRoles.split(","))
                .map(String::trim)
                .collect(toList());
            column.setRoles(roles);
          }
        }
        if (child.hasAttribute("group")) {
          String aGroups = child.getAttribute("group");
          if (aGroups.length() > 0) {
            List<String> groups = Stream.of(aGroups.split(","))
                .map(String::trim)
                .collect(toList());
            column.setGroups(groups);
          }
        }
        columns.add(column);
        LOG.trace("Adding column");
        appendWidgets(document, column, column.getWidgets(), child.getChildNodes(), widgetLibrary);
      }
    }
    // Perform a 2nd pass on the children to see if there are widgets here (short form)
    Column indirectColumn = new Column();
    appendWidgets(document, indirectColumn, indirectColumn.getWidgets(), children, widgetLibrary);
    if (!indirectColumn.getWidgets().isEmpty()) {
      LOG.trace("Adding indirect column");
      columns.add(indirectColumn);
    }
  }

  private static void appendWidgets(Document document, Column columnInfo, List<Widget> widgets, NodeList children, Map<String, String> widgetLibrary) {
    int len = children.getLength();
    for (int i = 0; i < len; i++) {
      if (children.item(i).getNodeType() != Element.ELEMENT_NODE) {
        continue;
      }
      Element child = (Element) children.item(i);
      String childName = child.getTagName();
      if (!childName.equals("widget")) {
        continue;
      }
      String name = child.getAttribute("name");
      if (child.hasAttribute("use")) {
        // Look for the shared widget
        String xmlId = child.getAttribute("use");
        LOG.debug("Looking for shared widget: " + xmlId);
        try {
          XPathFactory xpathFactory = XPathFactory.newInstance();
          XPath xpath = xpathFactory.newXPath();
          child = (Element) xpath.evaluate("//*[@id='" + xmlId + "']", document, XPathConstants.NODE);
          name = child.getAttribute("name");
        } catch (Exception e) {
          LOG.error("Could not parse", e);
          continue;
        }
      }
      LOG.trace("Found name: " + name);
      Widget widget = new Widget(name);
      String htmlId = child.getAttribute("id");
      if (StringUtils.isNotBlank(htmlId)) {
        widget.setHtmlId(htmlId);
      }
      String cssClass = child.getAttribute("class");
      if (StringUtils.isNotBlank(cssClass)) {
        widget.setCssClass(cssClass);
      }
      String cssStyle = child.getAttribute("style");
      if (StringUtils.isNotBlank(cssStyle)) {
        widget.setCssStyle(cssStyle);
      }
      String sticky = child.getAttribute("sticky");
      if ("true".equals(sticky)) {
        columnInfo.setSticky(true);
        widget.setSticky(true);
        if (StringUtils.isNotBlank(cssClass)) {
          widget.setCssClass(cssClass + " sticky");
        } else {
          widget.setCssClass("sticky");
        }
      }
      if (child.hasAttribute("hr") && child.getAttribute("hr").equals("true")) {
        widget.setHr(true);
      }
      if (child.hasAttribute("role")) {
        String aRoles = child.getAttribute("role");
        if (aRoles.length() > 0) {
          List<String> roles = Stream.of(aRoles.split(","))
              .map(String::trim)
              .collect(toList());
          widget.setRoles(roles);
        }
      }
      if (child.hasAttribute("group")) {
        String aGroups = child.getAttribute("group");
        if (aGroups.length() > 0) {
          List<String> groups = Stream.of(aGroups.split(","))
              .map(String::trim)
              .collect(toList());
          widget.setGroups(groups);
        }
      }
      widget.setWidgetName(name);
      if (widgetLibrary.containsKey(name)) {
        LOG.trace("Adding widget to layout: " + name);
        widget.setWidgetClassName(widgetLibrary.get(name));
        widgets.add(widget);
        addWidgetPreferences(widget, child.getChildNodes());
      }
    }
  }

  private static void addWidgetPreferences(Widget widget, NodeList children) {
    addWidgetPreferences(widget.getPreferences(), children);
  }

  public static void addWidgetPreferences(Map<String, String> preferenceMap, NodeList children) {
    int len = children.getLength();
    for (int i = 0; i < len; i++) {
      if (children.item(i).getNodeType() != Element.ELEMENT_NODE) {
        continue;
      }
      // Determine the value(s)
      Element child = (Element) children.item(i);
      String childName = child.getTagName();
      String value = child.getTextContent();
      if (StringUtils.isNotBlank(value)) {
        LOG.debug("Adding preference: " + childName + "=" + value);
        preferenceMap.put(childName, value);
        continue;
      }
      if (!child.hasChildNodes()) {
        continue;
      }
      // Serialize the extended values
      // fields=  name=Summary;value=something;type=something;|
      NodeList fields = child.getChildNodes();
      StringBuilder sb = new StringBuilder();
      int fieldCount = 0;
      for (int j = 0; j < fields.getLength(); j++) {
        if (fields.item(j).getNodeType() != Element.ELEMENT_NODE) {
          continue;
        }
        ++fieldCount;
        Element element = (Element) fields.item(j);
        if (!element.hasAttributes()) {
          LOG.debug("No attributes");
          continue;
        }
        NamedNodeMap attributes = element.getAttributes();
        if (sb.length() > 0) {
          // Record separator
          sb.append("|||");
        }
        for (int a = 0; a < attributes.getLength(); a++) {
          Node attribute = attributes.item(a);
          String attributeName = attribute.getNodeName();
          String attributeValue = attribute.getNodeValue();
          if (StringUtils.isBlank(attributeName) || StringUtils.isBlank(attributeValue)) {
            continue;
          }
          if (a > 0) {
            // Field separator
            sb.append("||");
          }
          sb.append(attribute.getNodeName()).append("|").append(attribute.getNodeValue());
        }
      }
      if (sb.length() > 0) {
        if (LOG.isDebugEnabled()) {
          LOG.trace("Adding preference: " + childName + "(" + fieldCount + ")" + ": [" + sb.toString() + "]");
        }
        preferenceMap.put(childName, sb.toString());
      }
    }
  }
}
