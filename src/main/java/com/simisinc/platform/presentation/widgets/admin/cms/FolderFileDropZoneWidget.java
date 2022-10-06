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

package com.simisinc.platform.presentation.widgets.admin.cms;

import java.lang.reflect.InvocationTargetException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.CheckFolderPermissionCommand;
import com.simisinc.platform.application.cms.SaveFileCommand;
import com.simisinc.platform.application.cms.SaveFilePartCommand;
import com.simisinc.platform.application.cms.ValidateFileCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 12/13/18 10:16 AM
 */
public class FolderFileDropZoneWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  private static String JSP = "/admin/folder-file-drop-zone.jsp";
  private static Log LOG = LogFactory.getLog(FolderFileDropZoneWidget.class);

  /**
   * Prepare the drop zone for uploads
   *
   * @param context
   * @return
   */
  public WidgetContext execute(WidgetContext context) {

    // Use the folder for permissions
    Folder folder;

    // Check for a sub-folder
    long subFolderId = context.getParameterAsLong("subFolderId");
    if (subFolderId > -1) {
      SubFolder subFolder = SubFolderRepository.findById(subFolderId);
      if (subFolder == null) {
        context.setErrorMessage("Error. Sub-Folder was not found.");
        return context;
      }
      context.getRequest().setAttribute("subFolder", subFolder);
      folder = FolderRepository.findById(subFolder.getFolderId());
    } else {
      // Determine the folder
      long folderId = context.getParameterAsLong("folderId");
      folder = FolderRepository.findById(folderId);
    }
    if (folder == null) {
      context.setErrorMessage("Error. Folder was not found.");
      return context;
    }
    context.getRequest().setAttribute("folder", folder);

    // Check permissions
    if (!context.hasRole("admin")) {
      if (!CheckFolderPermissionCommand.userHasAddPermission(folder.getId(), context.getUserId())) {
        return null;
      }
    }

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  /**
   * Adding files
   *
   * @param context
   * @return
   * @throws InvocationTargetException
   * @throws IllegalAccessException
   */
  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Check permissions
    long folderId = context.getParameterAsLong("folderId", -1);
    if (!context.hasRole("admin")) {
      if (!CheckFolderPermissionCommand.userHasAddPermission(folderId, context.getUserId())) {
        return null;
      }
    }

    // Check for a sub-folder
    long subFolderId = context.getParameterAsLong("subFolderId", -1);

    FileItem fileItemBean = null;
    try {
      // Check for a file
      fileItemBean = SaveFilePartCommand.saveFile(context);
      if (fileItemBean == null) {
        LOG.warn("File part was not found in request");
        throw new DataException("A file was not found, please choose a file and try again");
      }
      fileItemBean.setFolderId(folderId);
      fileItemBean.setSubFolderId(subFolderId);
      fileItemBean.setVersion("1.0");
      fileItemBean.setCreatedBy(context.getUserId());
      fileItemBean.setModifiedBy(context.getUserId());
      // Validate the file
      ValidateFileCommand.checkFile(fileItemBean);
      // Save it
      FileItem fileItem = SaveFileCommand.saveFile(fileItemBean);
      if (fileItem == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
      // GET uri /assets/file/20180503171549-5/logo.png
      // yyyyMMddHHmmss
      // Return Json
      LOG.debug("Finished!");
      context.setJson("{\"location\": \"" + "/assets/file/" + fileItem.getUrl() + "\"}");
      return context;
    } catch (DataException data) {
      // Clean up the file if it exists
      SaveFilePartCommand.cleanupFile(fileItemBean);
      // Let the user know
      context.setErrorMessage(data.getMessage());
      return context;
    }
  }
}
