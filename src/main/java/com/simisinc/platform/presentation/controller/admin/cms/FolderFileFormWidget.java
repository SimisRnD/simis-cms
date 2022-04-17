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

package com.simisinc.platform.presentation.controller.admin.cms;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.CheckFolderPermissionCommand;
import com.simisinc.platform.application.cms.FolderException;
import com.simisinc.platform.application.cms.SaveFileCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.domain.model.cms.FolderCategory;
import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.infrastructure.persistence.cms.FolderCategoryRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Widget for displaying a system administration form to add/update files
 *
 * @author matt rajkowski
 * @created 9/6/2019 3:07 PM
 */
public class FolderFileFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  private static String JSP = "/admin/folder-file-form.jsp";
  private static Log LOG = LogFactory.getLog(FolderFileFormWidget.class);


  /**
   * Prepare the file item form
   *
   * @param context
   * @return
   */
  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // This page can return to different places
    String returnPage = context.getSharedRequestValue("returnPage");
    if (returnPage == null) {
      returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    }
    context.getRequest().setAttribute("returnPage", returnPage);

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

    // Load the categories for the drop-down
    List<FolderCategory> folderCategoryList = FolderCategoryRepository.findAllByFolderId(folder.getId());
    context.getRequest().setAttribute("folderCategoryList", folderCategoryList);

    // Check permissions
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
   * Adding file
   *
   * @param context
   * @return
   * @throws InvocationTargetException
   * @throws IllegalAccessException
   */
  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Check permissions
    long folderId = context.getParameterAsLong("folderId", -1);
    LOG.debug("Found folderId: " + folderId);
    if (!context.hasRole("admin")) {
      if (!CheckFolderPermissionCommand.userHasAddPermission(folderId, context.getUserId())) {
        return null;
      }
    }

    // Check for a sub-folder
    long subFolderId = context.getParameterAsLong("subFolderId", -1);
    LOG.debug("Found subFolderId: " + subFolderId);

    // Populate the fields
    FileItem fileItemBean = new FileItem();
    BeanUtils.populate(fileItemBean, context.getParameterMap());
    fileItemBean.setCreatedBy(context.getUserId());
    fileItemBean.setModifiedBy(context.getUserId());

    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));

    // Set the types of files that can be saved here
    if (StringUtils.isNotBlank(fileItemBean.getFilename())) {
      if (UrlCommand.isUrlValid(fileItemBean.getFilename())) {
        fileItemBean.setVersion("1.0");
        fileItemBean.setFileType("URL");
        fileItemBean.setExtension("url");
        fileItemBean.setMimeType("text/uri-list");
        fileItemBean.setFileLength(0);
      }
    }

    // Determine the page to redirect to
    if (StringUtils.isNotBlank(returnPage)) {
      context.setRedirect(returnPage);
    } else {
      context.setRedirect("/admin/sub-folder-details?subFolderId=" + subFolderId + "&folderId=" + folderId);
    }

    // Save the file item
    FileItem fileItem = null;
    try {
      fileItem = SaveFileCommand.saveFile(fileItemBean);
      if (fileItem == null) {
        throw new FolderException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | FolderException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(fileItemBean);
//      context.addSharedRequestValue("returnPage", returnPage);
      return context;
    }

    context.setSuccessMessage("File was saved");
    return context;
  }
}
