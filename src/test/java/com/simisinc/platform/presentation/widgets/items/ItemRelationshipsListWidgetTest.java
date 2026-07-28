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

package com.simisinc.platform.presentation.widgets.items;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.items.CheckCollectionPermissionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.items.ItemRelationshipRepository;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * removeRelationshipViaPostCallsRepositoryWhenAuthorized guards a real regression: the "Remove" button
 * submits via a real HTTP POST (issue #358 moved state-changing actions off GET query strings), so
 * WebContainerContext routes the request to post(), not action() below -- action()'s "removeRelationship"
 * dispatch (and its collection edit-permission check) was correct but unreachable, and this widget had no
 * post() override at all, so the request silently no-opped (redirect back to the same page, no error, no
 * repository call). This test calls post() directly, the same method a real request now reaches, so it
 * fails if that dispatch gap reopens. removeRelationshipViaPostRefusesWithoutEditPermission confirms the fix
 * didn't bypass the permission check while restoring the dispatch.
 */
class ItemRelationshipsListWidgetTest extends WidgetBase {

  @Test
  void removeRelationshipViaPostCallsRepositoryWhenAuthorized() throws Exception {
    Item item = new Item();
    item.setId(1L);
    item.setUniqueId("item-1");
    item.setCollectionId(100L);

    Item relatedItem = new Item();
    relatedItem.setId(2L);
    relatedItem.setUniqueId("item-2");

    widgetContext.getCoreData().put("itemUniqueId", "item-1");
    addQueryParameter(widgetContext, "relatedItemId", "2");
    addQueryParameter(widgetContext, "action", "removeRelationship");

    try (MockedStatic<LoadItemCommand> loadItem = mockStatic(LoadItemCommand.class);
        MockedStatic<CheckCollectionPermissionCommand> permission = mockStatic(CheckCollectionPermissionCommand.class);
        MockedStatic<ItemRelationshipRepository> repository = mockStatic(ItemRelationshipRepository.class)) {
      loadItem.when(() -> LoadItemCommand.loadItemByUniqueId("item-1")).thenReturn(item);
      loadItem.when(() -> LoadItemCommand.loadItemById(2L)).thenReturn(relatedItem);
      permission.when(() -> CheckCollectionPermissionCommand.userHasEditPermission(100L, widgetContext.getUserId())).thenReturn(true);

      new ItemRelationshipsListWidget().post(widgetContext);

      repository.verify(() -> ItemRelationshipRepository.removeRelationship(item, relatedItem), times(1));
    }
  }

  @Test
  void removeRelationshipViaPostRefusesWithoutEditPermission() throws Exception {
    Item item = new Item();
    item.setId(1L);
    item.setUniqueId("item-1");
    item.setCollectionId(100L);

    widgetContext.getCoreData().put("itemUniqueId", "item-1");
    addQueryParameter(widgetContext, "relatedItemId", "2");
    addQueryParameter(widgetContext, "action", "removeRelationship");

    try (MockedStatic<LoadItemCommand> loadItem = mockStatic(LoadItemCommand.class);
        MockedStatic<CheckCollectionPermissionCommand> permission = mockStatic(CheckCollectionPermissionCommand.class);
        MockedStatic<ItemRelationshipRepository> repository = mockStatic(ItemRelationshipRepository.class)) {
      loadItem.when(() -> LoadItemCommand.loadItemByUniqueId("item-1")).thenReturn(item);
      permission.when(() -> CheckCollectionPermissionCommand.userHasEditPermission(100L, widgetContext.getUserId())).thenReturn(false);

      new ItemRelationshipsListWidget().post(widgetContext);

      repository.verify(() -> ItemRelationshipRepository.removeRelationship(any(), any()), never());
    }
  }
}
