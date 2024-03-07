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

package com.simisinc.platform.application.items;

import java.io.File;
import java.nio.file.Paths;

import javax.servlet.http.Part;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemFileItem;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Validates and saves an item's uploaded file item object
 *
 * @author matt rajkowski
 * @created 4/19/2021 1:00 PM
 */
public class SaveItemFilePartCommand {

  private static Log LOG = LogFactory.getLog(SaveItemFilePartCommand.class);

  public static ItemFileItem saveFile(WidgetContext context, Item item) throws DataException {

    // Prepare to save the file
    String serverRootPath = FileSystemCommand.getFileServerRootPathValue();
    String serverSubPath = FileSystemCommand.generateFileServerSubPath("item-uploads");
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
    ItemFileItem fileItemBean = new ItemFileItem();
    fileItemBean.setItemId(item.getId());
    fileItemBean.setFilename(submittedFilename);
    fileItemBean.setFileLength(fileLength);
    fileItemBean.setFileServerPath(serverSubPath + uniqueFilename + "." + extension);
    fileItemBean.setExtension(extension);
    fileItemBean.setCreatedBy(context.getUserId());
    fileItemBean.setModifiedBy(context.getUserId());
    return fileItemBean;
  }

  public static void cleanupFile(ItemFileItem fileItemBean) {
    if (fileItemBean == null) {
      return;
    }
    File tempFile = FileSystemCommand.getFileServerRootPath(fileItemBean.getFileServerPath());
    if (tempFile.exists()) {
      LOG.warn("Deleting an uploaded file: " + tempFile.getPath());
      tempFile.delete();
    }
  }
}
