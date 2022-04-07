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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 2/3/19 1:56 PM
 */
public class ValidateGeoJsonDatasetCommand {

  private static Log LOG = LogFactory.getLog(ValidateGeoJsonDatasetCommand.class);

  public static void checkFile(Dataset datasetBean) throws DataException {

    // Get a file handle
    File datasetFile = DatasetFileCommand.getFile(datasetBean);
    if (datasetFile == null) {
      return;
    }

    // Check mime type
    // application/json
    // application/vnd.geo+json
    String mimeType = null;
    try {
      mimeType = Files.probeContentType(datasetFile.toPath());
      LOG.debug("MimeType: " + mimeType);
    } catch (Exception e) {
      LOG.error("Error checking file for data", e);
    }
    if (mimeType == null) {
      String extension = FilenameUtils.getExtension(datasetFile.getAbsolutePath());
      if ("geojson".equalsIgnoreCase(extension)) {
        mimeType = "application/vnd.geo+json";
      } else {
        throw new DataException("Could not determine type");
      }
    }
    LOG.debug("MimeType: " + mimeType);
    datasetBean.setFileType(mimeType);

    // Determine the field names
    ArrayList<String> fieldNames = new ArrayList<>();
    try {
      JsonNode config = JsonLoader.fromFile(datasetFile);
      Iterator<JsonNode> fields = config.get("fields").elements();
      while (fields.hasNext()) {
        JsonNode node = fields.next();
        fieldNames.add(node.get("name").asText());
      }
    } catch (Exception e) {
      LOG.error("GeoJson Error: " + e.getMessage());
      throw new DataException("Could not find any fields");
    }

    // Read the GeoJSON file for columns and row counts
    int rowCount = 0;
    try {
      JsonNode config = JsonLoader.fromFile(datasetFile);
      Iterator<JsonNode> features = config.get("features").elements();
      while (features.hasNext()) {
        features.next();
        ++rowCount;
      }
    } catch (Exception e) {
      LOG.error("GeoJson Error: " + e.getMessage());
      throw new DataException("Could not find any records");
    }

    datasetBean.setRowCount(rowCount);
    datasetBean.setColumnCount(fieldNames.size());
    datasetBean.setColumnNames(fieldNames.toArray(new String[0]));
  }

  public static boolean validateAllRows(Dataset dataset) throws DataException {

    // Load the rows
    List<String[]> rows = LoadGeoJsonFeedCommand.loadRows(dataset, Integer.MAX_VALUE);

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
