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

package com.simisinc.platform.application.cms;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.http.Part;
import java.io.File;
import java.nio.file.Paths;

/**
 * Validates, retrieves http file parts, and saves file item objects
 *
 * @author matt rajkowski
 * @created 12/18/18 3:11 PM
 */
public class SaveFilePartCommand {

  private static Log LOG = LogFactory.getLog(SaveFilePartCommand.class);

  public static FileItem saveFile(WidgetContext context) throws DataException {

    // Prepare to save the file
    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    String serverSubPath = FileSystemCommand.generateFileServerSubPath("uploads");
    String serverCompletePath = serverRootPath + serverSubPath;
    String uniqueFilename = FileSystemCommand.generateUniqueFilename(context.getUserId());

    // Find the file in the request and save it
    String submittedFilename = null;
    String extension = null;
    long fileLength = 0;
    File tempFile = null;
    try {
      Part filePart = context.getRequest().getPart("file");
      if (filePart == null) {
        return null;
      }
      fileLength = filePart.getSize();
      if (fileLength <= 0) {
        LOG.debug("The file size was 0");
        return null;
      }

      LOG.debug("Found a file...");
      submittedFilename = Paths.get(filePart.getSubmittedFileName()).getFileName().toString(); // MSIE fix.
      extension = FilenameUtils.getExtension(submittedFilename);
      tempFile = new File(serverCompletePath + uniqueFilename + "." + extension);

      LOG.debug("Writing file " + fileLength + " bytes");
      filePart.write(serverCompletePath + uniqueFilename + "." + extension);

    } catch (Exception e) {
      LOG.warn("Could not handle file: " + e.getMessage());
      // Clean up the file
      if (tempFile != null && tempFile.exists()) {
        LOG.warn("Deleting an uploaded file: " + serverCompletePath + uniqueFilename + "." + extension);
        tempFile.delete();
      }
      throw new DataException("There was an issue with the file");
    }

    // Populate the fields
    FileItem fileItemBean = new FileItem();
    fileItemBean.setFilename(submittedFilename);
    fileItemBean.setFileLength(fileLength);
    fileItemBean.setFileServerPath(serverSubPath + uniqueFilename + "." + extension);
    fileItemBean.setExtension(extension);
    fileItemBean.setCreatedBy(context.getUserId());
    fileItemBean.setModifiedBy(context.getUserId());
    return fileItemBean;
  }

  public static void cleanupFile(FileItem fileItemBean) {
    if (fileItemBean == null) {
      return;
    }
    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    File tempFile = new File(serverRootPath + fileItemBean.getFileServerPath());
    if (tempFile.exists()) {
      LOG.warn("Deleting an uploaded file: " + serverRootPath + fileItemBean.getFileServerPath());
      tempFile.delete();
    }
  }
}
