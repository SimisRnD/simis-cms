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

import java.lang.reflect.InvocationTargetException;
import java.util.List;

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

    List<MenuTab> menuTabList = LoadMenuTabsCommand.findAllIncludeMenuItemList();
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
      // Check for a renamed menu tab
      String name = context.getParameter("menuTab" + thisTab.getId() + "name");
      if (StringUtils.isNotBlank(name) && !name.equals(thisTab.getName())) {
        thisTab.setName(name);
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


    // Check for a new tab order
    String menuTabOrder = context.getParameter("menuTabOrder");
    if (StringUtils.isNotBlank(menuTabOrder)) {
      String[] strArray = menuTabOrder.split(",");
      Long[] longArray = new Long[strArray.length];
      for (int i = 0; i < strArray.length; i++) {
        String item = strArray[i];
        longArray[i] = Long.parseLong(item.substring(item.lastIndexOf("-") + 1));
      }
      SaveMenuTabCommand.updateTabOrder(longArray);
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
