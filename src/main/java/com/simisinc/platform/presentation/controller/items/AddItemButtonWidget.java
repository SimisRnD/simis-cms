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
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

import org.apache.commons.lang3.StringUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 9/26/18 1:11 PM
 */
public class AddItemButtonWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/items/add-item-button.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Form Permission
    String requiresPermissionValue = context.getPreferences().getOrDefault("requiresPermission", "true");
    boolean requiresPermission = "true".equals(requiresPermissionValue);

    // Determine the collection
    String collectionUniqueId = context.getPreferences().get("collectionUniqueId");
    if (StringUtils.isBlank(collectionUniqueId)) {
      LOG.debug("collectionUniqueId is empty: " + collectionUniqueId);
      return null;
    }
    Collection collection = LoadCollectionCommand.loadCollectionByUniqueId(collectionUniqueId);
    if (collection == null) {
      LOG.debug("Collection not found: " + collectionUniqueId);
      return null;
    }
    context.getRequest().setAttribute("collection", collection);

    // Check user group permissions
    boolean canAddItem = CheckCollectionPermissionCommand.userHasAddPermission(collection.getId(), context.getUserId());
    if (requiresPermission && !canAddItem) {
      return null;
    }

    // Preferences
    context.getRequest().setAttribute("buttonName", context.getPreferences().getOrDefault("buttonName", "Add an item"));
    context.getRequest().setAttribute("addUrl", context.getPreferences().get("addUrl"));
    context.getRequest().setAttribute("returnPage", context.getPreferences().getOrDefault("returnPage", null));

    context.setJsp(JSP);
    return context;
  }
}
