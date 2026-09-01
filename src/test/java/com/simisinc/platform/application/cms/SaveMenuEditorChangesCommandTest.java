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

package com.simisinc.platform.application.cms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.cms.MenuItem;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.infrastructure.persistence.cms.MenuItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.MenuTabRepository;

/**
 * The navigation menu parsing that /admin/sitemap and /admin/sitemap-editor used to hold a copy of
 * each (issue #1732).
 *
 * <p>What matters most here is the property that makes one shared parser safe for two screens with
 * different forms: every step is a no-op when its parameters are absent. Each screen therefore gets
 * the handling for the fields it actually posts and nothing more, which is why calling a method that
 * also understands the other screen's fields cannot give a screen behaviour its form cannot reach.
 *
 * @author SimIS Inc.
 */
class SaveMenuEditorChangesCommandTest extends WidgetBase {

  private MenuTab tab(long id, String name, String link) {
    MenuTab menuTab = new MenuTab();
    menuTab.setId(id);
    menuTab.setName(name);
    menuTab.setLink(link);
    return menuTab;
  }

  private MenuItem item(long id, String name, String link) {
    MenuItem menuItem = new MenuItem();
    menuItem.setId(id);
    menuItem.setName(name);
    menuItem.setLink(link);
    return menuItem;
  }

