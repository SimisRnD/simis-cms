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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.application.cms.EditorPermissionCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P5.4: Data Table Widget — Inline-editable table for structured data
 *
 * Renders accessible tables with proper {@code <th scope>} and ARIA labels.
 * In edit mode, cells are click-to-edit with keyboard navigation.
 * Table data stored as JSON: {headers: [...], rows: [[...], ...]}
 *
 * <p>{@code tableData} is attacker-reachable content: it is written by
 * {@link com.simisinc.platform.application.cms.MutateLayoutCommand#setWidgetPreferences} /
 * {@code #addWidget} (the visual editor's preference-save path, reached from PageServlet's
 * {@code setWidgetPreferences} / {@code addWidget} actions) and persisted into the page's draft XML.
 * {@link #isValidTableData(String)} is the shape/size gate for that save path -- it must reject
 * malformed or oversized data there, before it is ever written. The check in {@link #execute} below
 * is defense in depth only, in case bad data reaches render some other way (an old backup, a future
 * save path that forgets to validate, direct DB edits, etc.).
 *
 * @author claude
 * @created 7/27/26
 */
public class TableWidget extends GenericWidget {

  static final long serialVersionUID = -6748294819228482831L;
  protected static Log LOG = LogFactory.getLog(TableWidget.class);

  static String JSP = "/cms/table-widget.jsp";
  static String EDIT_JSP = "/cms/table-widget-edit.jsp";

  /**
   * This widget's registered name in widget-library.xml. Used by MutateLayoutCommand to know when a
   * "tableData" preference value belongs to this widget and needs {@link #isValidTableData}.
   */
  public static final String WIDGET_NAME = "dataTable";

  /**
   * Raw JSON string length allowed before it is even parsed -- a cheap first guard against
   * pathological payloads, far above any realistic page-content table (checked ahead of the
   * structural limits below so an oversized payload never reaches the JSON parser).
   */
  public static final int MAX_JSON_LENGTH = 500_000;

  /**
   * Maximum number of data rows. This is page content edited by hand in a CMS, not a data
   * import/export feature -- a few hundred rows is already a very large table to maintain this way.
   */
  public static final int MAX_ROWS = 500;

  /**
   * Maximum number of columns (and header cells). Page tables built with Foundation-style layouts
   * realistically use a handful of columns; 50 is generous headroom while still bounding worst-case
   * render cost and preventing a single "row" from smuggling an unbounded array.
   */
  public static final int MAX_COLUMNS = 50;

  /**
   * Maximum length of any single header or cell's text. Mirrors the VARCHAR(255)-class limits used
   * for short user-facing text fields elsewhere (see
   * {@code ContentReviewCommand.MAX_RELEASE_REFERENCE_LENGTH}), loosened somewhat since a table cell
   * may reasonably hold a full sentence rather than just a label.
   */
  public static final int MAX_CELL_LENGTH = 1000;

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public WidgetContext execute(WidgetContext context) {

    // Check if in edit mode. The gate is two parts, deliberately: "pageEditMode" is the real,
    // established page-level flag PageServlet.java computes from the session's pageEditMode flag
    // AND EditorPermissionCommand.canEditContent, then publishes as a request attribute for every
    // widget on the page to read -- unlike a per-widget preference, nothing ever needs to "set" it
    // for this widget specifically, so the editable toolbar is actually reachable through the
    // normal "Edit" toggle every other widget uses. (ItemsListWidget also reads this same
    // "pageEditMode" attribute, but is not a permission-parity precedent to follow: its isEditMode
    // ORs in a raw, unchecked "editMode" request parameter with no permission check at all, a
    // separate pre-existing gap in that widget, not replicated here.)
    //
    // The permission check is repeated here on top of that rather than trusted to have already
    // been applied: relying solely on the page-level flag would mean a stored/cached response, or
    // any future caller of this widget that forgets to route through PageServlet, could serve the
    // editable toolbar -- including its structural add/remove controls -- to a visitor without
    // edit rights. Checking again here costs nothing and closes that gap the same way every other
    // content widget's render path does.
    //
    // This must check canBuildLayout(), not canEditContent(): the editable UI below includes a
    // Save button that POSTs PageServlet's "setWidgetPreferences" action, and that action (see
    // PageServlet's mutateDraftLayout dispatch) is itself gated on
    // pageEditMode && EditorPermissionCommand.canBuildLayout(userSession) -- content-editor holds
    // canEditContent but is deliberately excluded from canBuildLayout (see that method's javadoc:
    // "authors get content guardrails, designers get the canvas"). Gating on canEditContent here
    // would show a content-editor the full editable Save UI, but every Save click would then be
    // silently dropped server-side (PageServlet falls through to a normal page render instead of
    // the JSON response the client expects), discarding the user's edits with a confusing
    // "Error: HTTP 200" instead of ever persisting them. canBuildLayout keeps this widget's
    // reachable-editor tier the same as the tier that can actually save it, matching the
    // "⚙ Prefs" panel this Save button follows the pattern of -- platform-editor.js only ever
    // inserts that panel's controls when layoutMode (its own canBuildLayout-derived flag) is true.
    boolean pageEditMode = "true".equals(context.getRequest().getAttribute("pageEditMode"));
    boolean isEditMode = pageEditMode && EditorPermissionCommand.canBuildLayout(context.getUserSession());

    try {
      // Parse table data from content or create default
      String tableDataJson = context.getRequestObject() instanceof String ?
          (String) context.getRequestObject() :
          context.getPreferences().get("tableData");

      JsonNode tableData;
      if (tableDataJson != null && !tableDataJson.isEmpty()) {
        // Defense in depth: the save path validates before persisting (see class javadoc), but guard
        // render too rather than trust that every possible source of this value did so.
        if (!isValidTableData(tableDataJson)) {
          LOG.warn("Ignoring malformed or oversized table data at render time");
          tableData = objectMapper.readTree("{\"headers\": [], \"rows\": []}");
        } else {
          tableData = objectMapper.readTree(tableDataJson);
        }
      } else {
        tableData = objectMapper.readTree("{\"headers\": [], \"rows\": []}");
      }

      // Store parsed data for JSP rendering as plain Java collections, never as the raw Jackson
      // JsonNode/ArrayNode -- JSTL's <c:forEach> only knows how to iterate a Collection, array,
      // java.util.Iterator, Map, or String (see jakarta's ForEachSupport). JsonNode/ArrayNode is
      // none of those -- it implements Iterable<JsonNode>, which looks iterable in plain Java but
      // is not one of the types JSTL's tag actually dispatches on -- so handing it to <c:forEach>
      // throws JspTagException("Don't know how to iterate over supplied \"items\"") for any
      // non-empty table, a crash PageServlet's outer catch silently turns into an empty HTTP 200.
      Map<String, Object> tableViewModel = toViewModel(tableData);
      context.getRequest().setAttribute("tableData", tableViewModel);
      context.getRequest().setAttribute("isEditMode", String.valueOf(isEditMode));

      // Select appropriate JSP
      if (isEditMode) {
        context.setJsp(EDIT_JSP);
      } else {
        context.setJsp(JSP);
      }

    } catch (Exception e) {
      LOG.error("Error processing table widget: " + e.getMessage(), e);
      context.setErrorMessage("Error rendering table: " + e.getMessage());
    }

    return context;
  }

  /**
   * Converts the parsed table JSON into plain Java collections that JSTL's {@code <c:forEach>} can
   * actually iterate: {@code headers} becomes a {@code List<String>}, {@code rows} becomes a
   * {@code List<List<String>>}. See the comment at the {@link #execute} call site for why the raw
   * {@link JsonNode} must never reach the JSP directly.
   */
  private static Map<String, Object> toViewModel(JsonNode tableData) {
    List<String> headers = new ArrayList<>();
    for (JsonNode header : tableData.path("headers")) {
      headers.add(header.asText());
    }
    List<List<String>> rows = new ArrayList<>();
    for (JsonNode row : tableData.path("rows")) {
      List<String> rowValues = new ArrayList<>();
      for (JsonNode cell : row) {
        rowValues.add(cell.asText());
      }
      rows.add(rowValues);
    }
    Map<String, Object> viewModel = new LinkedHashMap<>();
    viewModel.put("headers", headers);
    viewModel.put("rows", rows);
    return viewModel;
  }

  /**
   * Validate table data structure and size before it is trusted -- called from the save path
   * (MutateLayoutCommand) to reject bad data before it is persisted, and from {@link #execute} as a
   * defense-in-depth check before rendering.
   *
   * <p>Ensures {@code headers} and {@code rows} are properly formatted arrays, within
   * {@link #MAX_ROWS} / {@link #MAX_COLUMNS}, and that every header/cell is a scalar JSON value
   * (string/number/boolean/null) no longer than {@link #MAX_CELL_LENGTH} -- rejecting nested
   * objects/arrays as cell values, which bounds effective JSON depth as well as overall size.
   */
  public static boolean isValidTableData(String jsonData) {
    if (jsonData == null || jsonData.length() > MAX_JSON_LENGTH) {
      return false;
    }
    try {
      JsonNode node = new ObjectMapper().readTree(jsonData);
      if (!node.isObject() ||
          !node.has("headers") || !node.get("headers").isArray() ||
          !node.has("rows") || !node.get("rows").isArray()) {
        return false;
      }

      JsonNode headers = node.get("headers");
      if (headers.size() > MAX_COLUMNS || !allCellsValid(headers)) {
        return false;
      }

      JsonNode rows = node.get("rows");
      if (rows.size() > MAX_ROWS) {
        return false;
      }
      for (JsonNode row : rows) {
        if (!row.isArray() || row.size() > MAX_COLUMNS || !allCellsValid(row)) {
          return false;
        }
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * True if every element of {@code arrayNode} (a header or row array) is a JSON scalar within
   * {@link #MAX_CELL_LENGTH} characters. A nested object/array as a "cell" is rejected outright.
   */
  private static boolean allCellsValid(JsonNode arrayNode) {
    for (JsonNode cell : arrayNode) {
      if (!cell.isValueNode() || cell.asText().length() > MAX_CELL_LENGTH) {
        return false;
      }
    }
    return true;
  }
}
