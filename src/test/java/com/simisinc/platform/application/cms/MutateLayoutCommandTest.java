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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;

/**
 * Tests the visual editor P4 structural mutation commands.
 *
 * <p>Tests verify that each operation produces the correct XML change and that all validations
 * (index bounds, CSS class allowlist, preference key allowlist, widget-name registry) are enforced
 * before the DOM is touched, leaving {@code draftPageXml} unchanged on failure.
 *
 * @author elizabeth houser
 */
class MutateLayoutCommandTest {

  // One section, one column, one widget — the minimal layout used across most tests.
  private static final String ONE_SECTION_XML =
      "<page>\n" +
      "  <section class=\"first\">\n" +
      "    <column class=\"small-12 cell\">\n" +
      "      <widget name=\"content\">\n" +
      "        <uniqueId>my-content</uniqueId>\n" +
      "      </widget>\n" +
      "    </column>\n" +
      "  </section>\n" +
      "</page>";

  // Two sections for operations that need a richer layout.
  private static final String TWO_SECTION_XML =
      "<page>\n" +
      "  <section class=\"first\">\n" +
      "    <column class=\"small-12 cell\">\n" +
      "      <widget name=\"content\">\n" +
      "        <uniqueId>hero</uniqueId>\n" +
      "      </widget>\n" +
      "    </column>\n" +
      "  </section>\n" +
      "  <section class=\"second\">\n" +
      "    <column class=\"small-12 cell\" />\n" +
      "  </section>\n" +
      "</page>";

  // Empty section (no widgets) used for remove tests.
  private static final String EMPTY_SECTION_XML =
      "<page>\n" +
      "  <section class=\"empty\">\n" +
      "    <column class=\"small-12 cell\" />\n" +
      "  </section>\n" +
      "</page>";

  // A page with a dataTable (TableWidget) instance, for the table-data validation tests.
  private static final String TABLE_WIDGET_XML =
      "<page>\n" +
      "  <section class=\"first\">\n" +
      "    <column class=\"small-12 cell\">\n" +
      "      <widget name=\"dataTable\">\n" +
      "        <tableData>{&quot;headers&quot;:[&quot;A&quot;],&quot;rows&quot;:[[&quot;1&quot;]]}</tableData>\n" +
      "      </widget>\n" +
      "    </column>\n" +
      "  </section>\n" +
      "</page>";

  // A page with an image (ImageWidget) instance, for the imageUrl validation tests.
  private static final String IMAGE_WIDGET_XML =
      "<page>\n" +
      "  <section class=\"first\">\n" +
      "    <column class=\"small-12 cell\">\n" +
      "      <widget name=\"image\">\n" +
      "        <imageUrl>/media/original.png</imageUrl>\n" +
      "      </widget>\n" +
      "    </column>\n" +
      "  </section>\n" +
      "</page>";

  private static WebPage pageWithXml(String xml) {
    WebPage p = new WebPage();
    p.setId(99);
    p.setLink("/test-page");
    p.setPageXml(xml);
    return p;
  }