  @Test
  void anEmptyPostChangesNothingAtAll() {
    // the property the two screens rely on: a screen that posts none of these fields gets none of
    // the behaviour, so sharing one parser between two different forms is safe
    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class);
        MockedStatic<MenuItemRepository> menuItemRepository = mockStatic(MenuItemRepository.class);
        MockedStatic<SaveMenuTabCommand> saveMenuTabCommand = mockStatic(SaveMenuTabCommand.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(List.of(tab(1L, "Home", "/")));
      menuItemRepository.when(MenuItemRepository::findAll).thenReturn(Collections.<MenuItem> emptyList());

      SaveMenuEditorChangesCommand.applyChanges(widgetContext);

      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.renameTab(any()), never());
      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.updateTabLink(any()), never());
      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.updateTabOrder(any()), never());
      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.updateMenuItemOrder(anyLong(), anyLong(), anyInt()), never());
      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.updateMenuSubItemOrder(anyLong(), anyLong(), anyInt()), never());
    }
  }

  @Test
  void theSecondLevelOrderRestartsAtZeroForEachTab() {
    // the loop that existed verbatim in both widgets: order is per tab, so the counter has to reset
    // when the tab changes or the second tab's items continue the first tab's numbering
    addQueryParameter(widgetContext, "menuItemOrder",
        "tab-1,item-10|tab-1,item-11|tab-2,item-20");

    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class);
        MockedStatic<MenuItemRepository> menuItemRepository = mockStatic(MenuItemRepository.class);
        MockedStatic<SaveMenuTabCommand> saveMenuTabCommand = mockStatic(SaveMenuTabCommand.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(Collections.<MenuTab> emptyList());
      menuItemRepository.when(MenuItemRepository::findAll).thenReturn(Collections.<MenuItem> emptyList());

      SaveMenuEditorChangesCommand.applyChanges(widgetContext);

      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.updateMenuItemOrder(1L, 10L, 0));
      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.updateMenuItemOrder(1L, 11L, 1));
      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.updateMenuItemOrder(2L, 20L, 0));
    }
  }

  @Test
  void theThirdLevelOrderRestartsAtZeroForEachParentItem() {
    addQueryParameter(widgetContext, "menuSubItemOrder",
        "item-10,item-100|item-10,item-101|item-11,item-110");

    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class);
        MockedStatic<MenuItemRepository> menuItemRepository = mockStatic(MenuItemRepository.class);
        MockedStatic<SaveMenuTabCommand> saveMenuTabCommand = mockStatic(SaveMenuTabCommand.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(Collections.<MenuTab> emptyList());
      menuItemRepository.when(MenuItemRepository::findAll).thenReturn(Collections.<MenuItem> emptyList());

      SaveMenuEditorChangesCommand.applyChanges(widgetContext);

      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.updateMenuSubItemOrder(10L, 100L, 0));
      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.updateMenuSubItemOrder(10L, 101L, 1));
      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.updateMenuSubItemOrder(11L, 110L, 0));
    }
  }

  @Test
  void anUnreadableOrderEntryIsSkippedRatherThanAbortingTheWholeSave() {
    // this used to be an unguarded Long.parseLong: one bad token threw out of the entire save, so
    // the changes already written stayed and everything after them was silently lost
    addQueryParameter(widgetContext, "menuItemOrder",
        "tab-1,item-10|tab-bogus,item-oops|tab-2,item-20");

    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class);
        MockedStatic<MenuItemRepository> menuItemRepository = mockStatic(MenuItemRepository.class);
        MockedStatic<SaveMenuTabCommand> saveMenuTabCommand = mockStatic(SaveMenuTabCommand.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(Collections.<MenuTab> emptyList());
      menuItemRepository.when(MenuItemRepository::findAll).thenReturn(Collections.<MenuItem> emptyList());

      SaveMenuEditorChangesCommand.applyChanges(widgetContext);

      // the entry after the unreadable one still gets written -- that is the whole point
      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.updateMenuItemOrder(1L, 10L, 0));
      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.updateMenuItemOrder(2L, 20L, 0));
    }
  }

  @Test
  void aStagedTabIsCreatedAndItsPositionResolvesToTheRealId() {
    // temp ids are deliberately non-numeric ("new1"), so they can never collide with a database id
    addQueryParameter(widgetContext, "newMenuTabIds", "new1");
    addQueryParameter(widgetContext, "menuTabnew1name", "Contract Vehicles");
    addQueryParameter(widgetContext, "menuTabnew1link", "/contract-vehicles");
    addQueryParameter(widgetContext, "menuTabOrder",
        "site-map-menu-tab-container-1,site-map-menu-tab-container-new1");

    MenuTab created = tab(42L, "Contract Vehicles", "/contract-vehicles");

    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class);
        MockedStatic<MenuItemRepository> menuItemRepository = mockStatic(MenuItemRepository.class);
        MockedStatic<SaveMenuTabCommand> saveMenuTabCommand = mockStatic(SaveMenuTabCommand.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(Collections.<MenuTab> emptyList());
      menuItemRepository.when(MenuItemRepository::findAll).thenReturn(Collections.<MenuItem> emptyList());
      saveMenuTabCommand.when(() -> SaveMenuTabCommand.appendNewTab(any())).thenReturn(created);

      SaveMenuEditorChangesCommand.applyChanges(widgetContext);

      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.updateTabOrder(new Long[] { 1L, 42L }));
    }
  }

  @Test
  void aStagedTabThatFailedToCreateDoesNotBreakTheOrderOfTheRest() {
    // appendNewTab returning null leaves "new1" unresolvable; the real tabs around it must still be
    // ordered rather than the whole updateTabOrder call being lost
    addQueryParameter(widgetContext, "newMenuTabIds", "new1");
    addQueryParameter(widgetContext, "menuTabnew1name", "Doomed");
    addQueryParameter(widgetContext, "menuTabOrder",
        "site-map-menu-tab-container-1,site-map-menu-tab-container-new1,site-map-menu-tab-container-3");

    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class);
        MockedStatic<MenuItemRepository> menuItemRepository = mockStatic(MenuItemRepository.class);
        MockedStatic<SaveMenuTabCommand> saveMenuTabCommand = mockStatic(SaveMenuTabCommand.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(Collections.<MenuTab> emptyList());
      menuItemRepository.when(MenuItemRepository::findAll).thenReturn(Collections.<MenuItem> emptyList());
      saveMenuTabCommand.when(() -> SaveMenuTabCommand.appendNewTab(any())).thenReturn(null);

      SaveMenuEditorChangesCommand.applyChanges(widgetContext);

      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.updateTabOrder(new Long[] { 1L, 3L }));
    }
  }

  @Test
  void aLinkChangeIsSavedAndAnUnchangedLinkIsNot() {
    MenuTab about = tab(5L, "About", "/about");
    MenuItem team = item(50L, "Team", "/team");

    addQueryParameter(widgetContext, "menuTab5link", "/about-us");
    addQueryParameter(widgetContext, "menuItem50link", "/team"); // unchanged

    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class);
        MockedStatic<MenuItemRepository> menuItemRepository = mockStatic(MenuItemRepository.class);
        MockedStatic<SaveMenuTabCommand> saveMenuTabCommand = mockStatic(SaveMenuTabCommand.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(List.of(about));
      menuItemRepository.when(MenuItemRepository::findAll).thenReturn(List.of(team));

      SaveMenuEditorChangesCommand.applyChanges(widgetContext);

      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.updateTabLink(about));
      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.updateMenuItemLink(any()), never());
    }
  }

  @Test
  void aBlankNameIsNotTreatedAsARename() {
    // the locked first ("Home") tab renders no Name/Link/Icon inputs on either screen, so its
    // parameters arrive absent -- saving must not try to rename it to null
    MenuTab home = tab(1L, "Home", "/");
    addQueryParameter(widgetContext, "menuTab1name", "");

    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class);
        MockedStatic<MenuItemRepository> menuItemRepository = mockStatic(MenuItemRepository.class);
        MockedStatic<SaveMenuTabCommand> saveMenuTabCommand = mockStatic(SaveMenuTabCommand.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(List.of(home));
      menuItemRepository.when(MenuItemRepository::findAll).thenReturn(Collections.<MenuItem> emptyList());

      SaveMenuEditorChangesCommand.applyChanges(widgetContext);

      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.renameTab(eq(home)), never());
    }
  }
  // -----------------------------------------------------------------------------------------------
  // Issue #1728: creating a third-level item. Everything else about nesting already worked -- store,
  // reorder, reparent, render, search -- but nothing could create the first sub-item, so the feature
  // could not be used at all through the admin.
  // -----------------------------------------------------------------------------------------------

  @Test
  void aPostedSubItemNameCreatesAThirdLevelItemUnderThatItem() {
    MenuItem parent = item(10L, "Autonomous Solutions", "/autonomous");
    parent.setMenuTabId(3L);
    addQueryParameter(widgetContext, "menuItem10subItemName", "USV-FOS");
    addQueryParameter(widgetContext, "menuItem10subItemLink", "/usv-fos");

    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class);
        MockedStatic<MenuItemRepository> menuItemRepository = mockStatic(MenuItemRepository.class);
        MockedStatic<SaveMenuTabCommand> saveMenuTabCommand = mockStatic(SaveMenuTabCommand.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(Collections.<MenuTab> emptyList());
      menuItemRepository.when(MenuItemRepository::findAll).thenReturn(List.of(parent));

      SaveMenuEditorChangesCommand.applyChanges(widgetContext);

      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.appendNewSubMenuItem(parent, "USV-FOS", "/usv-fos"));
    }
  }

  @Test
  void noSubItemIsCreatedWhenTheFieldIsBlank() {
    // the field renders on every eligible item, so an untouched form posts it empty for all of them
    MenuItem parent = item(10L, "Autonomous Solutions", "/autonomous");
    addQueryParameter(widgetContext, "menuItem10subItemName", "");

    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class);
        MockedStatic<MenuItemRepository> menuItemRepository = mockStatic(MenuItemRepository.class);
        MockedStatic<SaveMenuTabCommand> saveMenuTabCommand = mockStatic(SaveMenuTabCommand.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(Collections.<MenuTab> emptyList());
      menuItemRepository.when(MenuItemRepository::findAll).thenReturn(List.of(parent));

      SaveMenuEditorChangesCommand.applyChanges(widgetContext);

      saveMenuTabCommand.verify(() -> SaveMenuTabCommand.appendNewSubMenuItem(any(), any(), any()), never());
    }
  }

}
