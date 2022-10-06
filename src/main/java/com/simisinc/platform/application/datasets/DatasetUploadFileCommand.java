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
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.Part;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Functions for working with dataset files
 *
 * @author matt rajkowski
 * @created 2/7/2020 4:25 PM
 */
public class DatasetUploadFileCommand {

  private static Log LOG = LogFactory.getLog(DatasetUploadFileCommand.class);

  public static boolean handleUpload(WidgetContext context, Dataset dataset) {
    String fileType = dataset.getFileType();
    int type = DatasetFileCommand.type(fileType);
    String extension = DatasetFileCommand.extension(type);
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
    dataset.setFileHash(FileSystemCommand.getFileChecksum(tempFile));

    // Determine the web path for downloads, can randomize, etc.
    Date created = new Date(System.currentTimeMillis());
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
    dataset.setWebPath(sdf.format(created));

    // Verify the file content and enhance the dataset record
    if (!DatasetFileCommand.isValidDatasetFile(dataset, type)) {
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
}
