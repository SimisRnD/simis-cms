/*
 * Copyright 2023 SimIS Inc. (https://www.simiscms.com)
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

import static com.simisinc.platform.application.datasets.DatasetFieldOptionCommand.applyOptionsToField;
import static com.simisinc.platform.application.datasets.DatasetFieldOptionCommand.isSkipped;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.univocity.parsers.tsv.TsvParser;
import com.univocity.parsers.tsv.TsvParserSettings;

/**
 * Reads in dataset rows from a TSV dataset file
 *
 * @author matt rajkowski
 * @created 11/28/23 8:58 PM
 */
public class LoadTSVRowsCommand {

  private static Log LOG = LogFactory.getLog(LoadTSVRowsCommand.class);

  public static List<String[]> loadRows(Dataset dataset, int rowCountToReturn, boolean applyOptions)
      throws DataException {

    // Get a file handle
    File file = DatasetFileCommand.getFile(dataset);
    if (file == null) {
      return null;
    }

    // Compute some things...
    List<String> fieldOptions = dataset.getFieldOptionsList();

    // Determine the TSV configuration
    List<String[]> rows = new ArrayList<>();
    TsvParserSettings parserSettings = new TsvParserSettings();
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
    TsvParser parser = new TsvParser(parserSettings);
    try (InputStream inputStream = new FileInputStream(file)) {
      parser.beginParsing(inputStream, "ISO-8859-1");
      String[] row;
      while ((row = parser.parseNext()) != null) {
        // See if the count to return has been reached
        if (rowCountToReturn > -1 && count >= rowCountToReturn) {
          break;
        }
        // See if this row is being skipped, based on column rules
        boolean skipped = false;
        if (applyOptions) {
          for (int i = 0; i < row.length; i++) {
            if (i < fieldOptions.size()) {
              String value = row[i];
              String options = fieldOptions.get(i);
              // See if this row is being skipped, based on column rules
              if (isSkipped(options, value, uniqueColumnValueMap, i)) {
                skipped = true;
                break;
              }
              // Apply options to the field's value
              row[i] = applyOptionsToField(options, value);
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
      LOG.error("TSV Error: " + e.getMessage());
      throw new DataException("File could not be read");
    } finally {
      parser.stopParsing();
    }

    return rows;
  }
}
