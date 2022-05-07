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
import java.util.List;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.CheckFolderPermissionCommand;
import com.simisinc.platform.application.cms.DeleteSubFolderCommand;
import com.simisinc.platform.application.cms.LoadFolderCommand;
import com.simisinc.platform.application.cms.LoadSubFolderCommand;
import com.simisinc.platform.application.cms.SaveSubFolderCommand;
import com.simisinc.platform.application.cms.SubFolderException;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderRepository;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderSpecification;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

import org.apache.commons.beanutils.BeanUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/27/19 4:33 PM
 */
public class FolderSubFoldersListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/folder-sub-folders-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the folder
    long folderId = context.getParameterAsLong("folderId");
    Folder folder;
    if (context.hasRole("admin")) {
      folder = FolderRepository.findById(folderId);
    } else {
      folder = LoadFolderCommand.loadFolderByIdForAuthorizedUser(folderId, context.getUserId());
    }
    if (folder == null) {
      context.setErrorMessage("Error. Folder was not found.");
      return context;
    }
    context.getRequest().setAttribute("folder", folder);

    // Determine permissions for UI
    boolean canEdit = CheckFolderPermissionCommand.userHasEditPermission(folder.getId(), context.getUserId());
    boolean canDelete = CheckFolderPermissionCommand.userHasDeletePermission(folder.getId(), context.getUserId());
    if (context.hasRole("admin")) {
      canEdit = true;
      canDelete = true;
    }
    context.getRequest().setAttribute("canEdit", canEdit ? "true" : "false");
    context.getRequest().setAttribute("canDelete", canDelete ? "true" : "false");

    // Load the sub-folders for this folder
    SubFolderSpecification specification = new SubFolderSpecification();
    specification.setFolderId(folder.getId());
    List<SubFolder> subFolderList = SubFolderRepository.findAll(specification, null);
    context.getRequest().setAttribute("subFolderList", subFolderList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  /**
   * A file is being updated
   *
   * @param context
   * @return
   * @throws InvocationTargetException
   * @throws IllegalAccessException
   */
  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Permission is required
    if (!(context.hasRole("admin") || context.hasRole("content-manager"))) {
      LOG.warn("No permission to update the sub-folder");
      return context;
    }

    // Don't accept multiple form posts
    context.getUserSession().renewFormToken();

    // Determine the return page
    long currentFolderId = Long.parseLong(context.getRequest().getParameter("currentFolderId"));
    if (!context.hasRole("admin")) {
      if (!CheckFolderPermissionCommand.userHasAddPermission(currentFolderId, context.getUserId())) {
        LOG.warn("No permission to update modify the folder");
        return null;
      }
    }

    // Populate the fields
    SubFolder subFolderBean = new SubFolder();
    BeanUtils.populate(subFolderBean, context.getParameterMap());
    subFolderBean.setFolderId(currentFolderId);
    subFolderBean.setCreatedBy(context.getUserId());
    subFolderBean.setModifiedBy(context.getUserId());

    // Save the sub-folder
    SubFolder subFolder = null;
    try {
      subFolder = SaveSubFolderCommand.saveSubFolder(subFolderBean);
      if (subFolder == null) {
        throw new SubFolderException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | SubFolderException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(subFolderBean);
//      context.addSharedRequestValue("returnPage", returnPage);
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Sub-Folder was saved");
//    if (StringUtils.isNotBlank(returnPage)) {
//      context.setRedirect(returnPage);
//    } else {
    context.setRedirect("/admin/folder-details?folderId=" + currentFolderId);
//    }
    return context;
  }

  /**
   * A sub-folder is being deleted
   *
   * @param context
   * @return
   */
  public WidgetContext delete(WidgetContext context) {

    // Check for file to be deleted
    long subFolderId = context.getParameterAsLong("subFolderId", -1);
    SubFolder record;
    if (context.hasRole("admin")) {
      record = LoadSubFolderCommand.loadSubFolderById(subFolderId);
    } else {
      record = LoadSubFolderCommand.loadSubFolderByIdForAuthorizedUser(subFolderId, context.getUserId());
    }
    if (record == null) {
      LOG.warn("Sub folder record does not exist or no access: " + subFolderId);
      return null;
    }

    // @todo make sure the folder's user group can delete

    try {
      DeleteSubFolderCommand.deleteSubFolder(record);
      context.setSuccessMessage("Sub folder deleted");
      context.setRedirect("/admin/folder-details?folderId=" + record.getFolderId());
      return context;
    } catch (Exception e) {
      context.setErrorMessage("Error. File could not be deleted.");
//        context.setRedirect("/admin/collections");
    }

    return context;
  }
}
