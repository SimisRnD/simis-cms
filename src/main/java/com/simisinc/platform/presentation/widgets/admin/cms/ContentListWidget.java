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
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.cms.ContentHtmlCommand;
import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.application.cms.ContentUsageCommand;
import com.simisinc.platform.application.cms.EditorPermissionCommand;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ContentSpecification;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * The /admin/content-list admin page (issue #499): search (unique id substring OR body full-text,
 * combined into one box), last-modified date range and character-count range filters, usage
 * detection (which page(s) reference each content block, "Orphaned" when none do), and the governed
 * publish workflow's state (Live/Draft/Pending Review/Approved -- see {@link ContentReviewCommand})
 * per row, filterable server-side. This is read-only surfacing of that workflow's existing state,
 * not a new place to submit/approve/reject -- those actions stay in the individual content editor.
 *
 * @author matt rajkowski
 * @created 4/20/18 10:04 AM
 */
public class ContentListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/content-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "25"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    constraints.setDefaultColumnToSortBy("content_unique_id");
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    ContentSpecification specification = buildSpecification(context);

    // Load the list
    List<Content> contentList = ContentRepository.findAll(specification, constraints);
    context.getRequest().setAttribute("contentList", contentList);

    // For each content block, which page(s) (or filesystem templates) reference it -- drives the
    // "Used on: ..." display and the "Orphaned" badge (a block with no entry here is unreferenced). A
    // single scan produces both the real usage map and the templated-prefix locations below (see
    // ContentUsageCommand#scanUsage), so this page pays for the filesystem walk once, not twice.
    ContentUsageCommand.UsageScan usageScan = ContentUsageCommand.scanUsage(context.getRequest().getServletContext());
    Map<String, List<String>> contentUsageMap = usageScan.usageMap();
    context.getRequest().setAttribute("contentUsageMap", contentUsageMap);

    // Which uniqueIds are "Shared" -- more than one usage location, or even a single site-wide
    // filesystem-template location (see ContentUsageCommand#isShared) -- so the JSP's Shared badge
    // isn't undercounted for a block like site-footer (exactly one entry in contentUsageMap, but
    // actually rendered on every page via footer-layout.xml).
    Set<String> sharedUniqueIds = new LinkedHashSet<>();
    for (Map.Entry<String, List<String>> entry : contentUsageMap.entrySet()) {
      if (ContentUsageCommand.isShared(entry.getValue())) {
        sharedUniqueIds.add(entry.getKey());
      }
    }
    context.getRequest().setAttribute("sharedUniqueIds", sharedUniqueIds);

    // For a content block with no real usage entry above, whether its uniqueId matches a "templated"
    // per-item pattern instead (e.g. product-details-${item.uniqueId} in products-layout.xml) -- if
    // so it is genuinely wired to a live template, just not statically provable, so the JSP shows a
    // "Templated" badge instead of "Orphaned" (issue #499 follow-up).
    Map<String, List<String>> templatedContentLocations = new LinkedHashMap<>();
    if (contentList != null) {
      for (Content content : contentList) {
        String uniqueId = content.getUniqueId();
        if (uniqueId == null || contentUsageMap.containsKey(uniqueId)) {
          continue;
        }
        for (Map.Entry<String, List<String>> prefixEntry : usageScan.templatedPrefixLocations().entrySet()) {
          if (uniqueId.startsWith(prefixEntry.getKey())) {
            templatedContentLocations.put(uniqueId, prefixEntry.getValue());
            break;
          }
        }
      }
    }
    context.getRequest().setAttribute("templatedContentLocations", templatedContentLocations);

    // For each content block, its governed-publish-workflow status label -- drives the Status
    // column. The derivation lives in one place (ContentReviewCommand.listStatusLabel) so the JSP
    // never re-derives it, matching how contentUsageMap above is computed here and just displayed there.
    Map<String, String> contentStatusMap = new LinkedHashMap<>();
    if (contentList != null) {
      for (Content content : contentList) {
        contentStatusMap.put(content.getUniqueId(), ContentReviewCommand.listStatusLabel(content));
      }
    }
    context.getRequest().setAttribute("contentStatusMap", contentStatusMap);

    // Echo the filter values back so the form keeps its state
    echoFilterParameters(context);

    // Carry the filters through pagination (paging_control.jspf appends this to each page link).
    // URL-encoded here so the free-text search term cannot break the query string or the href.
    StringBuilder pagingParams = new StringBuilder();
    appendParam(pagingParams, "q", context.getParameter("q"));
    appendParam(pagingParams, "fromDate", context.getParameter("fromDate"));
    appendParam(pagingParams, "toDate", context.getParameter("toDate"));
    appendParam(pagingParams, "minLength", context.getParameter("minLength"));
    appendParam(pagingParams, "maxLength", context.getParameter("maxLength"));
    appendParam(pagingParams, "status", context.getParameter("status"));
    context.getRequest().setAttribute("recordPagingParams", pagingParams.toString());

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  /**
   * Deletes a content block from this list page (issue #499 follow-up: {@link ContentHtmlCommand
   * #deleteContent} was already fully implemented and audited, but nothing in the UI ever called it).
   * Reached via {@code command=delete} (see {@code content-list.jsp}'s confirm link), which the
   * controller routes to this method rather than {@link #execute} or {@link #post} -- see
   * WebContainerContext#isDelete().
   *
   * <p>Uses the same permission tier as every other content-mutating action ({@link
   * ContentHtmlCommand#performWebAction}) -- editor tier or above, not merely the page's own
   * "admin"/"content-manager" role gate (see {@link EditorPermissionCommand}) -- so this cannot drift
   * from the rest of the content system's permission model.
   */
  public WidgetContext delete(WidgetContext context) {
    if (!EditorPermissionCommand.canEditContent(context.getUserSession())) {
      context.setWarningMessage("You do not have permission to delete content");
      context.setRedirect(context.getUri());
      return context;
    }
    String uniqueId = context.getParameter("uniqueId");
    Content content = ContentRepository.findByUniqueId(uniqueId);
    if (content == null) {
      context.setWarningMessage("That content was not found; it may have already been deleted");
      context.setRedirect(context.getUri());
      return context;
    }
    // The actual delete + permission-scoped audit record is ContentHtmlCommand#deleteContent's job
    // already -- reused here rather than duplicated, since it was already fully implemented and
    // audited, just unreachable from any UI.
    context = ContentHtmlCommand.deleteContent(context, content);
    context.setRedirect(context.getUri());
    return context;
  }

  /** Builds the filter specification from request parameters. */
  private ContentSpecification buildSpecification(WidgetContext context) {
    String q = context.getParameter("q");
    String fromDate = context.getParameter("fromDate");
    String toDate = context.getParameter("toDate");
    int minLength = context.getParameterAsInt("minLength", -1);
    int maxLength = context.getParameterAsInt("maxLength", -1);
    String status = context.getParameter("status");

    ContentSpecification specification = new ContentSpecification();
    if (StringUtils.isNotBlank(q)) {
      specification.setSearchTerm(q.trim());
    }
    if (StringUtils.isNotBlank(status)) {
      specification.setStatus(status.trim());
    }

    // Parse the yyyy-MM-dd date range: from = start of that day, to = start of the day AFTER (half-open)
    Timestamp from = parseDate(fromDate, 0);
    Timestamp to = parseDate(toDate, 1);
    if (from != null) {
      specification.setDateModifiedAfter(from);
    }
    if (to != null) {
      specification.setDateModifiedBefore(to);
    }

    if (minLength >= 0) {
      specification.setMinLength(minLength);
    }
    if (maxLength >= 0) {
      specification.setMaxLength(maxLength);
    }
    return specification;
  }

  /** Echoes the raw filter parameters back to the request so the filter form keeps its state. */
  private void echoFilterParameters(WidgetContext context) {
    context.getRequest().setAttribute("q", context.getParameter("q"));
    context.getRequest().setAttribute("fromDate", context.getParameter("fromDate"));
    context.getRequest().setAttribute("toDate", context.getParameter("toDate"));
    context.getRequest().setAttribute("minLength", context.getParameter("minLength"));
    context.getRequest().setAttribute("maxLength", context.getParameter("maxLength"));
    context.getRequest().setAttribute("status", context.getParameter("status"));
  }

  /** Appends {@code name=urlEncoded(value)} to the paging query string when the value is present. */
  private void appendParam(StringBuilder sb, String name, String value) {
    if (StringUtils.isBlank(value)) {
      return;
    }
    if (sb.length() > 0) {
      sb.append("&");
    }
    sb.append(name).append("=").append(URLEncoder.encode(value, StandardCharsets.UTF_8));
  }

  /** Parses a yyyy-MM-dd string to a start-of-day Timestamp plus {@code plusDays}; null when blank/invalid. */
  private Timestamp parseDate(String value, int plusDays) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    try {
      LocalDate date = LocalDate.parse(value.trim()).plusDays(plusDays);
      return Timestamp.valueOf(date.atStartOfDay());
    } catch (Exception e) {
      return null;
    }
  }

}
