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
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarRepository;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * The /admin/calendars admin page (issue #501): a searchable, filterable, paginated table of
 * events across all calendars. This is UI wiring only -- CalendarEventRepository/
 * CalendarEventSpecification already support calendarId, publishedOnly, a date range, and
 * full-text search (the same query layer the public CalendarSearchResultsWidget uses), so no new
 * domain/DB fields or repository methods are needed.
 *
 * @author elizabeth houser
 */
public class CalendarEventListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908894L;

  static String JSP = "/admin/calendar-event-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "25"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    constraints.setDefaultColumnToSortBy("start_date");
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    CalendarEventSpecification specification = buildSpecification(context);

    // Load the list
    List<CalendarEvent> calendarEventList = CalendarEventRepository.findAll(specification, constraints);
    context.getRequest().setAttribute("calendarEventList", calendarEventList);

    // The Calendar filter dropdown
    List<Calendar> calendarList = CalendarRepository.findAll();
    context.getRequest().setAttribute("calendarList", calendarList);

    // Echo the filter values back so the form keeps its state
    echoFilterParameters(context);

    // Carry the filters through pagination (paging_control.jspf appends this to each page link).
    // URL-encoded here so the free-text search term cannot break the query string or the href.
    StringBuilder pagingParams = new StringBuilder();
    appendParam(pagingParams, "q", context.getParameter("q"));
    appendParam(pagingParams, "calendarId", context.getParameter("calendarId"));
    appendParam(pagingParams, "status", context.getParameter("status"));
    appendParam(pagingParams, "fromDate", context.getParameter("fromDate"));
    appendParam(pagingParams, "toDate", context.getParameter("toDate"));
    context.getRequest().setAttribute("recordPagingParams", pagingParams.toString());

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  /** Builds the filter specification from request parameters. */
  private CalendarEventSpecification buildSpecification(WidgetContext context) {
    String q = context.getParameter("q");
    long calendarId = context.getParameterAsLong("calendarId", -1);
    String status = context.getParameter("status");
    String fromDate = context.getParameter("fromDate");
    String toDate = context.getParameter("toDate");

    CalendarEventSpecification specification = new CalendarEventSpecification();
    if (StringUtils.isNotBlank(q)) {
      specification.setSearchTerm(q.trim());
    }
    if (calendarId > -1) {
      specification.setCalendarId(calendarId);
    }
    if ("published".equals(status)) {
      specification.setPublishedOnly(true);
    } else if ("draft".equals(status)) {
      specification.setPublishedOnly(false);
    }

    // Parse the yyyy-MM-dd date range: from = start of that day, to = start of the day AFTER (half-open)
    Timestamp from = parseDate(fromDate, 0);
    Timestamp to = parseDate(toDate, 1);
    if (from != null) {
      specification.setStartingDateRange(from);
    }
    if (to != null) {
      specification.setEndingDateRange(to);
    }
    return specification;
  }

  /** Echoes the raw filter parameters back to the request so the filter form keeps its state. */
  private void echoFilterParameters(WidgetContext context) {
    context.getRequest().setAttribute("q", context.getParameter("q"));
    context.getRequest().setAttribute("calendarId", context.getParameter("calendarId"));
    context.getRequest().setAttribute("status", context.getParameter("status"));
    context.getRequest().setAttribute("fromDate", context.getParameter("fromDate"));
    context.getRequest().setAttribute("toDate", context.getParameter("toDate"));
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
