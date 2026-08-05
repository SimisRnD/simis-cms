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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.cms.DeleteImageCommand;
import com.simisinc.platform.application.cms.ImageUsageCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.domain.model.cms.ImageVariant;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ImageSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.ImageVariantRepository;
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

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine the search
    String query = StringUtils.trimToNull(context.getParameter("query"));
    context.getRequest().setAttribute("query", query);

    // Determine the record paging (issue #498 slice 2) -- at most one page's worth of images is
    // loaded per request, not all 200+. Follows the same page/items request-param convention as
    // ContentListWidget/AllowedIPListWidget/etc.
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", String.valueOf(DEFAULT_PAGE_SIZE)));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Carry the current search term through pagination links (paging_control.jspf appends this
    // to each page link's query string) so paging forward/back doesn't lose the search. URL-encoded
    // so the free-text search term cannot break the query string or the href.
    if (query != null) {
      context.getRequest().setAttribute("recordPagingParams", "query=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
    }

    List<Image> imageList;
    if (query != null) {
      ImageSpecification specification = new ImageSpecification();
      specification.setMatchesName(query);
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
    }
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
   * Builds the post-delete redirect back to the image grid, preserving both the search term and
   * the page the admin was on (issue #498 slice 2) -- otherwise every delete bounces back to page 1
   * of the (optionally search-filtered) grid instead of the page the admin was triaging.
   */
  private String redirectWithQuery(WidgetContext context) {
    String query = StringUtils.trimToNull(context.getParameter("query"));
    int page = context.getParameterAsInt("page", 1);

    StringBuilder redirect = new StringBuilder("/admin/images");
    String separator = "?";
    if (query != null) {
      redirect.append(separator).append("query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
      separator = "&";
    }
    if (page > 1) {
      redirect.append(separator).append("page=").append(page);
    }
    return redirect.toString();
  }
}
