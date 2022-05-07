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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.cms.ValidateFileCommand;
import com.simisinc.platform.application.items.*;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemFileItem;
import com.simisinc.platform.domain.model.items.ItemFolder;
import com.simisinc.platform.infrastructure.persistence.items.ItemFolderRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.InvocationTargetException;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/19/2021 1:00 PM
 */
public class ItemFileDropZoneWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  private static String JSP = "/items/item-file-drop-zone.jsp";
  private static Log LOG = LogFactory.getLog(ItemFileDropZoneWidget.class);


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

    // Check permissions (allow drop-box permissions)
    if (!context.hasRole("admin")) {
      String defaultName = "Documents";
      ItemFolder folder = ItemFolderRepository.findByName(defaultName, item.getId());
      if (folder == null || !CheckItemFolderPermissionCommand.userHasAddPermission(folder.getId(), context.getUserId())) {
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

    // Check permissions (allow drop-box permissions)
    if (!context.hasRole("admin")) {
      String defaultName = "Documents";
      ItemFolder folder = ItemFolderRepository.findByName(defaultName, item.getId());
      if (folder == null || !CheckItemFolderPermissionCommand.userHasAddPermission(folder.getId(), context.getUserId())) {
        return null;
      }
    }

    ItemFileItem fileItemBean = null;
    try {
      // Check for a file
      fileItemBean = SaveItemFilePartCommand.saveFile(context, item);
      if (fileItemBean == null) {
        LOG.warn("File part was not found in request");
        throw new DataException("A file was not found, please choose a file and try again");
      }
//      fileItemBean.setFolderId(folder.getId());
      fileItemBean.setVersion("1.0");
      fileItemBean.setCreatedBy(context.getUserId());
      fileItemBean.setModifiedBy(context.getUserId());
      // Validate the file
      ValidateFileCommand.checkFile(fileItemBean);
      // Save it
      ItemFileItem fileItem = SaveItemFileCommand.saveFile(fileItemBean);
      if (fileItem == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
      // GET uri /show/*/assets/file/20180503171549-5/logo.png
      // yyyyMMddHHmmss
      // Return Json
      LOG.debug("Finished!");
      context.setJson("{\"location\": \"" + "/show/" + itemUniqueId + "/assets/file/" + System.currentTimeMillis() + "-" + fileItem.getId() + "/" + UrlCommand.encodeUri(fileItem.getFilename()) + "\"}");
      return context;
    } catch (DataException data) {
      // Clean up the file if it exists
      SaveItemFilePartCommand.cleanupFile(fileItemBean);
      // Let the user know
      LOG.debug(data.getMessage(), data);
      context.setErrorMessage(data.getMessage());
      return context;
    }
  }
}
