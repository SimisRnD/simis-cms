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

package com.simisinc.platform.presentation.widgets.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.fasterxml.jackson.databind.JsonNode;
import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.junit.jupiter.api.Test;

/**
 * @author claude
 * @created 7/29/26
 */
class TableWidgetTest extends WidgetBase {

  // ── execute() / rendering ─────────────────────────────────────────────────

  @Test
  void executeWithValidTableDataRendersTheParsedTable() {
    preferences.put("tableData", "{\"headers\": [\"Name\", \"Age\"], \"rows\": [[\"Alice\", \"30\"], [\"Bob\", \"25\"]]}");

    WidgetContext result = new TableWidget().execute(widgetContext);

    assertEquals(TableWidget.JSP, result.getJsp(), "non-edit mode should render the plain table JSP");
    JsonNode tableData = (JsonNode) result.getRequest().getAttribute("tableData");
    assertEquals(2, tableData.get("headers").size());
    assertEquals("Name", tableData.get("headers").get(0).asText());
    assertEquals(2, tableData.get("rows").size());
    assertEquals("Bob", tableData.get("rows").get(1).get(0).asText());
    assertEquals("false", result.getRequest().getAttribute("isEditMode"));
  }

  @Test
  void executeInEditModeSelectsTheEditJsp() {
    preferences.put("editMode", "true");
    preferences.put("tableData", "{\"headers\": [\"A\"], \"rows\": [[\"1\"]]}");

    WidgetContext result = new TableWidget().execute(widgetContext);

    assertEquals(TableWidget.EDIT_JSP, result.getJsp());
    assertEquals("true", result.getRequest().getAttribute("isEditMode"));
  }

  @Test
  void executeReadsTableDataFromTheRequestObjectOverPreferences() {
    // execute() prefers a String request object (a post-back render) over the stored preference.
    preferences.put("tableData", "{\"headers\": [\"Stored\"], \"rows\": []}");
    widgetContext.setRequestObject("{\"headers\": [\"FromRequest\"], \"rows\": []}");

    WidgetContext result = new TableWidget().execute(widgetContext);

    JsonNode tableData = (JsonNode) result.getRequest().getAttribute("tableData");
    assertEquals("FromRequest", tableData.get("headers").get(0).asText());
  }

  @Test
  void executeDoesNotThrowWhenTableDataPreferenceIsMissing() {
    // No "tableData" preference set at all -- null, not empty string.
    WidgetContext result = assertDoesNotThrow(() -> new TableWidget().execute(widgetContext));

    assertEquals(TableWidget.JSP, result.getJsp());
    JsonNode tableData = (JsonNode) result.getRequest().getAttribute("tableData");
    assertTrue(tableData.get("headers").isEmpty());
    assertTrue(tableData.get("rows").isEmpty());
  }

  @Test
  void executeDoesNotThrowWhenTableDataPreferenceIsBlank() {
    preferences.put("tableData", "");

    WidgetContext result = assertDoesNotThrow(() -> new TableWidget().execute(widgetContext));

    assertEquals(TableWidget.JSP, result.getJsp());
    JsonNode tableData = (JsonNode) result.getRequest().getAttribute("tableData");
    assertTrue(tableData.get("headers").isEmpty());
    assertTrue(tableData.get("rows").isEmpty());
  }

  @Test
  void executeFallsBackToAnEmptyTableWhenStoredDataIsMalformedInsteadOfThrowing() {
    // Defense in depth: malformed data that somehow reached the preference (e.g. an old backup)
    // must not blow up rendering -- it should quietly degrade to an empty table.
    preferences.put("tableData", "{\"headers\": \"not-an-array\", \"rows\": []}");

    WidgetContext result = assertDoesNotThrow(() -> new TableWidget().execute(widgetContext));

    assertEquals(TableWidget.JSP, result.getJsp());
    JsonNode tableData = (JsonNode) result.getRequest().getAttribute("tableData");
    assertTrue(tableData.get("headers").isEmpty());
    assertTrue(tableData.get("rows").isEmpty());
  }

  @Test
  void executeFallsBackToAnEmptyTableWhenStoredDataExceedsTheRowLimit() {
    StringBuilder rows = new StringBuilder();
    for (int i = 0; i < TableWidget.MAX_ROWS + 1; i++) {
      if (i > 0) {
        rows.append(",");
      }
      rows.append("[\"x\"]");
    }
    preferences.put("tableData", "{\"headers\": [\"A\"], \"rows\": [" + rows + "]}");

    WidgetContext result = assertDoesNotThrow(() -> new TableWidget().execute(widgetContext));

    JsonNode tableData = (JsonNode) result.getRequest().getAttribute("tableData");
    assertTrue(tableData.get("rows").isEmpty(), "oversized stored data should render as an empty table, not the raw rows");
  }

  // ── isValidTableData(): shape ─────────────────────────────────────────────

