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
import com.simisinc.platform.application.cms.FolderException;
import com.simisinc.platform.application.cms.LoadFolderCommand;
import com.simisinc.platform.application.cms.SaveFolderCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.domain.model.cms.FolderCategory;
import com.simisinc.platform.domain.model.cms.FolderGroup;
import com.simisinc.platform.domain.model.items.PrivacyType;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Widget for displaying a system administration form to add/update folders
 *
 * @author matt rajkowski
 * @created 12/12/18 3:37 PM
 */
public class FolderFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/folder-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Check for access
    if (!context.hasRole("admin")) {
      LOG.warn("No access to modify folders");
      return null;
    }

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
    Folder folder = null;
    if (context.getRequestObject() != null) {
      folder = (Folder) context.getRequestObject();
      context.getRequest().setAttribute("folder", folder);
    } else {
      long folderId = context.getParameterAsLong("folderId");
      if (folderId > -1) {
        folder = LoadFolderCommand.loadFolderById(folderId);
        context.getRequest().setAttribute("folder", folder);
      }
    }

    // Set some form values
    List<Group> groupList = GroupRepository.findAll();
    context.getRequest().setAttribute("groupList", groupList);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Check for access
    if (!context.hasRole("admin")) {
      LOG.warn("No access to create folders");
      return null;
    }

    // Populate the fields
    Folder folderBean = new Folder();
    BeanUtils.populate(folderBean, context.getParameterMap());
    folderBean.setCreatedBy(context.getUserId());
    folderBean.setModifiedBy(context.getUserId());

    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));

    // Check for groups
    List<FolderGroup> folderGroupList = new ArrayList<>();
    List<Group> groupList = GroupRepository.findAll();
    if (groupList != null) {
      for (Group group : groupList) {
        String groupPrivacyTypeValue = context.getParameter("groupId" + group.getId() + "privacyType");
        if (StringUtils.isNotBlank(groupPrivacyTypeValue)) {
          FolderGroup folderGroup = new FolderGroup();
          folderGroup.setGroupId(group.getId());
          folderGroup.setPrivacyType(PrivacyType.getTypeIdFromString(groupPrivacyTypeValue));
          String groupAddPermission = context.getParameter("groupId" + group.getId() + "add");
          folderGroup.setAddPermission(groupAddPermission != null);
          String groupEditPermission = context.getParameter("groupId" + group.getId() + "edit");
          folderGroup.setEditPermission(groupEditPermission != null);
          String groupDeletePermission = context.getParameter("groupId" + group.getId() + "delete");
          folderGroup.setDeletePermission(groupDeletePermission != null);
          folderGroupList.add(folderGroup);
        }
      }
      if (!folderGroupList.isEmpty()) {
        folderBean.setFolderGroupList(folderGroupList);
      }
    }

    // Check for categories
    List<FolderCategory> folderCategoryList = new ArrayList<>();
    int categoryCounter = 0;
    String thisCategoryName = null;
    while ((thisCategoryName = context.getParameter("category" + categoryCounter + "name")) != null) {
      long thisCategoryId = context.getParameterAsLong("category" + categoryCounter + "id", -1);
      if (StringUtils.isNotBlank(thisCategoryName) || thisCategoryId > -1) {
        FolderCategory folderCategory = new FolderCategory();
        folderCategory.setId(thisCategoryId);
        folderCategory.setName(thisCategoryName);
        folderCategory.setEnabled(StringUtils.isNotBlank(thisCategoryName));
        folderCategoryList.add(folderCategory);
      }
      ++categoryCounter;
    }
    if (!folderCategoryList.isEmpty()) {
      folderBean.setFolderCategoryList(folderCategoryList);
    }

    // Save the folder
    Folder folder = null;
    try {
      folder = SaveFolderCommand.saveFolder(folderBean);
      if (folder == null) {
        throw new FolderException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | FolderException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(folderBean);
      context.addSharedRequestValue("returnPage", returnPage);
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Folder was saved");
    if (StringUtils.isNotBlank(returnPage)) {
      context.setRedirect(returnPage);
    } else {
      context.setRedirect("/admin/folders");
    }
    return context;
  }
}
