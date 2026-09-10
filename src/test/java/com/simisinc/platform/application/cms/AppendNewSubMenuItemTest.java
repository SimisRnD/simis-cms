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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.MenuItem;
import com.simisinc.platform.infrastructure.persistence.cms.MenuItemRepository;

/**
 * Creating a third-level menu item (issue #1728).
 *
 * <p>Until this existed the third level could be stored, reordered, reparented, rendered and
 * searched, but never created through the admin: the only code that set a parent was
 * updateMenuSubItemOrder, reached from the drag-and-drop wire format, which can only move an item
 * that is nested already. The feature was complete except for the one step that starts it.
 *
 * @author SimIS Inc.
 */
class AppendNewSubMenuItemTest {

  private static MenuItem parent(long id, long menuTabId) {
    MenuItem menuItem = new MenuItem();
    menuItem.setId(id);
    menuItem.setMenuTabId(menuTabId);
    menuItem.setName("Autonomous Solutions");
    menuItem.setLink("/autonomous-solutions");
    return menuItem;
  }

  @Test
  void aSubItemIsCreatedUnderItsParentAndInsideThatParentsTab() throws DataException {
    MenuItem parentItem = parent(10L, 3L);

    try (MockedStatic<MenuItemRepository> repository = mockStatic(MenuItemRepository.class)) {
      repository.when(() -> MenuItemRepository.getNextSubItemOrder(parentItem)).thenReturn(0);
      repository.when(() -> MenuItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      MenuItem created = SaveMenuTabCommand.appendNewSubMenuItem(parentItem, "USV-FOS", "/usv-fos");

      assertEquals(10L, created.getParentMenuItemId());
      // the tab comes from the parent, never the form -- an item rendered under one tab and ordered
      // under another is the failure this prevents
      assertEquals(3L, created.getMenuTabId());
      assertEquals("/usv-fos", created.getLink());
    }
  }

  @Test
  void nestingUnderAnAlreadyNestedItemIsRefused() {
    // the same three-level cap updateMenuSubItemOrder enforces. Checked here too rather than
    // trusted: both paths that can set a parent have to refuse a fourth level, and the form field
    // name is guessable so the markup alone cannot be the rule.
    MenuItem alreadyNested = parent(11L, 3L);
    alreadyNested.setParentMenuItemId(10L);

    try (MockedStatic<MenuItemRepository> repository = mockStatic(MenuItemRepository.class)) {
      DataException exception = assertThrows(DataException.class,
          () -> SaveMenuTabCommand.appendNewSubMenuItem(alreadyNested, "Too deep", "/too-deep"));

      assertTrue(exception.getMessage().contains("three levels"), exception.getMessage());
      repository.verify(() -> MenuItemRepository.save(any()), never());
    }
  }

  @Test
  void aBlankNameIsRefused() {
    MenuItem parentItem = parent(10L, 3L);

    try (MockedStatic<MenuItemRepository> repository = mockStatic(MenuItemRepository.class)) {
      DataException exception = assertThrows(DataException.class,
          () -> SaveMenuTabCommand.appendNewSubMenuItem(parentItem, "  ", "/somewhere"));

      assertTrue(exception.getMessage().contains("name is required"), exception.getMessage());
      repository.verify(() -> MenuItemRepository.save(any()), never());
    }
  }

  @Test
  void aMissingLinkIsDerivedFromTheName() throws DataException {
    MenuItem parentItem = parent(10L, 3L);

    try (MockedStatic<MenuItemRepository> repository = mockStatic(MenuItemRepository.class)) {
      repository.when(() -> MenuItemRepository.getNextSubItemOrder(parentItem)).thenReturn(0);
      repository.when(() -> MenuItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      MenuItem created = SaveMenuTabCommand.appendNewSubMenuItem(parentItem, "Robotic Human Type Targets", null);

      assertTrue(created.getLink().startsWith("/"), created.getLink());
      assertTrue(created.getLink().length() > 1, "a derived link must not be just a slash");
    }
  }

  @Test
  void aLinkWithoutALeadingSlashGetsOne() throws DataException {
    MenuItem parentItem = parent(10L, 3L);

    try (MockedStatic<MenuItemRepository> repository = mockStatic(MenuItemRepository.class)) {
      repository.when(() -> MenuItemRepository.getNextSubItemOrder(parentItem)).thenReturn(0);
      repository.when(() -> MenuItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      MenuItem created = SaveMenuTabCommand.appendNewSubMenuItem(parentItem, "USV-FOS", "usv-fos");

      assertEquals("/usv-fos", created.getLink());
    }
  }

  @Test
  void theOrderComesFromTheParentsOwnChildrenNotTheWholeTab() throws DataException {
    // two parents' children each number from their own beginning, which is why this uses
    // getNextSubItemOrder rather than getNextTabOrder
    MenuItem parentItem = parent(10L, 3L);

    try (MockedStatic<MenuItemRepository> repository = mockStatic(MenuItemRepository.class)) {
      repository.when(() -> MenuItemRepository.getNextSubItemOrder(parentItem)).thenReturn(2);
      repository.when(() -> MenuItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      MenuItem created = SaveMenuTabCommand.appendNewSubMenuItem(parentItem, "Third child", "/third");

      assertEquals(2, created.getItemOrder());
      repository.verify(() -> MenuItemRepository.getNextTabOrder(any()), never());
    }
  }
}
