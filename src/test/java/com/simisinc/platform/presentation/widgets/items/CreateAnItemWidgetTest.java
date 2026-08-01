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

package com.simisinc.platform.presentation.widgets.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.items.ItemCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.SaveItemCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;

/**
 * Regression test for a bug found reviewing issue #815's fix: the public "Create an Item" widget
 * built a brand-new {@link Item} and handed it straight to {@link SaveItemCommand#saveItem}
 * without ever calling {@link ItemRepository#getNextItemOrder}, so every item a site visitor
 * submitted through this form landed at the {@link Item} domain model's static default order
 * (100) instead of appending after the collection's existing items -- silently reordering any
 * collection whose real item_order values had already grown past 100.
 *
 * @author SimIS Inc.
 */
class CreateAnItemWidgetTest extends WidgetBase {

  private static Collection collection(long id) {
    Collection collection = new Collection();
    collection.setId(id);
    collection.setUniqueId("widgets");
    return collection;
  }

  @Test
  void postAppendsANewItemAtTheEndOfTheCollectionRatherThanTheDomainModelsStaticDefault() throws Exception {
    preferences.put("collectionUniqueId", "widgets");
    // Skip the permission-check and captcha branches -- neither is relevant to this bug, and
    // WidgetBase logs the test user in by default, which combined with this also skips the
    // captcha branch (it only applies to a logged-out, no-permission-required submission).
    preferences.put("requiresPermission", "false");
    addQueryParameter(widgetContext, "name", "New Widget");

    try (MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<ItemRepository> itemRepository = mockStatic(ItemRepository.class);
        MockedStatic<SaveItemCommand> saveItemCommand = mockStatic(SaveItemCommand.class);
        MockedStatic<ItemCommand> itemCommand = mockStatic(ItemCommand.class)) {

      loadCollection.when(() -> LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(eq("widgets"), anyLong()))
          .thenReturn(collection(5L));
      categoryRepository.when(() -> CategoryRepository.findAllByCollectionId(5L)).thenReturn(new ArrayList<>());
      // A collection that has already been reordered past the domain model's static default.
      itemRepository.when(() -> ItemRepository.getNextItemOrder(5L)).thenReturn(7);

      Item savedItem = new Item();
      savedItem.setId(99L);
      saveItemCommand.when(() -> SaveItemCommand.saveItem(any(Item.class))).thenReturn(savedItem);

      new CreateAnItemWidget().post(widgetContext);

      ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
      saveItemCommand.verify(() -> SaveItemCommand.saveItem(itemCaptor.capture()));
      assertEquals(7, itemCaptor.getValue().getItemOrder(),
          "a newly submitted item must append after the collection's existing items "
              + "(getNextItemOrder), not silently fall back to the domain model's static default");
    }
  }
}
