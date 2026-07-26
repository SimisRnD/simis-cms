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

package com.simisinc.platform.application.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Reorders sections/columns/widgets in a page's draft XML layout.
 * Receives a JSON descriptor of the new order (original indices → new positions),
 * rewrites the DOM, and persists to draftPageXml via WebPageRepository.
 */
public class SaveDraftLayoutCommand {

  private static Log LOG = LogFactory.getLog(SaveDraftLayoutCommand.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * @param webPage    the page whose layout is being changed
   * @param layoutJson JSON string: {"sections":[{"s":origIdx,"columns":[{"c":origIdx,"widgets":[origIdx,...]},...]},...]}
   */
  public static void saveDraftLayout(WebPage webPage, String layoutJson) throws DataException {
    if (webPage == null || webPage.getId() == -1) {
      throw new DataException("Page not found");
    }

    String sourceXml = StringUtils.isNotBlank(webPage.getDraftPageXml())
        ? webPage.getDraftPageXml()
        : webPage.getPageXml();
    if (StringUtils.isBlank(sourceXml)) {
      throw new DataException("Page has no XML layout to reorder");
    }
    if (StringUtils.isBlank(layoutJson)) {
      throw new DataException("Layout description is required");
    }

    try {
      // Parse source XML
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setExpandEntityReferences(false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(new InputSource(new StringReader(sourceXml)));

      Element pageEl = doc.getDocumentElement();
      List<Element> sections = childElements(pageEl, "section");

      // Parse layout JSON
      JsonNode root = MAPPER.readTree(layoutJson);
      JsonNode sectionsNode = root.get("sections");
      if (sectionsNode == null || !sectionsNode.isArray()) {
        throw new DataException("Invalid layout: missing sections array");
      }

      // Validate all indices are in-range before touching the DOM
      for (JsonNode sNode : sectionsNode) {
        int sIdx = sNode.path("s").asInt(-1);
        if (sIdx < 0 || sIdx >= sections.size()) {
          throw new DataException("Invalid layout: section index " + sIdx + " out of range");
        }
        Element sectionEl = sections.get(sIdx);
        List<Element> columns = childElements(sectionEl, "column");
        JsonNode colsNode = sNode.get("columns");
        if (colsNode == null) continue;
        for (JsonNode cNode : colsNode) {
          int cIdx = cNode.path("c").asInt(-1);
          if (cIdx < 0 || cIdx >= columns.size()) {
            throw new DataException("Invalid layout: column index " + cIdx + " in section " + sIdx + " out of range");
          }
          Element colEl = columns.get(cIdx);
          List<Element> widgets = childElements(colEl, "widget");
          JsonNode widgetsNode = cNode.get("widgets");
          if (widgetsNode == null) continue;
          for (JsonNode wNode : widgetsNode) {
            int wIdx = wNode.asInt(-1);
            if (wIdx < 0 || wIdx >= widgets.size()) {
              throw new DataException("Invalid layout: widget index " + wIdx + " out of range");
            }
          }
        }
      }

      // Detach all sections from the page root
      for (Element s : sections) {
        pageEl.removeChild(s);
      }

      // Re-attach in new order with reordered columns and widgets
      for (JsonNode sNode : sectionsNode) {
        int sIdx = sNode.path("s").asInt();
        Element sectionEl = sections.get(sIdx);

        JsonNode colsNode = sNode.get("columns");
        if (colsNode != null && colsNode.isArray()) {
          List<Element> columns = childElements(sectionEl, "column");
          for (Element c : columns) sectionEl.removeChild(c);

          for (JsonNode cNode : colsNode) {
            int cIdx = cNode.path("c").asInt();
            Element colEl = columns.get(cIdx);

            JsonNode widgetsNode = cNode.get("widgets");
            if (widgetsNode != null && widgetsNode.isArray()) {
              List<Element> widgets = childElements(colEl, "widget");
              for (Element w : widgets) colEl.removeChild(w);
              for (JsonNode wNode : widgetsNode) {
                colEl.appendChild(widgets.get(wNode.asInt()));
              }
            }

            sectionEl.appendChild(colEl);
          }
        }

        pageEl.appendChild(sectionEl);
      }

      // Serialize
      TransformerFactory tf = TransformerFactory.newInstance();
      tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Transformer transformer = tf.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      StringWriter sw = new StringWriter();
      transformer.transform(new DOMSource(doc), new StreamResult(sw));
      String newXml = sw.toString().trim();

      // Persist
      webPage.setDraftPageXml(newXml);
      webPage.setDraft(true);
      WebPageRepository.save(webPage);
      WebPageXmlLayoutCommand.removeCustomPage(webPage.getLink());
      LOG.debug("Draft layout saved for: " + webPage.getLink());

    } catch (DataException e) {
      throw e;
    } catch (Exception e) {
      LOG.error("saveDraftLayout failed for " + webPage.getLink(), e);
      throw new DataException("Could not save draft layout: " + e.getMessage());
    }
  }

  private static List<Element> childElements(Element parent, String tagName) {
    List<Element> result = new ArrayList<>();
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE && tagName.equals(child.getNodeName())) {
        result.add((Element) child);
      }
    }
    return result;
  }
}
