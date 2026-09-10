/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.LoadMenuTabsCommand;
import com.simisinc.platform.application.cms.ValidateUserAccessToWebPageCommand;
import com.simisinc.platform.domain.model.cms.MenuItem;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.presentation.controller.RequestConstants;

/**
 * The third navigation level has to survive the access-checking copy (issue #1771).
 *
 * <p>The widget has two paths. An admin or content manager gets the loaded tree passed through
 * untouched; everyone else gets a hand-built copy so the cached objects are not mutated and every
 * link is access-checked. That copy carried name and link only, which silently flattened the menu
 * for the whole public -- and because the admin path is the one that works, the person building the
 * menu was the one person who could not see it.
 *
 * @author SimIS Inc.
 */
class MainMenuWidgetTest extends WidgetBase {

  private static MenuItem item(String name, String link, MenuItem... children) {
    MenuItem menuItem = new MenuItem();
    menuItem.setName(name);
    menuItem.setLink(link);
    if (children.length > 0) {
      List<MenuItem> childList = new ArrayList<>();
      for (MenuItem child : children) {
        childList.add(child);
      }
      menuItem.setMenuItemList(childList);
    }
    return menuItem;
  }

  /** Solutions > Autonomous Solutions > Human Type Targets, plus a sibling with no children. */
  private static List<MenuTab> threeLevelMenu() {
    MenuTab menuTab = new MenuTab();
    menuTab.setName("Solutions");
    menuTab.setLink("/solutions");
    List<MenuItem> menuItemList = new ArrayList<>();
    menuItemList.add(item("Autonomous Solutions", "/autonomous-solutions",
        item("Human Type Targets (HTT)", "/htt"),
        item("Internal Roadmap", "/internal-roadmap")));
    menuItemList.add(item("Cybersecurity", "/cybersecurity"));
    menuTab.setMenuItemList(menuItemList);
    List<MenuTab> menuTabList = new ArrayList<>();
    menuTabList.add(menuTab);
    return menuTabList;
  }

  @SuppressWarnings("unchecked")
  private static List<MenuTab> renderedMenu(WidgetBase base) {
    return (List<MenuTab>) base.widgetContext.getRequest()
        .getAttribute(RequestConstants.MASTER_MENU_TAB_LIST);
  }

  private static MenuItem findItem(List<MenuTab> menuTabList, String name) {
    for (MenuTab menuTab : menuTabList) {
      if (menuTab.getMenuItemList() == null) {
        continue;
      }
      for (MenuItem menuItem : menuTab.getMenuItemList()) {
        if (name.equals(menuItem.getName())) {
          return menuItem;
        }
      }
    }
    return null;
  }

  @Test
  void aVisitorWithoutRolesSeesTheThirdLevel() {
    List<MenuTab> loaded = threeLevelMenu();
    try (MockedStatic<LoadMenuTabsCommand> menu = mockStatic(LoadMenuTabsCommand.class);
        MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ValidateUserAccessToWebPageCommand> access = mockStatic(
            ValidateUserAccessToWebPageCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.online")).thenReturn(true);
      menu.when(LoadMenuTabsCommand::loadActiveIncludeMenuItemList).thenReturn(loaded);
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(any(), any())).thenReturn(true);

      new MainMenuWidget().execute(widgetContext);

      MenuItem parent = findItem(renderedMenu(this), "Autonomous Solutions");
      Assertions.assertNotNull(parent, "the second level must still render");
      Assertions.assertNotNull(parent.getMenuItemList(),
          "the third level was dropped for a visitor with no roles");
      Assertions.assertEquals(2, parent.getMenuItemList().size());
      Assertions.assertEquals("Human Type Targets (HTT)", parent.getMenuItemList().get(0).getName());
      Assertions.assertEquals("/htt", parent.getMenuItemList().get(0).getLink());
    }
  }

