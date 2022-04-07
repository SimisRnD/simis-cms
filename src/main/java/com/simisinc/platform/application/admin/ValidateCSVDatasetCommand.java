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
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

/**
 * Checks and processes CSV files
 *
 * @author matt rajkowski
 * @created 4/25/18 9:05 AM
 */
public class ValidateCSVDatasetCommand {

  private static Log LOG = LogFactory.getLog(ValidateCSVDatasetCommand.class);

  public static void checkFile(Dataset datasetBean) throws DataException {

    // Get a file handle
    File datasetFile = DatasetFileCommand.getFile(datasetBean);
    if (datasetFile == null) {
      return;
    }

    // Check mime type
    // text/csv
    // text/plain
    // application/vnd.ms-excel
    String mimeType = null;
    try {
      mimeType = Files.probeContentType(datasetFile.toPath());
    } catch (Exception e) {
      LOG.error("Error checking file for data", e);
    }
    if (mimeType == null) {
      String extension = FilenameUtils.getExtension(datasetFile.getAbsolutePath());
      if ("csv".equalsIgnoreCase(extension)) {
        mimeType = "text/csv";
      } else if ("txt".equalsIgnoreCase(extension)) {
        mimeType = "text/plain";
      } else {
        throw new DataException("Could not determine type");
      }
    }
    LOG.debug("MimeType: " + mimeType);
    datasetBean.setFileType(mimeType);

    // Read the CSV file for columns and row counts
    CsvParserSettings parserSettings = new CsvParserSettings();
    parserSettings.setLineSeparatorDetectionEnabled(true);
    boolean isSingleColumn = false;
    String[] headerRow = null;
    int rowCount = 0;
    CsvParser parser = new CsvParser(parserSettings);
    try (InputStream inputStream = new FileInputStream(datasetFile)) {
//      Reader reader = new InputStreamReader(inputStream);
//      parser.beginParsing(reader);
      parser.beginParsing(inputStream, "ISO-8859-1");
      String[] row;
      boolean firstLineFound = false;
      while ((row = parser.parseNext()) != null) {
        if (!firstLineFound) {
          firstLineFound = true;
          if (row.length == 1) {
            isSingleColumn = true;
            ++rowCount;
          } else {
            headerRow = row;
          }
        } else {
          ++rowCount;
        }
      }
    } catch (Exception e) {
      LOG.error("CSV Error: " + e.getMessage());
      throw new DataException("File type is incorrect");
    } finally {
      parser.stopParsing();
    }
    datasetBean.setRowCount(rowCount);

    // Determine the configuration
    if (isSingleColumn || headerRow == null) {
      datasetBean.setFileType("text/csv;single");
      datasetBean.setColumnCount(1);
      datasetBean.setColumnNames(new String[]{"Item Name"});
    } else {
//      datasetBean.setFileType(datasetBean.getFileType());
      datasetBean.setColumnCount(headerRow.length);
      datasetBean.setColumnNames(headerRow);
      datasetBean.setFieldTitles(headerRow);
    }
  }

  public static boolean validateAllRows(Dataset dataset) throws DataException {

    // Access the file
    File dataFile = DatasetFileCommand.getFile(dataset);
    if (dataFile == null) {
      throw new DataException("Dataset file not found");
    }

    // Use the field mappings
    List<String> fieldMappings = dataset.getFieldMappingsList();

    // Determine the CSV configuration
    CsvParserSettings parserSettings = new CsvParserSettings();
    parserSettings.setLineSeparatorDetectionEnabled(true);
    if ("text/csv;single".equals(dataset.getFileType()) || "text/plain".equals(dataset.getFileType())) {
      parserSettings.setHeaderExtractionEnabled(false);
    } else {
      parserSettings.setHeaderExtractionEnabled(true);
    }

    // Read the file and validate the records
    int rowCount = 0;
    CsvParser parser = new CsvParser(parserSettings);
    try (InputStream inputStream = new FileInputStream(dataFile)) {
      parser.beginParsing(inputStream, "ISO-8859-1");
      String[] row;
      while ((row = parser.parseNext()) != null) {
        ++rowCount;
        // Transform the row to item, then save
        String errorMessage = DatasetRecordCommand.validateRow(row, fieldMappings);
        if (errorMessage != null) {
          throw new DataException(errorMessage);
        }
      }
    } catch (Exception e) {
      LOG.error("CSV Error: " + e.getMessage());
      throw new DataException("Row validation error in CSV file at row " + rowCount + ": " + e.getMessage());
    } finally {
      parser.stopParsing();
    }
    return true;
  }
}
