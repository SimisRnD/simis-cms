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

package com.simisinc.platform.presentation.controller.items;

import com.simisinc.platform.application.items.CheckCollectionPermissionCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadCollectionRelationshipListCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.CollectionRelationship;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemRelationship;
import com.simisinc.platform.infrastructure.persistence.items.ItemRelationshipRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/30/18 5:03 PM
 */
public class ItemRelationshipsListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/items/item-relationships.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Preferences
    String collectionUniqueId = context.getPreferences().get("collectionUniqueId");
    boolean showRemoveRelationship = ("true".equals(context.getPreferences().get("showRemoveRelationship")));

    // Load the authorized item
    Item item = LoadItemCommand.loadItemByUniqueId(context.getCoreData().get("itemUniqueId"));
    if (item == null) {
      return null;
    }
    context.getRequest().setAttribute("item", item);

    // Determine which relationships to show (or all)
    List<ItemRelationship> itemRelationshipList = null;
    if (StringUtils.isNotBlank(collectionUniqueId)) {
      // Look for a specific collection
      Collection collection = LoadCollectionCommand.loadCollectionByUniqueId(collectionUniqueId);
      if (collection != null) {
        itemRelationshipList = ItemRelationshipRepository.findRelatedItemsForItemIdInCollection(item, collection);
      }
    } else {
      // See if there are any available relationship types
      List<CollectionRelationship> collectionRelationshipList = LoadCollectionRelationshipListCommand.findAllByCollectionId(item.getCollectionId());
      if (collectionRelationshipList.isEmpty()) {
        return null;
      }
      if (collectionRelationshipList.size() > 1) {
        context.getRequest().setAttribute("showRelatedCollectionName", "true");
      }
      // Show any existing relationships
      itemRelationshipList = ItemRelationshipRepository.findRelatedItemsForItemId(item.getId());
    }

    // Skip if there are no relationships
    if (itemRelationshipList == null || itemRelationshipList.isEmpty()) {
      if (!"true".equals(context.getPreferences().getOrDefault("showWhenEmpty", "false"))) {
        LOG.debug("Skipping, no relationships found");
        return context;
      }
    }
    context.getRequest().setAttribute("itemRelationshipList", itemRelationshipList);

    // Determine if the remove relationship button is shown
    if (showRemoveRelationship) {
      // See if the user is in a collection group with "edit" permissions
      boolean canEditRelationship = CheckCollectionPermissionCommand.userHasEditPermission(item.getCollectionId(), context.getUserId());
      if (canEditRelationship) {
        LOG.debug("showRemoveRelationshipButton: yes");
        context.getRequest().setAttribute("showRemoveRelationshipButton", "true");
      }
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext action(WidgetContext context) {
    // Check the action
    String action = context.getParameter("action");
    if (!"removeRelationship".equals(action)) {
      return null;
    }
    // Load the authorized item
    Item item = LoadItemCommand.loadItemByUniqueId(context.getCoreData().get("itemUniqueId"));
    if (item == null) {
      return null;
    }
    // Check permissions
    boolean canEditRelationship = CheckCollectionPermissionCommand.userHasEditPermission(item.getCollectionId(), context.getUserId());
    if (!canEditRelationship) {
      return null;
    }
    // Check for the item to remove
    long relatedItemId = context.getParameterAsLong("relatedItemId");
    Item relatedItem = LoadItemCommand.loadItemById(relatedItemId);
    // Remove the relationship
    ItemRelationshipRepository.removeRelationship(item, relatedItem);
    return context;
  }
}
