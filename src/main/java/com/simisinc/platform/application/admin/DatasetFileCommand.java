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
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
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
 * Description
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
    if ("application/vnd.geo+json".equals(fileType)) {
      return GEO_JSON;
    } else if ("application/rss+xml".equals(fileType)) {
      return RSS;
    } else if ("text/csv".equals(fileType)) {
      return CSV;
    } else if ("text/plain".equals(fileType)) {
      return TEXT;
    } else if ("application/json".equals(fileType)) {
      return JSON;
    }
    return UNKNOWN;
  }

  public static boolean handleNewFile(WidgetContext context, Dataset dataset, String fileType) {
    String extension = null;
    int type = type(fileType);
    switch (type) {
      case CSV:
        extension = "csv";
        break;
      case JSON:
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
    if (extension == null) {
      LOG.warn("File type not found: " + fileType);
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

    File tempFile = new File(filesystemPath);
    try {
      if (filePart != null && StringUtils.isNotBlank(filePart.getSubmittedFileName())) {
        // Save the file from the request
        String filename = saveFile(filePart, filesystemPath, tempFile);
        if (filename == null) {
          LOG.debug("Filename not found");
          throw new Exception("File upload error");
        }
        dataset.setFilename(filename);
        dataset.setLastDownload(new Timestamp(System.currentTimeMillis()));
      } else if (StringUtils.isNotBlank(dataset.getSourceUrl())) {
        // Download the specified file
        if (!downloadFile(dataset.getSourceUrl(), tempFile)) {
          throw new Exception("File download error from: " + dataset.getSourceUrl());
        }
        dataset.setLastDownload(new Timestamp(System.currentTimeMillis()));
      }
    } catch (Exception e) {
      LOG.warn("An error occurred", e);
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
    try {
      switch (type) {
        case CSV:
          ValidateCSVDatasetCommand.checkFile(dataset);
          break;
        case JSON:
          ValidateJSONDatasetCommand.checkFile(dataset);
          break;
        case GEO_JSON:
          ValidateGeoJsonDatasetCommand.checkFile(dataset);
          break;
        case RSS:
          ValidateRSSDatasetCommand.checkFile(dataset);
          break;
        default:
          throw new DataException("File type not found: " + fileType);
      }
    } catch (DataException de) {
      LOG.error("Data exception", de);
      return false;
    }

    // Update the dataset repository
    try {
      Dataset savedDataset = SaveDatasetCommand.saveDataset(dataset);
      if (savedDataset == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
      // Share the new id with the caller
      dataset.setId(savedDataset.getId());
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
      InputStream stream = entity.getContent();
      try {
        BufferedInputStream inputStream = new BufferedInputStream(stream);
        BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(tempFile));
        int inByte;
        while ((inByte = inputStream.read()) != -1) {
          outputStream.write(inByte);
        }
        inputStream.close();
        outputStream.close();
      } catch (IOException ex) {
        LOG.debug("Could not save file: " + ex.getMessage());
        throw ex;
      } finally {
        stream.close();
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
      case GEO_JSON:
        // return ConvertGeoJsonFeedCommand.validateAllRows(dataset);
      case RSS:
        // return ConvertRSSFeedCommand.validateAllRows(dataset);
      default:
        return false;
    }
  }
}
