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
 * @author claude
 * @created 7/27/26
 */
public class TableWidget extends GenericWidget {

  static final long serialVersionUID = -6748294819228482831L;
  protected static Log LOG = LogFactory.getLog(TableWidget.class);

  static String JSP = "/cms/table-widget.jsp";
  static String EDIT_JSP = "/cms/table-widget-edit.jsp";

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
        tableData = objectMapper.readTree(tableDataJson);
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
   * Validate table data structure.
   * Ensures headers and rows are properly formatted arrays.
   */
  public static boolean isValidTableData(String jsonData) {
    try {
      JsonNode node = new ObjectMapper().readTree(jsonData);
      return node.isObject() &&
          node.has("headers") && node.get("headers").isArray() &&
          node.has("rows") && node.get("rows").isArray();
    } catch (Exception e) {
      return false;
    }
  }
}
