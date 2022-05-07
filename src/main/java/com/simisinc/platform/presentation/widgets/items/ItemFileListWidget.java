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

package com.simisinc.platform.presentation.widgets.items;

import com.simisinc.platform.application.items.*;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemFileItem;
import com.simisinc.platform.domain.model.items.ItemFolder;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.items.ItemFileItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemFileSpecification;
import com.simisinc.platform.infrastructure.persistence.items.ItemFolderRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/19/2021 1:00 PM
 */
public class ItemFileListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  private static Log LOG = LogFactory.getLog(ItemFileListWidget.class);

  private static String JSP = "/items/item-file-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Preferences
    context.getRequest().setAttribute("useViewer", context.getPreferences().getOrDefault("useViewer", "false"));
    context.getRequest().setAttribute("showLinks", context.getPreferences().getOrDefault("showLinks", "true"));
    String fileType = context.getPreferences().get("type");
    String rules = context.getPreferences().get("rules");
    String orderBy = context.getPreferences().get("orderBy");
    int withinLastDays = Integer.parseInt(context.getPreferences().getOrDefault("withinLastDays", "-1"));
    String showWhenEmpty = context.getPreferences().getOrDefault("showWhenEmpty", "true");
    context.getRequest().setAttribute("emptyMessage", context.getPreferences().getOrDefault("emptyMessage", "No documents were found"));

    // Verify access to the item
    String itemUniqueId = context.getPreferences().getOrDefault("uniqueId", context.getCoreData().get("itemUniqueId"));
    Item item = LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(itemUniqueId, context.getUserId());
    if (item == null) {
      return null;
    }
    Collection collection = LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(item.getCollectionId(), context.getUserId());
    if (collection == null) {
      return null;
    }
    context.getRequest().setAttribute("item", item);
    context.getRequest().setAttribute("collection", collection);

    // Determine the specifications
    ItemFileSpecification fileSpecification = new ItemFileSpecification();
    fileSpecification.setItemId(item.getId());
    if (withinLastDays > 0) {
      fileSpecification.setWithinLastDays(withinLastDays);
    }
    if (StringUtils.isNotBlank(fileType)) {
      fileSpecification.setFileType(fileType);
    }
    // Determine the permissions for viewing files
    if (rules != null && rules.contains("user-created")) {
      // Let the user see the files they created
      fileSpecification.setCreatedBy(context.getUserId());
    } else {
      // Enforce the folder access (like drop box rule)
      fileSpecification.setForUserId(context.getUserId());
    }

    // Determine the constraints
    DataConstraints constraints = new DataConstraints();
    if ("newest".equals(orderBy)) {
      constraints.setColumnToSortBy("created", "desc");
    } else if ("oldest".equals(orderBy)) {
      constraints.setColumnToSortBy("created", "asc");
    } else if ("reverse".equals(orderBy) || "descending".equals(orderBy)) {
      constraints.setColumnToSortBy("title", "desc");
    } else {
      constraints.setColumnToSortBy("title", "asc");
    }
    constraints.setPageSize(-1);

    // Determine permissions for UI
    String defaultName = "Documents";
    ItemFolder folder = ItemFolderRepository.findByName(defaultName, item.getId());
    boolean canEdit = false;
    boolean canDelete = false;
    if (folder != null) {
      canEdit = CheckItemFolderPermissionCommand.userHasEditPermission(folder.getId(), context.getUserId());
      canDelete = CheckItemFolderPermissionCommand.userHasDeletePermission(folder.getId(), context.getUserId());
    }
    if (context.hasRole("admin")) {
      canEdit = true;
      canDelete = true;
    }
    context.getRequest().setAttribute("canEdit", canEdit ? "true" : "false");
    context.getRequest().setAttribute("canDelete", canDelete ? "true" : "false");

    // Load the list
    List<ItemFileItem> fileItemList = ItemFileItemRepository.findAll(fileSpecification, constraints);
    if (fileItemList == null || fileItemList.isEmpty()) {
      if (!"true".equals(showWhenEmpty)) {
        return context;
      }
    }
    context.getRequest().setAttribute("fileItemList", fileItemList);

    // Show the view
    context.setJsp(JSP);
    return context;
  }

  /**
   * A file is being deleted
   *
   * @param context
   * @return
   */
  public WidgetContext delete(WidgetContext context) {

    // Verify access to the item
    String itemUniqueId = context.getPreferences().getOrDefault("uniqueId", context.getCoreData().get("itemUniqueId"));
    Item item = LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(itemUniqueId, context.getUserId());
    if (item == null) {
      return null;
    }
    Collection collection = LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(item.getCollectionId(), context.getUserId());
    if (collection == null) {
      return null;
    }

    // Check for file to be deleted
    long fileId = context.getParameterAsLong("fileId", -1);
    ItemFileItem record;
    if (context.hasRole("admin")) {
      record = LoadItemFileCommand.loadItemById(fileId);
    } else {
      record = LoadItemFileCommand.loadFileByIdForAuthorizedUser(fileId, context.getUserId(), item.getId());
    }
    if (record == null) {
      LOG.warn("File record does not exist or no access: " + fileId);
      return null;
    }

    // Determine permissions for UI
    String defaultName = "Documents";
    ItemFolder folder = ItemFolderRepository.findByName(defaultName, item.getId());
    boolean canDelete = false;
    if (folder != null) {
      canDelete = CheckItemFolderPermissionCommand.userHasDeletePermission(folder.getId(), context.getUserId());
    }
    if (context.hasRole("admin")) {
      canDelete = true;
    }
    if (!canDelete) {
      context.setErrorMessage("User does not have permission to delete the file");
      return context;
    }

    // Delete the file
    try {
      DeleteItemFileCommand.deleteFile(record);
      context.setSuccessMessage("File deleted");
      return context;
    } catch (Exception e) {
      context.setErrorMessage("Error. File could not be deleted.");
    }
    return context;
  }
}
