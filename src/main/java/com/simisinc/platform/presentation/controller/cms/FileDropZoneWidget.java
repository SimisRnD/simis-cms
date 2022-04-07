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

package com.simisinc.platform.presentation.controller.cms;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.*;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.Folder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.InvocationTargetException;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/23/2019 4:38 PM
 */
public class FileDropZoneWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  private static String JSP = "/cms/file-drop-zone.jsp";
  private static Log LOG = LogFactory.getLog(FileDropZoneWidget.class);


  /**
   * Prepare the drop zone for uploads
   *
   * @param context
   * @return
   */
  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine the folder
    Folder folder = null;
    String folderUniqueId = context.getPreferences().get("folderUniqueId");
    if (StringUtils.isNotBlank(folderUniqueId)) {
      folder = LoadFolderCommand.loadFolderByUniqueIdForAuthorizedUser(folderUniqueId, context.getUserId());
    }
    if (folder == null) {
      LOG.warn("Specified folderUniqueId was not found: " + folderUniqueId);
      return null;
    }
    context.getRequest().setAttribute("folder", folder);

    // Check permissions (allow drop-box permissions)
    if (!context.hasRole("admin")) {
      if (!CheckFolderPermissionCommand.userHasAddPermission(folder.getId(), context.getUserId())) {
        return null;
      }
    }

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

    // Determine the folder
    Folder folder = null;
    String folderUniqueId = context.getPreferences().get("folderUniqueId");
    if (StringUtils.isNotBlank(folderUniqueId)) {
      folder = LoadFolderCommand.loadFolderByUniqueIdForAuthorizedUser(folderUniqueId, context.getUserId());
    }
    if (folder == null) {
      LOG.warn("Specified folderUniqueId was not found: " + folderUniqueId);
      return null;
    }

    // Check permissions
    if (!context.hasRole("admin")) {
      if (!CheckFolderPermissionCommand.userHasAddPermission(folder.getId(), context.getUserId())) {
        return null;
      }
    }

    FileItem fileItemBean = null;
    try {
      // Check for a file
      fileItemBean = SaveFilePartCommand.saveFile(context);
      if (fileItemBean == null) {
        LOG.warn("File part was not found in request");
        throw new DataException("A file was not found, please choose a file and try again");
      }
      fileItemBean.setFolderId(folder.getId());
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
      context.setJson("{\"location\": \"" + "/assets/file/" + System.currentTimeMillis() + "-" + fileItem.getId() + "/" + UrlCommand.encodeUri(fileItem.getFilename()) + "\"}");
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
