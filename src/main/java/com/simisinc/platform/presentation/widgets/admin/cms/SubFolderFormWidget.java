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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.*;
import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;

/**
 * Widget for displaying a system administration form to add/update sub-folders
 *
 * @author matt rajkowski
 * @created 9/3/19 12:01 PM
 */
public class SubFolderFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/sub-folder-form.jsp";

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

    // Form bean
    SubFolder subFolder = null;
    if (context.getRequestObject() != null) {
      subFolder = (SubFolder) context.getRequestObject();
      context.getRequest().setAttribute("subFolder", subFolder);
    } else {
      long subFolderId = context.getParameterAsLong("subFolderId");
      if (subFolderId > -1) {
        subFolder = LoadSubFolderCommand.loadSubFolderById(subFolderId);
        context.getRequest().setAttribute("subFolder", subFolder);
      }
    }

    // Check permissions
    if (!context.hasRole("admin") && !context.hasRole("content-manager")) {
      if (!CheckFolderPermissionCommand.userHasAddPermission(subFolder.getFolderId(), context.getUserId())) {
        return null;
      }
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields
    SubFolder subFolderBean = new SubFolder();
    BeanUtils.populate(subFolderBean, context.getParameterMap());
    subFolderBean.setCreatedBy(context.getUserId());
    subFolderBean.setModifiedBy(context.getUserId());

    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));

    // Check permissions
    if (!context.hasRole("admin") && !context.hasRole("content-manager")) {
      if (!CheckFolderPermissionCommand.userHasAddPermission(subFolderBean.getFolderId(), context.getUserId())) {
        return null;
      }
    }

    // Save the sub-folder
    SubFolder subFolder = null;
    try {
      subFolder = SaveSubFolderCommand.saveSubFolder(subFolderBean);
      if (subFolder == null) {
        throw new FolderException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | FolderException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(subFolderBean);
      context.addSharedRequestValue("returnPage", returnPage);
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Sub-Folder was saved");
    if (StringUtils.isNotBlank(returnPage)) {
      context.setRedirect(returnPage);
    } else {
//      context.setRedirect("/admin/folder-details?folderId=" + subFolder.getFolderId());
      context.setRedirect("/admin/sub-folder-details?subFolderId=" + subFolder.getId() + "&folderId=" + subFolder.getFolderId());
    }
    return context;
  }
}
