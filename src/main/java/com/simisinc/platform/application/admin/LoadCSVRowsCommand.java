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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.simisinc.platform.application.admin.SaveDatasetRowCommand.extractValue;
import static com.simisinc.platform.application.admin.SaveDatasetRowCommand.hasOption;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/11/18 10:08 AM
 */
public class LoadCSVRowsCommand {

  private static Log LOG = LogFactory.getLog(LoadCSVRowsCommand.class);

  public static List<String[]> loadRows(Dataset dataset, int rowCountToReturn, boolean applyOptions) throws DataException {

    // Get a file handle
    File file = DatasetFileCommand.getFile(dataset);
    if (file == null) {
      return null;
    }

    // Compute some things...
    List<String> fieldOptions = dataset.getFieldOptionsList();

    // Determine the CSV configuration
    List<String[]> rows = new ArrayList<>();
    CsvParserSettings parserSettings = new CsvParserSettings();
    parserSettings.setLineSeparatorDetectionEnabled(true);
    if ("single".equals(dataset.getFileType())) {
      parserSettings.setHeaderExtractionEnabled(false);
    } else {
      parserSettings.setHeaderExtractionEnabled(true);
    }

    if (!applyOptions && rowCountToReturn > -1) {
      parserSettings.setNumberOfRecordsToRead(rowCountToReturn);
    }

    // Track some things
    Map<String, String> uniqueColumnValueMap = new HashMap<>();

    // Read the file
    int count = 0;
    CsvParser parser = new CsvParser(parserSettings);
    try (InputStream inputStream = new FileInputStream(file)) {
      parser.beginParsing(inputStream, "ISO-8859-1");
      String[] row;
      while ((row = parser.parseNext()) != null) {
        boolean skipped = false;
        if (applyOptions) {
          // See if the count to return has been reached
          if (rowCountToReturn > -1 && count >= rowCountToReturn) {
            break;
          }
          // See if this row is being skipped, based on column rules
          // @todo refactor this and SaveDatasetRowCommand options, and preview
          for (int i = 0; i < row.length; i++) {
            if (i < fieldOptions.size()) {
              String value = row[i];
              String options = fieldOptions.get(i);
              if (StringUtils.isNotBlank(options)) {
                // Check for an equals("") value (value must equal this value to be valid)
                String equalsValue = extractValue(options, "equals");
                if (equalsValue != null && !value.equalsIgnoreCase(equalsValue)) {
                  // Skip the record
                  skipped = true;
                  break;
                }
                // Check for a contains("") value (value must contain this value to be valid)
                String containsValue = extractValue(options, "contains");
                if (containsValue != null && !value.toLowerCase().contains(containsValue.toLowerCase())) {
                  // Skip the record
                  skipped = true;
                  break;
                }
                // Other checks
                if (hasOption(options, "skipDuplicates")) {
                  if (!uniqueColumnValueMap.containsKey(i + "-" + value)) {
                    uniqueColumnValueMap.put(i + "-" + value, "0");
                  } else {
//                    String storedValue = uniqueColumnValueMap.get(value);
                    // @todo condition, then
                    // Skip the record
                    skipped = true;
                    break;
                  }
                }
              }
            }
          }
        }

        // Store the values for this record
        if (!skipped) {
          ++count;
          rows.add(row);
        }
      }
      LOG.debug("Records found: " + count);
    } catch (Exception e) {
      LOG.error("CSV Error: " + e.getMessage());
      throw new DataException("File could not be read");
    } finally {
      parser.stopParsing();
    }

    return rows;
  }
}
