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

package com.simisinc.platform.application.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemSpecification;

/**
 * Issue #827: loadItemByUniqueIdForAuthorizedUser's excludeArchived parameter is what stops a
 * deactivated item from remaining reachable by uniqueId alone on a public route/REST call. These
 * tests verify the ItemSpecification#setIncludeArchived flag that flows from it, and that the
 * default (2-arg) overload's behavior for every existing call site is unchanged.
 *
 * @author SimIS Inc.
 */
class LoadItemCommandTest {

  private static Item stubItem() {
    Item item = new Item();
    item.setId(42L);
    item.setUniqueId("some-item");
    return item;
  }

  @Test
  void twoArgOverloadIncludesArchivedItemsJustLikeBeforeIssue827() {
    // Regression guard: every pre-existing call site (EditItemFormWidget, ItemFileDropZoneWidget,
    // DownloadItemFileWidget, MenuWidget, ItemMembersListWidget, ItemFileListWidget, and
    // PageServlet/ItemService before this fix) relied on a deactivated item still resolving here.
    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class)) {
      ArgumentCaptor<ItemSpecification> specCaptor = ArgumentCaptor.forClass(ItemSpecification.class);
      repository.when(() -> ItemRepository.findAll(specCaptor.capture(), any()))
          .thenReturn(List.of(stubItem()));

      Item result = LoadItemCommand.loadItemByUniqueIdForAuthorizedUser("some-item", 7L);

      assertNotNull(result, "the 2-arg overload must keep resolving an archived item, unchanged from before #827");
      assertTrue(specCaptor.getValue().getIncludeArchived());
    }
  }

  @Test
  void threeArgOverloadWithExcludeArchivedFalseBehavesLikeTheTwoArgOverload() {
    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class)) {
      ArgumentCaptor<ItemSpecification> specCaptor = ArgumentCaptor.forClass(ItemSpecification.class);
      repository.when(() -> ItemRepository.findAll(specCaptor.capture(), any()))
          .thenReturn(List.of(stubItem()));

      Item result = LoadItemCommand.loadItemByUniqueIdForAuthorizedUser("some-item", 7L, false);

      assertNotNull(result);
      assertTrue(specCaptor.getValue().getIncludeArchived());
    }
  }

  @Test
  void threeArgOverloadWithExcludeArchivedTrueQueriesWithIncludeArchivedFalse() {
    // This is the actual behavior PageServlet (public routes) and ItemService.get() now rely on:
    // a deactivated item must not be resolvable this way. The archived-filtering itself lives in
    // ItemRepository's query building (see ItemRepositoryTest); this only proves the flag is
    // threaded through correctly.
    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class)) {
      ArgumentCaptor<ItemSpecification> specCaptor = ArgumentCaptor.forClass(ItemSpecification.class);
      repository.when(() -> ItemRepository.findAll(specCaptor.capture(), any()))
          .thenReturn(List.of());

      Item result = LoadItemCommand.loadItemByUniqueIdForAuthorizedUser("some-item", 7L, true);

      assertNull(result, "a deactivated item excluded by the repository query must resolve to null, i.e. a 404");
      assertFalse(specCaptor.getValue().getIncludeArchived());
    }
  }

  @Test
  void threeArgOverloadStillPopulatesUniqueIdAndForUserIdRegardlessOfExcludeArchived() {
    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class)) {
      ArgumentCaptor<ItemSpecification> specCaptor = ArgumentCaptor.forClass(ItemSpecification.class);
      repository.when(() -> ItemRepository.findAll(specCaptor.capture(), any()))
          .thenReturn(List.of(stubItem()));

      LoadItemCommand.loadItemByUniqueIdForAuthorizedUser("some-item", 7L, true);

      assertEquals("some-item", specCaptor.getValue().getUniqueId());
      assertEquals(7L, specCaptor.getValue().getForUserId());
    }
  }

  @Test
  void returnsNullForABlankUniqueIdWithoutQueryingTheRepository() {
    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class)) {
      assertNull(LoadItemCommand.loadItemByUniqueIdForAuthorizedUser("", 7L, true));
      assertNull(LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(null, 7L, true));
      repository.verifyNoInteractions();
    }
  }

  @Test
  void returnsNullForAnUnauthenticatedUserIdWithoutQueryingTheRepository() {
    // userId == -1 is UserSession's not-logged-in sentinel value distinct from GUEST_ID
    try (MockedStatic<ItemRepository> repository = mockStatic(ItemRepository.class)) {
      assertNull(LoadItemCommand.loadItemByUniqueIdForAuthorizedUser("some-item", -1L, true));
      repository.verifyNoInteractions();
    }
  }
}