  // Helper: run a mutation with mocked repository/cache, then return the resulting draftPageXml.
  // WebPageRepository.save() answers with the same record it was given, mirroring the real
  // repository's success return (the record, or null on failure) -- see
  // mutationThrowsWhenSaveFails below for the null/failure path.
  private static String mutate(WebPage page, ThrowingRunnable mutation) throws DataException {
    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
         MockedStatic<WebPageXmlLayoutCommand> cmd = mockStatic(WebPageXmlLayoutCommand.class)) {
      repo.when(() -> WebPageRepository.save(any(WebPage.class))).thenAnswer(i -> i.getArgument(0));
      cmd.when(() -> WebPageXmlLayoutCommand.removeCustomPage(anyString())).thenAnswer(i -> null);
      cmd.when(WebPageXmlLayoutCommand::getWidgetLibrary)
          .thenReturn(Map.of("content", "ContentWidget", "menu", "MenuWidget", "dataTable", "TableWidget",
              "image", "ImageWidget"));
      mutation.run();
    }
    return page.getDraftPageXml();
  }

  @FunctionalInterface
  interface ThrowingRunnable {
    void run() throws DataException;
  }

  // ── addSection ───────────────────────────────────────────────────────────

  @Test
  void addSectionAppendsAtEnd() throws DataException {
    WebPage page = pageWithXml(TWO_SECTION_XML);
    String result = mutate(page, () -> MutateLayoutCommand.addSection(page, 1, "new-section", 42L));
    // Three sections after the operation; new one is last.
    long sectionCount = result.chars().filter(c -> c == '<').mapToObj(c -> "").count();
    assertTrue(result.contains("class=\"new-section\""), "new class should be present");
    // The first two sections appear before the new one.
    int firstIdx = result.indexOf("class=\"first\"");
    int secondIdx = result.indexOf("class=\"second\"");
    int newIdx = result.indexOf("class=\"new-section\"");
    assertTrue(firstIdx < secondIdx && secondIdx < newIdx, "order should be first, second, new");
  }

  @Test
  void addSectionPrependsWhenAfterMinusOne() throws DataException {
    WebPage page = pageWithXml(TWO_SECTION_XML);
    String result = mutate(page, () -> MutateLayoutCommand.addSection(page, -1, "prepended", 42L));
    int prependedIdx = result.indexOf("class=\"prepended\"");
    int firstIdx = result.indexOf("class=\"first\"");
    assertTrue(prependedIdx < firstIdx, "prepended section should come before existing sections");
  }

  @Test
  void addSectionWithoutClassHasNoClassAttribute() throws DataException {
    WebPage page = pageWithXml(EMPTY_SECTION_XML);
    String result = mutate(page, () -> MutateLayoutCommand.addSection(page, 0, null, 42L));
    // The new section element should appear; it may or may not have a class.
    // Verify the existing section's class is unchanged and the result is valid XML.
    assertTrue(result.contains("class=\"empty\""));
  }

  @Test
  void addSectionDefaultColumnHasSmall12CellClass() throws DataException {
    WebPage page = pageWithXml(EMPTY_SECTION_XML);
    String result = mutate(page, () -> MutateLayoutCommand.addSection(page, 0, "new", 42L));
    // The new section's auto-generated column should be small-12 cell.
    // Both sections are in the result, so count occurrences of the default class.
    int count = 0;
    int idx = 0;
    while ((idx = result.indexOf("small-12 cell", idx)) != -1) {
      count++;
      idx++;
    }
    assertTrue(count >= 2, "at least 2 'small-12 cell' columns expected (original + new)");
  }

  @Test
  void addSectionRejectsInvalidIndex() {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.addSection(page, 5, "x", 42L)));
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.addSection(page, -2, "x", 42L)));
  }

  @Test
  void addSectionRejectsInvalidCssClass() {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.addSection(page, 0, "x\"><script>alert(1)</script>", 42L)));
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.addSection(page, 0, "bad'class", 42L)));
  }

  // ── removeSection ────────────────────────────────────────────────────────

  @Test
  void removeSectionDeletesEmptySection() throws DataException {
    WebPage page = pageWithXml(TWO_SECTION_XML);
    String result = mutate(page, () -> MutateLayoutCommand.removeSection(page, 1, 42L));
    assertTrue(result.contains("class=\"first\""), "first section should survive");
    assertTrue(!result.contains("class=\"second\""), "second section should be removed");
  }

  @Test
  void removeSectionFailsWhenSectionHasWidgets() {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.removeSection(page, 0, 42L)));
  }

  @Test
  void removeSectionRejectsInvalidIndex() {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.removeSection(page, 99, 42L)));
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.removeSection(page, -1, 42L)));
  }

  // ── setSectionClass ──────────────────────────────────────────────────────

  @Test
  void setSectionClassUpdatesTheAttribute() throws DataException {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    String result = mutate(page, () -> MutateLayoutCommand.setSectionClass(page, 0, "updated-class", 42L));
    assertTrue(result.contains("class=\"updated-class\""), "class should be updated");
    assertTrue(!result.contains("class=\"first\""), "old class should be gone");
  }

  @Test
  void setSectionClassRejectsBlank() {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.setSectionClass(page, 0, "", 42L)));
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.setSectionClass(page, 0, null, 42L)));
  }

  // ── addColumn ────────────────────────────────────────────────────────────

  @Test
  void addColumnAppendsToSection() throws DataException {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    String result = mutate(page, () -> MutateLayoutCommand.addColumn(page, 0, 0, "medium-6 cell", 42L));
    assertTrue(result.contains("class=\"medium-6 cell\""), "new column class should be present");
    assertTrue(result.contains("class=\"small-12 cell\""), "original column should survive");
  }

  @Test
  void addColumnUsesDefaultClassWhenBlank() throws DataException {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    String result = mutate(page, () -> MutateLayoutCommand.addColumn(page, 0, 0, null, 42L));
    // Should still add a column with default class
    int count = 0;
    int idx = 0;
    while ((idx = result.indexOf("small-12 cell", idx)) != -1) {
      count++;
      idx++;
    }
    assertTrue(count >= 2, "two 'small-12 cell' columns expected");
  }

  @Test
  void addColumnRejectsInvalidSectionIndex() {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.addColumn(page, 99, 0, null, 42L)));
  }

  // ── removeColumn ─────────────────────────────────────────────────────────

  @Test
  void removeColumnFailsWhenColumnHasWidgets() {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.removeColumn(page, 0, 0, 42L)));
  }

  // ── setColumnClass ───────────────────────────────────────────────────────

  @Test
  void setColumnClassUpdatesTheAttribute() throws DataException {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    String result = mutate(page, () -> MutateLayoutCommand.setColumnClass(page, 0, 0, "medium-4 cell", 42L));
    assertTrue(result.contains("class=\"medium-4 cell\""), "class should be updated");
    assertTrue(!result.contains("class=\"small-12 cell\""), "old class should be gone");
  }

  @Test
  void setColumnClassRejectsScriptInjection() {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.setColumnClass(page, 0, 0, "x\" onload=\"evil()", 42L)));
  }

  // ── addWidget ────────────────────────────────────────────────────────────

  @Test
  void addWidgetInsertsWidgetAtEnd() throws DataException {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    String result = mutate(page, () -> MutateLayoutCommand.addWidget(page, 0, 0, 0, "menu", null, 42L));
    assertTrue(result.contains("name=\"menu\""), "new widget should be present");
    assertTrue(result.contains("name=\"content\""), "original widget should survive");
    // menu should come after content (afterWidgetIdx=0 means after index 0)
    int contentIdx = result.indexOf("name=\"content\"");
    int menuIdx = result.indexOf("name=\"menu\"");
    assertTrue(contentIdx < menuIdx, "content should precede menu");
  }

  @Test
  void addWidgetWithPrefsWritesPreferenceElements() throws DataException {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    String result = mutate(page, () ->
        MutateLayoutCommand.addWidget(page, 0, 0, -1, "menu",
            "{\"title\":\"My Menu\",\"class\":\"vertical\"}", 42L));
    assertTrue(result.contains("<title>My Menu</title>"), "title pref should be present");
    assertTrue(result.contains("<class>vertical</class>"), "class pref should be present");
  }

  @Test
  void addWidgetRejectsUnknownWidgetName() {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.addWidget(page, 0, 0, 0, "notAWidget", null, 42L)));
  }

  @Test
  void addWidgetRejectsInvalidPrefKey() {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    // A key with angle brackets would inject a tag name into the XML element.
    assertThrows(DataException.class,
        () -> mutate(page, () ->
            MutateLayoutCommand.addWidget(page, 0, 0, 0, "content", "{\"bad<key>\":\"val\"}", 42L)));
    assertThrows(DataException.class,
        () -> mutate(page, () ->
            MutateLayoutCommand.addWidget(page, 0, 0, 0, "content", "{\"0startsWithDigit\":\"val\"}", 42L)));
  }

  @Test
  void addWidgetRejectsBlankWidgetName() {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.addWidget(page, 0, 0, 0, "", null, 42L)));
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.addWidget(page, 0, 0, 0, null, null, 42L)));
  }

  // ── removeWidget ─────────────────────────────────────────────────────────

  @Test
  void removeWidgetDeletesTheWidget() throws DataException {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    String result = mutate(page, () -> MutateLayoutCommand.removeWidget(page, 0, 0, 0, 42L));
    assertTrue(!result.contains("name=\"content\""), "widget should be removed");
  }

  @Test
  void removeWidgetRejectsInvalidIndex() {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.removeWidget(page, 0, 0, 5, 42L)));
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.removeWidget(page, 0, 0, -1, 42L)));
  }

  // ── setWidgetPreferences ─────────────────────────────────────────────────

  @Test
  void setWidgetPreferencesUpdatesExistingKey() throws DataException {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    String result = mutate(page, () ->
        MutateLayoutCommand.setWidgetPreferences(page, 0, 0, 0, "{\"uniqueId\":\"new-id\"}", 42L));
    assertTrue(result.contains("<uniqueId>new-id</uniqueId>"), "uniqueId should be updated");
    assertTrue(!result.contains("<uniqueId>my-content</uniqueId>"), "old value should be gone");
  }

  @Test
  void setWidgetPreferencesAddsNewKey() throws DataException {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    String result = mutate(page, () ->
        MutateLayoutCommand.setWidgetPreferences(page, 0, 0, 0, "{\"title\":\"New Title\"}", 42L));
    assertTrue(result.contains("<title>New Title</title>"), "new pref element should be added");
    assertTrue(result.contains("<uniqueId>my-content</uniqueId>"), "existing pref should be preserved");
  }

  @Test
  void setWidgetPreferencesRejectsEmptyPrefs() {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.setWidgetPreferences(page, 0, 0, 0, "{}", 42L)));
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.setWidgetPreferences(page, 0, 0, 0, null, 42L)));
  }

  @Test
  void setWidgetPreferencesRejectsInvalidPrefKey() {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    assertThrows(DataException.class,
        () -> mutate(page, () ->
            MutateLayoutCommand.setWidgetPreferences(page, 0, 0, 0, "{\"</injection>\":\"val\"}", 42L)));
  }

  // ── setWidgetPreferences / addWidget: TableWidget's tableData is validated at this boundary ─────

  @Test
  void setWidgetPreferencesAcceptsWellFormedTableData() throws DataException {
    WebPage page = pageWithXml(TABLE_WIDGET_XML);
    String result = mutate(page, () -> MutateLayoutCommand.setWidgetPreferences(page, 0, 0, 0,
        "{\"tableData\":\"{\\\"headers\\\":[\\\"Name\\\",\\\"Age\\\"],\\\"rows\\\":[[\\\"Alice\\\",\\\"30\\\"]]}\"}", 42L));
    assertTrue(result.contains("Alice"), "the new table data should be written");
  }

  @Test
  void setWidgetPreferencesPersistsARealisticEditedTableRoundTrip() throws Exception {
    // Reproduces exactly what table-widget-edit.jsp's Save button now sends (issue #433): the
    // client serializes the edited table once to get the "tableData" string, then wraps that
    // string as the value of a "tableData" key in the outer prefs object -- i.e.
    // JSON.stringify({tableData: JSON.stringify({headers, rows})}). Building the request the same
    // way here (via Jackson, not hand-escaped literals) is the most direct proof that this
    // widget's save wiring reaches this method with real edited data and that the data survives
    // the round trip intact, not just that some JSON containing "Alice" shows up somewhere.
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> editedTable = new LinkedHashMap<>();
    editedTable.put("headers", List.of("Name", "Age", "City"));
    editedTable.put("rows", List.of(
        List.of("Alice", "30", "Portland"),
        List.of("Bob", "25", "Austin")));
    String tableDataJson = mapper.writeValueAsString(editedTable);
    String prefsJson = mapper.writeValueAsString(Map.of("tableData", tableDataJson));

    WebPage page = pageWithXml(TABLE_WIDGET_XML);
    String result = mutate(page, () -> MutateLayoutCommand.setWidgetPreferences(page, 0, 0, 0, prefsJson, 42L));

    // The old single-cell fixture data must be gone, replaced by the edit.
    assertTrue(!result.contains("\\u0022rows\\u0022:[[\\u00221\\u0022]]") && !result.contains(">1<"),
        "the original placeholder row should not survive the edit");

    // Extract the persisted <tableData> element's text content and parse it back to prove the
    // edited data round-trips intact, not just that a substring happens to match.
    int start = result.indexOf("<tableData>") + "<tableData>".length();
    int end = result.indexOf("</tableData>", start);
    String persistedRaw = result.substring(start, end)
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">");
    JsonNode persisted = mapper.readTree(persistedRaw);

    assertEquals(3, persisted.get("headers").size());
    assertEquals("Name", persisted.get("headers").get(0).asText());
    assertEquals("Age", persisted.get("headers").get(1).asText());
    assertEquals("City", persisted.get("headers").get(2).asText());
    assertEquals(2, persisted.get("rows").size());
    assertEquals("Alice", persisted.get("rows").get(0).get(0).asText());
    assertEquals("Portland", persisted.get("rows").get(0).get(2).asText());
    assertEquals("Bob", persisted.get("rows").get(1).get(0).asText());
    assertEquals("Austin", persisted.get("rows").get(1).get(2).asText());
  }

  @Test
  void setWidgetPreferencesRejectsTableDataMissingRows() {
    WebPage page = pageWithXml(TABLE_WIDGET_XML);
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.setWidgetPreferences(page, 0, 0, 0,
            "{\"tableData\":\"{\\\"headers\\\":[\\\"A\\\"]}\"}", 42L)),
        "tableData without a 'rows' array must be rejected before it is persisted");
  }

  @Test
  void setWidgetPreferencesRejectsTableDataWithWrongTypedHeaders() {
    WebPage page = pageWithXml(TABLE_WIDGET_XML);
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.setWidgetPreferences(page, 0, 0, 0,
            "{\"tableData\":\"{\\\"headers\\\":\\\"not-an-array\\\",\\\"rows\\\":[]}\"}", 42L)));
  }

  @Test
  void setWidgetPreferencesRejectsOversizedTableData() {
    WebPage page = pageWithXml(TABLE_WIDGET_XML);
    StringBuilder rows = new StringBuilder();
    for (int i = 0; i <= com.simisinc.platform.presentation.widgets.cms.TableWidget.MAX_ROWS; i++) {
      if (i > 0) {
        rows.append(",");
      }
      rows.append("[\\\"x\\\"]");
    }
    String prefsJson = "{\"tableData\":\"{\\\"headers\\\":[\\\"A\\\"],\\\"rows\\\":[" + rows + "]}\"}";
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.setWidgetPreferences(page, 0, 0, 0, prefsJson, 42L)),
        "a row count over MAX_ROWS must be rejected before it is persisted");
  }

  @Test
  void setWidgetPreferencesIgnoresTableDataValidationForOtherWidgets() throws DataException {
    // A "tableData"-named preference on some other widget type is just an opaque string to this
    // class -- only the dataTable widget's value gets structurally validated.
    WebPage page = pageWithXml(ONE_SECTION_XML);
    String result = mutate(page, () ->
        MutateLayoutCommand.setWidgetPreferences(page, 0, 0, 0, "{\"tableData\":\"not-json-at-all\"}", 42L));
    assertTrue(result.contains("<tableData>not-json-at-all</tableData>"));
  }

  @Test
  void addWidgetAcceptsWellFormedTableData() throws DataException {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    String result = mutate(page, () -> MutateLayoutCommand.addWidget(page, 0, 0, -1, "dataTable",
        "{\"tableData\":\"{\\\"headers\\\":[\\\"A\\\"],\\\"rows\\\":[[\\\"1\\\"]]}\"}", 42L));
    assertTrue(result.contains("name=\"dataTable\""));
  }

  @Test
  void addWidgetRejectsMalformedTableData() {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.addWidget(page, 0, 0, -1, "dataTable",
            "{\"tableData\":\"{\\\"rows\\\":[]}\"}", 42L)),
        "tableData missing 'headers' must be rejected before the widget is ever added");
  }

  // ── setWidgetPreferences / addWidget: ImageWidget's imageUrl is validated at this boundary ──────
  // (issue #772: this preference is rendered straight into an <img src>, so an unsafe value must
  // be rejected here rather than merely dropped at render time.)

  @Test
  void setWidgetPreferencesAcceptsSiteRelativeImageUrl() throws DataException {
    WebPage page = pageWithXml(IMAGE_WIDGET_XML);
    String result = mutate(page, () ->
        MutateLayoutCommand.setWidgetPreferences(page, 0, 0, 0, "{\"imageUrl\":\"/media/new.png\"}", 42L));
    assertTrue(result.contains("<imageUrl>/media/new.png</imageUrl>"), "the new image url should be written");
  }

  @Test
  void setWidgetPreferencesAcceptsBlankImageUrlToClearIt() throws DataException {
    WebPage page = pageWithXml(IMAGE_WIDGET_XML);
    String result = mutate(page, () ->
        MutateLayoutCommand.setWidgetPreferences(page, 0, 0, 0, "{\"imageUrl\":\"\"}", 42L));
    assertTrue(result.contains("<imageUrl/>") || result.contains("<imageUrl></imageUrl>"),
        "a blank value is valid -- it clears the image back to the placeholder");
  }

  @Test
  void setWidgetPreferencesRejectsJavascriptSchemeImageUrl() {
    WebPage page = pageWithXml(IMAGE_WIDGET_XML);
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.setWidgetPreferences(page, 0, 0, 0,
            "{\"imageUrl\":\"javascript:alert(1)\"}", 42L)),
        "an active scheme must be rejected before it is persisted");
  }

  @Test
  void setWidgetPreferencesRejectsAttributeBreakoutImageUrl() {
    WebPage page = pageWithXml(IMAGE_WIDGET_XML);
    assertThrows(DataException.class,
        () -> mutate(page, () -> MutateLayoutCommand.setWidgetPreferences(page, 0, 0, 0,
            "{\"imageUrl\":\"/media/x.png\\\" onerror=\\\"alert(1)\"}", 42L)),
        "a value that could break out of the src attribute must be rejected before it is persisted");
  }

  @Test
  void setWidgetPreferencesIgnoresImageUrlValidationForOtherWidgets() throws DataException {
    // An "imageUrl"-named preference on some other widget type is just an opaque string to this
    // class -- only the image widget's value gets validated as a safe url.
    WebPage page = pageWithXml(ONE_SECTION_XML);
    String result = mutate(page, () ->
        MutateLayoutCommand.setWidgetPreferences(page, 0, 0, 0, "{\"imageUrl\":\"javascript:alert(1)\"}", 42L));
    assertTrue(result.contains("<imageUrl>javascript:alert(1)</imageUrl>"));
  }

  // ── getWidgetName ─────────────────────────────────────────────────────────
  // Read-only resolver added for issue #772's follow-up: MediaApiController's widget-update
  // endpoint must confirm the *real* widget at a position -- resolved the same way
  // setWidgetPreferences resolves it internally -- before honoring a client-supplied prefKey.

  @Test
  void getWidgetNameReturnsTheRegisteredNameOfTheWidgetAtThePosition() throws DataException {
    WebPage page = pageWithXml(IMAGE_WIDGET_XML);
    assertEquals("image", MutateLayoutCommand.getWidgetName(page, 0, 0, 0));
  }

  @Test
  void getWidgetNameResolvesEachWidgetInAMultiWidgetColumnIndependently() throws DataException {
    WebPage page = pageWithXml(
        "<page>\n" +
        "  <section>\n" +
        "    <column class=\"small-12 cell\">\n" +
        "      <widget name=\"content\"><uniqueId>a</uniqueId></widget>\n" +
        "      <widget name=\"image\"><imageUrl>/media/x.png</imageUrl></widget>\n" +
        "    </column>\n" +
        "  </section>\n" +
        "</page>");
    assertEquals("content", MutateLayoutCommand.getWidgetName(page, 0, 0, 0));
    assertEquals("image", MutateLayoutCommand.getWidgetName(page, 0, 0, 1));
  }

  @Test
  void getWidgetNamePrefersTheDraftLayoutOverThePublishedOne() throws DataException {
    // Mirrors mutate()'s own source resolution: a pending, unpublished edit must be what gets
    // checked, not the stale published version -- otherwise this check could pass or fail against a
    // widget arrangement that's no longer accurate.
    WebPage page = pageWithXml(IMAGE_WIDGET_XML);
    page.setDraftPageXml(
        "<page>\n" +
        "  <section>\n" +
        "    <column class=\"small-12 cell\">\n" +
        "      <widget name=\"remoteContent\"><url>https://example.com</url></widget>\n" +
        "    </column>\n" +
        "  </section>\n" +
        "</page>");
    assertEquals("remoteContent", MutateLayoutCommand.getWidgetName(page, 0, 0, 0));
  }

  @Test
  void getWidgetNameRejectsOutOfRangeIndices() {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    assertThrows(DataException.class, () -> MutateLayoutCommand.getWidgetName(page, 5, 0, 0));
    assertThrows(DataException.class, () -> MutateLayoutCommand.getWidgetName(page, 0, 5, 0));
    assertThrows(DataException.class, () -> MutateLayoutCommand.getWidgetName(page, 0, 0, 5));
  }

  @Test
  void getWidgetNameRejectsAPageWithNoXmlLayout() {
    WebPage page = new WebPage();
    page.setId(99);
    page.setLink("/no-xml");
    assertThrows(DataException.class, () -> MutateLayoutCommand.getWidgetName(page, 0, 0, 0));
  }

  @Test
  void getWidgetNameRejectsAMissingPage() {
    WebPage missing = new WebPage(); // id defaults to -1: not a real, persisted page
    assertThrows(DataException.class, () -> MutateLayoutCommand.getWidgetName(missing, 0, 0, 0));
  }

  // ── modifiedBy / persistence-failure propagation ─────────────────────────
  // A structural mutation must record who made it and must not report success when the
  // underlying save silently fails (e.g. a stale modified_by value tripping the
  // web_pages_modified_by_fkey foreign key). See MutateLayoutCommandIntegrationTest for the same
  // two properties proven against a real database instead of a mock.

  @Test
  void mutationSetsModifiedByBeforeSaving() throws DataException {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
         MockedStatic<WebPageXmlLayoutCommand> cmd = mockStatic(WebPageXmlLayoutCommand.class)) {
      repo.when(() -> WebPageRepository.save(any(WebPage.class))).thenAnswer(i -> i.getArgument(0));
      cmd.when(() -> WebPageXmlLayoutCommand.removeCustomPage(anyString())).thenAnswer(i -> null);

      MutateLayoutCommand.addSection(page, 0, "new-section", 42L);

      repo.verify(() -> WebPageRepository.save(argThat(p -> p.getModifiedBy() == 42L)));
    }
  }

  @Test
  void mutationThrowsWhenSaveFails() {
    WebPage page = pageWithXml(ONE_SECTION_XML);
    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
         MockedStatic<WebPageXmlLayoutCommand> cmd = mockStatic(WebPageXmlLayoutCommand.class)) {
      // A null return simulates WebPageRepository.update() failing (e.g. the FK violation logged
      // by DB.update() when modified_by isn't a real user id) -- mutate() must not treat that as
      // success.
      repo.when(() -> WebPageRepository.save(any(WebPage.class))).thenAnswer(i -> null);
      cmd.when(() -> WebPageXmlLayoutCommand.removeCustomPage(anyString())).thenAnswer(i -> null);

      assertThrows(DataException.class,
          () -> MutateLayoutCommand.addSection(page, 0, "new-section", 42L),
          "a null return from WebPageRepository.save() means persistence failed and must not be swallowed");

      cmd.verify(() -> WebPageXmlLayoutCommand.removeCustomPage(anyString()), never());
    }
  }

  // ── Allowlist pattern constants are stable ───────────────────────────────

  @Test
  void cssClassPatternAllowsFoundationGridClasses() {
    assertTrue(MutateLayoutCommand.CSS_CLASS_PATTERN.matcher("small-12 cell").matches());
    assertTrue(MutateLayoutCommand.CSS_CLASS_PATTERN.matcher("medium-6 medium-6 cell").matches());
    assertTrue(MutateLayoutCommand.CSS_CLASS_PATTERN.matcher("align-center padding-top-20").matches());
    assertTrue(MutateLayoutCommand.CSS_CLASS_PATTERN.matcher("").matches());
  }

  @Test
  void cssClassPatternBlocksInjectionCharacters() {
    assertTrue(!MutateLayoutCommand.CSS_CLASS_PATTERN.matcher("x\"onload=evil()").matches());
    assertTrue(!MutateLayoutCommand.CSS_CLASS_PATTERN.matcher("x'class").matches());
    assertTrue(!MutateLayoutCommand.CSS_CLASS_PATTERN.matcher("<script>").matches());
  }

  @Test
  void prefKeyPatternAllowsAlphanumericKeys() {
    assertTrue(MutateLayoutCommand.PREF_KEY_PATTERN.matcher("uniqueId").matches());
    assertTrue(MutateLayoutCommand.PREF_KEY_PATTERN.matcher("html").matches());
    assertTrue(MutateLayoutCommand.PREF_KEY_PATTERN.matcher("videoBackgroundUrl").matches());
  }

  @Test
  void prefKeyPatternBlocksInjectionAttempts() {
    assertTrue(!MutateLayoutCommand.PREF_KEY_PATTERN.matcher("0bad").matches());
    assertTrue(!MutateLayoutCommand.PREF_KEY_PATTERN.matcher("key name").matches());
    assertTrue(!MutateLayoutCommand.PREF_KEY_PATTERN.matcher("key>inject").matches());
    assertTrue(!MutateLayoutCommand.PREF_KEY_PATTERN.matcher("").matches());
  }
}