  @Test
  void aThirdLevelItemThePageIsClosedToIsOmitted() {
    // The copy loop exists to enforce per-page access; a child must be checked like its parent,
    // not carried along because its parent passed.
    List<MenuTab> loaded = threeLevelMenu();
    try (MockedStatic<LoadMenuTabsCommand> menu = mockStatic(LoadMenuTabsCommand.class);
        MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ValidateUserAccessToWebPageCommand> access = mockStatic(
            ValidateUserAccessToWebPageCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.online")).thenReturn(true);
      menu.when(LoadMenuTabsCommand::loadActiveIncludeMenuItemList).thenReturn(loaded);
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(any(), any())).thenReturn(true);
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/internal-roadmap"), any()))
          .thenReturn(false);

      new MainMenuWidget().execute(widgetContext);

      MenuItem parent = findItem(renderedMenu(this), "Autonomous Solutions");
      Assertions.assertNotNull(parent.getMenuItemList());
      Assertions.assertEquals(1, parent.getMenuItemList().size(),
          "the restricted third-level item must not be listed");
      Assertions.assertEquals("Human Type Targets (HTT)", parent.getMenuItemList().get(0).getName());
    }
  }

  @Test
  void anItemWhoseChildrenAreAllFilteredOutStillRendersAsAPlainLink() {
    // An empty child list would still make main-menu.jsp treat it as a submenu parent: it would
    // draw the flyout arrow and open an empty panel on hover.
    List<MenuTab> loaded = threeLevelMenu();
    try (MockedStatic<LoadMenuTabsCommand> menu = mockStatic(LoadMenuTabsCommand.class);
        MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ValidateUserAccessToWebPageCommand> access = mockStatic(
            ValidateUserAccessToWebPageCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.online")).thenReturn(true);
      menu.when(LoadMenuTabsCommand::loadActiveIncludeMenuItemList).thenReturn(loaded);
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(any(), any())).thenReturn(true);
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/htt"), any()))
          .thenReturn(false);
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/internal-roadmap"), any()))
          .thenReturn(false);

      new MainMenuWidget().execute(widgetContext);

      MenuItem parent = findItem(renderedMenu(this), "Autonomous Solutions");
      Assertions.assertNotNull(parent, "the parent itself is still reachable and must render");
      Assertions.assertTrue(
          parent.getMenuItemList() == null || parent.getMenuItemList().isEmpty(),
          "no children survived the access check, so no submenu should be built");
    }
  }

  @Test
  void theCachedMenuIsNotMutatedByTheCopy() {
    // The loaded tree comes straight out of CacheManager and is shared by every request; the copy
    // loop exists so one visitor's filtering cannot become everyone's menu.
    List<MenuTab> loaded = threeLevelMenu();
    try (MockedStatic<LoadMenuTabsCommand> menu = mockStatic(LoadMenuTabsCommand.class);
        MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ValidateUserAccessToWebPageCommand> access = mockStatic(
            ValidateUserAccessToWebPageCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.online")).thenReturn(true);
      menu.when(LoadMenuTabsCommand::loadActiveIncludeMenuItemList).thenReturn(loaded);
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(any(), any())).thenReturn(true);
      access.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/internal-roadmap"), any()))
          .thenReturn(false);

      new MainMenuWidget().execute(widgetContext);

      MenuItem cachedParent = loaded.get(0).getMenuItemList().get(0);
      Assertions.assertEquals(2, cachedParent.getMenuItemList().size(),
          "the cached tree must still hold both children after one visitor was filtered");
      MenuItem renderedParent = findItem(renderedMenu(this), "Autonomous Solutions");
      Assertions.assertNotSame(cachedParent.getMenuItemList(), renderedParent.getMenuItemList(),
          "the rendered submenu must be a copy, not the cached list");
    }
  }

  @Test
  void adminOutputIsUnchanged() {
    // The admin branch passes the loaded tree through untouched; this fix must not disturb it.
    List<MenuTab> loaded = threeLevelMenu();
    setRoles(widgetContext, ADMIN);
    try (MockedStatic<LoadMenuTabsCommand> menu = mockStatic(LoadMenuTabsCommand.class);
        MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.online")).thenReturn(true);
      menu.when(LoadMenuTabsCommand::loadActiveIncludeMenuItemList).thenReturn(loaded);

      new MainMenuWidget().execute(widgetContext);

      MenuItem parent = findItem(renderedMenu(this), "Autonomous Solutions");
      Assertions.assertSame(loaded.get(0).getMenuItemList().get(0), parent);
      Assertions.assertEquals(2, parent.getMenuItemList().size());
    }
  }
}
