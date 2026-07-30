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
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * P5.4: Data Table Widget — Inline-editable table for structured data
 *
 * Renders accessible tables with proper <th scope> and ARIA labels.
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

    // Check if in edit mode
    boolean isEditMode = "true".equals(context.getPreferences().get("editMode"));

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

      // Store parsed data for JSP rendering
      context.getRequest().setAttribute("tableData", tableData);
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
