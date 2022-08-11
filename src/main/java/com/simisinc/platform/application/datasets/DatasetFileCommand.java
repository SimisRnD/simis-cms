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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.elearning.PERLSCourseListCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import javax.servlet.http.Part;
import java.io.*;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.List;

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

  public static final String GEO_JSON_TYPE = "application/vnd.geo+json";
  public static final String JSON_TYPE = "application/json";
  public static final String JSON_API_TYPE = "application/vnd.api+json";
  public static final String CSV_TYPE = "text/csv";
  public static final String TEXT_TYPE = "text/plain";
  public static final String RSS_TYPE = "application/rss+xml";

  public static File getFile(Dataset dataset) {
    // Get a file handle
    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    File serverFile = new File(serverRootPath + dataset.getFileServerPath());
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
        default:
          throw new DataException("File type not found: " + type);
      }
    } catch (DataException de) {
      LOG.error("Data exception", de);
      return false;
    }
    return true;
  }

  public static boolean handleUpload(WidgetContext context, Dataset dataset) {
    String fileType = dataset.getFileType();
    int type = type(fileType);
    String extension = extension(type);
    if (extension == null) {
      context.setErrorMessage("File type not supported");
      context.setRequestObject(dataset);
      return false;
    }

    // Prepare to save the file
    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    String serverSubPath = FileSystemCommand.generateFileServerSubPath("datasets");
    String serverCompletePath = serverRootPath + serverSubPath;
    String uniqueFilename = FileSystemCommand.generateUniqueFilename(context.getUserId());
    String filesystemPath = serverCompletePath + uniqueFilename + "." + extension;
    String dataPath = serverSubPath + uniqueFilename + "." + extension;

    // Determine if a file was submitted
    Part filePart = null;
    try {
      filePart = context.getRequest().getPart("file");
    } catch (Exception e) {
      LOG.debug("File part was not found, continuing...");
    }
    if (filePart == null || StringUtils.isBlank(filePart.getSubmittedFileName())) {
      context.setErrorMessage("An uploaded file was not found in the multipart form-data");
      context.setRequestObject(dataset);
      return false;
    }

    // Save the file and update the dataset object
    File tempFile = new File(filesystemPath);
    try {
      // Save the file from the request
      String filename = saveFile(filePart, filesystemPath, tempFile);
      if (filename == null) {
        LOG.debug("Filename not found");
        throw new DataException("File upload error");
      }
      dataset.setFilename(filename);
      dataset.setLastDownload(new Timestamp(System.currentTimeMillis()));
    } catch (Exception e) {
      LOG.warn("An error occurred", e);
      context.setErrorMessage("An error occurred with the file");
      context.setRequestObject(dataset);
      return false;
    }
    if (dataset.getFilename() == null) {
      dataset.setFilename("data." + extension);
    }
    dataset.setFileType(fileType);
    dataset.setFileLength(tempFile.length());
    dataset.setFileServerPath(dataPath);

    // Verify the file content and enhance the dataset record
    if (!isValidDatasetFile(dataset, type)) {
      context.setErrorMessage("The file could not be validated");
      return false;
    }

    try {
      // Get a handle on the previous file if there is one
      Dataset previousDataset = DatasetRepository.findById(dataset.getId());
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
      return true;
    } catch (DataException e) {
      // Clean up the file
      if (tempFile.exists()) {
        LOG.warn("Deleting the temporary file: " + filesystemPath);
        tempFile.delete();
      }
    }
    return false;
  }

  public static void handleRemoteFileDownload(Dataset dataset, long userId) throws DataException {
    if (StringUtils.isBlank(dataset.getSourceUrl())) {
      throw new DataException("A source url is required");
    }

    String fileType = dataset.getFileType();
    int type = type(fileType);
    String extension = extension(type);
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
        if (!downloadFile(dataset.getSourceUrl(), tempFile)) {
          throw new DataException("File download error from: " + dataset.getSourceUrl());
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

    // Verify the file content and enhance the dataset record
    if (!isValidDatasetFile(dataset, type)) {
      throw new DataException("The file could not be validated");
    }

    try {
      // Get a handle on the previous file (if there is one)
      Dataset previousDataset = DatasetRepository.findById(dataset.getId());
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
  }

  public static String saveFile(Part filePart, String completePath, File tempFile) {

    // Validate the parameters
    if (filePart == null || StringUtils.isBlank(filePart.getSubmittedFileName())) {
      return null;
    }

    // Check for the file in the request and save it
    String submittedFilename = null;
    String extension = null;
    long fileLength = 0;

    try {
      submittedFilename = Paths.get(filePart.getSubmittedFileName()).getFileName().toString();
      extension = FilenameUtils.getExtension(submittedFilename);
      fileLength = filePart.getSize();
      LOG.debug("File submittedFilename: " + submittedFilename);
      LOG.debug("File extension: " + extension);
      LOG.debug("File length: " + fileLength);
      if (fileLength > 0) {
        filePart.write(completePath);
      }
    } catch (Exception e) {
      // Clean up the file
      if (tempFile.exists()) {
        LOG.warn("Deleting an uploaded file: " + completePath);
        tempFile.delete();
      }
      return null;
    }

    // Make sure a file was received
    if (fileLength <= 0) {
      tempFile.delete();
      return null;
    }

    return submittedFilename;
  }

  public static boolean downloadFile(String url, File tempFile) {
    // Validate the parameters
    if (StringUtils.isBlank(url)) {
      LOG.debug("No url");
      return false;
    }
    String[] schemes = { "http", "https" };
    UrlValidator urlValidator = new UrlValidator(schemes);
    if (!urlValidator.isValid(url)) {
      LOG.debug("Invalid url: " + url);
      return false;
    }
    // Download to a file
    try {
      HttpClient client = HttpClientBuilder.create().build();
      HttpGet request = new HttpGet(url);
      HttpResponse response = client.execute(request);
      if (response == null) {
        LOG.debug("No response");
        return false;
      }

      // Check the status code
      int status = response.getStatusLine().getStatusCode();
      if (status < 200 || status >= 300) {
        LOG.debug("Received status: " + status);
        return false;
      }

      // Use the entity
      HttpEntity entity = response.getEntity();
      if (entity == null) {
        LOG.debug("No entity");
        return false;
      }

      // Save it
      try (InputStream stream = entity.getContent()) {
        try (BufferedInputStream inputStream = new BufferedInputStream(stream)) {
          try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            int inByte;
            while ((inByte = inputStream.read()) != -1) {
              outputStream.write(inByte);
            }
          }
        }
      } catch (IOException ex) {
        LOG.debug("Could not save file: " + ex.getMessage());
        throw ex;
      }
    } catch (Exception e) {
      // Clean up the file
      if (tempFile.exists()) {
        LOG.warn("Deleting an uploaded file: " + tempFile.getAbsolutePath());
        tempFile.delete();
      }
      LOG.error("downloadFile error", e);
      return false;
    }

    // Make sure a file was received
    if (tempFile.length() <= 0) {
      tempFile.delete();
      LOG.debug("File length 0");
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
      default:
        return false;
    }
  }
}
