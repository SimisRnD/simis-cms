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

package com.simisinc.platform.presentation.widgets.admin.cms;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jobrunr.scheduling.BackgroundJobRequest;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.DeleteImageCommand;
import com.simisinc.platform.application.cms.DeleteImageTagCommand;
import com.simisinc.platform.application.cms.ImageUsageCommand;
import com.simisinc.platform.application.cms.SaveImageTagCommand;
import com.simisinc.platform.application.cms.RegenerateImageVariantsCommand;
import com.simisinc.platform.application.cms.GenerateImageVariantsCommand;
import com.simisinc.platform.application.cms.ScanForDuplicateImagesCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.domain.model.cms.ImageTag;
import com.simisinc.platform.domain.model.cms.ImageVariant;
import com.simisinc.platform.infrastructure.database.AutoRollback;
import com.simisinc.platform.infrastructure.database.AutoStartTransaction;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ImageSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.ImageTagMapRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ImageTagRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ImageVariantRepository;
import com.simisinc.platform.infrastructure.scheduler.cms.FocalPointVariantJob;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Widget to display the image browser to system administrators
 *
 * @author matt rajkowski
 * @created 7/11/18 4:21 PM
 */
public class AdminImageBrowserWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  private static Log LOG = LogFactory.getLog(AdminImageBrowserWidget.class);

  static String JSP = "/admin/image-browser.jsp";

  // A bulk delete request is rejected outright above this many ids, rather than silently
  // truncated -- truncation could apply the delete to a different subset than the admin reviewed
  // and confirmed. Mirrors UsersListWidget's MAX_BULK_SELECTION.
  static final int MAX_BULK_SELECTION = 100;

  // Default page size for the image grid (issue #498 slice 2). Most admin list widgets in this
  // codebase default their "limit" preference to 20 (UsersListWidget, AllowedIPListWidget,
  // BlockedIPListWidget, CollectionItemsListWidget, ecommerce lists, etc.) -- there is no
  // site-wide page-size property to defer to. But those are dense text tables, and this page
  // renders actual <img> thumbnails in a grid (small-up-2 medium-up-3 large-up-5, see
  // image-browser.jsp), so a text-list-sized page would be an unusually short/wide grid. 40
  // gives a clean 8 rows at the widest (5-column) breakpoint.
  static final int DEFAULT_PAGE_SIZE = 40;

  public WidgetContext execute(WidgetContext context) {

    // AJAX usage pre-check for a single image, used by the delete-confirmation UI and the
    // per-row "Orphaned"/"Used on" badge. This is deliberately computed on demand for one image
    // at a time (see ImageUsageCommand's class docs) rather than for the whole list up front.
    if (context.getParameterAsBoolean("checkUsage")) {
      return checkUsageAction(context);
    }

    // Duplicate-detection review view (?view=duplicates) -- a separate rendering path from the
    // normal paginated grid below, since it's grouped by file_hash rather than one flat list.
    if ("duplicates".equals(context.getParameter("view"))) {
      return duplicatesViewAction(context);
    }

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine the search
    String query = StringUtils.trimToNull(context.getParameter("query"));
    context.getRequest().setAttribute("query", query);

    // Determine the tag filter
    long tagId = context.getParameterAsLong("tagId", -1);
    context.getRequest().setAttribute("tagId", tagId);
    List<ImageTag> allImageTags = ImageTagRepository.findAll();
    context.getRequest().setAttribute("allImageTags", allImageTags);
    context.getRequest().setAttribute("imageTagCounts", ImageTagRepository.countAllByImageTagId());

    // Determine the record paging (issue #498 slice 2) -- at most one page's worth of images is
    // loaded per request, not all 200+. Follows the same page/items request-param convention as
    // ContentListWidget/AllowedIPListWidget/etc.
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", String.valueOf(DEFAULT_PAGE_SIZE)));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Sort by date/name/size -- an invalid or missing value falls back to "date", which maps to
    // the same "created DESC" order this list used before sorting existed, so a plain page load
    // (no sortBy param) keeps its prior ordering. Mirrors FolderFilesListWidget's sortBy switch
    // (issue #502) -- the column name is chosen from this fixed allowlist, never taken from the
    // request parameter directly, so it cannot be used to inject arbitrary SQL.
    String sortBy = context.getParameter(RequestConstants.RECORD_SORT_BY, "date");
    switch (sortBy) {
      case "name":
        constraints.setColumnToSortBy("filename");
        break;
      case "size":
        constraints.setColumnToSortBy("file_length", "desc");
        break;
      case "date":
      default:
        sortBy = "date";
        constraints.setColumnToSortBy("created", "desc");
        break;
    }
    context.getRequest().setAttribute(RequestConstants.RECORD_SORT_BY, sortBy);

    // Carry the current search term and sort through pagination links (paging_control.jspf appends
    // this to each page link's query string) so paging forward/back doesn't lose either. URL-encoded
    // so the free-text search term cannot break the query string or the href.
    StringBuilder pagingParams = new StringBuilder();
    if (query != null) {
      pagingParams.append("query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
    }
    if (!"date".equals(sortBy)) {
      if (pagingParams.length() > 0) {
        pagingParams.append("&");
      }
      pagingParams.append("sortBy=").append(sortBy);
    }
    if (pagingParams.length() > 0) {
      context.getRequest().setAttribute("recordPagingParams", pagingParams.toString());
    }

    List<Image> imageList;
    if (query != null || tagId > -1) {
      ImageSpecification specification = new ImageSpecification();
      if (query != null) {
        specification.setMatchesName(query);
      }
      if (tagId > -1) {
        specification.setTagId(tagId);
      }
      imageList = ImageRepository.findAll(specification, constraints);
    } else {
      imageList = ImageRepository.findAll(null, constraints);
    }
    context.getRequest().setAttribute("imageList", imageList);

    // Batch-fetch existing image variants for every listed image in one query (issue #411 PR2)
    List<Long> browserImageIds = new ArrayList<>();
    if (imageList != null) {
      for (Image listedImage : imageList) {
        browserImageIds.add(listedImage.getId());
      }
    }
    Map<Long, List<ImageVariant>> imageVariantsByImageId = ImageVariantRepository.findByImageIds(browserImageIds);
    context.getRequest().setAttribute("imageVariantsByImageId", imageVariantsByImageId);
    // The originals' widths, so srcset can offer the full-size file as a candidate rather than
    // topping out at a thumbnail (issue #1370). One extra query for the whole page, not per row.
    context.getRequest().setAttribute("imageWidthsByImageId", ImageRepository.findWidthsByIds(browserImageIds));

    // Batch-fetch every listed image's tags in one query, same pattern as the variants above
    Map<Long, List<ImageTag>> imageTagsByImageId = ImageTagRepository.findByImageIds(browserImageIds);
    context.getRequest().setAttribute("imageTagsByImageId", imageTagsByImageId);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  /**
   * Returns {"imageId":N,"orphaned":true|false,"usages":[{"type":"...","label":"..."}]} for one image.
   */
  private WidgetContext checkUsageAction(WidgetContext context) {
    long imageId = context.getParameterAsLong("imageId", -1);
    Image image = imageId > -1 ? ImageRepository.findById(imageId) : null;
    if (image == null) {
      context.setJson("{\"imageId\":" + imageId + ",\"orphaned\":true,\"usages\":[]}");
      return context;
    }
    List<ImageUsageCommand.UsageReference> usages = ImageUsageCommand.findUsages(image);
    StringBuilder json = new StringBuilder();
    json.append("{\"imageId\":").append(image.getId());
    json.append(",\"orphaned\":").append(usages.isEmpty());
    json.append(",\"usages\":[");
    for (int i = 0; i < usages.size(); i++) {
      if (i > 0) {
        json.append(",");
      }
      ImageUsageCommand.UsageReference usage = usages.get(i);
      json.append("{\"type\":\"").append(JsonCommand.toJson(usage.getSourceType())).append("\"");
      json.append(",\"label\":\"").append(JsonCommand.toJson(usage.getLabel())).append("\"}");
    }
    json.append("]}");
    context.setJson(json.toString());
    return context;
  }

  /**
   * Renders the duplicate-review grid: every {@code file_hash} shared by 2+ images, each with its
   * member images fetched underneath it. Unlike the main grid, this is not paginated -- duplicate
   * groups are expected to be a small fraction of the library, not something to page through.
   */
  private WidgetContext duplicatesViewAction(WidgetContext context) {
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("duplicatesView", true);

    // The shared Tags modal (tagsReveal, rendered once regardless of which view is active) reads
    // this to list every existing tag as a checkbox -- without it, the modal would silently show
    // no existing tags when opened from a card here.
    context.getRequest().setAttribute("allImageTags", ImageTagRepository.findAll());

    List<String> duplicateHashes = ImageRepository.findDuplicateFileHashes();
    Map<String, List<Image>> duplicateGroups = new LinkedHashMap<>();
    List<Long> allImageIds = new ArrayList<>();
    for (String hash : duplicateHashes) {
      ImageSpecification specification = new ImageSpecification();
      specification.setFileHash(hash);
      List<Image> groupImages = ImageRepository.findAll(specification, null);
      duplicateGroups.put(hash, groupImages);
      for (Image groupImage : groupImages) {
        allImageIds.add(groupImage.getId());
      }
    }
    context.getRequest().setAttribute("duplicateGroups", duplicateGroups);

    // Same batch-prefetch pattern as the main grid, just scoped to the images actually shown here
    context.getRequest().setAttribute("imageVariantsByImageId", ImageVariantRepository.findByImageIds(allImageIds));
    // Originals' widths for srcset, same as the browser grid above (issue #1370).
    context.getRequest().setAttribute("imageWidthsByImageId", ImageRepository.findWidthsByIds(allImageIds));
    context.getRequest().setAttribute("imageTagsByImageId", ImageTagRepository.findByImageIds(allImageIds));

    context.setJsp(JSP);
    return context;
  }

  /**
   * A single image is being deleted (Delete button + confirmPostAction(), see image-browser.jsp).
   */
  public WidgetContext delete(WidgetContext context) {

    if (!(context.hasRole("admin") || context.hasRole("content-manager"))) {
      LOG.warn("No permission to delete an image");
      return context;
    }

    long imageId = context.getParameterAsLong("imageId", -1);
    Image image = imageId > -1 ? ImageRepository.findById(imageId) : null;
    if (image == null) {
      context.setErrorMessage("Error. Image was not found.");
      context.setRedirect(redirectWithQuery(context));
      return context;
    }

    boolean removed = deleteOne(context, image);
    if (removed) {
      context.setSuccessMessage("Image deleted");
    } else {
      context.setErrorMessage("Error. Image could not be deleted.");
    }
    context.setRedirect(redirectWithQuery(context));
    return context;
  }

  /**
   * Bulk delete (multi-select checkboxes + "Delete Selected" bar, see image-browser.jsp), and any
   * future non-delete commands this widget grows.
   */
  public WidgetContext post(WidgetContext context) {

    if (!(context.hasRole("admin") || context.hasRole("content-manager"))) {
      LOG.warn("No permission to modify images");
      return context;
    }

    context.getUserSession().renewFormToken();

    String command = context.getParameter("command");
    if ("bulkDelete".equals(command)) {
      return bulkDeleteAction(context);
    } else if ("deleteDuplicates".equals(command)) {
      return deleteDuplicatesAction(context);
    } else if ("scanForDuplicates".equals(command)) {
      return scanForDuplicatesAction(context);
    } else if ("generateMissingSizes".equals(command)) {
      return generateMissingSizesAction(context);
    } else if ("setFocalPoint".equals(command)) {
      return setFocalPointAction(context);
    } else if ("setAltText".equals(command)) {
      return setAltTextAction(context);
    } else if ("setTags".equals(command)) {
      return setTagsAction(context);
    } else if ("deleteTag".equals(command)) {
      return deleteTagAction(context);
    }
    return context;
  }

  /**
   * Enqueues the "Scan for Duplicates" backfill (see {@code ScanForDuplicateImagesCommand}) for
   * every image that has no {@code file_hash} yet. Idempotent -- a re-click only re-enqueues
   * whatever is still un-hashed, so it's safe to click again after new uploads.
   */
  /**
   * Backfills a variant rung across images uploaded before that rung existed (issue #1422).
   * Variants are generated once at upload, so widening the ladder does nothing for the existing
   * library on its own.
   */
  private WidgetContext generateMissingSizesAction(WidgetContext context) {
    int enqueued = RegenerateImageVariantsCommand.startBackfill(
        GenerateImageVariantsCommand.SMALL, GenerateImageVariantsCommand.SMALL_MAX_DIMENSION);
    if (enqueued > 0) {
      context.setSuccessMessage(enqueued + " image" + (enqueued == 1 ? "" : "s")
          + " queued. Check the Job Queue for progress -- pages will start serving the smaller size as each finishes.");
    } else {
      context.setSuccessMessage("Every image already has all of its sizes. Nothing to generate.");
    }
    context.setRedirect(redirectWithQuery(context));
    return context;
  }

  private WidgetContext scanForDuplicatesAction(WidgetContext context) {
    int enqueued = ScanForDuplicateImagesCommand.startScan();
    if (enqueued > 0) {
      context.setSuccessMessage(enqueued + " image" + (enqueued == 1 ? "" : "s")
          + " queued for scanning. Check the Job Queue for progress, then come back to Duplicates.");
    } else {
      // Nothing left to hash is not the same as nothing found. Reporting only "already scanned"
      // reads as a null result, so an admin whose library does contain duplicates concludes the
      // scan found none and never opens the Duplicates view where they are already listed.
      int duplicateSets = ImageRepository.findDuplicateFileHashes().size();
      if (duplicateSets > 0) {
        context.setSuccessMessage("Every image has already been scanned. " + duplicateSets + " set"
            + (duplicateSets == 1 ? "" : "s") + " of duplicates found -- see Duplicates.");
      } else {
        context.setSuccessMessage("Every image has already been scanned. No duplicates found.");
      }
    }
    context.setRedirect(redirectWithQuery(context));
    return context;
  }

  /**
   * Deletes selected images from the duplicates view (see image-browser.jsp's reuse of
   * bulkDeleteReveal with command=deleteDuplicates). Unlike the general {@link #bulkDeleteAction},
   * this specifically re-checks usage server-side before each delete and skips (rather than
   * deletes) anything still in use -- the client-side usage warning alone isn't a real guarantee,
   * and this action exists specifically to make bulk-deleting "duplicates" safe by default. The
   * general bulk-delete path is intentionally left unchanged.
   */
  private WidgetContext deleteDuplicatesAction(WidgetContext context) {
    List<Long> imageIds = resolveSelectedImageIds(context);
    if (imageIds == null) {
      context.setErrorMessage("Too many images were selected (maximum " + MAX_BULK_SELECTION
          + "). Select fewer images and try again.");
      context.setRedirect(redirectWithQuery(context));
      return context;
    }
    if (imageIds.isEmpty()) {
      context.setErrorMessage("No images were selected");
      context.setRedirect(redirectWithQuery(context));
      return context;
    }

    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    int skippedInUse = 0;
    for (Long imageId : imageIds) {
      Image image = ImageRepository.findById(imageId);
      if (image == null) {
        ++notFound;
        continue;
      }
      if (!ImageUsageCommand.findUsages(image).isEmpty()) {
        ++skippedInUse;
        continue;
      }
      if (deleteOne(context, image)) {
        ++succeeded;
      } else {
        ++failed;
      }
    }

    StringBuilder message = new StringBuilder();
    message.append(succeeded).append(" of ").append(imageIds.size()).append(" selected image")
        .append(imageIds.size() == 1 ? "" : "s").append(" deleted.");
    if (skippedInUse > 0) {
      message.append(" ").append(skippedInUse).append(" skipped -- still in use.");
    }
    if (notFound > 0) {
      message.append(" ").append(notFound).append(" were already gone.");
    }
    if (failed > 0) {
      message.append(" ").append(failed).append(" could not be deleted.");
    }
    if (succeeded > 0) {
      context.setSuccessMessage(message.toString());
    } else {
      context.setErrorMessage(message.toString());
    }
    context.setRedirect(redirectWithQuery(context));
    return context;
  }

  private WidgetContext bulkDeleteAction(WidgetContext context) {
    List<Long> imageIds = resolveSelectedImageIds(context);
    if (imageIds == null) {
      context.setErrorMessage("Too many images were selected (maximum " + MAX_BULK_SELECTION
          + "). Select fewer images and try again.");
      context.setRedirect(redirectWithQuery(context));
      return context;
    }
    if (imageIds.isEmpty()) {
      context.setErrorMessage("No images were selected");
      context.setRedirect(redirectWithQuery(context));
      return context;
    }

    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    for (Long imageId : imageIds) {
      Image image = ImageRepository.findById(imageId);
      if (image == null) {
        ++notFound;
        continue;
      }
      if (deleteOne(context, image)) {
        ++succeeded;
      } else {
        ++failed;
      }
    }

    StringBuilder message = new StringBuilder();
    message.append(succeeded).append(" of ").append(imageIds.size()).append(" selected image")
        .append(imageIds.size() == 1 ? "" : "s").append(" deleted.");
    if (notFound > 0) {
      message.append(" ").append(notFound).append(" were already gone.");
    }
    if (failed > 0) {
      message.append(" ").append(failed).append(" could not be deleted.");
    }
    if (succeeded > 0) {
      context.setSuccessMessage(message.toString());
    } else {
      context.setErrorMessage(message.toString());
    }
    context.setRedirect(redirectWithQuery(context));
    return context;
  }

  /**
   * Sets an image's focal point (issue #411 PR3, see the focal-point modal in image-browser.jsp)
   * and enqueues the background job that regenerates its focal-point-dependent square variant.
   */
  private WidgetContext setFocalPointAction(WidgetContext context) {
    long imageId = context.getParameterAsLong("imageId", -1);
    Image image = imageId > -1 ? ImageRepository.findById(imageId) : null;
    if (image == null) {
      context.setErrorMessage("Error. Image was not found.");
      context.setRedirect(redirectWithQuery(context));
      return context;
    }

    BigDecimal focalX = parsePercent(context.getParameter("focalX"));
    BigDecimal focalY = parsePercent(context.getParameter("focalY"));
    if (focalX == null || focalY == null) {
      context.setErrorMessage("Error. The focal point values were not valid.");
      context.setRedirect(redirectWithQuery(context));
      return context;
    }

    image.setFocalX(focalX);
    image.setFocalY(focalY);
    boolean saved = ImageRepository.save(image) != null;
    AuditEventCommand.record(context, AuditEventCommand.CONTENT, "image.setFocalPoint",
        saved ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
        "image", String.valueOf(image.getId()), image.getFilename(), null);
    if (!saved) {
      context.setErrorMessage("Error. The focal point could not be saved.");
      context.setRedirect(redirectWithQuery(context));
      return context;
    }

    BackgroundJobRequest.enqueue(new FocalPointVariantJob(image.getId()));
    context.setSuccessMessage("Focal point saved");
    context.setRedirect(redirectWithQuery(context));
    return context;
  }

  // Matches the images.alt_text column width -- caught here with a clear message rather than
  // surfacing a raw "value too long" SQLException from a blind save.
  private static final int MAX_ALT_TEXT_LENGTH = 255;

  /**
   * Sets an image's library-level alt text (see the alt-text modal in image-browser.jsp). This is
   * a library-management field only -- it is not yet read by any public-facing <img> rendering
   * (Item/BlogPost/Product etc. store a flat imageUrl string today, not an image_id reference back
   * to this row, so there is nowhere for those pages to read it from without a larger change) --
   * the modal says so directly rather than leaving that as a silent gap. Same shape as
   * setFocalPointAction: load the record, set the one field, save directly (not routed through
   * SaveImageCommand, which is the upload funnel, not a general field editor).
   */
  private WidgetContext setAltTextAction(WidgetContext context) {
    long imageId = context.getParameterAsLong("imageId", -1);
    Image image = imageId > -1 ? ImageRepository.findById(imageId) : null;
    if (image == null) {
      context.setErrorMessage("Error. Image was not found.");
      context.setRedirect(redirectWithQuery(context));
      return context;
    }

    String altText = StringUtils.trimToNull(context.getParameter("altText"));
    if (altText != null && altText.length() > MAX_ALT_TEXT_LENGTH) {
      context.setErrorMessage("Alt text must be " + MAX_ALT_TEXT_LENGTH + " characters or fewer.");
      context.setRedirect(redirectWithQuery(context));
      return context;
    }

    image.setAltText(altText);
    boolean saved = ImageRepository.save(image) != null;
    AuditEventCommand.record(context, AuditEventCommand.CONTENT, "image.setAltText",
        saved ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
        "image", String.valueOf(image.getId()), image.getFilename(), null);
    if (!saved) {
      context.setErrorMessage("Error. The alt text could not be saved.");
      context.setRedirect(redirectWithQuery(context));
      return context;
    }

    context.setSuccessMessage("Alt text saved");
    context.setRedirect(redirectWithQuery(context));
    return context;
  }

  /**
   * Replaces one image's full tag assignment in a single transaction (delete-all-then-insert,
   * rather than diffing) -- called from the tag-assignment modal in image-browser.jsp. Existing
   * tags are chosen via checkboxes ({@code assignTagId} params -- deliberately NOT {@code tagId},
   * since the tagsReveal form has no action attribute and so POSTs to the current document URL,
   * which can still carry the page's own {@code ?tagId=} filter query param; sharing the name
   * would let that filter value silently re-enter the merged parameter array here regardless of
   * checkbox state). {@code newTagName} is an optional free-text field that finds-or-creates a tag
   * by name and includes it too, so an admin can introduce a brand new tag and assign it in the
   * same save.
   */
  private WidgetContext setTagsAction(WidgetContext context) {
    long imageId = context.getParameterAsLong("imageId", -1);
    Image image = imageId > -1 ? ImageRepository.findById(imageId) : null;
    if (image == null) {
      context.setErrorMessage("Error. Image was not found.");
      context.setRedirect(redirectWithQuery(context));
      return context;
    }

    Set<Long> tagIds = new LinkedHashSet<>();
    String[] tagIdParams = context.getParameterMap().get("assignTagId");
    if (tagIdParams != null) {
      for (String rawTagId : tagIdParams) {
        try {
          tagIds.add(Long.parseLong(rawTagId.trim()));
        } catch (NumberFormatException e) {
          // Dropped, not treated as a batch-ending error
        }
      }
    }

    String newTagName = StringUtils.trimToNull(context.getParameter("newTagName"));
    if (newTagName != null) {
      try {
        ImageTag newTag = SaveImageTagCommand.saveImageTag(newTagName, context.getUserId());
        tagIds.add(newTag.getId());
      } catch (DataException e) {
        context.setErrorMessage(e.getMessage());
        context.setRedirect(redirectWithQuery(context));
        return context;
      }
    }

    boolean saved = false;
    try (Connection connection = DB.getConnection();
        AutoStartTransaction a = new AutoStartTransaction(connection);
        AutoRollback transaction = new AutoRollback(connection)) {
      ImageTagMapRepository.removeAll(connection, image);
      for (Long tagId : tagIds) {
        ImageTagMapRepository.insertImageTagId(connection, image, tagId);
      }
      transaction.commit();
      saved = true;
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }

    AuditEventCommand.record(context, AuditEventCommand.CONTENT, "image.setTags",
        saved ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
        "image", String.valueOf(image.getId()), image.getFilename(), null);
    if (saved) {
      context.setSuccessMessage("Tags saved");
    } else {
      context.setErrorMessage("Error. The tags could not be saved.");
    }
    context.setRedirect(redirectWithQuery(context));
    return context;
  }

  /**
   * Deletes a tag globally -- from the "Manage Tags" panel in image-browser.jsp, unassigning it
   * from every image that carries it. Admin-only, unlike every other action on this page (mirrors
   * WebPageListWidget's stricter admin-only gate on its own most destructive, blast-radius-widest
   * action) since this removes a tag from potentially many images at once, not just one.
   */
  private WidgetContext deleteTagAction(WidgetContext context) {
    if (!context.hasRole("admin")) {
      LOG.warn("No permission to delete an image tag");
      return context;
    }

    long imageTagId = context.getParameterAsLong("imageTagId", -1);
    ImageTag imageTag = imageTagId > -1 ? ImageTagRepository.findById(imageTagId) : null;
    if (imageTag == null) {
      context.setErrorMessage("Error. The tag was not found.");
      context.setRedirect(redirectWithQuery(context));
      return context;
    }

    boolean removed;
    try {
      removed = DeleteImageTagCommand.deleteImageTag(imageTag);
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
      context.setRedirect(redirectWithQuery(context));
      return context;
    }

    AuditEventCommand.record(context, AuditEventCommand.CONTENT, "imageTag.delete",
        removed ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
        "imageTag", String.valueOf(imageTag.getId()), imageTag.getName(), null);
    if (removed) {
      context.setSuccessMessage("Tag deleted");
    } else {
      context.setErrorMessage("Error. The tag could not be deleted.");
    }
    context.setRedirect(redirectWithQuery(context));
    return context;
  }

  /**
   * Parses a 0-100 focal-point percentage, rejecting anything non-numeric or out of range rather
   * than trusting the client-side picker's own clamping.
   */
  private static BigDecimal parsePercent(String raw) {
    String trimmed = StringUtils.trimToNull(raw);
    if (trimmed == null) {
      return null;
    }
    try {
      BigDecimal value = new BigDecimal(trimmed);
      if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) > 0) {
        return null;
      }
      return value;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Deletes one image and records the outcome, used by both the single-delete and bulk-delete paths.
   */
  private boolean deleteOne(WidgetContext context, Image image) {
    String targetId = String.valueOf(image.getId());
    String targetLabel = image.getFilename();
    try {
      boolean removed = DeleteImageCommand.deleteImage(image);
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "image.delete",
          removed ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
          "image", targetId, targetLabel, null);
      return removed;
    } catch (Exception e) {
      LOG.error("Error deleting image " + targetId, e);
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "image.delete",
          AuditEventCommand.FAILURE, "image", targetId, targetLabel, e.getMessage());
      return false;
    }
  }

  /**
   * Parses and dedupes the selected image ids from the repeated {@code imageId} checkbox inputs,
   * silently dropping any non-numeric entry. Returns {@code null} when the selection exceeds
   * {@link #MAX_BULK_SELECTION} -- see that field's comment for why this rejects rather than truncates.
   */
  private List<Long> resolveSelectedImageIds(WidgetContext context) {
    String[] rawIds = context.getParameterMap().get("imageId");
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
      LOG.warn("Bulk image delete rejected: " + ids.size() + " ids exceeds MAX_BULK_SELECTION (" + MAX_BULK_SELECTION + ")");
      return null;
    }
    return new ArrayList<>(ids);
  }

  /**
   * Builds the post-delete redirect back to the image grid, preserving the search term, sort, and
   * the page the admin was on (issue #498 slice 2) -- otherwise every delete bounces back to page 1
   * of the (optionally search-filtered/sorted) grid instead of the page the admin was triaging.
   */
  private String redirectWithQuery(WidgetContext context) {
    String query = StringUtils.trimToNull(context.getParameter("query"));
    String sortBy = context.getParameter(RequestConstants.RECORD_SORT_BY, "date");
    int page = context.getParameterAsInt("page", 1);
    // Any action taken from the duplicates view (delete, scan, focal point, tags -- its cards
    // share the same buttons as the main grid) POSTs to the current document URL, which still
    // carries ?view=duplicates as an ordinary request param -- preserve it so the redirect lands
    // back on the duplicates view instead of resetting to the main grid.
    String view = context.getParameter("view");

    StringBuilder redirect = new StringBuilder("/admin/images");
    String separator = "?";
    if (query != null) {
      redirect.append(separator).append("query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
      separator = "&";
    }
    if (!"date".equals(sortBy)) {
      redirect.append(separator).append("sortBy=").append(sortBy);
      separator = "&";
    }
    if (page > 1) {
      redirect.append(separator).append("page=").append(page);
      separator = "&";
    }
    if ("duplicates".equals(view)) {
      redirect.append(separator).append("view=duplicates");
    }
    return redirect.toString();
  }
}
