/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.presentation.widgets.cms.ImageWidget;
import com.simisinc.platform.presentation.widgets.cms.TableWidget;

/**
 * Structural mutations for the visual editor (Project #6, Phase 4): add, remove, and configure
 * sections, columns, and widgets in a page's draft XML layout.
 *
 * <p>All mutations write to {@code draftPageXml} only — {@code pageXml} is never touched until the
 * layout builder explicitly publishes. The draft-only guarantee means every mutation is reversible
 * (discard clears it) and is never visible to site visitors until publish.
 *
 * <p>Every public method validates its inputs before touching the DOM; the XML is parsed and
 * re-serialised atomically so a failed validation leaves {@code draftPageXml} unchanged.
 *
 * <p>The CSS class allowlist ({@link #CSS_CLASS_PATTERN}) and preference-key allowlist
 * ({@link #PREF_KEY_PATTERN}) are enforced at this boundary, not in the calling servlet, so no
 * screen can bypass them.
 *
 * @author elizabeth houser
 */
public class MutateLayoutCommand {

  private static final Log LOG = LogFactory.getLog(MutateLayoutCommand.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * CSS class value allowlist: letters, digits, spaces, hyphens, underscores. Covers all
   * Foundation grid classes ("small-12 cell", "medium-6 medium-6 cell", etc.) while preventing
   * attribute-injection via unescaped quotes or angle brackets.
   */
  static final Pattern CSS_CLASS_PATTERN = Pattern.compile("[a-zA-Z0-9 \\-_]*");

  /**
   * Widget preference key allowlist: must start with a letter, then only alphanumeric. This
   * prevents XML element-name injection (e.g. "foo><bar") in the preference round-trip.
   */
  static final Pattern PREF_KEY_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9]*");

  private MutateLayoutCommand() {
    // Static command
  }

  // ── Section operations ────────────────────────────────────────────────────

  /**
   * Inserts a new section with one empty column into the draft layout.
   *
   * @param afterSectionIdx index of the section to insert after; {@code -1} prepends before all
   *                        sections
   * @param sectionClass    optional Foundation CSS class for the new section element; null or blank
   *                        means no class attribute
   * @param modifiedBy      user id of the person making this change
   */
  public static void addSection(WebPage webPage, int afterSectionIdx, String sectionClass, long modifiedBy)
      throws DataException {
    if (StringUtils.isNotBlank(sectionClass)) {
      validateCssClass(sectionClass);
    }
    mutate(webPage, modifiedBy, doc -> {
      Element pageEl = doc.getDocumentElement();
      List<Element> sections = childElements(pageEl, "section");
      if (afterSectionIdx < -1 || afterSectionIdx >= sections.size()) {
        throw new DataException("Invalid section index: " + afterSectionIdx);
      }
      Element newSection = doc.createElement("section");
      if (StringUtils.isNotBlank(sectionClass)) {
        newSection.setAttribute("class", sectionClass);
      }
      Element defaultColumn = doc.createElement("column");
      defaultColumn.setAttribute("class", "small-12 cell");
      newSection.appendChild(defaultColumn);
      insertAfter(pageEl, sections, afterSectionIdx, newSection);
    });
  }

  /**
   * Removes the section at {@code sectionIdx}. Fails if the section contains any widgets (in any
   * column) — the caller must remove all widgets first.
   */
  public static void removeSection(WebPage webPage, int sectionIdx, long modifiedBy) throws DataException {
    mutate(webPage, modifiedBy, doc -> {
      Element pageEl = doc.getDocumentElement();
      List<Element> sections = childElements(pageEl, "section");
      checkSectionIdx(sections, sectionIdx);
      Element sectionEl = sections.get(sectionIdx);
      for (Element col : childElements(sectionEl, "column")) {
        if (!childElements(col, "widget").isEmpty()) {
          throw new DataException("Cannot remove a section that contains widgets — remove all widgets first");
        }
      }
      pageEl.removeChild(sectionEl);
    });
  }

  /**
   * Replaces the {@code class} attribute on the section at {@code sectionIdx}.
   */
  public static void setSectionClass(WebPage webPage, int sectionIdx, String sectionClass, long modifiedBy)
      throws DataException {
    if (StringUtils.isBlank(sectionClass)) {
      throw new DataException("Section class is required");
    }
    validateCssClass(sectionClass);
    mutate(webPage, modifiedBy, doc -> {
      List<Element> sections = childElements(doc.getDocumentElement(), "section");
      checkSectionIdx(sections, sectionIdx);
      sections.get(sectionIdx).setAttribute("class", sectionClass);
    });
  }

