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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.cms.MenuItem;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.infrastructure.persistence.cms.MenuItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.MenuTabRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Guards against a bug where "Save Site Map Changes" would attempt to null out a tab's Name and
 * Icon on every save, for every tab, because this page never renders inputs for them (unlike the
 * Edit Links page) -- the old code always called setName()/setIcon() with whatever
 * getParameter() returned, which is null when no such input exists.
 *
 * @author SimIS Inc.
 */
class SiteMapWidgetTest extends WidgetBase {

  @Test
  void savingTheSiteMapWithNoNameOrIconParametersDoesNotAttemptToRenameAnyTab() throws InvocationTargetException, IllegalAccessException {
    MenuTab home = new MenuTab();
    home.setId(1L);
    home.setName("Home");
    home.setLink("/");

    addQueryParameter(widgetContext, "method", "sitemap-editor");

    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class);
        MockedStatic<MenuItemRepository> menuItemRepository = mockStatic(MenuItemRepository.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(List.of(home));
      menuItemRepository.when(MenuItemRepository::findAll).thenReturn(Collections.<MenuItem>emptyList());

      new SiteMapWidget().post(widgetContext);

      menuTabRepository.verify(() -> MenuTabRepository.save(any()), never());
    }
  }

  @Test
  void savingASiteMapChangeWithARealNameSavesIt() throws InvocationTargetException, IllegalAccessException {
    MenuTab solutions = new MenuTab();
    solutions.setId(7L);
    solutions.setName("Solutions");
    solutions.setLink("/solutions");

    addQueryParameter(widgetContext, "method", "sitemap-editor");
    addQueryParameter(widgetContext, "menuTab7name", "Our Solutions");

    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class);
        MockedStatic<MenuItemRepository> menuItemRepository = mockStatic(MenuItemRepository.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(List.of(solutions));
      menuItemRepository.when(MenuItemRepository::findAll).thenReturn(Collections.<MenuItem>emptyList());
      menuTabRepository.when(() -> MenuTabRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      new SiteMapWidget().post(widgetContext);

      Assertions.assertEquals("Our Solutions", solutions.getName());
      menuTabRepository.verify(() -> MenuTabRepository.save(solutions));
    }
  }

  @Test
  void executeShowsTheEditor() {
    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(Collections.<MenuTab>emptyList());

      WidgetContext result = new SiteMapWidget().execute(widgetContext);

      Assertions.assertEquals(SiteMapWidget.JSP, result.getJsp());
    }
  }

  /**
   * Guards against a regression where the Navigation Menu Editor's "Add Tab" and delete ("x")
   * controls POSTed to the server immediately, bypassing Save/Cancel -- a tab added or deleted was
   * permanent before Save was ever clicked. Both are now staged client-side and reach the server as
   * part of a single "Save Site Map Changes" submit (see sitemap.jsp's stageNewTab()/
   * wireDeleteTabLink()/wireDeleteItemLink() and this class's processSiteMapChanges()).
   */
  @Test
  void savingWithAStagedNewTabCreatesIt() throws InvocationTargetException, IllegalAccessException {
    addQueryParameter(widgetContext, "method", "sitemap-editor");
    addQueryParameter(widgetContext, "newMenuTabIds", "new1");
    addQueryParameter(widgetContext, "menuTabnew1name", "Solutions");
    addQueryParameter(widgetContext, "menuTabnew1link", "/solutions");
    addQueryParameter(widgetContext, "menuTabnew1icon", "");

    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class);
        MockedStatic<MenuItemRepository> menuItemRepository = mockStatic(MenuItemRepository.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(Collections.<MenuTab>emptyList());
      menuItemRepository.when(MenuItemRepository::findAll).thenReturn(Collections.<MenuItem>emptyList());
      menuTabRepository.when(() -> MenuTabRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      new SiteMapWidget().post(widgetContext);

      menuTabRepository.verify(() -> MenuTabRepository
          .save(org.mockito.ArgumentMatchers.argThat(tab -> "Solutions".equals(tab.getName()) && "/solutions".equals(tab.getLink()))));
    }
  }

  @Test
  void savingWithAStagedTabDeletionDeletesIt() throws InvocationTargetException, IllegalAccessException {
    MenuTab solutions = new MenuTab();
    solutions.setId(7L);
    solutions.setName("Solutions");
    solutions.setLink("/solutions");

    addQueryParameter(widgetContext, "method", "sitemap-editor");
    addQueryParameter(widgetContext, "menuTabsToDelete", "7");

    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class);
        MockedStatic<MenuItemRepository> menuItemRepository = mockStatic(MenuItemRepository.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(List.of(solutions));
      menuItemRepository.when(MenuItemRepository::findAll).thenReturn(Collections.<MenuItem>emptyList());
      menuTabRepository.when(() -> MenuTabRepository.findById(7L)).thenReturn(solutions);
      menuTabRepository.when(() -> MenuTabRepository.remove(any())).thenReturn(true);

      new SiteMapWidget().post(widgetContext);

      menuTabRepository.verify(() -> MenuTabRepository.remove(solutions));
    }
  }

  @Test
  void savingWithAStagedItemDeletionDeletesIt() throws InvocationTargetException, IllegalAccessException {
    MenuItem governmentServices = new MenuItem();
    governmentServices.setId(12L);
    governmentServices.setName("Government Services");
    governmentServices.setLink("/government-services");

    addQueryParameter(widgetContext, "method", "sitemap-editor");
    addQueryParameter(widgetContext, "menuItemsToDelete", "12");

    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class);
        MockedStatic<MenuItemRepository> menuItemRepository = mockStatic(MenuItemRepository.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(Collections.<MenuTab>emptyList());
      menuItemRepository.when(MenuItemRepository::findAll).thenReturn(Collections.<MenuItem>emptyList());
      menuItemRepository.when(() -> MenuItemRepository.findById(12L)).thenReturn(governmentServices);
      menuItemRepository.when(() -> MenuItemRepository.remove(any())).thenReturn(true);

      new SiteMapWidget().post(widgetContext);

      menuItemRepository.verify(() -> MenuItemRepository.remove(governmentServices));
    }
  }

  /**
   * The trickiest part of the staged-tab-creation change: menuTabOrder is computed client-side
   * before the new tab exists in the database, so its DOM id (and its entry in menuTabOrder) is the
   * temporary id, not a real one. This proves that token gets resolved to the tab's newly-assigned
   * real id before being used, rather than a raw Long.parseLong("new1") being attempted (which would
   * throw and silently drop the tab from the reorder instead).
   */
  @Test
  void aStagedNewTabsPositionInMenuTabOrderResolvesToItsRealId() throws InvocationTargetException, IllegalAccessException {
    addQueryParameter(widgetContext, "method", "sitemap-editor");
    addQueryParameter(widgetContext, "newMenuTabIds", "new1");
    addQueryParameter(widgetContext, "menuTabnew1name", "Solutions");
    addQueryParameter(widgetContext, "menuTabnew1link", "/solutions");
    addQueryParameter(widgetContext, "menuTabOrder", "site-map-menu-tab-container-new1");

    MenuTab createdTab = new MenuTab();
    createdTab.setId(42L);
    createdTab.setName("Solutions");
    createdTab.setLink("/solutions");

    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class);
        MockedStatic<MenuItemRepository> menuItemRepository = mockStatic(MenuItemRepository.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(Collections.<MenuTab>emptyList());
      menuItemRepository.when(MenuItemRepository::findAll).thenReturn(Collections.<MenuItem>emptyList());
      menuTabRepository.when(() -> MenuTabRepository.save(any())).thenAnswer(invocation -> {
        MenuTab tab = invocation.getArgument(0);
        if (tab.getId() == null || tab.getId() == -1L) {
          tab.setId(42L);
        }
        return tab;
      });
      menuTabRepository.when(() -> MenuTabRepository.findById(42L)).thenReturn(createdTab);
      menuTabRepository.when(() -> MenuTabRepository.findByLink("/")).thenReturn(null);

      new SiteMapWidget().post(widgetContext);

      // updateTabOrder() looks the tab up by id to set its order -- seeing 42L (the real id) proves
      // "new1" was resolved, not passed straight to Long.parseLong().
      menuTabRepository.verify(() -> MenuTabRepository.findById(42L));
    }
  }
}
