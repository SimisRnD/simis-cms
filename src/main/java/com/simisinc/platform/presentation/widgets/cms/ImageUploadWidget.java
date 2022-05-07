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

package com.simisinc.platform.presentation.widgets.cms;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.SaveImageCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.cms.ValidateImageCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.http.Part;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/3/18 4:00 PM
 */
public class ImageUploadWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
//  private static String JSP = "/admin/dataset-upload-form.jsp";
  private static Log LOG = LogFactory.getLog(ImageUploadWidget.class);

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Prepare to save the file
    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    String serverSubPath = FileSystemCommand.generateFileServerSubPath("images");
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
        context.setWarningMessage("A file was not found, please choose a file and try again");
        return context;
      }
      submittedFilename = Paths.get(filePart.getSubmittedFileName()).getFileName().toString(); // MSIE fix.
      extension = FilenameUtils.getExtension(submittedFilename);
      tempFile = new File(serverCompletePath + uniqueFilename + "." + extension);
      fileLength = filePart.getSize();
      if (fileLength > 0) {
        filePart.write(serverCompletePath + uniqueFilename + "." + extension);
      }
    } catch (Exception e) {
      // Clean up the file
      if (tempFile != null && tempFile.exists()) {
        LOG.warn("Deleting an uploaded file: " + serverCompletePath + uniqueFilename + "." + extension);
        tempFile.delete();
      }
      return context;
    }

    // Make sure a file was processed
    if (fileLength <= 0) {
      if (tempFile.exists()) {
        LOG.warn("Deleting an uploaded file: " + serverCompletePath + uniqueFilename + "." + extension);
        tempFile.delete();
      }
      context.setErrorMessage("The file size was 0 and could not be saved");
      return context;
    }

    // Populate the fields
    Image imageBean = new Image();
    imageBean.setFilename(submittedFilename);
    imageBean.setFileLength(fileLength);
    imageBean.setFileServerPath(serverSubPath + uniqueFilename + "." + extension);
    imageBean.setCreatedBy(context.getUserId());

    // Save the record
    Image image = null;
    try {
      ValidateImageCommand.checkFile(imageBean);
      image = SaveImageCommand.saveImage(imageBean);
      if (image == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      // Clean up the file
      if (tempFile.exists()) {
        LOG.warn("Deleting an uploaded file: " + serverCompletePath + uniqueFilename);
        tempFile.delete();
      }
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(imageBean);
      return context;
    }

    // GET uri /assets/img/20180503171549-5/logo.png
    // yyyyMMddHHmmss

    // Return Json
    context.setJson("{\"location\": \"" + "/assets/img/" + System.currentTimeMillis() + "-" + image.getId() + "/" + UrlCommand.encodeUri(image.getFilename()) + "\"}");
    return context;
  }
}