  // ── Column operations ─────────────────────────────────────────────────────

  /**
   * Inserts a new empty column into the section at {@code sectionIdx}.
   *
   * @param afterColumnIdx index within the section to insert after; {@code -1} prepends
   * @param columnClass    Foundation grid class for the new column; defaults to {@code "small-12 cell"}
   *                       if blank
   * @param modifiedBy     user id of the person making this change
   */
  public static void addColumn(WebPage webPage, int sectionIdx, int afterColumnIdx, String columnClass,
      long modifiedBy) throws DataException {
    String effectiveClass = StringUtils.isNotBlank(columnClass) ? columnClass : "small-12 cell";
    validateCssClass(effectiveClass);
    mutate(webPage, modifiedBy, doc -> {
      List<Element> sections = childElements(doc.getDocumentElement(), "section");
      checkSectionIdx(sections, sectionIdx);
      Element sectionEl = sections.get(sectionIdx);
      List<Element> columns = childElements(sectionEl, "column");
      if (afterColumnIdx < -1 || afterColumnIdx >= columns.size()) {
        throw new DataException("Invalid column index: " + afterColumnIdx);
      }
      Element newColumn = doc.createElement("column");
      newColumn.setAttribute("class", effectiveClass);
      insertAfter(sectionEl, columns, afterColumnIdx, newColumn);
    });
  }

  /**
   * Removes the column at {@code sectionIdx}:{@code columnIdx}. Fails if the column contains any
   * widgets.
   */
  public static void removeColumn(WebPage webPage, int sectionIdx, int columnIdx, long modifiedBy)
      throws DataException {
    mutate(webPage, modifiedBy, doc -> {
      List<Element> sections = childElements(doc.getDocumentElement(), "section");
      checkSectionIdx(sections, sectionIdx);
      Element sectionEl = sections.get(sectionIdx);
      List<Element> columns = childElements(sectionEl, "column");
      checkColumnIdx(columns, columnIdx, sectionIdx);
      Element colEl = columns.get(columnIdx);
      if (!childElements(colEl, "widget").isEmpty()) {
        throw new DataException("Cannot remove a column that contains widgets — remove all widgets first");
      }
      sectionEl.removeChild(colEl);
    });
  }

  /**
   * Replaces the {@code class} attribute on the column at {@code sectionIdx}:{@code columnIdx}.
   */
  public static void setColumnClass(WebPage webPage, int sectionIdx, int columnIdx, String columnClass,
      long modifiedBy) throws DataException {
    if (StringUtils.isBlank(columnClass)) {
      throw new DataException("Column class is required");
    }
    validateCssClass(columnClass);
    mutate(webPage, modifiedBy, doc -> {
      List<Element> sections = childElements(doc.getDocumentElement(), "section");
      checkSectionIdx(sections, sectionIdx);
      List<Element> columns = childElements(sections.get(sectionIdx), "column");
      checkColumnIdx(columns, columnIdx, sectionIdx);
      columns.get(columnIdx).setAttribute("class", columnClass);
    });
  }

  // ── Widget operations ─────────────────────────────────────────────────────

