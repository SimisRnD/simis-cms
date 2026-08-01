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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.presentation.controller.WidgetContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * @author claude
 * @created 7/29/26
 */
class TableWidgetTest extends WidgetBase {

  // ── execute() / rendering ─────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private static Map<String, Object> tableDataOf(WidgetContext result) {
    Object attr = result.getRequest().getAttribute("tableData");
    // The fix under test: JSTL's <c:forEach> cannot iterate a Jackson JsonNode/ArrayNode, so the
    // request attribute must be a plain Map of Lists, never the raw parsed JsonNode -- asserting
    // the concrete type here is the regression check for that crash.
    assertInstanceOf(Map.class, attr, "tableData request attribute must be a java.util.Map, not a JsonNode");
    return (Map<String, Object>) attr;
  }

  @Test
  void executeWithRealisticMultiRowMultiColumnDataDoesNotThrowAndProducesJstlIterableAttributes() {
    // Genuinely non-empty, realistic data -- not the empty placeholder -- exercising the exact
    // shape that used to crash JSTL's <c:forEach> in both table-widget.jsp and
    // table-widget-edit.jsp (tableData.get('headers') being a Jackson ArrayNode).
    preferences.put("tableData", "{\"headers\": [\"Name\", \"Age\", \"City\"], "
        + "\"rows\": [[\"Alice\", \"30\", \"Portland\"], [\"Bob\", \"25\", \"Austin\"], [\"Cara\", \"41\", \"Reno\"]]}");

    WidgetContext result = assertDoesNotThrow(() -> new TableWidget().execute(widgetContext));

    assertEquals(TableWidget.JSP, result.getJsp(), "non-edit mode should render the plain table JSP");
    Map<String, Object> tableData = tableDataOf(result);

    Object headersAttr = tableData.get("headers");
    assertInstanceOf(List.class, headersAttr, "headers must be a List<String> that <c:forEach> can iterate");
    List<String> headers = (List<String>) headersAttr;
    assertEquals(List.of("Name", "Age", "City"), headers);

    Object rowsAttr = tableData.get("rows");
    assertInstanceOf(List.class, rowsAttr, "rows must be a List<List<String>> that <c:forEach> can iterate");
    List<List<String>> rows = (List<List<String>>) rowsAttr;
    assertEquals(3, rows.size());
    assertEquals(List.of("Alice", "30", "Portland"), rows.get(0));
    assertEquals(List.of("Bob", "25", "Austin"), rows.get(1));
    assertEquals(List.of("Cara", "41", "Reno"), rows.get(2));
    // Every row must itself be a real List too, not left as a nested ArrayNode.
    rows.forEach(row -> assertInstanceOf(List.class, row));

    assertEquals("false", result.getRequest().getAttribute("isEditMode"));
  }

  @Test
  void executeInEditModeSelectsTheEditJspWhenPageEditModeAndPermissionBothHold() {
    // "on" case: the real page-level flag PageServlet publishes, plus edit permission.
    setRoles(widgetContext, CONTENT_MANAGER);
    request.setAttribute("pageEditMode", "true");
    preferences.put("tableData", "{\"headers\": [\"A\"], \"rows\": [[\"1\"]]}");

    WidgetContext result = new TableWidget().execute(widgetContext);

    assertEquals(TableWidget.EDIT_JSP, result.getJsp());
    assertEquals("true", result.getRequest().getAttribute("isEditMode"));
  }

  @Test
  void executeIgnoresPageEditModeForAContentEditorBecauseTheyCannotReachTheSaveEndpoint() {
    // content-editor holds EditorPermissionCommand.canEditContent() but is deliberately excluded
    // from canBuildLayout() -- and the Save button in table-widget-edit.jsp POSTs PageServlet's
    // "setWidgetPreferences" action, which is itself gated on canBuildLayout (see PageServlet's
    // mutateDraftLayout dispatch). Serving the editable Save UI to a content-editor here would let
    // them edit and click Save, only to have the server silently drop the request every time --
    // regression check for that permission-tier mismatch.
    setRoles(widgetContext, CONTENT_EDITOR);
    request.setAttribute("pageEditMode", "true");
    preferences.put("tableData", "{\"headers\": [\"A\"], \"rows\": [[\"1\"]]}");

    WidgetContext result = new TableWidget().execute(widgetContext);

    assertEquals(TableWidget.JSP, result.getJsp(), "content-editor cannot save this widget, so must not receive the editable Save UI");
    assertEquals("false", result.getRequest().getAttribute("isEditMode"));
  }

  @Test
  void executeIgnoresPageEditModeForAUserWithoutEditPermission() {
    // "off" case #1: the default logged-in test user (see WidgetBase.login) has no roles at all --
    // the page-level flag alone must not be sufficient to serve the editable toolbar, matching
    // every other in-place-editable widget's permission check.
    request.setAttribute("pageEditMode", "true");
    preferences.put("tableData", "{\"headers\": [\"A\"], \"rows\": [[\"1\"]]}");

    WidgetContext result = new TableWidget().execute(widgetContext);

    assertEquals(TableWidget.JSP, result.getJsp(), "without edit permission, pageEditMode=true must still render the read-only JSP");
    assertEquals("false", result.getRequest().getAttribute("isEditMode"));
  }

  @Test
  void executeIgnoresPageEditModeWhenNotLoggedIn() {
    // "off" case #2: an anonymous visitor, even if pageEditMode were somehow still set.
    logout(widgetContext);
    request.setAttribute("pageEditMode", "true");
    preferences.put("tableData", "{\"headers\": [\"A\"], \"rows\": [[\"1\"]]}");

    WidgetContext result = new TableWidget().execute(widgetContext);

    assertEquals(TableWidget.JSP, result.getJsp());
    assertEquals("false", result.getRequest().getAttribute("isEditMode"));
  }

  @Test
  void executeStaysInReadOnlyModeWhenPageEditModeRequestAttributeIsNotSet() {
    // "off" case #3: the ordinary render path -- no visual editor session at all.
    setRoles(widgetContext, CONTENT_MANAGER);
    preferences.put("tableData", "{\"headers\": [\"A\"], \"rows\": [[\"1\"]]}");

    WidgetContext result = new TableWidget().execute(widgetContext);

    assertEquals(TableWidget.JSP, result.getJsp());
    assertEquals("false", result.getRequest().getAttribute("isEditMode"));
  }

  @Test
  void executeNoLongerHonorsTheOldPerWidgetEditModePreference() {
    // Regression check for the actual bug: this widget used to gate on its own per-widget
    // "editMode" preference, which nothing in the codebase ever set, making the edit UI
    // unreachable. Even with permission, that preference alone (without the real pageEditMode
    // request attribute) must NOT select the edit JSP.
    setRoles(widgetContext, CONTENT_MANAGER);
    preferences.put("editMode", "true");
    preferences.put("tableData", "{\"headers\": [\"A\"], \"rows\": [[\"1\"]]}");

    WidgetContext result = new TableWidget().execute(widgetContext);

    assertEquals(TableWidget.JSP, result.getJsp(), "the stale per-widget editMode preference must have no effect");
    assertEquals("false", result.getRequest().getAttribute("isEditMode"));
  }

  @Test
  void executeReadsTableDataFromTheRequestObjectOverPreferences() {
    // execute() prefers a String request object (a post-back render) over the stored preference.
    preferences.put("tableData", "{\"headers\": [\"Stored\"], \"rows\": []}");
    widgetContext.setRequestObject("{\"headers\": [\"FromRequest\"], \"rows\": []}");

    WidgetContext result = new TableWidget().execute(widgetContext);

    Map<String, Object> tableData = tableDataOf(result);
    List<String> headers = (List<String>) tableData.get("headers");
    assertEquals("FromRequest", headers.get(0));
  }

  @Test
  void executeDoesNotThrowWhenTableDataPreferenceIsMissing() {
    // No "tableData" preference set at all -- null, not empty string.
    WidgetContext result = assertDoesNotThrow(() -> new TableWidget().execute(widgetContext));

    assertEquals(TableWidget.JSP, result.getJsp());
    Map<String, Object> tableData = tableDataOf(result);
    assertTrue(((List<?>) tableData.get("headers")).isEmpty());
    assertTrue(((List<?>) tableData.get("rows")).isEmpty());
  }

  @Test
  void executeDoesNotThrowWhenTableDataPreferenceIsBlank() {
    preferences.put("tableData", "");

    WidgetContext result = assertDoesNotThrow(() -> new TableWidget().execute(widgetContext));

    assertEquals(TableWidget.JSP, result.getJsp());
    Map<String, Object> tableData = tableDataOf(result);
    assertTrue(((List<?>) tableData.get("headers")).isEmpty());
    assertTrue(((List<?>) tableData.get("rows")).isEmpty());
  }

  @Test
  void executeFallsBackToAnEmptyTableWhenStoredDataIsMalformedInsteadOfThrowing() {
    // Defense in depth: malformed data that somehow reached the preference (e.g. an old backup)
    // must not blow up rendering -- it should quietly degrade to an empty table.
    preferences.put("tableData", "{\"headers\": \"not-an-array\", \"rows\": []}");

    WidgetContext result = assertDoesNotThrow(() -> new TableWidget().execute(widgetContext));

    assertEquals(TableWidget.JSP, result.getJsp());
    Map<String, Object> tableData = tableDataOf(result);
    assertTrue(((List<?>) tableData.get("headers")).isEmpty());
    assertTrue(((List<?>) tableData.get("rows")).isEmpty());
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

    Map<String, Object> tableData = tableDataOf(result);
    assertTrue(((List<?>) tableData.get("rows")).isEmpty(), "oversized stored data should render as an empty table, not the raw rows");
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
