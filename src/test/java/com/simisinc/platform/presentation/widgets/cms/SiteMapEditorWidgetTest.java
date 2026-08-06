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
 * Guards against the same null-write bug as {@link SiteMapWidgetTest}: the locked/first ("Home")
 * tab has no rendered Name/Link/Icon inputs on this page either, so saving must not attempt to
 * rename it to null.
 *
 * @author SimIS Inc.
 */
class SiteMapEditorWidgetTest extends WidgetBase {

  @Test
  void savingWithNoNameOrIconParameterForATabDoesNotAttemptToRenameIt() throws InvocationTargetException, IllegalAccessException {
    MenuTab home = new MenuTab();
    home.setId(1L);
    home.setName("Home");
    home.setLink("/");

    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class);
        MockedStatic<MenuItemRepository> menuItemRepository = mockStatic(MenuItemRepository.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(List.of(home));
      menuItemRepository.when(MenuItemRepository::findAll).thenReturn(Collections.<MenuItem>emptyList());

      new SiteMapEditorWidget().post(widgetContext);

      menuTabRepository.verify(() -> MenuTabRepository.save(any()), never());
    }
  }

  @Test
  void savingARealNameChangeSavesIt() throws InvocationTargetException, IllegalAccessException {
    MenuTab solutions = new MenuTab();
    solutions.setId(7L);
    solutions.setName("Solutions");
    solutions.setLink("/solutions");

    addQueryParameter(widgetContext, "menuTab7name", "Our Solutions");

    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class);
        MockedStatic<MenuItemRepository> menuItemRepository = mockStatic(MenuItemRepository.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(List.of(solutions));
      menuItemRepository.when(MenuItemRepository::findAll).thenReturn(Collections.<MenuItem>emptyList());
      menuTabRepository.when(() -> MenuTabRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      new SiteMapEditorWidget().post(widgetContext);

      Assertions.assertEquals("Our Solutions", solutions.getName());
      menuTabRepository.verify(() -> MenuTabRepository.save(solutions));
    }
  }

  @Test
  void executeShowsTheEditor() {
    try (MockedStatic<MenuTabRepository> menuTabRepository = mockStatic(MenuTabRepository.class)) {
      menuTabRepository.when(MenuTabRepository::findAll).thenReturn(Collections.<MenuTab>emptyList());

      WidgetContext result = new SiteMapEditorWidget().execute(widgetContext);

      Assertions.assertEquals(SiteMapEditorWidget.JSP, result.getJsp());
    }
  }
}