  @Test
  void isValidTableDataAcceptsAWellFormedTable() {
    assertTrue(TableWidget.isValidTableData("{\"headers\": [\"Name\", \"Age\"], \"rows\": [[\"Alice\", \"30\"]]}"));
  }

  @Test
  void isValidTableDataAcceptsEmptyHeadersAndRows() {
    assertTrue(TableWidget.isValidTableData("{\"headers\": [], \"rows\": []}"));
  }

  @Test
  void isValidTableDataRejectsNull() {
    assertFalse(TableWidget.isValidTableData(null));
  }

  @Test
  void isValidTableDataRejectsNonJson() {
    assertFalse(TableWidget.isValidTableData("not json at all"));
  }

  @Test
  void isValidTableDataRejectsAJsonArrayAtTheTopLevel() {
    assertFalse(TableWidget.isValidTableData("[\"headers\", \"rows\"]"));
  }

  @Test
  void isValidTableDataRejectsMissingHeaders() {
    assertFalse(TableWidget.isValidTableData("{\"rows\": []}"));
  }

  @Test
  void isValidTableDataRejectsMissingRows() {
    assertFalse(TableWidget.isValidTableData("{\"headers\": []}"));
  }

  @Test
  void isValidTableDataRejectsHeadersOfTheWrongType() {
    assertFalse(TableWidget.isValidTableData("{\"headers\": \"Name,Age\", \"rows\": []}"));
  }

  @Test
  void isValidTableDataRejectsRowsOfTheWrongType() {
    assertFalse(TableWidget.isValidTableData("{\"headers\": [], \"rows\": {}}"));
  }

  @Test
  void isValidTableDataRejectsANonArrayRowEntry() {
    assertFalse(TableWidget.isValidTableData("{\"headers\": [\"A\"], \"rows\": [\"not-a-row\"]}"));
  }

  @Test
  void isValidTableDataRejectsANestedObjectAsACell() {
    // A cell must be a scalar; a nested object/array would smuggle unbounded structure past the
    // row/column/length checks below.
    assertFalse(TableWidget.isValidTableData("{\"headers\": [\"A\"], \"rows\": [[{\"nested\": \"object\"}]]}"));
  }

  @Test
  void isValidTableDataRejectsANestedArrayAsAHeader() {
    assertFalse(TableWidget.isValidTableData("{\"headers\": [[\"nested\"]], \"rows\": []}"));
  }

  // ── isValidTableData(): size limits ───────────────────────────────────────

  @Test
  void isValidTableDataRejectsTooManyRows() {
    String rows = buildRows(TableWidget.MAX_ROWS + 1);
    assertFalse(TableWidget.isValidTableData("{\"headers\": [\"A\"], \"rows\": [" + rows + "]}"));
  }

  @Test
  void isValidTableDataAcceptsExactlyTheMaximumRowCount() {
    String rows = buildRows(TableWidget.MAX_ROWS);
    assertTrue(TableWidget.isValidTableData("{\"headers\": [\"A\"], \"rows\": [" + rows + "]}"));
  }

  @Test
  void isValidTableDataRejectsTooManyColumnsInHeaders() {
    String headers = buildHeaders(TableWidget.MAX_COLUMNS + 1);
    assertFalse(TableWidget.isValidTableData("{\"headers\": [" + headers + "], \"rows\": []}"));
  }

  @Test
  void isValidTableDataRejectsTooManyColumnsInARow() {
    String cells = buildHeaders(TableWidget.MAX_COLUMNS + 1);
    assertFalse(TableWidget.isValidTableData("{\"headers\": [], \"rows\": [[" + cells + "]]}"));
  }

  @Test
  void isValidTableDataRejectsACellLongerThanTheMax() {
    String tooLong = "x".repeat(TableWidget.MAX_CELL_LENGTH + 1);
    assertFalse(TableWidget.isValidTableData("{\"headers\": [\"A\"], \"rows\": [[\"" + tooLong + "\"]]}"));
  }

  @Test
  void isValidTableDataAcceptsACellAtExactlyTheMaxLength() {
    String exact = "x".repeat(TableWidget.MAX_CELL_LENGTH);
    assertTrue(TableWidget.isValidTableData("{\"headers\": [\"A\"], \"rows\": [[\"" + exact + "\"]]}"));
  }

  @Test
  void isValidTableDataRejectsRawJsonLongerThanTheMaxBeforeEvenParsingIt() {
    // Padded with filler well past MAX_JSON_LENGTH; doesn't need to be valid JSON since the length
    // gate runs before parsing is attempted.
    String oversized = "x".repeat(TableWidget.MAX_JSON_LENGTH + 1);
    assertFalse(TableWidget.isValidTableData(oversized));
  }

  private static String buildRows(int count) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < count; i++) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append("[\"x\"]");
    }
    return sb.toString();
  }

  private static String buildHeaders(int count) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < count; i++) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append("\"h").append(i).append("\"");
    }
    return sb.toString();
  }
}