  /**
   * Inserts a new widget into the column at {@code sectionIdx}:{@code columnIdx}.
   *
   * @param afterWidgetIdx index within the column to insert after; {@code -1} prepends
   * @param widgetName     widget name; must exist in the widget library
   * @param prefsJson      optional JSON object of initial preference key/value pairs, e.g.
   *                       {@code {"uniqueId":"my-content"}}; null or blank means no preferences
   * @param modifiedBy     user id of the person making this change
   */
  public static void addWidget(WebPage webPage, int sectionIdx, int columnIdx, int afterWidgetIdx,
      String widgetName, String prefsJson, long modifiedBy) throws DataException {
    if (StringUtils.isBlank(widgetName)) {
      throw new DataException("Widget name is required");
    }
    if (!WebPageXmlLayoutCommand.getWidgetLibrary().containsKey(widgetName)) {
      throw new DataException("Unknown widget: " + widgetName);
    }
    Map<String, String> prefs = parsePrefsJson(prefsJson);
    validateWidgetPreferenceValues(widgetName, prefs);
    mutate(webPage, modifiedBy, doc -> {
      List<Element> sections = childElements(doc.getDocumentElement(), "section");
      checkSectionIdx(sections, sectionIdx);
      List<Element> columns = childElements(sections.get(sectionIdx), "column");
      checkColumnIdx(columns, columnIdx, sectionIdx);
      Element colEl = columns.get(columnIdx);
      List<Element> widgets = childElements(colEl, "widget");
      if (afterWidgetIdx < -1 || afterWidgetIdx >= widgets.size()) {
        throw new DataException("Invalid widget index: " + afterWidgetIdx);
      }
      Element newWidget = doc.createElement("widget");
      newWidget.setAttribute("name", widgetName);
      for (Map.Entry<String, String> e : prefs.entrySet()) {
        Element prefEl = doc.createElement(e.getKey());
        prefEl.setTextContent(e.getValue());
        newWidget.appendChild(prefEl);
      }
      insertAfter(colEl, widgets, afterWidgetIdx, newWidget);
    });
  }

  /**
   * Removes the widget at {@code sectionIdx}:{@code columnIdx}:{@code widgetIdx}.
   */
  public static void removeWidget(WebPage webPage, int sectionIdx, int columnIdx, int widgetIdx,
      long modifiedBy) throws DataException {
    mutate(webPage, modifiedBy, doc -> {
      List<Element> sections = childElements(doc.getDocumentElement(), "section");
      checkSectionIdx(sections, sectionIdx);
      List<Element> columns = childElements(sections.get(sectionIdx), "column");
      checkColumnIdx(columns, columnIdx, sectionIdx);
      Element colEl = columns.get(columnIdx);
      List<Element> widgets = childElements(colEl, "widget");
      checkWidgetIdx(widgets, widgetIdx, sectionIdx, columnIdx);
      colEl.removeChild(widgets.get(widgetIdx));
    });
  }

  /**
   * Merges preference key/value pairs into the widget at {@code sectionIdx}:{@code columnIdx}:
   * {@code widgetIdx}. Existing keys are updated; new keys are appended. Keys not in
   * {@code prefsJson} are left unchanged.
   *
   * @param prefsJson  JSON object of preference key/value pairs to merge
   * @param modifiedBy user id of the person making this change
   */
  public static void setWidgetPreferences(WebPage webPage, int sectionIdx, int columnIdx,
      int widgetIdx, String prefsJson, long modifiedBy) throws DataException {
    Map<String, String> prefs = parsePrefsJson(prefsJson);
    if (prefs.isEmpty()) {
      throw new DataException("At least one preference is required");
    }
    mutate(webPage, modifiedBy, doc -> {
      List<Element> sections = childElements(doc.getDocumentElement(), "section");
      checkSectionIdx(sections, sectionIdx);
      List<Element> columns = childElements(sections.get(sectionIdx), "column");
      checkColumnIdx(columns, columnIdx, sectionIdx);
      Element colEl = columns.get(columnIdx);
      List<Element> widgets = childElements(colEl, "widget");
      checkWidgetIdx(widgets, widgetIdx, sectionIdx, columnIdx);
      Element widgetEl = widgets.get(widgetIdx);
      validateWidgetPreferenceValues(widgetEl.getAttribute("name"), prefs);
      for (Map.Entry<String, String> e : prefs.entrySet()) {
        List<Element> existing = childElements(widgetEl, e.getKey());
        if (!existing.isEmpty()) {
          existing.get(0).setTextContent(e.getValue());
        } else {
          Element newPref = doc.createElement(e.getKey());
          newPref.setTextContent(e.getValue());
          widgetEl.appendChild(newPref);
        }
      }
    });
  }

  // ── Internal plumbing ─────────────────────────────────────────────────────

  @FunctionalInterface
  private interface DomMutation {
    void apply(Document doc) throws DataException;
  }

