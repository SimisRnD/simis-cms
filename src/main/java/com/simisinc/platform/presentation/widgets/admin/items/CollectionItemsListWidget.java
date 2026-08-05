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

package com.simisinc.platform.presentation.widgets.admin.items;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.simisinc.platform.application.cms.ImageCommand;
import com.simisinc.platform.application.items.CheckCollectionPermissionCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.ImageVariant;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemTag;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.ImageVariantRepository;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemSpecification;
import com.simisinc.platform.infrastructure.persistence.items.ItemTagRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * The /admin/collection-records{?collectionId} admin page (issue #427): a paginated table of a
 * single collection's items, plus bulk actions (Publish, Unpublish, Archive, Move, Delete) selected
 * from row checkboxes -- mirroring the bulk-action mechanics PR #911 shipped for /admin/calendars'
 * {@code CalendarEventListWidget}, and the shape {@code WebPageListWidget}/{@code
 * AdminBlogPostListWidget} added for /admin/web-pages and /admin/blog-posts (issue #427's other two
 * slices).
 *
 * <p>Items have no draft/published boolean or domain-event class of their own (confirmed: nothing
 * under {@code domain/events/items}), so Publish/Unpublish here reuse the closest existing
 * single-item precedent -- {@code approved}/{@code approved_by}, the same fields {@code
 * ApproveItemButtonWidget} already flips -- and fire no domain event, matching that precedent
 * exactly (the "fires the same domain events as individual publish/unpublish" acceptance criterion
 * is satisfied vacuously, since individual publish/unpublish fires none either). Archive reuses the
 * {@code archived}/{@code archived_by} columns {@code PageServlet#deactivateCollectionItem} already
 * sets. Move is interpreted as a category reassignment within the item's own collection (items are
 * collection-scoped -- custom fields, tags, and categories are all tied to one collection's schema
 * -- so a cross-collection move is not a good structural fit; see {@code EditItemFormWidget}'s own
 * categoryId handling, which this mirrors).
 *
 * <p>Issue #903 (PR #910) hardened {@code PageServlet}'s item-mutation actions to re-verify, per
 * item, that the acting user is authorized for that specific item's collection -- a raw {@code
 * ItemRepository.findById()} plus the page's own role check was not enough, since a bulk id list is
 * client-supplied and could reference an item outside the collection the admin is looking at. Every
 * bulk action below re-derives each item's collection via {@link
 * LoadCollectionCommand#loadCollectionByIdForAuthorizedUser} and re-checks {@link
 * CheckCollectionPermissionCommand} per row, exactly like {@code EditItemFormWidget.post()} already
 * does for a single item -- a row that fails either check is a per-row failure (not a batch abort),
 * which is what #427's "e.g. permission missing" acceptance-criteria example describes.
 *
 * @author elizabeth houser
 */
public class CollectionItemsListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/items-list.jsp";

  // A crafted POST is the only thing this bounds -- normal usage never approaches it, since
  // selection is scoped to the current page. An id list over this cap is rejected outright, never
  // silently truncated. Mirrors CalendarEventListWidget/WebPageListWidget/AdminBlogPostListWidget's
  // MAX_BULK_SELECTION exactly (issue #427).
  static final int MAX_BULK_SELECTION = 100;

  // Code-review fix (issue #427): per-row failure reasons were previously recorded only to the
  // audit log (/admin/audit-log, role="admin" only) and never returned in the response, so a
  // data-manager -- who can trigger these bulk actions without holding the admin role -- had no way
  // to learn which row failed or why beyond a bare aggregate count. Spelling out every failed/
  // not-found row's id and reason inline in the response message would make a large batch's message
  // unreadable, so only the first MAX_DETAIL_LINES rows are spelled out; the rest are still counted
  // and still fully detailed in the audit log.
  static final int MAX_DETAIL_LINES = 5;

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("showPaging", context.getPreferences().getOrDefault("showPaging", "true"));
    context.getRequest().setAttribute("columns", context.getPreferences().getOrDefault("columns", "all"));

    // Determine the parent collection. Issue #903-class fix: resolve through the same
    // group-permission-aware lookup EditItemFormWidget/ApproveItemButtonWidget already use for
    // single-item access, rather than a raw CollectionRepository.findById() -- an admin/data-manager
    // viewing this list is not automatically a member of every collection's authorized groups, and
    // the bulk actions below enforce the identical per-item check, so the read path should not be
    // laxer than the write path it feeds.
    long collectionId = context.getParameterAsLong("collectionId");
    Collection collection = LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(collectionId, context.getUserId());
    if (collection == null) {
      context.setErrorMessage("Error. Collection was not found.");
      return context;
    }
    context.getRequest().setAttribute("collection", collection);

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "20"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Determine criteria
    ItemSpecification specification = new ItemSpecification();
    specification.setCollectionId(collection.getId());
