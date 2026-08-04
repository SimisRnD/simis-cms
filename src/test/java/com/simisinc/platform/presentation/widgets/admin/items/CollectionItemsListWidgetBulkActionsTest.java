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

package com.simisinc.platform.presentation.widgets.admin.items;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.application.items.CheckCollectionPermissionCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemTag;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemTagRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Covers the 5 bulk actions on /admin/collection-records (issue #427, mirroring
 * CalendarEventListWidgetBulkActionsTest/AdminBlogPostListWidgetBulkActionsTest's shape): a batch
 * over MAX_BULK_SELECTION is rejected outright rather than truncated, an empty selection is
 * rejected, one id that no longer resolves never aborts the rest of the batch, and all 5 commands
 * share a single admin-or-data-manager gate matching the page's own role.
 *
 * <p>Unlike the calendar/blog-post siblings, items carry collection-scoped group permissions (issue
 * #903, PR #910's lesson): a resolved item's OWN collection is re-verified per row via {@link
 * LoadCollectionCommand#loadCollectionByIdForAuthorizedUser} and {@link
 * CheckCollectionPermissionCommand}, never trusting the page's own {@code collectionId} query
 * parameter. The dedicated per-row-authorization tests below are what actually proves that #903's
 * bug class was not reintroduced by this bulk endpoint.
 *
 * @author SimIS Inc.
 */
class CollectionItemsListWidgetBulkActionsTest extends WidgetBase {

  private static Item itemWithId(long id, long collectionId) {
    Item item = new Item();
    item.setId(id);
    item.setCollectionId(collectionId);
    item.setName("Item " + id);
    item.setUniqueId("item-" + id);
    return item;
  }

  private static Category categoryWithId(long id, long collectionId) {
    Category category = new Category();
    category.setId(id);
    category.setCollectionId(collectionId);
    category.setName("Category " + id);
    return category;
  }

  private static Collection collectionWithId(long id) {
    Collection collection = new Collection();
    collection.setId(id);
    return collection;
  }

  private static User adminUser() {
    User user = new User();
    user.setId(1L);
    return user;
  }

  private void multiValue(String name, String... values) {
    widgetContext.getParameterMap().put(name, values);
  }

  /** Stubs both per-row authorization checks as passing for the given collection id. */
  private void stubRowAuthorized(MockedStatic<LoadCollectionCommand> loadCollectionCommand,
      MockedStatic<CheckCollectionPermissionCommand> checkPermission, long collectionId) {
    loadCollectionCommand.when(() -> LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(eq(collectionId), anyLong()))
        .thenReturn(collectionWithId(collectionId));
    checkPermission.when(() -> CheckCollectionPermissionCommand.userHasEditPermission(eq(collectionId), anyLong()))
        .thenReturn(true);
    checkPermission.when(() -> CheckCollectionPermissionCommand.userHasDeletePermission(eq(collectionId), anyLong()))
        .thenReturn(true);
  }

  // --- execute() read-path authorization (issue #903-class fix alongside the bulk actions) ---

  @Test
  void executeReturnsAnErrorWhenTheCollectionIsNotFoundOrNotAuthorizedForTheUser() {
    addQueryParameter(widgetContext, "collectionId", "10");

    try (MockedStatic<LoadCollectionCommand> loadCollectionCommand = mockStatic(LoadCollectionCommand.class)) {
      loadCollectionCommand.when(() -> LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(eq(10L), anyLong()))
          .thenReturn(null);

      WidgetContext result = new CollectionItemsListWidget().execute(widgetContext);

      assertEquals("Error. Collection was not found.", result.getErrorMessage());
    }
  }

  @Test
  void executePopulatesTheItemListWhenTheCollectionIsAuthorized() {
    addQueryParameter(widgetContext, "collectionId", "10");

    Collection collection = collectionWithId(10L);
    Item item = itemWithId(5L, 10L);

    try (MockedStatic<LoadCollectionCommand> loadCollectionCommand = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CategoryRepository> categoryRepo = mockStatic(CategoryRepository.class);
        MockedStatic<ItemRepository> repo = mockStatic(ItemRepository.class)) {
      loadCollectionCommand.when(() -> LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(eq(10L), anyLong()))
          .thenReturn(collection);
      categoryRepo.when(() -> CategoryRepository.findAllByCollectionId(10L)).thenReturn(new ArrayList<>());
      repo.when(() -> ItemRepository.findAll(any(), any())).thenReturn(List.of(item));

      WidgetContext result = new CollectionItemsListWidget().execute(widgetContext);

      assertEquals(List.of(item), result.getRequest().getAttribute("itemList"));
      assertEquals(collection, result.getRequest().getAttribute("collection"));
    }
  }

  // --- Permission gate ---

  @Test
  void nonAdminNonDataManagerCannotReachAnyBulkAction() {
    setRoles(widgetContext, CONTENT_MANAGER);
    multiValue("itemId", "5");
    addQueryParameter(widgetContext, "command", "bulkArchive");

    try (MockedStatic<ItemRepository> repo = mockStatic(ItemRepository.class)) {
      new CollectionItemsListWidget().post(widgetContext);

      repo.verify(() -> ItemRepository.findById(anyLong()), never());
    }
  }

  @Test
  void dataManagerCanReachBulkActions() {
    setRoles(widgetContext, DATA_MANAGER);
    multiValue("itemId", "5");
    addQueryParameter(widgetContext, "command", "bulkArchive");
    addQueryParameter(widgetContext, "collectionId", "10");

    Item item = itemWithId(5L, 10L);
    try (MockedStatic<ItemRepository> repo = mockStatic(ItemRepository.class);
        MockedStatic<ItemTagRepository> itemTagRepo = mockStatic(ItemTagRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollectionCommand = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CheckCollectionPermissionCommand> checkPermission = mockStatic(CheckCollectionPermissionCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      stubRowAuthorized(loadCollectionCommand, checkPermission, 10L);
      repo.when(() -> ItemRepository.findById(5L)).thenReturn(item);
      itemTagRepo.when(() -> ItemTagRepository.findAllByItemId(5L)).thenReturn(new ArrayList<>());
      repo.when(() -> ItemRepository.save(item)).thenReturn(item);

      WidgetContext result = new CollectionItemsListWidget().post(widgetContext);

      repo.verify(() -> ItemRepository.save(item), times(1));
      assertTrue(result.getSuccessMessage().contains("1 of 1"));
    }
  }

  @Test
  void adminCanReachBulkDelete() {
    setRoles(widgetContext, ADMIN);
    multiValue("itemId", "5");
    addQueryParameter(widgetContext, "command", "bulkDelete");
    addQueryParameter(widgetContext, "collectionId", "10");

    Item item = itemWithId(5L, 10L);
    try (MockedStatic<ItemRepository> repo = mockStatic(ItemRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollectionCommand = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CheckCollectionPermissionCommand> checkPermission = mockStatic(CheckCollectionPermissionCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      stubRowAuthorized(loadCollectionCommand, checkPermission, 10L);
      repo.when(() -> ItemRepository.findById(5L)).thenReturn(item);
      repo.when(() -> ItemRepository.remove(item)).thenReturn(true);

      WidgetContext result = new CollectionItemsListWidget().post(widgetContext);

      repo.verify(() -> ItemRepository.remove(item), times(1));
      assertTrue(result.getSuccessMessage().contains("1 of 1"));
    }
  }

  // --- Selection bounds (shared shape across all 5 commands; exercised once each) ---

  @Test
  void overCapSelectionIsRejectedWithNoRepositoryCalls() {
    setRoles(widgetContext, ADMIN);
    String[] tooMany = new String[CollectionItemsListWidget.MAX_BULK_SELECTION + 1];
    for (int i = 0; i < tooMany.length; i++) {
      tooMany[i] = String.valueOf(i + 100);
    }
    multiValue("itemId", tooMany);
    addQueryParameter(widgetContext, "command", "bulkArchive");

    try (MockedStatic<ItemRepository> repo = mockStatic(ItemRepository.class)) {
      WidgetContext result = new CollectionItemsListWidget().post(widgetContext);

      repo.verify(() -> ItemRepository.findById(anyLong()), never());
      assertTrue(result.getErrorMessage().contains("Too many items"));
    }
  }

  @Test
  void emptySelectionIsRejectedForBulkDelete() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "bulkDelete");
    // No itemId parameters at all

    try (MockedStatic<ItemRepository> repo = mockStatic(ItemRepository.class)) {
      WidgetContext result = new CollectionItemsListWidget().post(widgetContext);

      repo.verify(() -> ItemRepository.findById(anyLong()), never());
      assertEquals("No items were selected", result.getErrorMessage());
    }
  }

  @Test
  void anIdThatNoLongerResolvesIsSkippedButTheRestOfTheBatchStillRuns() {
    setRoles(widgetContext, ADMIN);
    multiValue("itemId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkArchive");
    addQueryParameter(widgetContext, "collectionId", "10");

    Item found = itemWithId(5L, 10L);

    try (MockedStatic<ItemRepository> repo = mockStatic(ItemRepository.class);
        MockedStatic<ItemTagRepository> itemTagRepo = mockStatic(ItemTagRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollectionCommand = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CheckCollectionPermissionCommand> checkPermission = mockStatic(CheckCollectionPermissionCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      stubRowAuthorized(loadCollectionCommand, checkPermission, 10L);
      repo.when(() -> ItemRepository.findById(5L)).thenReturn(found);
      repo.when(() -> ItemRepository.findById(6L)).thenReturn(null); // deleted concurrently / tampered id
      itemTagRepo.when(() -> ItemTagRepository.findAllByItemId(5L)).thenReturn(new ArrayList<>());
      repo.when(() -> ItemRepository.save(found)).thenReturn(found);

      WidgetContext result = new CollectionItemsListWidget().post(widgetContext);

      repo.verify(() -> ItemRepository.save(any()), times(1));
      assertTrue(result.getWarningMessage().contains("1 of 2"));
      assertTrue(result.getWarningMessage().contains("Not found: 1"));
    }
  }

  // --- Per-row authorization (issue #903-class regression coverage) ---

  @Test
  void itemInACollectionTheUserIsNotAuthorizedForIsReportedAsAPerRowFailureNotABatchAbort() {
    setRoles(widgetContext, ADMIN);
    multiValue("itemId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkArchive");
    addQueryParameter(widgetContext, "collectionId", "10");

    // Item 5's OWN collection (10) is one the user is not authorized for, even though the page's
    // own collectionId query parameter (also 10, above) is the same value -- the point of this test
    // is that authorization is re-derived from the resolved item, not assumed from the page context.
    Item unauthorizedItem = itemWithId(5L, 10L);
    Item authorizedItem = itemWithId(6L, 20L);

    try (MockedStatic<ItemRepository> repo = mockStatic(ItemRepository.class);
        MockedStatic<ItemTagRepository> itemTagRepo = mockStatic(ItemTagRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollectionCommand = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CheckCollectionPermissionCommand> checkPermission = mockStatic(CheckCollectionPermissionCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      loadCollectionCommand.when(() -> LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(eq(10L), anyLong()))
          .thenReturn(null); // not authorized
      stubRowAuthorized(loadCollectionCommand, checkPermission, 20L);
      repo.when(() -> ItemRepository.findById(5L)).thenReturn(unauthorizedItem);
      repo.when(() -> ItemRepository.findById(6L)).thenReturn(authorizedItem);
      itemTagRepo.when(() -> ItemTagRepository.findAllByItemId(6L)).thenReturn(new ArrayList<>());
      repo.when(() -> ItemRepository.save(authorizedItem)).thenReturn(authorizedItem);

      WidgetContext result = new CollectionItemsListWidget().post(widgetContext);

      assertNull(unauthorizedItem.getArchived(), "an item outside the user's authorized collections must never be mutated");
      assertNotNull(authorizedItem.getArchived());
      repo.verify(() -> ItemRepository.save(unauthorizedItem), never());
      repo.verify(() -> ItemRepository.save(authorizedItem), times(1));
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.archive"),
          eq("failure"), anyLong(), any(), any(), any(), eq("item"), any(), any(), any()), times(1));
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.archive"),
          eq("success"), anyLong(), any(), any(), any(), eq("item"), any(), any(), any()), times(1));
      assertTrue(result.getWarningMessage().contains("1 of 2"));
      assertTrue(result.getWarningMessage().contains("Failed: 1"));
    }
  }

  @Test
  void itemInACollectionMissingEditPermissionIsReportedAsAPerRowFailure() {
    // Authorized to view/access the collection, but the user's groups don't carry edit_permission
    // for it -- a distinct failure mode from "collection not found" above, matching
    // EditItemFormWidget.post()'s own CheckCollectionPermissionCommand gate exactly.
    setRoles(widgetContext, ADMIN);
    multiValue("itemId", "5");
    addQueryParameter(widgetContext, "command", "bulkPublish");
    addQueryParameter(widgetContext, "collectionId", "10");

    Item item = itemWithId(5L, 10L);

    try (MockedStatic<ItemRepository> repo = mockStatic(ItemRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollectionCommand = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CheckCollectionPermissionCommand> checkPermission = mockStatic(CheckCollectionPermissionCommand.class);
        MockedStatic<LoadUserCommand> loadUserCommand = mockStatic(LoadUserCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      loadCollectionCommand.when(() -> LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(eq(10L), anyLong()))
          .thenReturn(collectionWithId(10L));
      checkPermission.when(() -> CheckCollectionPermissionCommand.userHasEditPermission(eq(10L), anyLong()))
          .thenReturn(false);
      repo.when(() -> ItemRepository.findById(5L)).thenReturn(item);
      loadUserCommand.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(adminUser());

      WidgetContext result = new CollectionItemsListWidget().post(widgetContext);

      repo.verify(() -> ItemRepository.approve(any(), any()), never());
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.publish"),
          eq("failure"), anyLong(), any(), any(), any(), eq("item"), any(), any(), any()), times(1));
      assertTrue(result.getErrorMessage().startsWith("0 of 1 selected item published. Failed: 1."));
      // Issue #427 code-review finding: the failed row's reason must reach the response, not just
      // the audit log (which a non-admin data-manager triggering this action cannot view).
      assertTrue(result.getErrorMessage().contains("Item 5 (#5): blocked: edit permission missing for collection"));
    }
  }

  @Test
  void bulkDeleteChecksDeletePermissionSeparatelyFromEditPermission() {
    // The user has edit permission but NOT delete permission for the collection -- proves delete
    // is gated by CheckCollectionPermissionCommand#userHasDeletePermission, not edit permission.
    setRoles(widgetContext, ADMIN);
    multiValue("itemId", "5");
    addQueryParameter(widgetContext, "command", "bulkDelete");
    addQueryParameter(widgetContext, "collectionId", "10");

    Item item = itemWithId(5L, 10L);

    try (MockedStatic<ItemRepository> repo = mockStatic(ItemRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollectionCommand = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CheckCollectionPermissionCommand> checkPermission = mockStatic(CheckCollectionPermissionCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      loadCollectionCommand.when(() -> LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(eq(10L), anyLong()))
          .thenReturn(collectionWithId(10L));
      checkPermission.when(() -> CheckCollectionPermissionCommand.userHasEditPermission(eq(10L), anyLong()))
          .thenReturn(true);
      checkPermission.when(() -> CheckCollectionPermissionCommand.userHasDeletePermission(eq(10L), anyLong()))
          .thenReturn(false);
      repo.when(() -> ItemRepository.findById(5L)).thenReturn(item);

      WidgetContext result = new CollectionItemsListWidget().post(widgetContext);

      repo.verify(() -> ItemRepository.remove(any()), never());
      assertTrue(result.getErrorMessage().startsWith("0 of 1 selected item deleted. Failed: 1."));
      assertTrue(result.getErrorMessage().contains("Item 5 (#5): blocked: delete permission missing for collection"));
    }
  }

  // --- bulkPublish ---

  @Test
  void bulkPublishApprovesEachResolvedItem() {
    setRoles(widgetContext, ADMIN);
    multiValue("itemId", "5");
    addQueryParameter(widgetContext, "command", "bulkPublish");
    addQueryParameter(widgetContext, "collectionId", "10");

    Item item = itemWithId(5L, 10L);
    User user = adminUser();

    try (MockedStatic<ItemRepository> repo = mockStatic(ItemRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollectionCommand = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CheckCollectionPermissionCommand> checkPermission = mockStatic(CheckCollectionPermissionCommand.class);
        MockedStatic<LoadUserCommand> loadUserCommand = mockStatic(LoadUserCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      stubRowAuthorized(loadCollectionCommand, checkPermission, 10L);
      repo.when(() -> ItemRepository.findById(5L)).thenReturn(item);
      loadUserCommand.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(user);
      repo.when(() -> ItemRepository.approve(item, user)).thenReturn(true);

      WidgetContext result = new CollectionItemsListWidget().post(widgetContext);

      repo.verify(() -> ItemRepository.approve(item, user), times(1));
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.publish"),
          eq("success"), anyLong(), any(), any(), any(), eq("item"), any(), any(), any()), times(1));
      assertTrue(result.getSuccessMessage().contains("1 of 1"));
    }
  }

  // --- bulkUnpublish ---

  @Test
  void bulkUnpublishRemovesApprovalFromEachResolvedItem() {
    setRoles(widgetContext, ADMIN);
    multiValue("itemId", "5");
    addQueryParameter(widgetContext, "command", "bulkUnpublish");
    addQueryParameter(widgetContext, "collectionId", "10");

    Item item = itemWithId(5L, 10L);
    User user = adminUser();

    try (MockedStatic<ItemRepository> repo = mockStatic(ItemRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollectionCommand = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CheckCollectionPermissionCommand> checkPermission = mockStatic(CheckCollectionPermissionCommand.class);
        MockedStatic<LoadUserCommand> loadUserCommand = mockStatic(LoadUserCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      stubRowAuthorized(loadCollectionCommand, checkPermission, 10L);
      repo.when(() -> ItemRepository.findById(5L)).thenReturn(item);
      loadUserCommand.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(user);
      repo.when(() -> ItemRepository.removeItemApproval(item, user)).thenReturn(true);

      WidgetContext result = new CollectionItemsListWidget().post(widgetContext);

      repo.verify(() -> ItemRepository.removeItemApproval(item, user), times(1));
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.unpublish"),
          eq("success"), anyLong(), any(), any(), any(), eq("item"), any(), any(), any()), times(1));
      assertTrue(result.getSuccessMessage().contains("1 of 1"));
    }
  }

  // --- bulkArchive ---

  @Test
  void bulkArchiveSetsArchivedAndArchivedByOnEachResolvedItem() {
    setRoles(widgetContext, ADMIN);
    multiValue("itemId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkArchive");
    addQueryParameter(widgetContext, "collectionId", "10");

    Item first = itemWithId(5L, 10L);
    Item second = itemWithId(6L, 10L);

    try (MockedStatic<ItemRepository> repo = mockStatic(ItemRepository.class);
        MockedStatic<ItemTagRepository> itemTagRepo = mockStatic(ItemTagRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollectionCommand = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CheckCollectionPermissionCommand> checkPermission = mockStatic(CheckCollectionPermissionCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      stubRowAuthorized(loadCollectionCommand, checkPermission, 10L);
      repo.when(() -> ItemRepository.findById(5L)).thenReturn(first);
      repo.when(() -> ItemRepository.findById(6L)).thenReturn(second);
      itemTagRepo.when(() -> ItemTagRepository.findAllByItemId(anyLong())).thenReturn(new ArrayList<>());
      repo.when(() -> ItemRepository.save(first)).thenReturn(first);
      repo.when(() -> ItemRepository.save(second)).thenReturn(second);

      WidgetContext result = new CollectionItemsListWidget().post(widgetContext);

      assertNotNull(first.getArchived());
      assertEquals(1L, first.getArchivedBy());
      assertNotNull(second.getArchived());
      repo.verify(() -> ItemRepository.save(first), times(1));
      repo.verify(() -> ItemRepository.save(second), times(1));
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.archive"),
          eq("success"), anyLong(), any(), any(), any(), eq("item"), any(), any(), any()), times(2));
      assertTrue(result.getSuccessMessage().contains("2 of 2"));
    }
  }

  @Test
  void bulkArchivePreservesTheItemsExistingTagsBeforeSaving() {
    // The load-bearing point: ItemRepository.findById() does not populate tagIdList the way it
    // populates categoryIdList, so saving straight after a plain findById() would otherwise be read
    // by ItemRepository.update() as "this item now has zero tags" and silently strip every tag.
    setRoles(widgetContext, ADMIN);
    multiValue("itemId", "5");
    addQueryParameter(widgetContext, "command", "bulkArchive");
    addQueryParameter(widgetContext, "collectionId", "10");

    Item item = itemWithId(5L, 10L);
    // Simulates a freshly loaded Item: tagIdList is null, exactly as ItemRepository.findById()
    // returns it today.

    ItemTag tagA = new ItemTag();
    tagA.setTagId(10L);
    ItemTag tagB = new ItemTag();
    tagB.setTagId(20L);

    try (MockedStatic<ItemRepository> repo = mockStatic(ItemRepository.class);
        MockedStatic<ItemTagRepository> itemTagRepo = mockStatic(ItemTagRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollectionCommand = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CheckCollectionPermissionCommand> checkPermission = mockStatic(CheckCollectionPermissionCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      stubRowAuthorized(loadCollectionCommand, checkPermission, 10L);
      repo.when(() -> ItemRepository.findById(5L)).thenReturn(item);
      itemTagRepo.when(() -> ItemTagRepository.findAllByItemId(5L)).thenReturn(List.of(tagA, tagB));
      repo.when(() -> ItemRepository.save(item)).thenReturn(item);

      new CollectionItemsListWidget().post(widgetContext);

      assertArrayEquals(new Long[] { 10L, 20L }, item.getTagIdList(),
          "the item's existing tags must be re-attached before an archive save, not wiped");
    }
  }

  // --- bulkMove ---

  @Test
  void bulkMoveWithoutAResolvableDestinationCategoryIsRejectedWithNoRepositoryCalls() {
    setRoles(widgetContext, ADMIN);
    multiValue("itemId", "5");
    addQueryParameter(widgetContext, "command", "bulkMove");
    addQueryParameter(widgetContext, "categoryId", "999");
    addQueryParameter(widgetContext, "collectionId", "10");

    try (MockedStatic<ItemRepository> repo = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepo = mockStatic(CategoryRepository.class)) {
      categoryRepo.when(() -> CategoryRepository.findById(999L)).thenReturn(null);

      WidgetContext result = new CollectionItemsListWidget().post(widgetContext);

      // Rejected before any item is even loaded -- the destination is resolved first
      repo.verify(() -> ItemRepository.findById(anyLong()), never());
      assertEquals("The destination category was not found", result.getErrorMessage());
    }
  }

  @Test
  void bulkMoveReassignsCategoryIdAndReplacesTheFullCategoryIdList() {
    setRoles(widgetContext, ADMIN);
    multiValue("itemId", "5");
    addQueryParameter(widgetContext, "command", "bulkMove");
    addQueryParameter(widgetContext, "categoryId", "42");
    addQueryParameter(widgetContext, "collectionId", "10");

    Category destination = categoryWithId(42L, 10L);
    Item item = itemWithId(5L, 10L);
    item.setCategoryId(7L);
    item.setCategoryIdList(new Long[] { 7L, 8L }); // previously in multiple categories

    try (MockedStatic<ItemRepository> repo = mockStatic(ItemRepository.class);
        MockedStatic<ItemTagRepository> itemTagRepo = mockStatic(ItemTagRepository.class);
        MockedStatic<CategoryRepository> categoryRepo = mockStatic(CategoryRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollectionCommand = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CheckCollectionPermissionCommand> checkPermission = mockStatic(CheckCollectionPermissionCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      categoryRepo.when(() -> CategoryRepository.findById(42L)).thenReturn(destination);
      stubRowAuthorized(loadCollectionCommand, checkPermission, 10L);
      repo.when(() -> ItemRepository.findById(5L)).thenReturn(item);
      itemTagRepo.when(() -> ItemTagRepository.findAllByItemId(5L)).thenReturn(new ArrayList<>());
      repo.when(() -> ItemRepository.save(item)).thenReturn(item);

      WidgetContext result = new CollectionItemsListWidget().post(widgetContext);

      assertEquals(42L, item.getCategoryId());
      assertArrayEquals(new Long[] { 42L }, item.getCategoryIdList());
      repo.verify(() -> ItemRepository.save(item), times(1));
      assertTrue(result.getSuccessMessage().contains("1 of 1"));
      assertTrue(result.getSuccessMessage().contains("Category 42"));
    }
  }

  @Test
  void bulkMoveRejectsAnItemWhoseCollectionDoesNotMatchTheDestinationCategorysCollection() {
    // Both per-row authorization checks pass (the item's own collection, 10, is one the user is
    // authorized to edit) -- isolating that THIS rejection comes specifically from the
    // category/collection mismatch check, a tampered-id defense distinct from #903's own checks.
    setRoles(widgetContext, ADMIN);
    multiValue("itemId", "5");
    addQueryParameter(widgetContext, "command", "bulkMove");
    addQueryParameter(widgetContext, "categoryId", "42");
    addQueryParameter(widgetContext, "collectionId", "10");

    Category destinationInOtherCollection = categoryWithId(42L, 99L);
    Item item = itemWithId(5L, 10L);

    try (MockedStatic<ItemRepository> repo = mockStatic(ItemRepository.class);
        MockedStatic<CategoryRepository> categoryRepo = mockStatic(CategoryRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollectionCommand = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CheckCollectionPermissionCommand> checkPermission = mockStatic(CheckCollectionPermissionCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      categoryRepo.when(() -> CategoryRepository.findById(42L)).thenReturn(destinationInOtherCollection);
      stubRowAuthorized(loadCollectionCommand, checkPermission, 10L);
      repo.when(() -> ItemRepository.findById(5L)).thenReturn(item);

      WidgetContext result = new CollectionItemsListWidget().post(widgetContext);

      repo.verify(() -> ItemRepository.save(any()), never());
      assertTrue(result.getErrorMessage().startsWith("0 of 1 selected item moved to Category 42. Failed: 1."));
      assertTrue(result.getErrorMessage()
          .contains("Item 5 (#5): blocked: destination category does not belong to this item's collection"));
    }
  }

  // --- bulkDelete ---

  @Test
  void bulkDeleteRemovesEachResolvedItem() {
    setRoles(widgetContext, ADMIN);
    multiValue("itemId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkDelete");
    addQueryParameter(widgetContext, "collectionId", "10");

    Item first = itemWithId(5L, 10L);
    Item second = itemWithId(6L, 10L);

    try (MockedStatic<ItemRepository> repo = mockStatic(ItemRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollectionCommand = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CheckCollectionPermissionCommand> checkPermission = mockStatic(CheckCollectionPermissionCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      stubRowAuthorized(loadCollectionCommand, checkPermission, 10L);
      repo.when(() -> ItemRepository.findById(5L)).thenReturn(first);
      repo.when(() -> ItemRepository.findById(6L)).thenReturn(second);
      repo.when(() -> ItemRepository.remove(first)).thenReturn(true);
      repo.when(() -> ItemRepository.remove(second)).thenReturn(true);

      WidgetContext result = new CollectionItemsListWidget().post(widgetContext);

      repo.verify(() -> ItemRepository.remove(first), times(1));
      repo.verify(() -> ItemRepository.remove(second), times(1));
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.delete"),
          eq("success"), anyLong(), any(), any(), any(), eq("item"), any(), any(), any()), times(2));
      assertTrue(result.getSuccessMessage().contains("2 of 2"));
    }
  }

  @Test
  void bulkDeleteCountsAFailedRemovalAsAFailureNotAnAbort() {
    setRoles(widgetContext, ADMIN);
    multiValue("itemId", "5");
    addQueryParameter(widgetContext, "command", "bulkDelete");
    addQueryParameter(widgetContext, "collectionId", "10");

    Item item = itemWithId(5L, 10L);

    try (MockedStatic<ItemRepository> repo = mockStatic(ItemRepository.class);
        MockedStatic<LoadCollectionCommand> loadCollectionCommand = mockStatic(LoadCollectionCommand.class);
        MockedStatic<CheckCollectionPermissionCommand> checkPermission = mockStatic(CheckCollectionPermissionCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      stubRowAuthorized(loadCollectionCommand, checkPermission, 10L);
      repo.when(() -> ItemRepository.findById(5L)).thenReturn(item);
      repo.when(() -> ItemRepository.remove(item)).thenReturn(false);

      WidgetContext result = new CollectionItemsListWidget().post(widgetContext);

      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.delete"),
          eq("failure"), anyLong(), any(), any(), any(), eq("item"), any(), any(), any()), times(1));
      assertTrue(result.getErrorMessage().startsWith("0 of 1 selected item deleted. Failed: 1."));
      assertTrue(result.getErrorMessage().contains("Item 5 (#5): delete failed"));
    }
  }
}
