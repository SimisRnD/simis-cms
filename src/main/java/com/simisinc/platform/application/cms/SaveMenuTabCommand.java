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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.MenuItem;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.infrastructure.persistence.cms.MenuItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.MenuTabRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Validates and saves menu tab objects
 *
 * @author matt rajkowski
 * @created 5/1/18 8:43 AM
 */
public class SaveMenuTabCommand {

  private static Log LOG = LogFactory.getLog(SaveMenuTabCommand.class);

  public static MenuTab appendNewTab(MenuTab menuTabBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(menuTabBean.getName())) {
      errorMessages.append("A tab name is required");
    }
    if ("/".equals(StringUtils.trim(menuTabBean.getName()))) {
      errorMessages.append("A valid tab name is required");
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    MenuTab menuTab;
    if (menuTabBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      menuTab = MenuTabRepository.findById(menuTabBean.getId());
      if (menuTab == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      menuTab = new MenuTab();
      if (StringUtils.isBlank(menuTabBean.getLink())) {
        menuTab.setLink("/" + GenerateLinkFromNameCommand.getLink(menuTabBean.getName()));
      } else {
        if (!menuTabBean.getLink().startsWith("/") && !menuTabBean.getLink().startsWith("#")) {
          menuTab.setLink("/" + menuTabBean.getLink().trim());
        } else {
          menuTab.setLink(menuTabBean.getLink().trim());
        }
      }
      menuTab.setIcon(menuTabBean.getIcon());
      menuTab.setDraft(false);
      menuTab.setEnabled(true);
      menuTab.setTabOrder(MenuTabRepository.getNextTabOrder());
    }
    menuTab.setName(menuTabBean.getName());

    return MenuTabRepository.save(menuTab);
  }

  public static MenuTab renameTab(MenuTab menuTabBean) throws DataException {
    return MenuTabRepository.save(menuTabBean);
  }

  public static MenuTab updateTabLink(MenuTab menuTabBean) throws DataException {
    if (!menuTabBean.getLink().startsWith("/") && !menuTabBean.getLink().startsWith("#")) {
      menuTabBean.setLink("/" + menuTabBean.getLink());
    }
    return MenuTabRepository.save(menuTabBean);
  }

  public static MenuItem renameMenuItem(MenuItem menuItemBean) throws DataException {
    return MenuItemRepository.update(menuItemBean);
  }

  public static MenuItem updateMenuItemLink(MenuItem menuItemBean) throws DataException {
    if (!menuItemBean.getLink().startsWith("/")) {
      menuItemBean.setLink("/" + menuItemBean.getLink());
    }
    return MenuItemRepository.update(menuItemBean);
  }

  public static boolean updateTabOrder(Long[] menuTabOrder) {
    if (menuTabOrder == null || menuTabOrder.length == 0) {
      return false;
    }
    int currentTabOrder = 1;
    for (Long menuTabId : menuTabOrder) {
      MenuTab menuTab = MenuTabRepository.findById(menuTabId);
      if (menuTab != null) {
        ++currentTabOrder;
        menuTab.setTabOrder(currentTabOrder);
        MenuTabRepository.save(menuTab);
      }
    }
    MenuTab homeTab = MenuTabRepository.findByLink("/");
    if (homeTab != null) {
      homeTab.setTabOrder(0);
      MenuTabRepository.save(homeTab);
    }
    return true;
  }

  public static boolean updateMenuItemOrder(long menuTabId, long menuItemId, int currentOrderValue) {
    MenuTab menuTab = MenuTabRepository.findById(menuTabId);
    if (menuTab == null) {
      return false;
    }
    MenuItem menuItem = MenuItemRepository.findById(menuItemId);
    if (menuItem == null) {
      return false;
    }
    menuItem.setMenuTabId(menuTabId);
    menuItem.setItemOrder(currentOrderValue);
    MenuItemRepository.update(menuItem);
    // A top-level item can be dragged to a different tab, and anything nested beneath it travels
    // with it. Those children carry their own menu_tab_id, so without this they keep pointing at
    // the tab they came from -- invisible in the menu (which walks parent links) but wrong for any
    // query that filters by tab, including the editor's own redraw (issue #1728).
    for (MenuItem childMenuItem : MenuItemRepository.findAllByParent(menuItem)) {
      if (childMenuItem.getMenuTabId() != menuTabId) {
        childMenuItem.setMenuTabId(menuTabId);
        MenuItemRepository.update(childMenuItem);
      }
    }
    return true;
  }

  /**
   * Places a menu item beneath another menu item, the third navigation level (issue #1728).
   *
   * Mirrors {@link #updateMenuItemOrder} one level down, and additionally keeps the child's
   * menu_tab_id equal to its parent's so the two can never disagree about which tab they belong to.
   *
   * Depth is capped at three: a parent that is itself nested is rejected rather than silently
   * accepted, because nothing renders a fourth level and a row that deep would simply vanish from
   * the menu with no error anywhere.
   */
  public static boolean updateMenuSubItemOrder(long parentMenuItemId, long menuItemId, int currentOrderValue) {
    if (parentMenuItemId == menuItemId) {
      LOG.warn("An item cannot be its own parent: " + menuItemId);
      return false;
    }
    MenuItem parentMenuItem = MenuItemRepository.findById(parentMenuItemId);
    if (parentMenuItem == null) {
      return false;
    }
    if (parentMenuItem.hasParentMenuItem()) {
      LOG.warn("Menu nesting is limited to three levels; cannot nest under an already-nested item: "
          + parentMenuItemId);
      return false;
    }
    MenuItem menuItem = MenuItemRepository.findById(menuItemId);
    if (menuItem == null) {
      return false;
    }
    menuItem.setParentMenuItemId(parentMenuItemId);
    menuItem.setMenuTabId(parentMenuItem.getMenuTabId());
    menuItem.setItemOrder(currentOrderValue);
    MenuItemRepository.update(menuItem);
    return true;
  }

  /**
   * Creates a third-level item beneath an existing menu item (issue #1728).
   *
   * <p>Until this existed the third level could be stored, reordered, reparented, rendered and
   * searched, but never created: the only code that set a parent was updateMenuSubItemOrder, reached
   * from the drag-and-drop wire format, which can only move an item that is nested already. There
   * was no way to nest the first one short of writing to the database by hand.
   *
   * <p>The depth cap is the same rule updateMenuSubItemOrder enforces, checked here too rather than
   * trusted: nesting is three levels, so an item that is itself nested cannot take children. Both
   * paths that can set a parent now refuse the fourth level.
   */
  public static MenuItem appendNewSubMenuItem(MenuItem parentMenuItem, String menuItemName, String menuItemLink)
      throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    if (parentMenuItem == null || parentMenuItem.getId() == null || parentMenuItem.getId() < 1) {
      errorMessages.append("A parent menu item is required");
    } else if (parentMenuItem.hasParentMenuItem()) {
      errorMessages.append("Menu nesting is limited to three levels, so this item cannot have sub-items");
    }
    if (StringUtils.isBlank(menuItemName)) {
      if (errorMessages.length() > 0) {
        errorMessages.append("; ");
      }
      errorMessages.append("The sub-item's name is required");
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    MenuItem menuItem = new MenuItem();
    // The tab comes from the parent, not from the form: a child always belongs to the tab its
    // parent is in, and letting the two disagree is how an item ends up rendered under one tab and
    // ordered under another.
    menuItem.setMenuTabId(parentMenuItem.getMenuTabId());
    menuItem.setParentMenuItemId(parentMenuItem.getId());
    menuItem.setName(menuItemName);
    if (StringUtils.isNotBlank(menuItemLink)) {
      if (!menuItemLink.startsWith("/")) {
        menuItemLink = "/" + menuItemLink.trim();
      }
      menuItem.setLink(menuItemLink);
    } else {
      menuItem.setLink("/" + GenerateLinkFromNameCommand.getLink(menuItemName));
    }
    menuItem.setDraft(false);
    menuItem.setEnabled(true);
    menuItem.setItemOrder(MenuItemRepository.getNextSubItemOrder(parentMenuItem));
    return MenuItemRepository.save(menuItem);
  }

  public static MenuItem appendNewMenuItem(MenuTab menuTab, String menuItemName, String menuItemLink) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(menuItemName)) {
      errorMessages.append("The item's name is required");
    }
    if ("/".equals(StringUtils.trim(menuTab.getName()))) {
      errorMessages.append("A valid tab name is required");
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    MenuItem menuItem = new MenuItem();
    menuItem.setMenuTabId(menuTab.getId());
    menuItem.setName(menuItemName);
    if (StringUtils.isNotBlank(menuItemLink)) {
      if (!menuItemLink.startsWith("/")) {
        menuItemLink = "/" + menuItemLink.trim();
      }
      menuItem.setLink(menuItemLink);
    } else {
      menuItem.setLink("/" + GenerateLinkFromNameCommand.getLink(menuItemName));
    }
    menuItem.setDraft(false);
    menuItem.setEnabled(true);
    menuItem.setItemOrder(MenuItemRepository.getNextTabOrder(menuTab));
    return MenuItemRepository.save(menuItem);
  }

}
