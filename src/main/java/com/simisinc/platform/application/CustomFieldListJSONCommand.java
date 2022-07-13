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

package com.simisinc.platform.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.CustomField;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Encodes and decodes custom fields
 *
 * @author matt rajkowski
 * @created 8/9/18 3:53 PM
 */
public class CustomFieldListJSONCommand {

  private static Log LOG = LogFactory.getLog(CustomFieldListJSONCommand.class);

  public static String createJSONString(List<CustomField> customFieldList) {
    if (customFieldList == null || customFieldList.isEmpty()) {
      LOG.debug("No fields found");
      return null;
    }

    StringBuilder sb = new StringBuilder();
    int count = 0;
    for (CustomField customField : customFieldList) {
      if (count > 0) {
        sb.append(",");
      }
      ++count;
      boolean entryAdded = false;
      sb.append("{");
      if (StringUtils.isNotBlank(customField.getName())) {
        sb.append("\"").append("name").append("\"").append(":").append("\"").append(JsonCommand.toJson(customField.getName())).append("\"");
        entryAdded = true;
      }
      if (StringUtils.isNotBlank(customField.getLabel())) {
        if (entryAdded) {
          sb.append(",");
        }
        sb.append("\"").append("label").append("\"").append(":").append("\"").append(JsonCommand.toJson(customField.getLabel())).append("\"");
        entryAdded = true;
      }
      if (StringUtils.isNotBlank(customField.getType())) {
        if (entryAdded) {
          sb.append(",");
        }
        sb.append("\"").append("type").append("\"").append(":").append("\"").append(JsonCommand.toJson(customField.getType())).append("\"");
        entryAdded = true;
      }
      if (StringUtils.isNotBlank(customField.getValue())) {
        if (entryAdded) {
          sb.append(",");
        }
        sb.append("\"").append("value").append("\"").append(":").append("\"").append(JsonCommand.toJson(customField.getValue())).append("\"");
        entryAdded = true;
      }
      sb.append("}");
    }
    if (sb.length() == 0) {
      LOG.debug("No values found");
      return null;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Using: " + "[" + sb.toString() + "]");
    }
    return "[" + sb.toString() + "]";
  }

  public static List<CustomField> populateFromJSONString(String jsonValue) throws SQLException {
    // Convert JSON string back into values
    if (StringUtils.isBlank(jsonValue)) {
      LOG.debug("populateFromJSONString value is empty");
      return null;
    }
    try {
      JsonNode config = JsonLoader.fromString(jsonValue);
      if (!config.isArray()) {
        LOG.error("populateFromJSONString value is not an array");
        return null;
      }

      // Determine the values
      List<CustomField> customFieldList = new ArrayList<>();
      Iterator<JsonNode> fields = config.elements();
      while (fields.hasNext()) {
        JsonNode node = fields.next();
        CustomField customField = new CustomField();
        if (node.has("name")) {
          customField.setName(node.get("name").asText());
        }
        if (node.has("label")) {
          customField.setLabel(node.get("label").asText());
        } else {
          // Use the name if not found
          customField.setLabel(customField.getName());
        }
        if (node.has("type")) {
          customField.setType(node.get("type").asText());
        }
        if (node.has("value")) {
          customField.setValue(node.get("value").asText());
        }
        customFieldList.add(customField);
      }
      // Store in the record
      return customFieldList;
    } catch (Exception e) {
      throw new SQLException("Could not convert from JSON", e.getMessage());
    }
  }
}
