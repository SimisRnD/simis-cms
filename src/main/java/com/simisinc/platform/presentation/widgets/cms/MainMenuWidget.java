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

import java.util.ArrayList;
import java.util.List;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.LoadMenuTabsCommand;
import com.simisinc.platform.application.cms.ValidateUserAccessToWebPageCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.domain.model.cms.MenuItem;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.UserSession;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/18/21 8:57 PM
 */
public class MainMenuWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/main-menu.jsp";
  static String FLAT_JSP = "/cms/main-menu-flat.jsp";
  static String NESTED_JSP = "/cms/main-menu-nested.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine if the site menu can be shown
    boolean siteIsOnline = LoadSitePropertyCommand.loadByNameAsBoolean("site.online");
    if (!context.getUserSession().isLoggedIn() && !siteIsOnline) {
      return context;
    }

    // Check for preferences
    String view = context.getPreferences().get("view");
    boolean checkUser = "true".equals(context.getPreferences().getOrDefault("checkUser", "true"));
    boolean highlightActiveTab = Boolean.parseBoolean(context.getPreferences().getOrDefault("useHighlight", "true"));
    context.getRequest().setAttribute("useHighlight", highlightActiveTab ? "true" : "false");
    boolean highlightSubmenuItem = Boolean
        .parseBoolean(context.getPreferences().getOrDefault("useSmallHighlight", "false"));
    context.getRequest().setAttribute("useSmallHighlight", highlightSubmenuItem ? "true" : "false");
    context.getRequest().setAttribute("showAdmin", context.getPreferences().getOrDefault("showAdmin", "true"));
    context.getRequest().setAttribute("menuClass", context.getPreferences().get("class"));
    context.getRequest().setAttribute("submenuIcon", context.getPreferences().get("submenuIcon"));
    context.getRequest().setAttribute("submenuIconClass", context.getPreferences().get("submenuIconClass"));

    // Base the menu on the user
    UserSession userSession = context.getUserSession();

    // Prepare the menu
    List<MenuTab> menuTabList = LoadMenuTabsCommand.loadActiveIncludeMenuItemList();
    List<MenuTab> menuTabListToUse = new ArrayList<>();

    // Check for a collection to match the title to
    Collection collection = null;
    String collectionUniqueId = context.getCoreData().get("collectionUniqueId");
    if (collectionUniqueId != null) {
      collection = LoadCollectionCommand.loadCollectionByUniqueId(collectionUniqueId);
      context.getRequest().setAttribute("collection", collection);
    }

    int menuTabCounter = 0;
      for (MenuTab menuTab : menuTabList) {
      ++menuTabCounter;
      // Remove redundant Home (the first one)
      if (menuTabCounter == 1 && menuTab.getLink().equals("/")) {
        continue;
      }
      // Verify the content manager, or that the page has content for other users, based on content, roles and groups
      if (context.hasRole("admin") || context.hasRole("content-manager") || !checkUser ||
          ValidateUserAccessToWebPageCommand.hasAccess(menuTab.getLink(), userSession)) {
          // Copy the MenuTab, since a cache was used
          MenuTab thisMenuTab = new MenuTab();
          thisMenuTab.setName(menuTab.getName());
          thisMenuTab.setLink(menuTab.getLink());
          thisMenuTab.setIcon(menuTab.getIcon());
        // Determine if the menuTab should be highlighted
        if (highlightActiveTab) {
          // Is active when menuTab matches the page path, or the collection name is a match
          if ((menuTab.getLink().equals(context.getRequest().getPagePath())) ||
              (collection != null && collection.getName().equalsIgnoreCase(menuTab.getName()))) {
            thisMenuTab.setActive(true);
          }
        }
          // Process the sub-menu items
          if (menuTab.getMenuItemList() != null) {
            List<MenuItem> thisMenuItemList = new ArrayList<>();
            for (MenuItem menuItem : menuTab.getMenuItemList()) {
              if (ValidateUserAccessToWebPageCommand.hasAccess(menuItem.getLink(), userSession)) {
                // Copy the menu item, since a cache was used
                MenuItem thisMenuItem = new MenuItem();
                thisMenuItem.setName(menuItem.getName());
                thisMenuItem.setLink(menuItem.getLink());
              // Is active when menuItem matches the page path
              if ((highlightActiveTab || highlightSubmenuItem) &&
                  thisMenuItem.getLink().equals(context.getRequest().getPagePath())) {
                thisMenuItem.setActive(true);
              }
              thisMenuItemList.add(thisMenuItem);
              }
            }
            if (!thisMenuItemList.isEmpty()) {
              thisMenuTab.setMenuItemList(thisMenuItemList);
            }
            menuTabListToUse.add(thisMenuTab);
        }
      }
    }
    context.getRequest().setAttribute(RequestConstants.MASTER_MENU_TAB_LIST, menuTabListToUse);

    // Show the JSP
    if ("flat".equals(view)) {
      context.setJsp(FLAT_JSP);
    } else if ("nested".equals(view)) {
      context.setJsp(NESTED_JSP);
    } else {
      context.setJsp(JSP);
    }
    return context;
  }
}
