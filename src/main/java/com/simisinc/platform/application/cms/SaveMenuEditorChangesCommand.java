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

package com.simisinc.platform.application.cms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.MenuItem;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.infrastructure.persistence.cms.MenuItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.MenuTabRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Applies a posted set of navigation menu changes.
 *
 * <p>Two admin screens edit the same navigation menu -- /admin/sitemap and /admin/sitemap-editor --
 * and each had its own copy of this parsing (issue #1732). The copies had already drifted: the
 * menuItemOrder loop was duplicated verbatim, the menuTabOrder loop existed in two versions where
 * only one resolved staged temporary ids, and the tab and item rename blocks were duplicated with
 * one screen additionally handling links. Every menu feature was therefore paid for twice, or paid
 * for once and silently missing from the other screen.
 *
 * <p>The parsing now lives here once. Each step is a no-op when its parameters are absent, which is
 * exactly how the two screens differ: a screen gets the handling for the fields its form actually
 * posts, and nothing else. That is what makes this safe to share -- neither screen gains or loses
 * behaviour by calling a method that also understands the other screen's fields.
 *
 * @author matt rajkowski
 */
public class SaveMenuEditorChangesCommand {

  private static Log LOG = LogFactory.getLog(SaveMenuEditorChangesCommand.class);

  /**
   * Applies every menu change present in the request, in an order the later steps depend on: a
   * staged tab has to exist before its position can be recorded, and existing records are renamed
   * before anything is created or removed so a rename is never applied to a row that is about to go.
   */
  public static void applyChanges(WidgetContext context) {
    applyTabChanges(context);
    applyMenuItemChanges(context);
    Map<String, Long> newMenuTabIdMap = createStagedTabs(context);
    applyStagedDeletions(context);
    applyTabOrder(context, newMenuTabIdMap);
    applyMenuItemOrder(context);
    applyMenuSubItemOrder(context);
  }

  /** Renames, re-icons and re-links existing tabs, and appends any item added under one. */
  private static void applyTabChanges(WidgetContext context) {
    List<MenuTab> menuTabList = MenuTabRepository.findAll();
    for (MenuTab thisTab : menuTabList) {
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
      String link = context.getParameter("menuTab" + thisTab.getId() + "link");
      if (StringUtils.isNotBlank(link) && !link.equals(thisTab.getLink())) {
        thisTab.setLink(link.trim());
        try {
          SaveMenuTabCommand.updateTabLink(thisTab);
        } catch (DataException e) {
          LOG.error("Tab link update error: " + e.getMessage());
        }
      }
      String menuItemName = context.getParameter("menuTab" + thisTab.getId() + "menuItemName");
      if (StringUtils.isNotBlank(menuItemName)) {
        try {
          SaveMenuTabCommand.appendNewMenuItem(thisTab, menuItemName,
              context.getParameter("menuTab" + thisTab.getId() + "menuItemLink"));
        } catch (DataException e) {
          LOG.error("Add menu item error: " + e.getMessage());
        }
      }
    }
  }

  /** Renames and re-links existing menu items, at any level. */
  private static void applyMenuItemChanges(WidgetContext context) {
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
      String link = context.getParameter("menuItem" + thisMenuItem.getId() + "link");
      if (StringUtils.isNotBlank(link) && !link.equals(thisMenuItem.getLink())) {
        thisMenuItem.setLink(link.trim());
        try {
          SaveMenuTabCommand.updateMenuItemLink(thisMenuItem);
        } catch (DataException e) {
          LOG.error("Menu item link update error: " + e.getMessage());
        }
      }
    }
  }

  /**
   * Creates the tabs staged client-side by the "Add Tab" control.
   *
   * <p>Those controls used to POST immediately, so a tab was permanent before Save was clicked and
   * Cancel could not undo it. They are now staged and arrive here as client-generated temporary ids
   * ("new1", "new2" -- deliberately non-numeric so they can never collide with a database id), each
   * carrying menuTab&lt;tempId&gt;name/link/icon fields in the same naming convention real tabs use.
   *
   * @return the temporary id to real id mapping, so a staged tab's position can be resolved below
   */
  private static Map<String, Long> createStagedTabs(WidgetContext context) {
    Map<String, Long> newMenuTabIdMap = new HashMap<>();
    String newMenuTabIds = context.getParameter("newMenuTabIds");
    if (StringUtils.isBlank(newMenuTabIds)) {
      return newMenuTabIdMap;
    }
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
              SaveMenuTabCommand.appendNewMenuItem(createdTab, menuItemName,
                  context.getParameter("menuTab" + tempId + "menuItemLink"));
            } catch (DataException e) {
              LOG.error("Add menu item to new tab error: " + e.getMessage());
            }
          }
        }
      } catch (DataException e) {
        LOG.error("Create new tab error: " + e.getMessage());
      }
    }
    return newMenuTabIdMap;
  }

  /**
   * Removes the tabs and items staged by the "x" controls, which record an id client-side rather
   * than posting a delete immediately, so nothing is removed until this save.
   */
  private static void applyStagedDeletions(WidgetContext context) {
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
  }

  /**
   * Records the tab order. Entries are DOM ids like "site-map-menu-tab-container-&lt;id&gt;", where
   * &lt;id&gt; is either a real database id or one of the temporary ids resolved above. A tab that
   * failed to create, or any other unresolvable value, is skipped rather than aborting the save.
   */
  private static void applyTabOrder(WidgetContext context, Map<String, Long> newMenuTabIdMap) {
    String menuTabOrder = context.getParameter("menuTabOrder");
    if (StringUtils.isBlank(menuTabOrder)) {
      return;
    }
    List<Long> resolvedOrder = new ArrayList<>();
    for (String item : menuTabOrder.split(",")) {
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

  /** Records the second-level order: pipe-separated "tabDomId,itemDomId" pairs. */
  private static void applyMenuItemOrder(WidgetContext context) {
    String menuItemOrder = context.getParameter("menuItemOrder");
    if (StringUtils.isBlank(menuItemOrder)) {
      return;
    }
    long lastMenuTabId = -1;
    int currentOrderValue = -1;
    for (String pair : menuItemOrder.split("\\|")) {
      String[] thisItemArray = pair.split(",");
      if (thisItemArray.length < 2) {
        continue;
      }
      long menuTabId = trailingId(thisItemArray[0]);
      long menuItemId = trailingId(thisItemArray[1]);
      if (menuTabId == -1 || menuItemId == -1) {
        continue;
      }
      if (menuTabId != lastMenuTabId) {
        currentOrderValue = -1;
        lastMenuTabId = menuTabId;
      }
      ++currentOrderValue;
      SaveMenuTabCommand.updateMenuItemOrder(menuTabId, menuItemId, currentOrderValue);
    }
  }

  /** Records the third-level order (issue #1728): the same pair shape, one level down. */
  private static void applyMenuSubItemOrder(WidgetContext context) {
    String menuSubItemOrder = context.getParameter("menuSubItemOrder");
    if (StringUtils.isBlank(menuSubItemOrder)) {
      return;
    }
    long lastParentMenuItemId = -1;
    int currentOrderValue = -1;
    for (String pair : menuSubItemOrder.split("\\|")) {
      String[] thisItemArray = pair.split(",");
      if (thisItemArray.length < 2) {
        continue;
      }
      long parentMenuItemId = trailingId(thisItemArray[0]);
      long menuItemId = trailingId(thisItemArray[1]);
      if (parentMenuItemId == -1 || menuItemId == -1) {
        continue;
      }
      if (parentMenuItemId != lastParentMenuItemId) {
        currentOrderValue = -1;
        lastParentMenuItemId = parentMenuItemId;
      }
      ++currentOrderValue;
      SaveMenuTabCommand.updateMenuSubItemOrder(parentMenuItemId, menuItemId, currentOrderValue);
    }
  }

  /**
   * The database id trailing a DOM id, or -1 when there isn't one.
   * <p>
   * Previously this was an unguarded Long.parseLong inside the ordering loops, so one malformed
   * entry threw NumberFormatException out of the whole save -- taking every later change in the
   * same post with it, after earlier ones had already been written. Skipping the entry keeps a
   * batched save from being half-applied because of a single unreadable token.
   */
  private static long trailingId(String domId) {
    try {
      return Long.parseLong(domId.substring(domId.lastIndexOf("-") + 1));
    } catch (NumberFormatException e) {
      // logged rather than skipped quietly: a token that cannot be read means the form and this
      // parser disagree about the wire format, which is worth finding in a log
      LOG.warn("Skipping unreadable menu order entry: " + domId);
      return -1;
    }
  }

  /** Deletes the single tab or item named by a delete request, shared by both editor screens. */
  public static void applyDeleteRequest(WidgetContext context) throws DataException {
    long menuTabId = context.getParameterAsLong("menuTabId");
    if (menuTabId != -1) {
      DeleteMenuTabCommand.deleteMenuTab(MenuTabRepository.findById(menuTabId));
      return;
    }
    long menuItemId = context.getParameterAsLong("menuItemId");
    if (menuItemId != -1) {
      DeleteMenuTabCommand.deleteMenuItem(MenuItemRepository.findById(menuItemId));
    }
  }
}
