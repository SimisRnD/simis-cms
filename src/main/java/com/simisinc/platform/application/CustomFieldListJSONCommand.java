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
import com.simisinc.platform.application.cms.FormFieldCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.CustomField;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encodes and decodes custom fields
 *
 * @author matt rajkowski
 * @created 8/9/18 3:53 PM
 */
public class CustomFieldListJSONCommand {

  private static Log LOG = LogFactory.getLog(CustomFieldListJSONCommand.class);

  public static String createJSONString(Map<String, CustomField> customFieldList) {
    if (customFieldList == null || customFieldList.isEmpty()) {
      LOG.debug("No fields found");
      return null;
    }

    StringBuilder sb = new StringBuilder();
    int count = 0;
    for (CustomField customField : customFieldList.values()) {
      if (count > 0) {
        sb.append(",");
      }
      ++count;
      boolean entryAdded = false;
      sb.append("{");
      if (StringUtils.isNotBlank(customField.getLabel())) {
        if (entryAdded) {
          sb.append(",");
        }
        sb.append("\"").append("label").append("\"").append(":").append("\"")
            .append(JsonCommand.toJson(customField.getLabel())).append("\"");
        entryAdded = true;
      }
      if (StringUtils.isNotBlank(customField.getName())) {
        if (entryAdded) {
          sb.append(",");
        }
        sb.append("\"").append("name").append("\"").append(":").append("\"")
            .append(JsonCommand.toJson(customField.getName())).append("\"");
        entryAdded = true;
      }
      if (StringUtils.isNotBlank(customField.getType())) {
        if (entryAdded) {
          sb.append(",");
        }
        sb.append("\"").append("type").append("\"").append(":").append("\"")
            .append(JsonCommand.toJson(customField.getType())).append("\"");
        entryAdded = true;
      }
      if (StringUtils.isNotBlank(customField.getValue())) {
        if (entryAdded) {
          sb.append(",");
        }
        sb.append("\"").append("value").append("\"").append(":").append("\"")
            .append(JsonCommand.toJson(customField.getValue())).append("\"");
        entryAdded = true;
      }
      if (customField.getListOfOptions() != null && !customField.getListOfOptions().isEmpty()) {
        StringBuilder listBuilder = new StringBuilder();
        for (Map.Entry<String, String> set : customField.getListOfOptions().entrySet()) {
          String name = set.getKey();
          String value = set.getValue();
          if (listBuilder.length() > 0) {
            listBuilder.append(",");
          }
          String generatedName = FormFieldCommand.generateHtmlName(value, null);
          if (!generatedName.equalsIgnoreCase(name)) {
            listBuilder.append(name + "=");
          }
          listBuilder.append(value);
        }
        if (listBuilder.length() > 0) {
          if (entryAdded) {
            sb.append(",");
          }
          sb.append("\"list\":\"").append(JsonCommand.toJson(listBuilder.toString())).append("\"");
          entryAdded = true;
        }
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

  public static Map<String, CustomField> populateFromJSONString(String jsonValue) throws SQLException {
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
      Map<String, CustomField> customFieldList = new LinkedHashMap<>();
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
        if (node.has("list")) {
          // Break into values
          Map<String, String> optionsMap = new LinkedHashMap<>();
          String[] listOfOptions = node.get("list").asText().split(",");
          for (String option : listOfOptions) {
            if (option.contains("=")) {
              String[] thisOption = option.split("=");
              optionsMap.put(FormFieldCommand.generateHtmlName(thisOption[0].trim(), null), thisOption[1].trim());
              continue;
            }
            optionsMap.put(FormFieldCommand.generateHtmlName(option.trim(), null), option.trim());
          }
          customField.setListOfOptions(optionsMap);
        }
        customFieldList.put(customField.getName(), customField);
      }
      // Store in the record
      return customFieldList;
    } catch (Exception e) {
      throw new SQLException("Could not convert from JSON", e.getMessage());
    }
  }
}
