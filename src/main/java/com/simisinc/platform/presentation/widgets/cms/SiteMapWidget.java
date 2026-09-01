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

package com.simisinc.platform.presentation.widgets.cms;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.DeleteMenuTabCommand;
import com.simisinc.platform.application.cms.LoadMenuTabsCommand;
import com.simisinc.platform.application.cms.SaveMenuTabCommand;
import com.simisinc.platform.domain.model.cms.MenuItem;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.persistence.cms.MenuItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.MenuTabRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/24/18 8:39 AM
 */
public class SiteMapWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/sitemap.jsp";

  public WidgetContext execute(WidgetContext context) {

    // The tree, so nested items are at least VISIBLE here (issue #1728). They are read-only on
    // this screen -- editing them lives in the Edit Links editor -- but showing a menu without
    // its third level would misrepresent the live site to whoever is reordering it.
    List<MenuTab> menuTabList = LoadMenuTabsCommand.findAllIncludeMenuItemTree();
    context.getRequest().setAttribute("menuTabList", menuTabList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the editor
    context.setJsp(JSP);
    return context;
  }


  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {
    // Determine the action
    String method = context.getParameter("method");
    WidgetContext updatedContext = null;
    if ("sitemap-editor".equals(method)) {
      updatedContext = processSiteMapChanges(context);
    } else {
      updatedContext = processNewTabForm(context);
    }
    // Trigger cache refresh
    CacheManager.invalidateObjectCacheKey(CacheManager.MENU_TAB_LIST);
    return updatedContext;
  }

  private WidgetContext processNewTabForm(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    LOG.debug("processNewTabForm...");

    // Populate the fields
    MenuTab menuTabBean = new MenuTab();
    BeanUtils.populate(menuTabBean, context.getParameterMap());

    // Save the record
    MenuTab menuTab = null;
    try {
      menuTab = SaveMenuTabCommand.appendNewTab(menuTabBean);
      if (menuTab == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(menuTabBean);
      return context;
    }

    // Determine the page to return to
//    context.setSuccessMessage("Menu Tab was saved");
    context.setRedirect("/admin/sitemap");
    return context;
  }


  private WidgetContext processSiteMapChanges(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    LOG.debug("processSiteMapChanges...");

    List<MenuTab> menuTabList = MenuTabRepository.findAll();
    for (MenuTab thisTab : menuTabList) {
      // Check for a renamed menu tab or icon
      String name = context.getParameter("menuTab" + thisTab.getId() + "name");
      String icon = context.getParameter("menuTab" + thisTab.getId() + "icon");
      boolean nameChanged = StringUtils.isNotBlank(name) && !name.equals(thisTab.getName());
      boolean iconChanged = StringUtils.isNotBlank(icon) && !icon.equals(thisTab.getIcon());
      if (nameChanged || iconChanged) {
        if (nameChanged) {
          thisTab.setName(name);
        }
        if (iconChanged) {
          thisTab.setIcon(icon);
        }
        try {
          SaveMenuTabCommand.renameTab(thisTab);
        } catch (DataException e) {
          LOG.error("Rename tab update error: " + e.getMessage());
        }
      }
      // Check for an added menu item
      String menuItemName = context.getParameter("menuTab" + thisTab.getId() + "menuItemName");
      if (StringUtils.isNotBlank(menuItemName)) {
        try {
          SaveMenuTabCommand.appendNewMenuItem(thisTab, menuItemName, context.getParameter("menuTab" + thisTab.getId() + "menuItemLink"));
        } catch (DataException e) {
          LOG.error("Rename tab update error: " + e.getMessage());
        }
      }
    }

    // Check for a renamed menu item
    List<MenuItem> menuItemList = MenuItemRepository.findAll();
    for (MenuItem thisMenuItem : menuItemList) {
      String name = context.getParameter("menuItem" + thisMenuItem.getId() + "name");
      if (StringUtils.isNotBlank(name) && !name.equals(thisMenuItem.getName())) {
        thisMenuItem.setName(name);
        try {
          SaveMenuTabCommand.renameMenuItem(thisMenuItem);
        } catch (DataException e) {
          LOG.error("Rename menu item update error: " + e.getMessage());
        }
      }
    }


    // The Navigation Menu Editor's "Add Tab" and delete ("x") controls used to POST to the server
    // immediately, bypassing Save/Cancel entirely -- a tab added or deleted was permanent before
    // Save was ever clicked, and Cancel could not undo it. Both are now staged client-side and only
    // reach here as part of this one batched save.

    // New tabs arrive as a list of client-generated temporary ids (never real database ids), each
    // with menuTab<tempId>name/link/icon fields using the exact same naming convention as the
    // rename/add-item handling above, just keyed by a temp id instead of a real one. Resolving each
    // to its new real id here lets a staged tab's own "Add Item" fields (also keyed by its temp id)
    // and its position in menuTabOrder below (also keyed by its temp id) be applied in this same save.
    Map<String, Long> newMenuTabIdMap = new HashMap<>();
    String newMenuTabIds = context.getParameter("newMenuTabIds");
    if (StringUtils.isNotBlank(newMenuTabIds)) {
      for (String tempId : newMenuTabIds.split(",")) {
        tempId = tempId.trim();
        if (tempId.isEmpty()) {
          continue;
        }
        MenuTab menuTabBean = new MenuTab();
        menuTabBean.setName(context.getParameter("menuTab" + tempId + "name"));
        menuTabBean.setLink(context.getParameter("menuTab" + tempId + "link"));
        menuTabBean.setIcon(context.getParameter("menuTab" + tempId + "icon"));
        try {
          MenuTab createdTab = SaveMenuTabCommand.appendNewTab(menuTabBean);
          if (createdTab != null) {
            newMenuTabIdMap.put(tempId, createdTab.getId());
            String menuItemName = context.getParameter("menuTab" + tempId + "menuItemName");
            if (StringUtils.isNotBlank(menuItemName)) {
              try {
                SaveMenuTabCommand.appendNewMenuItem(createdTab, menuItemName, context.getParameter("menuTab" + tempId + "menuItemLink"));
              } catch (DataException e) {
                LOG.error("Add menu item to new tab error: " + e.getMessage());
              }
            }
          }
        } catch (DataException e) {
          LOG.error("Create new tab error: " + e.getMessage());
        }
      }
    }

    // Staged tab/item deletions -- the "x" controls now remove the row from the page and record its
    // id client-side rather than posting a delete immediately, so nothing is removed until this save.
    String menuTabsToDelete = context.getParameter("menuTabsToDelete");
    if (StringUtils.isNotBlank(menuTabsToDelete)) {
      for (String tabIdStr : menuTabsToDelete.split(",")) {
        tabIdStr = tabIdStr.trim();
        if (tabIdStr.isEmpty()) {
          continue;
        }
        try {
          MenuTab menuTab = MenuTabRepository.findById(Long.parseLong(tabIdStr));
          DeleteMenuTabCommand.deleteMenuTab(menuTab);
        } catch (NumberFormatException | DataException e) {
          LOG.error("Delete tab error: " + e.getMessage());
        }
      }
    }

    String menuItemsToDelete = context.getParameter("menuItemsToDelete");
    if (StringUtils.isNotBlank(menuItemsToDelete)) {
      for (String itemIdStr : menuItemsToDelete.split(",")) {
        itemIdStr = itemIdStr.trim();
        if (itemIdStr.isEmpty()) {
          continue;
        }
        try {
          MenuItem menuItem = MenuItemRepository.findById(Long.parseLong(itemIdStr));
          DeleteMenuTabCommand.deleteMenuItem(menuItem);
        } catch (NumberFormatException | DataException e) {
          LOG.error("Delete menu item error: " + e.getMessage());
        }
      }
    }

    // Check for a new tab order -- entries are DOM ids like "site-map-menu-tab-container-<id>";
    // <id> is either a real database id or one of the temporary ids resolved above. A tab that
    // failed to create, or any other unresolvable value, is skipped rather than aborting the save.
    String menuTabOrder = context.getParameter("menuTabOrder");
    if (StringUtils.isNotBlank(menuTabOrder)) {
      String[] strArray = menuTabOrder.split(",");
      List<Long> resolvedOrder = new ArrayList<>();
      for (String item : strArray) {
        String token = item.substring(item.lastIndexOf("-") + 1);
        Long resolvedId = newMenuTabIdMap.get(token);
        if (resolvedId == null) {
          try {
            resolvedId = Long.parseLong(token);
          } catch (NumberFormatException e) {
            continue;
          }
        }
        resolvedOrder.add(resolvedId);
      }
      SaveMenuTabCommand.updateTabOrder(resolvedOrder.toArray(new Long[0]));
    }

    // Check for new menu item order...
    String menuItemOrder = context.getParameter("menuItemOrder");
    if (StringUtils.isNotBlank(menuItemOrder)) {
      String[] strArray = menuItemOrder.split("\\|");
      long lastMenuTabId = -1;
      int currentOrderValue = -1;
      for (int i = 0; i < strArray.length; i++) {
        String[] thisItemArray = strArray[i].split(",");
        String tab = thisItemArray[0];
        String item = thisItemArray[1];
        long menuTabId = Long.parseLong(tab.substring(tab.lastIndexOf("-") + 1));
        long menuItemId = Long.parseLong(item.substring(item.lastIndexOf("-") + 1));
        if (menuTabId != lastMenuTabId) {
          currentOrderValue = -1;
          lastMenuTabId = menuTabId;
        }
        ++currentOrderValue;
        SaveMenuTabCommand.updateMenuItemOrder(menuTabId, menuItemId, currentOrderValue);
      }
    }

    // Determine the page to return to
//    context.setSuccessMessage("Site map was saved!");
    context.setRedirect("/admin/sitemap");
    return context;
  }


  public WidgetContext delete(WidgetContext context) {
    // Execute the action
    WidgetContext updatedContext = executeDelete(context);
    // Trigger cache refresh
    CacheManager.invalidateObjectCacheKey(CacheManager.MENU_TAB_LIST);
    return updatedContext;
  }

  private WidgetContext executeDelete(WidgetContext context) {
    // Determine what's being deleted
    long menuTabId = context.getParameterAsLong("menuTabId");
    if (menuTabId != -1) {
      MenuTab menuTab = MenuTabRepository.findById(menuTabId);
      try {
        DeleteMenuTabCommand.deleteMenuTab(menuTab);
//        context.setSuccessMessage("Menu tab '" + menuTab.getName() + "' was deleted");
        context.setRedirect("/admin/sitemap");
        return context;
      } catch (Exception e) {
        context.setErrorMessage("Error. " + e.getMessage());
        context.setRedirect("/admin/sitemap");
        return context;
      }
    }

    long menuItemId = context.getParameterAsLong("menuItemId");
    if (menuItemId != -1) {
      MenuItem menuItem = MenuItemRepository.findById(menuItemId);
      try {
        DeleteMenuTabCommand.deleteMenuItem(menuItem);
//        context.setSuccessMessage("Menu item '" + menuItem.getName() + "' was deleted");
        context.setRedirect("/admin/sitemap");
        return context;
      } catch (Exception e) {
        context.setErrorMessage("Error. Menu item could not be deleted.");
        context.setRedirect("/admin/sitemap");
        return context;
      }
    }

    return context;
  }
}
