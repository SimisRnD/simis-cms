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

import java.io.File;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.domain.model.items.Collection;

/**
 * Functions for working with dataset files
 *
 * @author matt rajkowski
 * @created 2/7/2020 4:25 PM
 */
public class DatasetFileCommand {

  private static Log LOG = LogFactory.getLog(DatasetFileCommand.class);

  public static final int UNKNOWN = -1;
  public static final int CSV = 1;
  public static final int JSON = 2;
  public static final int GEO_JSON = 3;
  public static final int RSS = 4;
  public static final int TEXT = 5;
  public static final int JSON_API = 6;
  public static final int TSV = 7;

  public static final String GEO_JSON_TYPE = "application/vnd.geo+json";
  public static final String JSON_TYPE = "application/json";
  public static final String JSON_API_TYPE = "application/vnd.api+json";
  public static final String CSV_TYPE = "text/csv";
  public static final String TEXT_TYPE = "text/plain";
  public static final String TSV_TYPE = "text/tab-separated-values";
  public static final String RSS_TYPE = "application/rss+xml";

  public static File getFile(Dataset dataset) {
    // Get a file handle
    File serverFile = FileSystemCommand.getFileServerRootPath(dataset.getFileServerPath());
    if (!serverFile.exists()) {
      LOG.warn("File not found: " + serverFile.getAbsolutePath());
      return null;
    }
    if (serverFile.length() <= 0) {
      LOG.warn("File is empty: " + serverFile.getAbsolutePath());
      return null;
    }
    return serverFile;
  }

  public static int type(String fileType) {
    if (GEO_JSON_TYPE.equals(fileType)) {
      return GEO_JSON;
    } else if (RSS_TYPE.equals(fileType)) {
      return RSS;
    } else if (CSV_TYPE.equals(fileType)) {
      return CSV;
    } else if (TSV_TYPE.equals(fileType)) {
      return TSV;
    } else if (TEXT_TYPE.equals(fileType)) {
      return TEXT;
    } else if (JSON_TYPE.equals(fileType)) {
      return JSON;
    } else if (JSON_API_TYPE.equals(fileType)) {
      return JSON_API;
    }
    return UNKNOWN;
  }

  public static String extension(int type) {
    String extension = null;
    switch (type) {
      case CSV:
        extension = "csv";
        break;
      case JSON:
        extension = "json";
        break;
      case JSON_API:
        extension = "json";
        break;
      case GEO_JSON:
        extension = "geojson";
        break;
      case TEXT:
        extension = "txt";
        break;
      case TSV:
        extension = "tsv";
        break;
      case RSS:
        extension = "rss";
        break;
      default:
        break;
    }
    return extension;
  }

  public static boolean isValidDatasetFile(Dataset dataset, int type) {
    try {
      switch (type) {
        case CSV:
          ValidateCSVDatasetCommand.checkFile(dataset);
          break;
        case JSON:
          ValidateJSONDatasetCommand.checkFile(dataset);
          break;
        case JSON_API:
          ValidateJsonApiDatasetCommand.checkFile(dataset);
          break;
        case GEO_JSON:
          ValidateGeoJsonDatasetCommand.checkFile(dataset);
          break;
        case RSS:
          ValidateRSSDatasetCommand.checkFile(dataset);
          break;
        case TSV:
          ValidateTSVDatasetCommand.checkFile(dataset);
          break;
        default:
          throw new DataException("File type not found: " + type);
      }
    } catch (DataException de) {
      LOG.error("Data exception", de);
      return false;
    }
    return true;
  }

  public static List<String[]> loadRows(Dataset dataset, int rowsToReturn, boolean applyOptions) throws Exception {
    int type = type(dataset.getFileType());
    switch (type) {
      case CSV:
        return LoadCSVRowsCommand.loadRows(dataset, rowsToReturn, applyOptions);
      case JSON:
        return LoadJsonCommand.loadRecords(dataset, rowsToReturn, applyOptions);
      case JSON_API:
        return LoadJsonCommand.loadRecords(dataset, rowsToReturn, applyOptions);
      case GEO_JSON:
        return LoadGeoJsonFeedCommand.loadRows(dataset, rowsToReturn);
      case RSS:
        return LoadRSSFeedCommand.loadRows(dataset, rowsToReturn);
      case TSV:
        return LoadTSVRowsCommand.loadRows(dataset, rowsToReturn, applyOptions);
      default:
        return null;
    }
  }

  public static boolean validateAllRows(Dataset dataset) throws DataException {
    int type = type(dataset.getFileType());
    switch (type) {
      case CSV:
        return ValidateCSVDatasetCommand.validateAllRows(dataset);
      case JSON:
        return ValidateJSONDatasetCommand.validateAllRows(dataset);
      case JSON_API:
        return ValidateJSONDatasetCommand.validateAllRows(dataset);
      case GEO_JSON:
        return ValidateGeoJsonDatasetCommand.validateAllRows(dataset);
      case RSS:
        return ValidateRSSDatasetCommand.validateAllRows(dataset);
      case TSV:
        return ValidateTSVDatasetCommand.validateAllRows(dataset);
      default:
        return false;
    }
  }

  public static boolean convertFileToCollection(Dataset dataset, Collection collection) throws Exception {
    int type = type(dataset.getFileType());
    switch (type) {
      case CSV:
        return ConvertCSVFileCommand.convertFileToCollection(dataset, collection);
      case JSON:
        return ConvertJsonFileCommand.convertFileToCollection(dataset, collection);
      case JSON_API:
        // return ConvertJsonApiFileCommand.convertFileToCollection(dataset, collection);
      case GEO_JSON:
        // return ConvertGeoJsonFeedCommand.validateAllRows(dataset);
      case RSS:
        // return ConvertRSSFeedCommand.validateAllRows(dataset);
      case TSV:
        return ConvertTSVFileCommand.convertFileToCollection(dataset, collection);
      default:
        return false;
    }
  }
}
