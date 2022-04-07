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

package com.simisinc.platform.application.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Determines 'columns' when working with datasets
 *
 * @author matt rajkowski
 * @created 5/31/18 3:13 PM
 */
public class DatasetColumnJSONCommand {

  private static Log LOG = LogFactory.getLog(DatasetColumnJSONCommand.class);

  public static String detectColumnsFromDataset(Dataset dataset) {
    // Access the file
    File file = DatasetFileCommand.getFile(dataset);
    if (file == null) {
      return null;
    }

    // Use the records path
    String recordsPathValue = dataset.getRecordsPath();
    if (StringUtils.isBlank(recordsPathValue)) {
      recordsPathValue = "/";
      dataset.setRecordsPath(recordsPathValue);
    }

    try {
      // Load the file
      JsonNode json = JsonLoader.fromFile(file);
      // Advance to the records path, if known
      String[] recordsPath = recordsPathValue.split("/");
      for (String fieldName : recordsPath) {
        if (json.has(fieldName)) {
          json = json.get(fieldName);
        }
      }
      // Find the first record in the array
      if (json.isArray()) {
        json = json.elements().next();
      }
      StringBuilder sb = new StringBuilder();
      Iterator<String> fields = json.fieldNames();
      int count = 0;
      while (fields.hasNext()) {
        ++count;
        if (count > 1) {
          sb.append(System.lineSeparator());
        }
        sb.append(fields.next());
      }
      return sb.toString();
    } catch (Exception e) {
      LOG.error("Could not detect columns from file", e);
      return null;
    }
  }

  /**
   * Populates the dataset's JSON configuration field for storing in the repository
   *
   * @param record
   * @return
   */
  public static String createColumnJSONString(Dataset record) {
    if (record.getColumnNames() == null) {
      LOG.debug("No column names");
      return null;
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < record.getColumnNames().length; i++) {
      if (i > 0) {
        sb.append(",");
      }
      String columnName = record.getColumnNames()[i];
      sb.append("{");
      sb.append("\"").append("id").append("\"").append(":").append(i + 1).append(",");
      sb.append("\"").append("column").append("\"").append(":").append("\"").append(JsonCommand.toJson(columnName)).append("\"");
      if (record.getFieldTitles() != null && i < record.getFieldTitles().length) {
        String fieldTitle = record.getFieldTitles()[i];
        if (StringUtils.isNotBlank(fieldTitle)) {
          sb.append(",");
          sb.append("\"").append("title").append("\"").append(":").append("\"").append(JsonCommand.toJson(fieldTitle)).append("\"");
        }
      }
      if (record.getFieldMappings() != null && i < record.getFieldMappings().length) {
        String fieldMapping = record.getFieldMappings()[i];
        if (StringUtils.isNotBlank(fieldMapping)) {
          sb.append(",");
          sb.append("\"").append("field").append("\"").append(":").append("\"").append(JsonCommand.toJson(fieldMapping)).append("\"");
        }
      }
      if (record.getFieldOptions() != null && i < record.getFieldOptions().length) {
        String fieldOptions = record.getFieldOptions()[i];
        if (StringUtils.isNotBlank(fieldOptions)) {
          sb.append(",");
          sb.append("\"").append("options").append("\"").append(":").append("\"").append(JsonCommand.toJson(fieldOptions)).append("\"");
        }
      }
      sb.append("}");
    }

    if (sb.length() == 0) {
      LOG.debug("No column config content");
      return null;
    }
    LOG.debug("Using: " + "[" + sb.toString() + "]");
    return "[" + sb.toString() + "]";
  }

