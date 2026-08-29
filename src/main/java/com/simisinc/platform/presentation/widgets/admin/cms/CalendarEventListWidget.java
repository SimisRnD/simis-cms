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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.domain.events.cms.CalendarEventRemovedEvent;
import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
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

  // A crafted POST is the only thing this bounds -- normal usage never approaches it, since
  // selection is scoped to the current page (default page size 25). An id list over this cap is
  // rejected outright, never silently truncated. Mirrors UsersListWidget/AdminImageBrowserWidget's
  // MAX_BULK_SELECTION.
  static final int MAX_BULK_SELECTION = 100;

  public WidgetContext execute(WidgetContext context) {

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "25"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    // Same value the repository defaults to, so this was harmless -- but it is the setter
    // the repository owns, and reading it here is how the pattern spreads. Issue 1604.
    constraints.setColumnsToSortBy(new String[] { "start_date" });
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

  /**
   * Bulk actions selected from /admin/calendars' checkbox + action-bar UI (issue #882, mirroring
   * the bulk-action-bar mechanics PR #731 shipped for /admin/users' users-list.jsp).
   */
  public WidgetContext post(WidgetContext context) {

    // Permission is required -- matches CalendarWidget.post()/delete() and AdminImageBrowserWidget,
    // the codebase's existing admin/content-manager pairing for calendar and CMS-asset mutations.
    if (!(context.hasRole("admin") || context.hasRole("content-manager"))) {
      LOG.warn("No permission to modify calendar events");
      return context;
    }

    // Don't accept multiple form posts
    context.getUserSession().renewFormToken();

    String command = context.getParameter("command");
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

  private WidgetContext bulkArchiveAction(WidgetContext context) {
    List<Long> eventIds = resolveSelectedEventIds(context);
    if (eventIds == null) {
      return rejectBulkSelection(context);
    }
    if (eventIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    Timestamp now = new Timestamp(System.currentTimeMillis());
    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    for (Long eventId : eventIds) {
      CalendarEvent calendarEvent = CalendarEventRepository.findById(eventId);
      if (calendarEvent == null) {
        ++notFound;
        continue;
      }
      calendarEvent.setArchived(now);
      calendarEvent.setModifiedBy(context.getUserId());
      CalendarEvent result = CalendarEventRepository.update(calendarEvent);
      String outcome = result != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (result != null) {
        ++succeeded;
      } else {
        ++failed;
      }
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "calendarEvent.archive", outcome,
          "calendarEvent", String.valueOf(calendarEvent.getId()), calendarEvent.getTitle(), "(bulk)");
    }

    setBulkResultMessage(context, "archived", succeeded, eventIds.size(), notFound, failed);
    context.setRedirect("/admin/calendars");
    return context;
  }

  private WidgetContext bulkMoveAction(WidgetContext context) {
    long targetCalendarId = context.getParameterAsLong("calendarId", -1);
    Calendar targetCalendar = targetCalendarId > -1 ? CalendarRepository.findById(targetCalendarId) : null;
    if (targetCalendar == null) {
      context.setErrorMessage("The destination calendar was not found");
      context.setRedirect("/admin/calendars");
      return context;
    }

    List<Long> eventIds = resolveSelectedEventIds(context);
    if (eventIds == null) {
      return rejectBulkSelection(context);
    }
    if (eventIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    for (Long eventId : eventIds) {
      CalendarEvent calendarEvent = CalendarEventRepository.findById(eventId);
      if (calendarEvent == null) {
        ++notFound;
        continue;
      }
      calendarEvent.setCalendarId(targetCalendar.getId());
      calendarEvent.setModifiedBy(context.getUserId());
      CalendarEvent result = CalendarEventRepository.update(calendarEvent);
      String outcome = result != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (result != null) {
        ++succeeded;
      } else {
        ++failed;
      }
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "calendarEvent.move", outcome,
          "calendarEvent", String.valueOf(calendarEvent.getId()), calendarEvent.getTitle(),
          "movedTo=" + targetCalendar.getName() + " (bulk)");
    }

    setBulkResultMessage(context, "moved to " + targetCalendar.getName(), succeeded, eventIds.size(), notFound, failed);
    context.setRedirect("/admin/calendars");
    return context;
  }

  private WidgetContext bulkDeleteAction(WidgetContext context) {
    List<Long> eventIds = resolveSelectedEventIds(context);
    if (eventIds == null) {
      return rejectBulkSelection(context);
    }
    if (eventIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    for (Long eventId : eventIds) {
      CalendarEvent calendarEvent = CalendarEventRepository.findById(eventId);
      if (calendarEvent == null) {
        ++notFound;
        continue;
      }
      boolean removed = CalendarEventRepository.remove(calendarEvent);
      String outcome = removed ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (removed) {
        ++succeeded;
        // Matches CalendarWidget.delete()'s single-event path
        WorkflowManager.triggerWorkflowForEvent(new CalendarEventRemovedEvent(calendarEvent));
      } else {
        ++failed;
      }
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "calendarEvent.delete", outcome,
          "calendarEvent", String.valueOf(calendarEvent.getId()), calendarEvent.getTitle(), "(bulk)");
    }

    setBulkResultMessage(context, "deleted", succeeded, eventIds.size(), notFound, failed);
    context.setRedirect("/admin/calendars");
    return context;
  }

  /**
   * Parses and dedupes the selected event ids from the repeated {@code eventId} hidden inputs the
   * bulk modals inject, silently dropping any non-numeric entry (a tampered value is not a
   * batch-ending error). Returns {@code null} when the list exceeds {@link #MAX_BULK_SELECTION} --
   * the whole request is then rejected rather than silently truncated, since truncation could apply
   * the action to a different subset of events than the one the admin reviewed and confirmed.
   * Mirrors UsersListWidget#resolveSelectedUserIds / AdminImageBrowserWidget#resolveSelectedImageIds.
   */
  private List<Long> resolveSelectedEventIds(WidgetContext context) {
    String[] rawIds = context.getParameterMap().get("eventId");
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
      LOG.warn("Bulk calendar event action rejected: " + ids.size() + " ids exceeds MAX_BULK_SELECTION ("
          + MAX_BULK_SELECTION + ")");
      return null;
    }
    return new ArrayList<>(ids);
  }

  private WidgetContext rejectBulkSelection(WidgetContext context) {
    context.setErrorMessage("Too many events were selected (maximum " + MAX_BULK_SELECTION
        + "). Select fewer events and try again.");
    context.setRedirect("/admin/calendars");
    return context;
  }

  private WidgetContext rejectEmptySelection(WidgetContext context) {
    context.setErrorMessage("No events were selected");
    context.setRedirect("/admin/calendars");
    return context;
  }

  /**
   * Sets the single aggregate result message every bulk action reports (page_messages.jspf renders
   * exactly one of success/warning/error). Mirrors UsersListWidget#setBulkResultMessage.
   */
  private void setBulkResultMessage(WidgetContext context, String verb, int succeeded, int totalSelected,
      int notFound, int failed) {
    StringBuilder sb = new StringBuilder();
    sb.append(succeeded).append(" of ").append(totalSelected).append(" selected event")
        .append(totalSelected == 1 ? "" : "s").append(" ").append(verb).append(".");
    if (notFound > 0) {
      sb.append(" Not found: ").append(notFound).append(".");
    }
    if (failed > 0) {
      sb.append(" Failed: ").append(failed).append(".");
    }
    if (succeeded == 0) {
      context.setErrorMessage(sb.toString());
    } else if (succeeded != totalSelected) {
      context.setWarningMessage(sb.toString());
    } else {
      context.setSuccessMessage(sb.toString());
    }
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
    // issue #882: archived events are excluded from this list by default -- "Archived" is its own
    // status option (rather than combined with published/draft) since an event's archived state is
    // orthogonal to whether it was ever published.
    if ("archived".equals(status)) {
      specification.setArchivedOnly(true);
    } else {
      specification.setArchivedOnly(false);
      if ("published".equals(status)) {
        specification.setPublishedOnly(true);
      } else if ("draft".equals(status)) {
        specification.setPublishedOnly(false);
      }
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
