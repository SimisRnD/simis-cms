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
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.datasets.Dataset;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * Checks and processes JSON files
 *
 * @author matt rajkowski
 * @created 4/25/18 9:05 AM
 */
public class ValidateJSONDatasetCommand {

  private static Log LOG = LogFactory.getLog(ValidateJSONDatasetCommand.class);

  public static void checkFile(Dataset datasetBean) throws DataException {

    // Get a file handle
    File datasetFile = DatasetFileCommand.getFile(datasetBean);
    if (datasetFile == null) {
      return;
    }

    // Check mime type
    // text/json?
    // application/json
    String mimeType = null;
    try {
      mimeType = Files.probeContentType(datasetFile.toPath());
    } catch (Exception e) {
      LOG.error("Error checking file for data", e);
    }
    if (mimeType == null) {
      String extension = FilenameUtils.getExtension(datasetFile.getAbsolutePath());
      if ("json".equalsIgnoreCase(extension)) {
        mimeType = "application/json";
      } else {
        throw new DataException("Could not determine type");
      }
    }
    LOG.debug("MimeType: " + mimeType);
    datasetBean.setFileType(mimeType);

    // Read the file for columns and row counts
    JsonNode json = null;
    try {
      json = JsonLoader.fromFile(datasetFile);

      // If an array, use the document node
      if (StringUtils.isBlank(datasetBean.getRecordsPath())) {
        if (json.isArray()) {
          datasetBean.setRecordsPath("/");
        }
      }

      // Verify the specified data path
      if (StringUtils.isNotBlank(datasetBean.getRecordsPath())) {
        String[] recordsPath = datasetBean.getRecordsPath().split("/");
        for (String fieldName : recordsPath) {
          if (json.has(fieldName)) {
            json = json.get(fieldName);
          }
        }
        if (json.isArray()) {
          datasetBean.setRowCount(json.size());
        }
      }
    } catch (Exception e) {
      LOG.error("JSON Error: " + e.getMessage());
      throw new DataException("File type is incorrect: " + e.getMessage());
    }

    if (datasetBean.getColumnCount() == -1) {
      datasetBean.setColumnCount(0);
    }
  }

  public static boolean validateAllRows(Dataset dataset) throws DataException {

    // Load the rows
    List<String[]> rows = LoadJsonCommand.loadRecords(dataset, Integer.MAX_VALUE, true);

    // Use the field mappings
    List<String> fieldMappings = dataset.getFieldMappingsList();

    // Read the file and validate the records
    int rowCount = 0;
    for (String[] row : rows) {
      ++rowCount;
      String errorMessage = DatasetRecordCommand.validateRow(row, fieldMappings);
      if (errorMessage != null) {
        throw new DataException("Row validation error at row " + rowCount + ": " + errorMessage);
      }
    }
    return true;
  }
}
