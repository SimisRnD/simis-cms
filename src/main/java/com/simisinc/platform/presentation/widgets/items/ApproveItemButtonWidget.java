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

import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.items.ApproveItemCommand;
import com.simisinc.platform.application.items.CheckCollectionPermissionCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/14/19 5:08 PM
 */
public class ApproveItemButtonWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/items/approve-item-button.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the item
    String itemUniqueId = context.getPreferences().get("uniqueId");
    if (StringUtils.isBlank(itemUniqueId)) {
      LOG.debug("Unique id is empty: " + itemUniqueId);
      return null;
    }
    Item item = LoadItemCommand.loadItemByUniqueId(itemUniqueId);
    if (item == null) {
      LOG.debug("Item not found: " + itemUniqueId);
      return null;
    }
    if (item.getApproved() != null) {
      return null;
    }
    LOG.debug("Setting item id: " + item.getId());
    context.getRequest().setAttribute("item", item);

    // Check user group permissions
    boolean canApproveItem = CheckCollectionPermissionCommand.userHasEditPermission(item.getCollectionId(), context.getUserId());
    if (!canApproveItem) {
      return null;
    }

    // Set request items
    context.getRequest().setAttribute("title", context.getPreferences().getOrDefault("title", "Approve this listing"));
    context.getRequest().setAttribute("buttonClass", context.getPreferences().getOrDefault("buttonClass", "success"));
    context.getRequest().setAttribute("returnPage", UrlCommand.getValidReturnPage(context.getParameter("returnPage")));

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext action(WidgetContext context) {

    // Determine what's being approved
    String itemUniqueId = context.getParameter("itemUniqueId");
    if (StringUtils.isBlank(itemUniqueId)) {
      LOG.error("Approved called, but itemUniqueId is empty");
      return context;
    }
    Item item = LoadItemCommand.loadItemByUniqueId(itemUniqueId);
    if (item == null) {
      LOG.error("Approved called, but item is not found: " + itemUniqueId);
      return context;
    }

    // Check user group permissions
    boolean canApproveItem = CheckCollectionPermissionCommand.userHasEditPermission(item.getCollectionId(), context.getUserId());
    if (!canApproveItem) {
      return null;
    }

    // Approve the item
    try {
      ApproveItemCommand.approveItem(item, context.getUserSession().getUser());
    } catch (Exception e) {
      LOG.error("Item approval error: " + e.getMessage());
    }

    // Determine the return page
    String returnPage = context.getParameter("returnPage");
    if (StringUtils.isBlank(returnPage)) {
      Collection collection = LoadCollectionCommand.loadCollectionById(item.getCollectionId());
      returnPage = collection.createListingsLink();
    }
    context.setRedirect(context.getPreferences().getOrDefault("returnPage", UrlCommand.getValidReturnPage(returnPage)));
    return context;
  }
}
