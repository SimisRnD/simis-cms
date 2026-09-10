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

package com.simisinc.platform.domain.model.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The third navigation level (issue #1728) is expressed as -1 meaning "no parent", matching how the
 * rest of this codebase represents an unset id. The repository maps SQL NULL to -1 on the way out
 * and -1 back to NULL on the way in, so these two states have to stay in lockstep: a menu item that
 * reports itself as nested when it is not would render a whole level that does not exist.
 */
class MenuItemTest {

  @Test
  void aNewItemHasNoParent() {
    // Every row that existed before nesting has a NULL parent, so the unset default has to agree
    // with that, not with 0 or null.
    MenuItem menuItem = new MenuItem();
    assertEquals(-1L, menuItem.getParentMenuItemId());
    assertFalse(menuItem.hasParentMenuItem(), "an item with no parent sits directly under its tab");
  }

  @Test
  void anItemWithAParentReportsItself() {
    MenuItem menuItem = new MenuItem();
    menuItem.setParentMenuItemId(42L);
    assertTrue(menuItem.hasParentMenuItem());
  }

  @Test
  void aNullParentIsTreatedAsNoParent() {
    // The repository never sets null, but BeanUtils.populate and JSP form binding can, and a null
    // here would otherwise NPE inside the > comparison rather than reading as "top level".
    MenuItem menuItem = new MenuItem();
    menuItem.setParentMenuItemId(null);
    assertFalse(menuItem.hasParentMenuItem());
  }

  @Test
  void zeroIsNotAParent() {
    // -1 is the unset marker, but a defensive 0 must not read as a real parent id either: no
    // BIGSERIAL ever issues 0, so treating it as nesting would render an orphan level.
    MenuItem menuItem = new MenuItem();
    menuItem.setParentMenuItemId(0L);
    assertFalse(menuItem.hasParentMenuItem());
  }

  @Test
  void anItemHasNoChildrenUntilTheyAreLoaded() {
    // The children list is populated only by the callers that render a third level. Everything else
    // gets null, and must not be tricked into rendering an empty submenu.
    MenuItem menuItem = new MenuItem();
    assertFalse(menuItem.hasMenuItemList());
  }

  @Test
  void anEmptyChildListIsNotAThirdLevel() {
    // A parent whose children are all draft or disabled comes back as an empty list, not null. That
    // must not render as an empty flyout.
    MenuItem menuItem = new MenuItem();
    menuItem.setMenuItemList(new ArrayList<>());
    assertFalse(menuItem.hasMenuItemList());
  }

  @Test
  void anItemWithChildrenReportsThem() {
    MenuItem child = new MenuItem();
    child.setParentMenuItemId(7L);
    List<MenuItem> children = new ArrayList<>();
    children.add(child);

    MenuItem parent = new MenuItem();
    parent.setId(7L);
    parent.setMenuItemList(children);

    assertTrue(parent.hasMenuItemList());
    assertEquals(1, parent.getMenuItemList().size());
    assertTrue(parent.getMenuItemList().get(0).hasParentMenuItem());
  }
}
