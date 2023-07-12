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
import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.SaveTextFileCommand;
import com.simisinc.platform.application.elearning.PERLSCourseListCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.application.http.HttpDownloadFileCommand;
import com.simisinc.platform.application.http.HttpGetCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;

/**
 * Functions for working with dataset files
 *
 * @author matt rajkowski
 * @created 2/7/2020 4:25 PM
 */
public class DatasetDownloadRemoteFileCommand {

  private static Log LOG = LogFactory.getLog(DatasetDownloadRemoteFileCommand.class);

  public static final int UNKNOWN = -1;
  public static final int CSV = 1;
  public static final int JSON = 2;
  public static final int GEO_JSON = 3;
  public static final int RSS = 4;
  public static final int TEXT = 5;
  public static final int JSON_API = 6;

  public static final String GEO_JSON_TYPE = "application/vnd.geo+json";
  public static final String JSON_TYPE = "application/json";
  public static final String JSON_API_TYPE = "application/vnd.api+json";
  public static final String CSV_TYPE = "text/csv";
  public static final String TEXT_TYPE = "text/plain";
  public static final String RSS_TYPE = "application/rss+xml";

  public static boolean handleRemoteFileDownload(Dataset dataset, long userId) throws DataException {
    if (StringUtils.isBlank(dataset.getSourceUrl())) {
      throw new DataException("A source url is required");
    }

    String fileType = dataset.getFileType();
    int type = DatasetFileCommand.type(fileType);
    String extension = DatasetFileCommand.extension(type);
    if (extension == null) {
      throw new DataException("File type not supported");
    }

    // Prepare to save the file
    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    String serverSubPath = FileSystemCommand.generateFileServerSubPath("datasets");
    String serverCompletePath = serverRootPath + serverSubPath;
    String uniqueFilename = FileSystemCommand.generateUniqueFilename(userId);
    String filesystemPath = serverCompletePath + uniqueFilename + "." + extension;
    String dataPath = serverSubPath + uniqueFilename + "." + extension;

    // Download the file
    File tempFile = new File(filesystemPath);
    try {
      if ("application/vnd.api+json".equals(fileType)) {
        File result = PERLSCourseListCommand.retrieveCourseListToFile(tempFile);
        if (result == null) {
          throw new DataException("JSON API File download error");
        }
        dataset.setRecordsPath("/data");
      } else {
        // Determine if there could be multiple JSON files
        if (StringUtils.isNotBlank(dataset.getPagingUrlPath())) {
          if (!downloadPagedFile(dataset.getSourceUrl(), dataset.getPagingUrlPath(), dataset.getRecordsPath(),
              tempFile)) {
            throw new DataException("File with paging download error from: " + dataset.getSourceUrl());
          }
        } else {
          // Download a single JSON file
          if (!HttpDownloadFileCommand.execute(dataset.getSourceUrl(), tempFile)) {
            throw new DataException("File download error from: " + dataset.getSourceUrl());
          }
        }
      }
    } catch (Exception e) {
      LOG.warn("An error occurred", e);
      throw new DataException(e.getMessage());
    }
    dataset.setLastDownload(new Timestamp(System.currentTimeMillis()));
    if (dataset.getFilename() == null) {
      dataset.setFilename("data." + extension);
    }
    dataset.setFileType(fileType);
    dataset.setFileLength(tempFile.length());
    dataset.setFileServerPath(dataPath);
    dataset.setFileHash(FileSystemCommand.getFileChecksum(tempFile));

    // Determine the web path for downloads, can randomize, etc.
    Date created = new Date(System.currentTimeMillis());
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
    dataset.setWebPath(sdf.format(created));

    try {
      // Compare the file content with the previous version to see if it is new
      Dataset previousDataset = DatasetRepository.findById(dataset.getId());
      if (previousDataset != null && previousDataset.getFileHash() != null
          && previousDataset.getFileHash().equals(dataset.getFileHash())) {
        // The content is the same as the last download
        tempFile.delete();
        // Give it a new last_download attempt date
        DatasetRepository.markLastDownload(dataset);
        // Mark it as updated/unlocked
        DatasetRepository.markAsUnqueued(dataset);
        return false;
      }

      // Verify the file content and enhance the dataset record
      if (!DatasetFileCommand.isValidDatasetFile(dataset, type)) {
        throw new DataException("The file could not be validated");
      }

      // Update the dataset repository
      Dataset savedDataset = SaveDatasetCommand.saveDataset(dataset);
      if (savedDataset == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
      // Share the new id with the caller
      dataset.setId(savedDataset.getId());
      // Clean up the previous dataset file
      if (previousDataset != null) {
        DeleteDatasetCommand.deleteFile(previousDataset);
      }
    } catch (DataException e) {
      // Clean up the file
      if (tempFile.exists()) {
        LOG.warn("Deleting the temporary file: " + filesystemPath);
        tempFile.delete();
      }
    } catch (Exception e) {
      LOG.error("Unexpected exception: " + e.getMessage());
      throw new DataException("Unexpected error");
    }

    // Mark it as updated/unlocked
    DatasetRepository.markAsUnqueued(dataset);
    return true;
  }

  /**
   * Downloads a series of JSON files into a single merged file
   * 
   * @param url
   * @param jsonPagingPath
   * @param tempFile
   * @return
   */
  public static boolean downloadPagedFile(String url, String jsonPagingPath, String jsonRecordsPath, File tempFile) {

    // Download the first file, as a string
    String content = HttpGetCommand.execute(url);
    if (StringUtils.isBlank(content)) {
      return false;
    }

    try {
      // Check if there's additional pages (jsonPagingPath) and then download each
      JsonNode json = JsonLoader.fromString(content);

      // Advance to the records path, if known
      JsonNode jsonRecordsNode = null;
      String[] recordsPath = jsonRecordsPath.split("/");
      for (String fieldName : recordsPath) {
        if (jsonRecordsNode == null) {
          if (json.has(fieldName)) {
            jsonRecordsNode = json.get(fieldName);
          }
        } else {
          if (jsonRecordsNode.has(fieldName)) {
            jsonRecordsNode = jsonRecordsNode.get(fieldName);
          }
        }
      }
      if (jsonRecordsNode == null || !jsonRecordsNode.isArray()) {
        return false;
      }

      // Append any pages
      appendNextUrls(jsonRecordsNode, json, jsonPagingPath, jsonRecordsPath);

      // Write the whole JSON to a file
      SaveTextFileCommand.save(json.toPrettyString(), tempFile);
      return true;

    } catch (Exception e) {
      LOG.debug("JSON error", e);
      return false;
    }
  }

  private static void appendNextUrls(JsonNode jsonRecordsNode, JsonNode currentJson, String jsonPagingPath,
      String jsonRecordsPath) throws IOException {

    if (currentJson == null) {
      throw new IOException("currentJson is null");
    }

    // Advance to the paging path
    String nextUrl = null;
    String[] pagingPath = jsonPagingPath.split("/");
    for (String fieldName : pagingPath) {
      if (StringUtils.isNotBlank(fieldName) && currentJson.has(fieldName)) {
        JsonNode thisNode = currentJson.get(fieldName);
        if (!thisNode.isNull()) {
          nextUrl = thisNode.asText();
        }
      }
    }
    // Determine if there's another page
    if (StringUtils.isBlank(nextUrl)) {
      LOG.debug("Next url is empty");
      return;
    }
    LOG.debug("Next url: " + nextUrl);

    // Use the url to get the next page content
    String content = HttpGetCommand.execute(nextUrl);
    if (StringUtils.isBlank(content)) {
      throw new IOException("Content is blank");
    }

    // Access the new records and append them to the original json
    JsonNode nextJson = JsonLoader.fromString(content);
    JsonNode newRecordsJson = null;

    // Advance to the records path, if known
    String[] recordsPath = jsonRecordsPath.split("/");
    for (String fieldName : recordsPath) {
      if (StringUtils.isBlank(fieldName)) {
        continue;
      }
      if (newRecordsJson == null) {
        if (nextJson.has(fieldName)) {
          newRecordsJson = nextJson.get(fieldName);
        }
      } else {
        if (newRecordsJson.has(fieldName)) {
          newRecordsJson = newRecordsJson.get(fieldName);
        }
      }
    }
    if (newRecordsJson == null || !newRecordsJson.isArray()) {
      throw new IOException("No records in nextJson");
    }
    // Append the records
    for (JsonNode element : newRecordsJson) {
      ((ArrayNode) jsonRecordsNode).add(element);
    }

    // Keep going
    appendNextUrls(jsonRecordsNode, nextJson, jsonPagingPath, jsonRecordsPath);
  }

}
