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

package com.simisinc.platform.rest.services.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;

/**
 * Issue #827: {@code GET /item/{uniqueId}} must 404 for a deactivated item unconditionally --
 * unlike PageServlet's item routes, this REST endpoint has no admin-edit use case that needs to
 * still see one.
 *
 * @author SimIS Inc.
 */
class ItemServiceTest {

  private ServiceContext contextFor(String itemUniqueId, long userId) {
    ServiceContext context = new ServiceContext();
    context.setPathParam(itemUniqueId);
    User user = new User();
    user.setId(userId);
    context.setUser(user);
    return context;
  }

  @Test
  void getAlwaysPassesExcludeArchivedTrueToLoadItemCommand() {
    ServiceContext context = contextFor("some-item", 3L);

    try (MockedStatic<LoadItemCommand> loadItem = mockStatic(LoadItemCommand.class)) {
      loadItem.when(() -> LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(eq("some-item"), eq(3L), eq(true)))
          .thenReturn(null);

      new ItemService().get(context);

      loadItem.verify(() -> LoadItemCommand.loadItemByUniqueIdForAuthorizedUser("some-item", 3L, true));
      // The old 2-arg call (which defaults to includeArchived=true) must not be used here anymore
      loadItem.verify(() -> LoadItemCommand.loadItemByUniqueIdForAuthorizedUser("some-item", 3L), org.mockito.Mockito.never());
    }
  }

  @Test
  void getReturns404WhenLoadItemCommandExcludesADeactivatedItem() {
    // LoadItemCommand itself is what applies the archived filter (see LoadItemCommandTest); this
    // proves ItemService surfaces that as a plain 404, same as a genuinely nonexistent item.
    ServiceContext context = contextFor("deactivated-item", 3L);

    try (MockedStatic<LoadItemCommand> loadItem = mockStatic(LoadItemCommand.class)) {
      loadItem.when(() -> LoadItemCommand.loadItemByUniqueIdForAuthorizedUser("deactivated-item", 3L, true))
          .thenReturn(null);

      ServiceResponse response = new ItemService().get(context);

      assertEquals(404, response.getStatus());
      assertEquals("Item was not found", response.getError().get("title"));
    }
  }

  @Test
  void getReturns200ForAnActiveItemWithCollectionAccess() {
    ServiceContext context = contextFor("active-item", 3L);
    Item item = new Item();
    item.setUniqueId("active-item");
    item.setName("Active Item");
    item.setCollectionId(55L);
    Collection collection = new Collection();
    collection.setId(55L);

    try (MockedStatic<LoadItemCommand> loadItem = mockStatic(LoadItemCommand.class);
        MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class)) {
      loadItem.when(() -> LoadItemCommand.loadItemByUniqueIdForAuthorizedUser("active-item", 3L, true))
          .thenReturn(item);
      loadCollection.when(() -> LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(55L, 3L))
          .thenReturn(collection);

      ServiceResponse response = new ItemService().get(context);

      assertEquals(200, response.getStatus());
      ItemDetailsResponse data = (ItemDetailsResponse) response.getData();
      assertEquals("active-item", data.getUniqueId());
      assertEquals("Active Item", data.getName());
    }
  }
}
