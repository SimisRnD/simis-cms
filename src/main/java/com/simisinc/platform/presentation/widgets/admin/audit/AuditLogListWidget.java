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

package com.simisinc.platform.presentation.widgets.admin.audit;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.domain.model.audit.AuditLog;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogRepository;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogSpecification;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * The in-app security audit review UI (NIST 800-53 AU-6). Lists audit_log records with filters for
 * category, event type, outcome, actor, and an occurred-date range. Read-only and admin-only.
 *
 * @author SimIS Inc.
 */
public class AuditLogListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/audit-log-list.jsp";

  // The event categories the application emits (for the filter drop-down)
  static final List<String> CATEGORY_LIST = Arrays.asList(
      "authentication", "user_management", "authorization", "configuration", "content", "data_access");

  public WidgetContext execute(WidgetContext context) {

    // The audit log is sensitive; require admin even if the page were mis-configured (defense in depth)
    if (!context.hasRole("admin")) {
      return context;
    }

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "50"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    constraints.setColumnToSortBy("occurred", "desc");
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Determine the filter criteria
    String category = context.getParameter("category");
    String eventType = context.getParameter("eventType");
    String outcome = context.getParameter("outcome");
    String actor = context.getParameter("actor");
    String fromDate = context.getParameter("fromDate");
    String toDate = context.getParameter("toDate");

    AuditLogSpecification specification = new AuditLogSpecification();
    if (StringUtils.isNotBlank(category)) {
      specification.setEventCategory(category);
    }
    if (StringUtils.isNotBlank(eventType)) {
      specification.setEventType(eventType);
    }
    if (StringUtils.isNotBlank(outcome)) {
      specification.setOutcome(outcome);
    }
    if (StringUtils.isNotBlank(actor)) {
      specification.setActorUsername(actor.trim());
    }
    // Parse the yyyy-MM-dd date range: from = start of that day, to = start of the day AFTER (half-open)
    Timestamp from = parseDate(fromDate, 0);
    Timestamp to = parseDate(toDate, 1);
    if (from != null) {
      specification.setOccurredAfter(from);
    }
    if (to != null) {
      specification.setOccurredBefore(to);
    }

    // Load the list
    List<AuditLog> auditLogList = AuditLogRepository.findAll(specification, constraints);
    context.getRequest().setAttribute("auditLogList", auditLogList);

    // Echo the filter values back so the form keeps its state, plus the category options
    context.getRequest().setAttribute("categoryList", CATEGORY_LIST);
    context.getRequest().setAttribute("category", category);
    context.getRequest().setAttribute("eventType", eventType);
    context.getRequest().setAttribute("outcome", outcome);
    context.getRequest().setAttribute("actor", actor);
    context.getRequest().setAttribute("fromDate", fromDate);
    context.getRequest().setAttribute("toDate", toDate);

    // Carry the filters through pagination (paging_control.jspf appends this to each page link).
    // URL-encoded here so the free-text values (actor, eventType) cannot break the query string or the href.
    StringBuilder pagingParams = new StringBuilder();
    appendParam(pagingParams, "category", category);
    appendParam(pagingParams, "eventType", eventType);
    appendParam(pagingParams, "outcome", outcome);
    appendParam(pagingParams, "actor", actor);
    appendParam(pagingParams, "fromDate", fromDate);
    appendParam(pagingParams, "toDate", toDate);
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
