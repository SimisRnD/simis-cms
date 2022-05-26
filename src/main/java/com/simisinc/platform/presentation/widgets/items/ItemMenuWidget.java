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

import com.simisinc.platform.application.items.LoadCategoryCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.domain.model.items.*;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import com.simisinc.platform.infrastructure.persistence.items.CollectionTabRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.widgets.cms.PreferenceEntriesList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.simisinc.platform.presentation.controller.RequestConstants.*;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/17/18 2:23 PM
 */
public class ItemMenuWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String EXTENDED_MENU_JSP = "/items/item-extended-menu.jsp";
  static String JSP = "/items/item-menu.jsp";

  public WidgetContext execute(WidgetContext context) {

    boolean adminMode = Boolean.parseBoolean(context.getPreferences().getOrDefault("adminMode", "false"));
    if (adminMode) {
      return executeAdminMode(context);
    }

    // Page parameters
    String itemUniqueId = context.getCoreData().get("itemUniqueId");

    // Load the authorized item
    Item item = LoadItemCommand.loadItemByUniqueId(itemUniqueId);
    if (item == null) {
      return null;
    }
    context.getRequest().setAttribute("item", item);

    // Check access to the collection
    Collection collection = LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(item.getCollectionId(), context.getUserId());
    if (collection == null) {
      return null;
    }
    context.getRequest().setAttribute("collection", collection);

    // Use information from the category
    if (item.getCategoryId() > -1) {
      Category category = LoadCategoryCommand.loadCategoryById(item.getCategoryId());
      if (category != null) {
        context.getRequest().setAttribute("category", category);
      }
    }

    // Determine the tabs to display
    List<ItemTab> itemTabList = new ArrayList<>();

    // Check the tab preferences...
    PreferenceEntriesList entriesList = context.getPreferenceAsDataList("tabs");
    if (entriesList != null && !entriesList.isEmpty()) {
      for (Map<String, String> valueMap : entriesList) {
        ItemTab itemTab = new ItemTab();
        itemTab.setName(valueMap.get("name"));
        itemTab.setHref(valueMap.get("href"));
        itemTab.setIsActive(valueMap.containsKey("isActive") && "true".equals(valueMap.get("isActive")));
        itemTabList.add(itemTab);
      }
    } else {
      // Look for defaults in the configuration
      String pagePath = (String) context.getRequest().getAttribute(WEB_PAGE_PATH);
      List<CollectionTab> collectionTabList = CollectionTabRepository.findAllByCollectionId(collection.getId());
      if (collectionTabList != null) {
        for (CollectionTab tab : collectionTabList) {
          if (!tab.getEnabled()) {
            continue;
          }
          String link = "/show/" + item.getUniqueId() + tab.getLink();
          if ("/".equals(tab.getLink())) {
            link = "/show/" + item.getUniqueId();
          }
          ItemTab itemTab = new ItemTab();
          itemTab.setName(tab.getName());
          itemTab.setHref(context.getContextPath() + link);
          if (link.equals(pagePath)) {
            itemTab.setIsActive(true);
          }
          itemTabList.add(itemTab);
        }
      }
    }

    // Show the menu
    if ((!itemTabList.isEmpty() && itemTabList.size() > 1)) {
      context.getRequest().setAttribute("itemTabList", itemTabList);
    }

    // Determine the view
    if ("extended".equals(context.getPreferences().get("view"))) {
      context.setJsp(EXTENDED_MENU_JSP);
    } else {
      context.setJsp(JSP);
    }
    return context;
  }

  /**
   * Use the actual widget in admin to show what the tab is going to look like in the CMS
   *
   * @param context
   * @return
   */
  private WidgetContext executeAdminMode(WidgetContext context) {

    // Determine the parent collection
    long collectionId = context.getParameterAsLong("collectionId");
    Collection collection = CollectionRepository.findById(collectionId);
    if (collection == null) {
      context.setErrorMessage("Error. Collection was not found.");
      return context;
    }
    context.getRequest().setAttribute("collection", collection);

    // Construct an Item from the Collection
    Item item = new Item();
    item.setName(collection.getName());
    context.getRequest().setAttribute("item", item);

    // Determine the tabs to display
    //String pagePath = (String) context.getRequest().getAttribute(WEB_PAGE_PATH);
    List<ItemTab> itemTabList = new ArrayList<>();
    List<CollectionTab> collectionTabList = CollectionTabRepository.findAllByCollectionId(collection.getId());
    if (collectionTabList != null) {
      for (CollectionTab tab : collectionTabList) {
        if (!tab.getEnabled()) {
          continue;
        }
        ItemTab itemTab = new ItemTab();
        itemTab.setName(tab.getName());
        itemTab.setHref("#");
        if (itemTabList.isEmpty()) {
          itemTab.setIsActive(true);
        }
        itemTabList.add(itemTab);
      }
      context.getRequest().setAttribute("itemTabList", itemTabList);
    }
    context.setJsp(JSP);
    return context;
  }
}
