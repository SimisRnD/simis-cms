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

package com.simisinc.platform.application.datasets;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.datasets.Dataset;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

import static com.simisinc.platform.application.datasets.DatasetFieldOptionCommand.applyOptionsToField;
import static com.simisinc.platform.application.datasets.DatasetFieldOptionCommand.isSkipped;

/**
 * Reads in dataset rows from a JSON dataset file
 *
 * @author matt rajkowski
 * @created 1/28/20 1:08 PM
 */
public class LoadJsonCommand {

  private static Log LOG = LogFactory.getLog(LoadJsonCommand.class);

  public static List<String[]> loadRecords(Dataset dataset, int maxRowCountToReturn, boolean applyOptions)
      throws DataException {
    File file = DatasetFileCommand.getFile(dataset);
    if (file == null) {
      throw new DataException("Dataset file not found");
    }
    return loadRecords(dataset, file, maxRowCountToReturn, applyOptions);
  }

  private static List<String[]> loadRecords(Dataset dataset, File file, int maxRowCountToReturn, boolean applyOptions)
      throws DataException {
    List<String[]> rows = new ArrayList<>();
    if (file == null) {
      return rows;
    }
    try {
      // JSON requires columns... or just show all fields
      if (dataset.getColumnNamesList() == null) {
        LOG.debug("No columns specified");
        return rows;
      }

      // Verify there is a records path ("/path")
      if (StringUtils.isBlank(dataset.getRecordsPath()) || !dataset.getRecordsPath().startsWith("/")) {
        LOG.debug("No records path set");
        return rows;
      }

      // Load the file
      JsonNode json = JsonLoader.fromFile(file);

      // Find the start of the records
      String[] recordsPath = dataset.getRecordsPath().split("/");
      for (String fieldName : recordsPath) {
        if (json.has(fieldName)) {
          json = json.get(fieldName);
        }
      }
      if (!json.isArray()) {
        LOG.debug("JSON array not found");
        return rows;
      }

      // Compute some things...
      List<String> fieldOptions = dataset.getFieldOptionsList();

      // Track some things
      Map<String, String> uniqueColumnValueMap = new HashMap<>();

      // Process the records
      Iterator<JsonNode> records = json.elements();
      int count = 0;
      while (records.hasNext()
          && (maxRowCountToReturn == -1 || (maxRowCountToReturn > -1 && count < maxRowCountToReturn))) {

        // Determine if the row meets the criteria
        JsonNode thisRecord = records.next();

        // Process the row's fields
        List<String> row = new ArrayList<>();

        int columnCount = -1;
        for (String column : dataset.getColumnNamesList()) {
          ++columnCount;

          // The column could be renamed, so acquire just the definition part
          String[] columnInfo = column.split(Pattern.quote("="));
          if (columnInfo.length > 1) {
            column = columnInfo[0].trim();
          }

          // Determine if the column spec has a path to a deeper value
          String[] fieldPath = column.split(Pattern.quote("."));
          JsonNode fieldPointer = null;
          for (String fieldName : fieldPath) {
            // Remove the array index from the field name, if it is specified
            int arrayValue = -1;
            if (fieldName.contains("[") && fieldName.contains("]")) {
              int startIdx = fieldName.indexOf("[");
              int endIdx = fieldName.indexOf("]", startIdx);
              if (startIdx > -1 && endIdx > -1) {
                arrayValue = Integer.parseInt(fieldName.substring(startIdx + 1, endIdx));
                fieldName = fieldName.substring(0, startIdx);
              }
            }

            // Access the field
            if (fieldPointer != null) {
              if (fieldPointer.has(fieldName)) {
                fieldPointer = fieldPointer.get(fieldName);
              } else {
                // The pointer was not found so use an empty node value
                fieldPointer = null;
                break;
              }
            } else if (thisRecord.has(fieldName)) {
              fieldPointer = thisRecord.get(fieldName);
            }

            // Get the object in the array, if it is specified
            if (arrayValue > -1 && fieldPointer != null && fieldPointer.isArray()) {
              fieldPointer = fieldPointer.get(arrayValue);
            }
          }

          // Retrieve the field's value
          String nodeValue = "";
          if (fieldPointer != null) {
            if (fieldPointer.isContainerNode()) {
              // Use the container content
              nodeValue = fieldPointer.toString();
            } else if (fieldPointer.isValueNode()) {
              // Use the value as a string
              nodeValue = fieldPointer.asText();
            }
          }
          // Simplify the value
          if (nodeValue == null) {
            nodeValue = "";
          } else {
            nodeValue = nodeValue.trim();
          }
          // Apply options to the field's value
          if (applyOptions) {
            String options = fieldOptions.get(columnCount);
            nodeValue = applyOptionsToField(options, nodeValue);
          }
          row.add(nodeValue);
        }

        // See if this row is being skipped, based on column rules
        boolean skipped = false;
        if (applyOptions) {
          for (int i = 0; i < row.size(); i++) {
            if (i < fieldOptions.size()) {
              String value = row.get(i);
              String options = fieldOptions.get(i);
              if (isSkipped(options, value, uniqueColumnValueMap, i)) {
                skipped = true;
                break;
              }
            }
          }
        }

        // Store the values for this record
        if (!skipped) {
          ++count;
          rows.add(row.toArray(new String[0]));
        }
      }
      LOG.debug("Records found: " + count);
    } catch (Exception e) {
      LOG.error("Json Error: " + e.getMessage());
      throw new DataException("File could not be read");
    }
    return rows;
  }

  public static int retrieveRowCount(Dataset dataset) {
    // Verify there is a records path ("/path")
    if (StringUtils.isBlank(dataset.getRecordsPath()) || !dataset.getRecordsPath().startsWith("/")) {
      LOG.debug("No records path set");
      return -1;
    }
    // Access the file
    File file = DatasetFileCommand.getFile(dataset);
    if (file == null) {
      LOG.warn("File not found for dataset: " + dataset.getId());
      return -1;
    }
    // Determine the row count
    try {
      String[] recordsPath = dataset.getRecordsPath().split("/");
      // Load the file
      JsonNode json = JsonLoader.fromFile(file);
      // Find the start of the records
      for (String fieldName : recordsPath) {
        if (json.has(fieldName)) {
          json = json.get(fieldName);
        }
      }
      if (json.isArray()) {
        return json.size();
      }
    } catch (Exception e) {
      LOG.error("Json Error: " + e.getMessage());
    }
    return -1;
  }
}