  private static void mutate(WebPage webPage, long modifiedBy, DomMutation mutation) throws DataException {
    if (webPage == null || webPage.getId() == -1) {
      throw new DataException("Page not found");
    }
    String sourceXml = StringUtils.isNotBlank(webPage.getDraftPageXml())
        ? webPage.getDraftPageXml()
        : webPage.getPageXml();
    if (StringUtils.isBlank(sourceXml)) {
      throw new DataException("Page has no XML layout");
    }
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      dbf.setExpandEntityReferences(false);
      DocumentBuilder builder = dbf.newDocumentBuilder();
      Document doc = builder.parse(new InputSource(new StringReader(sourceXml)));

      mutation.apply(doc);

      TransformerFactory tf = TransformerFactory.newInstance();
      tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Transformer transformer = tf.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      StringWriter sw = new StringWriter();
      transformer.transform(new DOMSource(doc), new StreamResult(sw));

      webPage.setDraftPageXml(sw.toString().trim());
      webPage.setDraft(true);
      webPage.setModifiedBy(modifiedBy);
      if (WebPageRepository.save(webPage) == null) {
        throw new DataException("Could not save layout changes for " + webPage.getLink());
      }
      WebPageXmlLayoutCommand.removeCustomPage(webPage.getLink());
    } catch (DataException e) {
      throw e;
    } catch (Exception e) {
      LOG.error("MutateLayoutCommand failed for " + (webPage != null ? webPage.getLink() : "null"), e);
      throw new DataException("Could not mutate layout: " + e.getMessage());
    }
  }

  /**
   * Inserts {@code newEl} into {@code parent} after {@code siblings.get(afterIdx)}, or prepends
   * when {@code afterIdx == -1}.
   */
  private static void insertAfter(Element parent, List<Element> siblings, int afterIdx, Element newEl) {
    if (afterIdx == -1 || siblings.isEmpty()) {
      if (siblings.isEmpty()) {
        parent.appendChild(newEl);
      } else {
        parent.insertBefore(newEl, siblings.get(0));
      }
    } else {
      Node next = nextElementSibling(siblings.get(afterIdx));
      if (next == null) {
        parent.appendChild(newEl);
      } else {
        parent.insertBefore(newEl, next);
      }
    }
  }

  private static Node nextElementSibling(Node node) {
    Node next = node.getNextSibling();
    while (next != null && next.getNodeType() != Node.ELEMENT_NODE) {
      next = next.getNextSibling();
    }
    return next;
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

  private static Map<String, String> parsePrefsJson(String prefsJson) throws DataException {
    Map<String, String> prefs = new LinkedHashMap<>();
    if (StringUtils.isBlank(prefsJson)) {
      return prefs;
    }
    try {
      JsonNode root = MAPPER.readTree(prefsJson);
      if (!root.isObject()) {
        throw new DataException("Preferences must be a JSON object");
      }
      Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        validatePrefKey(entry.getKey());
        prefs.put(entry.getKey(), entry.getValue().asText());
      }
    } catch (DataException e) {
      throw e;
    } catch (Exception e) {
      throw new DataException("Invalid preferences JSON: " + e.getMessage());
    }
    return prefs;
  }

  private static void checkSectionIdx(List<Element> sections, int idx) throws DataException {
    if (idx < 0 || idx >= sections.size()) {
      throw new DataException("Section index " + idx + " out of range (page has " + sections.size() + " section(s))");
    }
  }

  private static void checkColumnIdx(List<Element> columns, int idx, int sectionIdx) throws DataException {
    if (idx < 0 || idx >= columns.size()) {
      throw new DataException(
          "Column index " + idx + " in section " + sectionIdx + " out of range (" + columns.size() + " column(s))");
    }
  }

  private static void checkWidgetIdx(List<Element> widgets, int idx, int sectionIdx, int columnIdx)
      throws DataException {
    if (idx < 0 || idx >= widgets.size()) {
      throw new DataException(
          "Widget index " + idx + " at " + sectionIdx + ":" + columnIdx + " out of range (" + widgets.size() + " widget(s))");
    }
  }

  private static void validateCssClass(String cssClass) throws DataException {
    if (!CSS_CLASS_PATTERN.matcher(cssClass).matches()) {
      throw new DataException(
          "Invalid CSS class '" + cssClass + "': only letters, digits, spaces, hyphens, and underscores are allowed");
    }
  }

  private static void validatePrefKey(String key) throws DataException {
    if (!PREF_KEY_PATTERN.matcher(key).matches()) {
      throw new DataException("Invalid preference key '" + key + "': must start with a letter and contain only alphanumeric characters");
    }
  }

  /**
   * Widget-specific value validation, layered on top of the generic key-allowlist in
   * {@link #parsePrefsJson}. Most preference values are opaque strings this class has no business
   * inspecting, but a few widgets store structured data whose shape/size matters for safe rendering
   * downstream -- this is the boundary where that gets enforced, so no caller (addWidget,
   * setWidgetPreferences, or anything added later) can persist it unchecked.
   *
   * <p>Called before any DOM mutation, so a rejected value never reaches {@code draftPageXml}.
   *
   * @param widgetName the widget's registered name (widget-library.xml {@code name} attribute)
   * @param prefs      the preference key/value pairs about to be written
   */
  private static void validateWidgetPreferenceValues(String widgetName, Map<String, String> prefs)
      throws DataException {
    if (TableWidget.WIDGET_NAME.equals(widgetName) && prefs.containsKey("tableData")) {
      String tableDataJson = prefs.get("tableData");
      if (!TableWidget.isValidTableData(tableDataJson)) {
        throw new DataException("Invalid table data: expected a JSON object with 'headers' and 'rows' arrays, "
            + "at most " + TableWidget.MAX_ROWS + " rows, " + TableWidget.MAX_COLUMNS + " columns, and "
            + TableWidget.MAX_CELL_LENGTH + " characters per header/cell");
      }
    }
    if (ImageWidget.WIDGET_NAME.equals(widgetName) && prefs.containsKey(ImageWidget.IMAGE_URL_PREF_KEY)
        && !ImageWidget.isValidImageUrl(prefs.get(ImageWidget.IMAGE_URL_PREF_KEY))) {
      throw new DataException("Invalid image url: expected a site-relative path or an http(s)/mailto/tel address");
    }
  }

  // ── Read-only queries ─────────────────────────────────────────────────────

  /**
   * Returns the widget-library {@code name} of the widget at {@code sectionIdx}:{@code columnIdx}:
   * {@code widgetIdx} in the page's current draft layout (or its published layout when there is no
   * draft) -- resolved from the same source XML, and by the same structural traversal, that
   * {@link #setWidgetPreferences} uses internally. That means the answer can never drift from what
   * a subsequent mutation at the same position would actually touch, unlike a second, independently
   * written traversal that might parse or resolve pages slightly differently.
   *
   * <p>This exists so a caller that must authorize *which* widget a mutation is about to touch --
   * before honoring a client-supplied preference key -- can check it against the real, structurally
   * resolved widget rather than trusting anything the client claims about it. (For example,
   * {@code MediaApiController}'s widget-update endpoint: its client-side "this is an image widget"
   * gate is UI-only, so the server must independently confirm the target widget really is one before
   * applying a client-chosen asset to it.) Read-only: never touches {@code draftPageXml}.
   *
   * @return the widget's {@code name} attribute (may be blank if the widget element has none)
   * @throws DataException if the page has no XML layout, or the position is out of range
   */
  public static String getWidgetName(WebPage webPage, int sectionIdx, int columnIdx, int widgetIdx)
      throws DataException {
    if (webPage == null || webPage.getId() == -1) {
      throw new DataException("Page not found");
    }
    String sourceXml = StringUtils.isNotBlank(webPage.getDraftPageXml())
        ? webPage.getDraftPageXml()
        : webPage.getPageXml();
    if (StringUtils.isBlank(sourceXml)) {
      throw new DataException("Page has no XML layout");
    }
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      dbf.setExpandEntityReferences(false);
      DocumentBuilder builder = dbf.newDocumentBuilder();
      Document doc = builder.parse(new InputSource(new StringReader(sourceXml)));

      List<Element> sections = childElements(doc.getDocumentElement(), "section");
      checkSectionIdx(sections, sectionIdx);
      List<Element> columns = childElements(sections.get(sectionIdx), "column");
      checkColumnIdx(columns, columnIdx, sectionIdx);
      List<Element> widgets = childElements(columns.get(columnIdx), "widget");
      checkWidgetIdx(widgets, widgetIdx, sectionIdx, columnIdx);
      return widgets.get(widgetIdx).getAttribute("name");
    } catch (DataException e) {
      throw e;
    } catch (Exception e) {
      LOG.error("MutateLayoutCommand.getWidgetName failed for " + webPage.getLink(), e);
      throw new DataException("Could not resolve widget: " + e.getMessage());
    }
  }
}
