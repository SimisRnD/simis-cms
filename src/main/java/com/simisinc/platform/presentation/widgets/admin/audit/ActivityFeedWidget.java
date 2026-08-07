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

package com.simisinc.platform.presentation.widgets.admin.audit;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.audit.DescribeAuditEventCommand;
import com.simisinc.platform.domain.model.audit.AuditLog;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogRepository;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * The high-level "what's been happening lately" activity feed at {@code /admin/activity} (issue #1006),
 * replacing the raw xAPI/Tin-Can statement dump that used to render at that URL (relocated to
 * {@code /admin/elearning-statements}). A simpler sibling of {@link AuditLogListWidget}: same underlying
 * {@code audit_log} table, same 6-category taxonomy ({@link AuditLogListWidget#CATEGORY_LIST}), but a
 * multi-category checkbox filter instead of a single-value dropdown, a default trailing 7-day window
 * instead of "everything", and plain-language {@code eventType} descriptions
 * ({@link com.simisinc.platform.application.audit.DescribeAuditEventCommand}) instead of raw event codes.
 *
 * <p>Deliberately renders identically for every role the page is reachable by -- the issue is explicit that
 * filtering is per-viewer (the category checkboxes), not baked into access control, so this widget has no
 * per-role branching. It also has no admin-only defense-in-depth gate the way AuditLogListWidget does:
 * unlike that page (admin-only even though a broader role list could theoretically reach it if
 * misconfigured), this page's whole purpose is to be shared by admin/content-manager/community-manager/
 * ecommerce-manager, so narrowing it here would just fight the page's own design. The role check below only
 * mirrors the page's own {@code role="admin,content-manager,community-manager,ecommerce-manager"} gate in
 * admin-layout.xml (defense in depth against that page-level gate ever being loosened or misconfigured),
 * not a narrower one.
 *
 * @author SimIS Inc.
 */
public class ActivityFeedWidget extends GenericWidget {

  static final long serialVersionUID = 1L;

  static String JSP = "/admin/activity-feed.jsp";

  static final int DEFAULT_WINDOW_DAYS = AuditLogRepository.DEFAULT_TRAILING_WINDOW_DAYS;

  public WidgetContext execute(WidgetContext context) {

    if (!context.hasRole("admin") && !context.hasRole("content-manager")
        && !context.hasRole("community-manager") && !context.hasRole("ecommerce-manager")) {
      return context;
    }

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "50"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    constraints.setColumnToSortBy("occurred", "desc");
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Category filter: checkboxes/chips, multi-value (unlike AuditLogListWidget's single-select dropdown).
    // No categories checked means "show every category", the same convention every other optional
    // AuditLogSpecification field already uses -- not "show nothing".
    String[] selectedCategories = context.getParameterMap().get("category");
    Set<String> categories = (selectedCategories != null && selectedCategories.length > 0)
        ? new LinkedHashSet<>(Arrays.asList(selectedCategories))
        : null;

    // Trailing window: default 7 days, widened via a day-range selector (mirroring AuditLogListWidget's
    // own quick-range preset buttons, adapted to day granularity since a week is this feed's baseline
    // rather than an hour).
    String range = context.getParameter("range");
    int windowDays = resolveWindowDays(range);
    Timestamp since = Timestamp.from(Instant.now().minus(windowDays, ChronoUnit.DAYS));

    List<AuditLog> auditLogList = AuditLogRepository.findRecentActivity(categories, since, null, constraints);
    List<ActivityEntry> activityList = new ArrayList<>();
    if (auditLogList != null) {
      for (AuditLog record : auditLogList) {
        activityList.add(new ActivityEntry(record));
      }
    }
    context.getRequest().setAttribute("activityList", activityList);

    // Category options for the filter chips, plus which ones are checked, plus the active window
    context.getRequest().setAttribute("categoryList", AuditLogListWidget.CATEGORY_LIST);
    context.getRequest().setAttribute("selectedCategories", categories != null ? categories : Collections.emptySet());
    context.getRequest().setAttribute("windowDays", windowDays);
    context.getRequest().setAttribute("range", StringUtils.isNotBlank(range) ? range : (DEFAULT_WINDOW_DAYS + "d"));

    // Carry the filters through pagination (paging_control.jspf appends this to each page link), URL-encoded
    // so a category value can never break the query string or the href -- same convention as
    // AuditLogListWidget#appendParam.
    StringBuilder pagingParams = new StringBuilder();
    if (categories != null) {
      for (String category : categories) {
        appendParam(pagingParams, "category", category);
      }
    }
    appendParam(pagingParams, "range", range);
    context.getRequest().setAttribute("recordPagingParams", pagingParams.toString());

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    context.setJsp(JSP);
    return context;
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

  /**
   * Resolves the day-range selector (7d/14d/30d/90d) to a window size in days; blank or unrecognized falls
   * back to the default trailing window (7 days).
   */
  static int resolveWindowDays(String range) {
    if (StringUtils.isBlank(range)) {
      return DEFAULT_WINDOW_DAYS;
    }
    switch (range.trim()) {
      case "7d":
        return 7;
      case "14d":
        return 14;
      case "30d":
        return 30;
      case "90d":
        return 90;
      default:
        return DEFAULT_WINDOW_DAYS;
    }
  }

  /**
   * A presentation-only view of one {@link AuditLog} row for activity-feed.jsp: adds the plain-language
   * {@code description} (see {@link DescribeAuditEventCommand}) without adding a presentation-layer field
   * to the shared {@code AuditLog} domain model itself. JSP EL's BeanELResolver only reaches these through
   * JavaBean getters, not public fields directly -- see WebVitalsWidget.VitalsSummary for the same pattern
   * and the same reason every getter below exists.
   */
  public static class ActivityEntry {
    private final Timestamp occurred;
    private final String eventCategory;
    private final String eventType;
    private final String description;
    private final String outcome;
    private final String actorUsername;
    private final String targetType;
    private final String targetLabel;

    ActivityEntry(AuditLog record) {
      this.occurred = record.getOccurred();
      this.eventCategory = record.getEventCategory();
      this.eventType = record.getEventType();
      this.description = DescribeAuditEventCommand.describe(record.getEventType());
      this.outcome = record.getOutcome();
      this.actorUsername = record.getActorUsername();
      this.targetType = record.getTargetType();
      this.targetLabel = record.getTargetLabel();
    }

    public Timestamp getOccurred() {
      return occurred;
    }

    public String getEventCategory() {
      return eventCategory;
    }

    public String getEventType() {
      return eventType;
    }

    public String getDescription() {
      return description;
    }

    public String getOutcome() {
      return outcome;
    }

    public String getActorUsername() {
      return actorUsername;
    }

    public String getTargetType() {
      return targetType;
    }

    public String getTargetLabel() {
      return targetLabel;
    }
  }
}
