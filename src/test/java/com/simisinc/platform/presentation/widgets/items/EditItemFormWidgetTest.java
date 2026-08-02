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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.items.CheckCollectionPermissionCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.application.items.SaveItemCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.infrastructure.persistence.items.TagRepository;

/**
 * Verifies {@link EditItemFormWidget#post} correctly parses the shared {@code tagId} checkbox
 * group (issue #632), mirroring the {@code categoryId} handling already covered informally by the
 * category form.
 *
 * @author SimIS Inc.
 */
class EditItemFormWidgetTest extends WidgetBase {

  private static Collection collection(long id) {
    Collection collection = new Collection();
    collection.setId(id);
    collection.setUniqueId("widgets");
    return collection;
  }

  private static Item item(long id, long collectionId) {
    Item item = new Item();
    item.setId(id);
    item.setCollectionId(collectionId);
    item.setUniqueId("the-item");
    return item;
  }

  @Test
  void postParsesTheSharedTagIdCheckboxGroupOntoTheItem() throws Exception {
    preferences.put("uniqueId", "the-item");
    addQueryParameter(widgetContext, "name", "Updated Widget");
    widgetContext.getParameterMap().put("tagId", new String[] { "10", "20", "10" });

    Item existingItem = item(1L, 5L);

    try (MockedStatic<LoadItemCommand> loadItemCommand = mockStatic(LoadItemCommand.class);
        MockedStatic<LoadCollectionCommand> loadCollectionCommand = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CheckCollectionPermissionCommand> checkPermission = mockStatic(CheckCollectionPermissionCommand.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<SaveItemCommand> saveItemCommand = mockStatic(SaveItemCommand.class)) {

      loadItemCommand.when(() -> LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(eq("the-item"), anyLong()))
          .thenReturn(existingItem);
      loadItemCommand.when(() -> LoadItemCommand.loadItemById(1L)).thenReturn(existingItem);
      loadCollectionCommand.when(() -> LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(eq(5L), anyLong()))
          .thenReturn(collection(5L));
      checkPermission.when(() -> CheckCollectionPermissionCommand.userHasEditPermission(eq(5L), anyLong()))
          .thenReturn(true);
      categoryRepository.when(() -> CategoryRepository.findAllByCollectionId(5L)).thenReturn(new ArrayList<>());

      Item savedItem = item(1L, 5L);
      saveItemCommand.when(() -> SaveItemCommand.saveItem(any(Item.class))).thenReturn(savedItem);

      new EditItemFormWidget().post(widgetContext);

      ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
      saveItemCommand.verify(() -> SaveItemCommand.saveItem(itemCaptor.capture()));
      assertArrayEquals(new Long[] { 10L, 20L }, itemCaptor.getValue().getTagIdList(),
          "duplicate tagId values in the submitted checkbox group must be de-duplicated");
    }
  }

  @Test
  void executeProvidesTheTagListForTheCheckboxGroup() {
    preferences.put("uniqueId", "the-item");
    Item existingItem = item(1L, 5L);

    try (MockedStatic<LoadItemCommand> loadItemCommand = mockStatic(LoadItemCommand.class);
        MockedStatic<LoadCollectionCommand> loadCollectionCommand = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CheckCollectionPermissionCommand> checkPermission = mockStatic(CheckCollectionPermissionCommand.class);
        MockedStatic<CategoryRepository> categoryRepository = mockStatic(CategoryRepository.class);
        MockedStatic<TagRepository> tagRepository = mockStatic(TagRepository.class)) {

      loadItemCommand.when(() -> LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(eq("the-item"), anyLong()))
          .thenReturn(existingItem);
      loadCollectionCommand.when(() -> LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(eq(5L), anyLong()))
          .thenReturn(collection(5L));
      checkPermission.when(() -> CheckCollectionPermissionCommand.userHasEditPermission(eq(5L), anyLong()))
          .thenReturn(true);
      categoryRepository.when(() -> CategoryRepository.findAllByCollectionId(5L)).thenReturn(new ArrayList<>());
      tagRepository.when(() -> TagRepository.findAllByCollectionId(5L)).thenReturn(new ArrayList<>());

      new EditItemFormWidget().execute(widgetContext);

      tagRepository.verify(() -> TagRepository.findAllByCollectionId(5L));
    }
  }
}