  /**
   * Populates the dataset object values from the repository JSON configuration field
   *
   * @param record
   * @param jsonValue
   * @throws SQLException
   */
  public static void populateFromColumnConfig(Dataset record, String jsonValue) throws SQLException {
    // Convert JSON string back into values
    if (StringUtils.isBlank(jsonValue)) {
      LOG.debug("populateFromColumnConfig value is empty");
      return;
    }
    try {
      // [
      // {"column": "SSN"},
      // {"field": "custom", "column": "LanguageCode"},
      // {"field": "name", "column": "Language"},
      // {"column": "LanguageProficiencyCategory"},
      // {"column": "LanguageEvalDate", "title":"EvaluationDate", "options": "equals(\"201601\")"},
      // {"column": "EvalMethod"}
      // ]
      JsonNode config = JsonLoader.fromString(jsonValue);
      if (!config.isArray()) {
        LOG.error("populateFromColumnConfig value is not an array");
        return;
      }
      List<String> columnNamesList = new ArrayList<>();
      List<String> fieldMappingsList = new ArrayList<>();
      List<String> optionsList = new ArrayList<>();
      List<String> titlesList = new ArrayList<>();
      // Determine the values
      Iterator<JsonNode> columns = config.elements();
      while (columns.hasNext()) {
        JsonNode node = columns.next();

        String column = "";
        if (node.has("column")) {
          column = node.get("column").asText();
        }
        columnNamesList.add(column);

        // Determine if there is a title, otherwise use the column name
        String title = column;
        if (node.has("title")) {
          title = node.get("title").asText();
        }
        titlesList.add(title);

        if (node.has("field")) {
          fieldMappingsList.add(node.get("field").asText());
        } else {
          fieldMappingsList.add("");
        }

        if (node.has("options")) {
          optionsList.add(node.get("options").asText());
        } else {
          optionsList.add("");
        }
      }
      // Store in the record
      record.setColumnNames(columnNamesList.toArray(new String[0]));
      record.setFieldTitles(titlesList.toArray(new String[0]));
      record.setFieldMappings(fieldMappingsList.toArray(new String[0]));
      record.setFieldOptions(optionsList.toArray(new String[0]));
    } catch (Exception e) {
      throw new SQLException("Could not convert from JSON", e.getMessage());
    }
  }

  /**
   * Returns a plain-text string of the configuration object intended for user editing
   *
   * @param record
   * @return
   */
  public static String createPlainTextString(Dataset record) {
    if (record.getColumnNames() == null) {
      LOG.debug("No column names");
      return null;
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < record.getColumnNames().length; i++) {
      if (i > 0) {
        sb.append(System.lineSeparator());
      }
      String columnName = record.getColumnNames()[i];
      String title = record.getFieldTitles()[i];
      String field = record.getFieldMappings()[i];
      String options = record.getFieldOptions()[i];

      sb.append(columnName);
      if (StringUtils.isNotBlank(title) && !columnName.equals(title)) {
        sb.append("=").append(title);
      }
      if (StringUtils.isNotBlank(field)) {
        sb.append(" | ").append("field=").append(field);
      }
      if (StringUtils.isNotBlank(options)) {
        sb.append(" | ").append("options=").append(options);
      }
    }

    if (sb.length() == 0) {
      LOG.debug("No column config content");
      return null;
    }
    LOG.debug("Plain text string created:\n" + sb.toString());
    return sb.toString();
  }

  public static void populateFromPlainText(Dataset record, String plainText) {
    // column_name=title;field=the_field;options=the_option_string
    // column_name=title;field=the_field[0].name;options=the_option_string
    // column_name=title;field=the_field;options=the_option_string
    List<String> columnNamesList = new ArrayList<>();
    List<String> fieldMappingsList = new ArrayList<>();
    List<String> optionsList = new ArrayList<>();
    List<String> titlesList = new ArrayList<>();

    // Convert the text string back into values
    if (StringUtils.isNotBlank(plainText)) {
      try (Scanner scanner = new Scanner(plainText)) {
        while (scanner.hasNextLine()) {
          String line = scanner.nextLine();
          LOG.debug("Scanner has new line: " + line);

          // Process the line
          String[] elements = line.split(Pattern.quote("|"));

          // Determine the column id, mapped field, and options
          String column = "";
          String title = "";
          String field = "";
          String options = "";
          int count = -1;
          for (String value : elements) {
            ++count;
            if (count == 0) {
              String[] columnInfo = value.split(Pattern.quote("="));
              column = value.trim();
              if (columnInfo.length > 1) {
                title = columnInfo[1].trim();
              }
              continue;
            }
            if (value.trim().startsWith("field=")) {
              field = value.substring(value.indexOf("field=") + "field=".length()).trim();
            } else if (value.trim().startsWith("options=")) {
              options = value.substring(value.indexOf("options=") + "options=".length()).trim();
            }
          }

          // Set the values
          columnNamesList.add(column);
          titlesList.add(title);
          fieldMappingsList.add(field);
          optionsList.add(options);
        }
      }
    }

    // Store in the record
    record.setColumnCount(columnNamesList.size());
    record.setColumnNames(columnNamesList.toArray(new String[0]));
    record.setFieldTitles(titlesList.toArray(new String[0]));
    record.setFieldMappings(fieldMappingsList.toArray(new String[0]));
    record.setFieldOptions(optionsList.toArray(new String[0]));
  }

  public static boolean plainTextIsEqual(String text1, String text2) {
    if (text1 == null || text2 == null) {
      return false;
    }
    return normalizeLineEnds(text1).equals(normalizeLineEnds(text2));
  }

  private static String normalizeLineEnds(String s) {
    return s.replace("\r\n", "\n").replace('\r', '\n');
  }
}


