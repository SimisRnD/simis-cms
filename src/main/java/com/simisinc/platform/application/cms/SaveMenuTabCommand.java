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

  private static final String allowedChars = "abcdefghijklmnopqrstuvwyxz0123456789";
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
        menuTab.setLink(generateLink(menuTabBean.getName()));
      } else {
        if (!menuTabBean.getLink().startsWith("/") && !menuTabBean.getLink().startsWith("#")) {
          menuTab.setLink("/" + menuTabBean.getLink().trim());
        } else {
          menuTab.setLink(menuTabBean.getLink().trim());
        }
      }
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
    return true;
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
      menuItem.setLink(generateLink(menuItemName));
    }
    menuItem.setDraft(false);
    menuItem.setEnabled(true);
    menuItem.setItemOrder(MenuItemRepository.getNextTabOrder(menuTab));
    return MenuItemRepository.save(menuItem);
  }


  private static String generateLink(String originalName) {

    // Create a new one
    StringBuilder sb = new StringBuilder("/");
    String name = originalName.toLowerCase();
    final int len = name.length();
    char lastChar = '-';
    for (int i = 0; i < len; i++) {
      char c = name.charAt(i);
      if (allowedChars.indexOf(name.charAt(i)) > -1) {
        sb.append(c);
        lastChar = c;
      } else if (c == '&') {
        sb.append("and");
        lastChar = '&';
      } else if (c == ' ' || c == '-') {
        if (lastChar != '-') {
          sb.append("-");
        }
        lastChar = '-';
      }
    }

    // Find the next available unique instance
//    int count = 1;
//    String originalGeneratedLink = sb.toString();
    return sb.toString();
  }

}