//    specification.setForUserId(context.getUserId());

    // Issue #427: items are excluded from this list by default when archived (ItemSpecification's
    // own default), same as every other item listing query -- but unlike the calendar/blog
    // precedents' 3-way status dropdown, ItemSpecification has no "archived only" mode, only an
    // include/exclude toggle, so this is a simple checkbox rather than a status dropdown. Without
    // this, a bulk-archived item would vanish from this list with no way to find it again.
    boolean includeArchived = "true".equals(context.getParameter("includeArchived"));
    specification.setIncludeArchived(includeArchived);
    context.getRequest().setAttribute("includeArchived", includeArchived);

    // Use the categories in the request
    Map<Long, Category> categoryMap = new HashMap<>();
    List<Category> categoryList = CategoryRepository.findAllByCollectionId(collection.getId());
    for (Category category : categoryList) {
      categoryMap.put(category.getId(), category);
    }
    context.getRequest().setAttribute("categoryMap", categoryMap);
    context.getRequest().setAttribute("categoryList", categoryList);

    // Query the data
    List<Item> itemList = ItemRepository.findAll(specification, constraints);
    context.getRequest().setAttribute("itemList", itemList);

    // Batch-fetch existing image variants for every item's image in one query (issue #411 PR2)
    List<Long> itemImageIds = new ArrayList<>();
    if (itemList != null) {
      for (Item listedItem : itemList) {
        Long imageId = ImageCommand.parseImageId(listedItem.getImageUrl());
        if (imageId != null) {
          itemImageIds.add(imageId);
        }
      }
    }
    Map<Long, List<ImageVariant>> imageVariantsByImageId = ImageVariantRepository.findByImageIds(itemImageIds);
    context.getRequest().setAttribute("imageVariantsByImageId", imageVariantsByImageId);

    // Carry the filter through pagination (paging_control.jspf appends this to each page link)
    String recordPagingParams = "collectionId=" + collection.getId() + (includeArchived ? "&includeArchived=true" : "");
    context.getRequest().setAttribute("recordPagingParams", recordPagingParams);

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  /**
   * Bulk actions selected from /admin/collection-records' checkbox + action-bar UI (issue #427,
   * mirroring the bulk-action mechanics PR #911 shipped for /admin/calendars).
   */
  public WidgetContext post(WidgetContext context) {

    // Matches the page's own role gate (role="admin,data-manager" in
    // admin-collections-layout.xml) and ApproveItemCommand's existing item-approval role check.
    if (!(context.hasRole("admin") || context.hasRole("data-manager"))) {
      LOG.warn("No permission to modify items");
      return context;
    }

    // Don't accept multiple form posts
    context.getUserSession().renewFormToken();

    String command = context.getParameter("command");
    if ("bulkPublish".equals(command)) {
      return bulkPublishAction(context);
    }
    if ("bulkUnpublish".equals(command)) {
      return bulkUnpublishAction(context);
    }
    if ("bulkArchive".equals(command)) {
      return bulkArchiveAction(context);
    }
    if ("bulkMove".equals(command)) {
      return bulkMoveAction(context);
    }
    if ("bulkDelete".equals(command)) {
      return bulkDeleteAction(context);
    }
    return context;
  }

  /**
   * Reuses {@code ItemRepository.approve(item, user)} directly -- the same mutation {@code
   * ApproveItemButtonWidget#action} triggers for a single item -- rather than going through {@code
   * ApproveItemCommand} (whose own role check would just duplicate {@link #post}'s gate above). Each
   * row is still independently re-authorized against its own collection (see the class javadoc).
   */
  private WidgetContext bulkPublishAction(WidgetContext context) {
    List<Long> itemIds = resolveSelectedItemIds(context);
    if (itemIds == null) {
      return rejectBulkSelection(context);
    }
    if (itemIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    long userId = context.getUserId();
    User user = context.getUserSession().getUser();
    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    List<String> rowIssues = new ArrayList<>();
    for (Long itemId : itemIds) {
      Item item = ItemRepository.findById(itemId);
      if (item == null) {
        ++notFound;
        rowIssues.add("#" + itemId + ": not found");
        continue;
      }
      String denialReason = checkRowAuthorization(item, userId, false);
      if (denialReason != null) {
        ++failed;
        rowIssues.add(item.getName() + " (#" + item.getId() + "): " + denialReason);
        AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.publish", AuditEventCommand.FAILURE,
            "item", String.valueOf(item.getId()), item.getName(), denialReason + " (bulk)");
        continue;
      }
      boolean result = ItemRepository.approve(item, user);
      String outcome = result ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (result) {
        ++succeeded;
      } else {
        ++failed;
        rowIssues.add(item.getName() + " (#" + item.getId() + "): save failed");
      }
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.publish", outcome,
          "item", String.valueOf(item.getId()), item.getName(), "(bulk)");
    }

    setBulkResultMessage(context, "published", succeeded, itemIds.size(), notFound, failed, rowIssues);
    context.setRedirect(returnToCollection(context));
    return context;
  }

  /** Reuses {@code ItemRepository.removeItemApproval(item, user)} directly; see {@link #bulkPublishAction}. */
  private WidgetContext bulkUnpublishAction(WidgetContext context) {
    List<Long> itemIds = resolveSelectedItemIds(context);
    if (itemIds == null) {
      return rejectBulkSelection(context);
    }
    if (itemIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    long userId = context.getUserId();
    User user = context.getUserSession().getUser();
    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    List<String> rowIssues = new ArrayList<>();
    for (Long itemId : itemIds) {
      Item item = ItemRepository.findById(itemId);
      if (item == null) {
        ++notFound;
        rowIssues.add("#" + itemId + ": not found");
        continue;
      }
      String denialReason = checkRowAuthorization(item, userId, false);
      if (denialReason != null) {
        ++failed;
        rowIssues.add(item.getName() + " (#" + item.getId() + "): " + denialReason);
        AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.unpublish", AuditEventCommand.FAILURE,
            "item", String.valueOf(item.getId()), item.getName(), denialReason + " (bulk)");
        continue;
      }
      boolean result = ItemRepository.removeItemApproval(item, user);
      String outcome = result ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (result) {
        ++succeeded;
      } else {
        ++failed;
        rowIssues.add(item.getName() + " (#" + item.getId() + "): save failed");
      }
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.unpublish", outcome,
          "item", String.valueOf(item.getId()), item.getName(), "(bulk)");
    }

    setBulkResultMessage(context, "unpublished", succeeded, itemIds.size(), notFound, failed, rowIssues);
    context.setRedirect(returnToCollection(context));
    return context;
  }

  /**
   * Sets {@code archivedBy}/{@code archived}, identical to what {@code
   * PageServlet#deactivateCollectionItem} already does for a single item, then saves via {@link
   * ItemRepository#save}. Because {@code save()} routes through the full record {@code update()}
   * (which also reconciles category/tag membership from the in-memory {@link Item#getTagIdList()}),
   * the item's current tag associations are re-loaded onto it first -- {@link
   * ItemRepository#findById} does not populate {@code tagIdList} the way it populates {@code
   * categoryIdList}, so saving straight after a plain {@code findById()} would otherwise be read as
   * "this item has zero tags" and silently strip every tag from it. This is the same latent gap
   * {@code PageServlet#deactivateCollectionItem} itself has today; flagged separately rather than
   * fixed at the source, since fixing {@code ItemRepository#buildRecord} touches every item read
   * path in the codebase, well beyond this bulk-actions change.
   */
  private WidgetContext bulkArchiveAction(WidgetContext context) {
    List<Long> itemIds = resolveSelectedItemIds(context);
    if (itemIds == null) {
      return rejectBulkSelection(context);
    }
    if (itemIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    long userId = context.getUserId();
    Timestamp now = new Timestamp(System.currentTimeMillis());
    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    List<String> rowIssues = new ArrayList<>();
    for (Long itemId : itemIds) {
      Item item = ItemRepository.findById(itemId);
      if (item == null) {
        ++notFound;
        rowIssues.add("#" + itemId + ": not found");
        continue;
      }
      String denialReason = checkRowAuthorization(item, userId, false);
      if (denialReason != null) {
        ++failed;
        rowIssues.add(item.getName() + " (#" + item.getId() + "): " + denialReason);
        AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.archive", AuditEventCommand.FAILURE,
            "item", String.valueOf(item.getId()), item.getName(), denialReason + " (bulk)");
        continue;
      }
      preserveExistingTags(item);
      item.setArchivedBy(userId);
      item.setArchived(now);
      item.setModifiedBy(userId);
      Item result = ItemRepository.save(item);
      String outcome = result != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (result != null) {
        ++succeeded;
      } else {
        ++failed;
        rowIssues.add(item.getName() + " (#" + item.getId() + "): save failed");
      }
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.archive", outcome,
          "item", String.valueOf(item.getId()), item.getName(), "(bulk)");
    }

    setBulkResultMessage(context, "archived", succeeded, itemIds.size(), notFound, failed, rowIssues);
    context.setRedirect(returnToCollection(context));
    return context;
  }

  /**
   * Interpreted as a category reassignment within the item's own collection (see the class
   * javadoc). The destination category is resolved before any item is loaded, mirroring {@code
   * CalendarEventListWidget#bulkMoveAction}'s destination-first-then-rows order, but -- unlike the
   * calendar precedent, where every event shares one calendar list -- each item's own collection is
   * still re-derived and the destination category is additionally checked against THAT collection,
   * not just the page's own {@code collectionId} query parameter, so a tampered {@code itemId}
   * pointing at a different collection cannot be moved into a category that does not belong to it.
   * This replaces the item's full category membership with just the destination (a "move", not an
   * "also add"), matching how a single-value field reassignment works for the calendar/blog move
   * precedents.
   */
  private WidgetContext bulkMoveAction(WidgetContext context) {
    long targetCategoryId = context.getParameterAsLong("categoryId", -1);
    Category targetCategory = targetCategoryId > -1 ? CategoryRepository.findById(targetCategoryId) : null;
    if (targetCategory == null) {
      context.setErrorMessage("The destination category was not found");
      context.setRedirect(returnToCollection(context));
      return context;
    }

    List<Long> itemIds = resolveSelectedItemIds(context);
    if (itemIds == null) {
      return rejectBulkSelection(context);
    }
    if (itemIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    long userId = context.getUserId();
    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    List<String> rowIssues = new ArrayList<>();
    for (Long itemId : itemIds) {
      Item item = ItemRepository.findById(itemId);
      if (item == null) {
        ++notFound;
        rowIssues.add("#" + itemId + ": not found");
        continue;
      }
      String denialReason = checkRowAuthorization(item, userId, false);
      if (denialReason == null && item.getCollectionId() != targetCategory.getCollectionId()) {
        denialReason = "blocked: destination category does not belong to this item's collection";
      }
      if (denialReason != null) {
        ++failed;
        rowIssues.add(item.getName() + " (#" + item.getId() + "): " + denialReason);
        AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.move", AuditEventCommand.FAILURE,
            "item", String.valueOf(item.getId()), item.getName(), denialReason + " (bulk)");
        continue;
      }
      item.setCategoryId(targetCategory.getId());
      item.setCategoryIdList(new Long[] { targetCategory.getId() });
      preserveExistingTags(item);
      item.setModifiedBy(userId);
      Item result = ItemRepository.save(item);
      String outcome = result != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (result != null) {
        ++succeeded;
      } else {
        ++failed;
        rowIssues.add(item.getName() + " (#" + item.getId() + "): save failed");
      }
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.move", outcome,
          "item", String.valueOf(item.getId()), item.getName(),
          "movedTo=" + targetCategory.getName() + " (bulk)");
    }

    setBulkResultMessage(context, "moved to " + targetCategory.getName(), succeeded, itemIds.size(), notFound, failed,
        rowIssues);
    context.setRedirect(returnToCollection(context));
    return context;
  }

  /**
   * No existing single-item admin "delete an item" widget exists to reuse -- this bulk action is
   * the first exposed item-delete affordance in the admin (see class javadoc), so the confirmation
   * reveal modal required by #427's acceptance criteria matters especially here. Gated by delete
   * permission specifically, not edit permission (see {@link #checkRowAuthorization}).
   */
  private WidgetContext bulkDeleteAction(WidgetContext context) {
    List<Long> itemIds = resolveSelectedItemIds(context);
    if (itemIds == null) {
      return rejectBulkSelection(context);
    }
    if (itemIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    long userId = context.getUserId();
    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    List<String> rowIssues = new ArrayList<>();
    for (Long itemId : itemIds) {
      Item item = ItemRepository.findById(itemId);
      if (item == null) {
        ++notFound;
        rowIssues.add("#" + itemId + ": not found");
        continue;
      }
      String denialReason = checkRowAuthorization(item, userId, true);
      if (denialReason != null) {
        ++failed;
        rowIssues.add(item.getName() + " (#" + item.getId() + "): " + denialReason);
        AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.delete", AuditEventCommand.FAILURE,
            "item", String.valueOf(item.getId()), item.getName(), denialReason + " (bulk)");
        continue;
      }
      boolean removed = ItemRepository.remove(item);
      String outcome = removed ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (removed) {
        ++succeeded;
      } else {
        ++failed;
        rowIssues.add(item.getName() + " (#" + item.getId() + "): delete failed");
      }
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.delete", outcome,
          "item", String.valueOf(item.getId()), item.getName(), "(bulk)");
    }

    setBulkResultMessage(context, "deleted", succeeded, itemIds.size(), notFound, failed, rowIssues);
    context.setRedirect(returnToCollection(context));
    return context;
  }

  /**
   * Issue #903-class per-row re-authorization: re-derives the item's OWN collection (never trusting
   * the page's {@code collectionId} query parameter, since the id list came off the request and
   * could be tampered to name an item outside the collection the admin is looking at) and checks it
   * two ways, exactly matching {@code EditItemFormWidget.post()}'s single-item gate: (1) the acting
   * user must be authorized for that collection at all ({@link
   * LoadCollectionCommand#loadCollectionByIdForAuthorizedUser}), and (2) the user's groups must
   * carry the collection's edit (or, for delete, delete) permission ({@link
   * CheckCollectionPermissionCommand}) -- a permission which is NOT implied by holding the
   * admin/data-manager role checked once in {@link #post}. Returns null when authorized, or a
   * human-readable reason (used as both the aggregate-message contributor and the per-row audit
   * detail) when not -- never throws, and never aborts the rest of the batch.
   */
  private String checkRowAuthorization(Item item, long userId, boolean requireDelete) {
    Collection itemCollection = LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(item.getCollectionId(), userId);
    if (itemCollection == null) {
      return "blocked: collection not found or not authorized";
    }
    boolean permitted = requireDelete
        ? CheckCollectionPermissionCommand.userHasDeletePermission(item.getCollectionId(), userId)
        : CheckCollectionPermissionCommand.userHasEditPermission(item.getCollectionId(), userId);
    if (!permitted) {
      return "blocked: " + (requireDelete ? "delete" : "edit") + " permission missing for collection";
    }
    return null;
  }

  /**
   * Re-loads the item's current tag associations onto it before a save that isn't itself changing
   * tags -- see {@link #bulkArchiveAction}'s javadoc for why this guard exists.
   */
  private void preserveExistingTags(Item item) {
    List<ItemTag> tagList = ItemTagRepository.findAllByItemId(item.getId());
    List<Long> tagIds = new ArrayList<>();
    if (tagList != null) {
      for (ItemTag itemTag : tagList) {
        tagIds.add(itemTag.getTagId());
      }
    }
    item.setTagIdList(tagIds.toArray(new Long[0]));
  }

  private String returnToCollection(WidgetContext context) {
    long collectionId = context.getParameterAsLong("collectionId", -1);
    return "/admin/collection-records?collectionId=" + collectionId;
  }

  /**
   * Parses and dedupes the selected item ids from the repeated {@code itemId} hidden inputs the
   * bulk modals inject, silently dropping any non-numeric entry (a tampered value is not a
   * batch-ending error). Returns {@code null} when the list exceeds {@link #MAX_BULK_SELECTION} --
   * the whole request is then rejected rather than silently truncated, since truncation could apply
   * the action to a different subset of items than the one the admin reviewed and confirmed.
   * Mirrors CalendarEventListWidget#resolveSelectedEventIds exactly.
   */
  private List<Long> resolveSelectedItemIds(WidgetContext context) {
    String[] rawIds = context.getParameterMap().get("itemId");
    Set<Long> ids = new LinkedHashSet<>();
    if (rawIds != null) {
      for (String rawId : rawIds) {
        try {
          ids.add(Long.parseLong(rawId.trim()));
        } catch (NumberFormatException e) {
          // Dropped, not treated as a batch-ending error
        }
      }
    }
    if (ids.size() > MAX_BULK_SELECTION) {
      LOG.warn("Bulk item action rejected: " + ids.size() + " ids exceeds MAX_BULK_SELECTION (" + MAX_BULK_SELECTION + ")");
      return null;
    }
    return new ArrayList<>(ids);
  }

  private WidgetContext rejectBulkSelection(WidgetContext context) {
    context.setErrorMessage("Too many items were selected (maximum " + MAX_BULK_SELECTION
        + "). Select fewer items and try again.");
    context.setRedirect(returnToCollection(context));
    return context;
  }

  private WidgetContext rejectEmptySelection(WidgetContext context) {
    context.setErrorMessage("No items were selected");
    context.setRedirect(returnToCollection(context));
    return context;
  }

  /**
   * Sets the single aggregate result message every bulk action reports (page_messages.jspf renders
   * exactly one of success/warning/error). Mirrors CalendarEventListWidget#setBulkResultMessage.
   *
   * <p>{@code rowIssues} carries a human-readable "name (#id): reason" entry for every not-found or
   * failed row (issue #427 code-review finding: these reasons were previously recorded only to the
   * admin-only audit log, never returned to the caller, so a data-manager -- who can trigger these
   * actions without holding the admin role needed to view /admin/audit-log -- had no way to learn
   * which row failed or why). Only the first {@link #MAX_DETAIL_LINES} are spelled out inline to
   * keep the message readable; the full detail for every row remains in the audit log regardless.
   */
  private void setBulkResultMessage(WidgetContext context, String verb, int succeeded, int totalSelected,
      int notFound, int failed, List<String> rowIssues) {
    StringBuilder sb = new StringBuilder();
    sb.append(succeeded).append(" of ").append(totalSelected).append(" selected item")
        .append(totalSelected == 1 ? "" : "s").append(" ").append(verb).append(".");
    if (notFound > 0) {
      sb.append(" Not found: ").append(notFound).append(".");
    }
    if (failed > 0) {
      sb.append(" Failed: ").append(failed).append(".");
    }
    appendRowIssueDetails(sb, rowIssues);
    if (succeeded == 0) {
      context.setErrorMessage(sb.toString());
    } else if (succeeded != totalSelected) {
      context.setWarningMessage(sb.toString());
    } else {
      context.setSuccessMessage(sb.toString());
    }
  }

  /**
   * Appends up to {@link #MAX_DETAIL_LINES} "name (#id): reason" entries to the aggregate message,
   * summarizing any remainder by count only. See {@link #setBulkResultMessage} for why this exists.
   */
  private void appendRowIssueDetails(StringBuilder sb, List<String> rowIssues) {
    if (rowIssues == null || rowIssues.isEmpty()) {
      return;
    }
    sb.append(" (");
    int shown = Math.min(rowIssues.size(), MAX_DETAIL_LINES);
    for (int i = 0; i < shown; i++) {
      if (i > 0) {
        sb.append("; ");
      }
      sb.append(rowIssues.get(i));
    }
    if (rowIssues.size() > shown) {
      sb.append("; and ").append(rowIssues.size() - shown).append(" more");
    }
    sb.append(")");
  }
}
