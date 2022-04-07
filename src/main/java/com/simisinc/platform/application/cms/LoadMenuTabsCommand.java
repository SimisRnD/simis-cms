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

import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.persistence.cms.MenuTabRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Description
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
}
