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

import com.simisinc.platform.domain.model.cms.MenuItem;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.persistence.cms.MenuTabRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Loads a list of menu tabs
 *
 * @author matt rajkowski
 * @created 5/1/18 10:57 AM
 */
public class LoadMenuTabsCommand {

  private static Log LOG = LogFactory.getLog(LoadMenuTabsCommand.class);

  public static List<MenuTab> loadActiveIncludeMenuItemList() {
    List<MenuTab> menuTabList = (List<MenuTab>) CacheManager.getFromObjectCache(CacheManager.MENU_TAB_LIST);
    if (menuTabList != null) {
      return menuTabList;
    }
    menuTabList = findAllActiveIncludeMenuItemList();
    if (menuTabList != null) {
      CacheManager.addToObjectCache(CacheManager.MENU_TAB_LIST, menuTabList);
    }
    return menuTabList;
  }

  public static List<MenuTab> findAllActiveIncludeMenuItemList() {
    List<MenuTab> menuTabList = MenuTabRepository.findAllActive();
    for (MenuTab menuTab : menuTabList) {
      menuTab.setMenuItemList(LoadMenuItemsCommand.findAllActiveByMenuTab(menuTab));
    }
    return menuTabList;
  }

  public static List<MenuTab> findAllIncludeMenuItemList() {
    List<MenuTab> menuTabList = MenuTabRepository.findAll();
    for (MenuTab menuTab : menuTabList) {
      menuTab.setMenuItemList(LoadMenuItemsCommand.findAllByMenuTab(menuTab));
    }
    return menuTabList;
  }

  /**
   * The full three-level tree including draft and disabled entries, for the admin editor.
   *
   * The editor has to show what is there, not what is live -- a draft item that is invisible in the
   * editor cannot be published, and a nested one would look deleted.
   */
  public static List<MenuTab> findAllIncludeMenuItemTree() {
    List<MenuTab> menuTabList = MenuTabRepository.findAll();
    for (MenuTab menuTab : menuTabList) {
      List<MenuItem> menuItemList = LoadMenuItemsCommand.findAllByMenuTab(menuTab);
      menuTab.setMenuItemList(menuItemList);
      if (menuItemList == null) {
        continue;
      }
      for (MenuItem menuItem : menuItemList) {
        menuItem.setMenuItemList(LoadMenuItemsCommand.findAllByParent(menuItem));
      }
    }
    return menuTabList;
  }

  /**
   * The full three-level tree: tabs, their items, and any items nested beneath those (issue #1728).
   *
   * Deliberately a separate method rather than a change to the two above. Every existing caller --
   * the header, the public sitemap, the admin editor, llms.txt -- keeps getting exactly the shape it
   * got before nesting existed, and opts in only when it can actually render a third level. A
   * renderer that silently received a deeper tree than it understands would drop the extra level
   * without saying so.
   */
  public static List<MenuTab> findAllActiveIncludeMenuItemTree() {
    List<MenuTab> menuTabList = MenuTabRepository.findAllActive();
    for (MenuTab menuTab : menuTabList) {
      List<MenuItem> menuItemList = LoadMenuItemsCommand.findAllActiveByMenuTab(menuTab);
      menuTab.setMenuItemList(menuItemList);
      if (menuItemList == null) {
        continue;
      }
      for (MenuItem menuItem : menuItemList) {
        menuItem.setMenuItemList(LoadMenuItemsCommand.findAllActiveByParent(menuItem));
      }
    }
    return menuTabList;
  }
}
