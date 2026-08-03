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

package com.simisinc.platform.presentation.widgets.calendar;

import com.simisinc.platform.application.FacetUrlCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.SearchAnalyticsCommand;
import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarRepository;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/28/19 11:31 AM
 */
public class CalendarSearchResultsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/calendar/calendar-search-results.jsp";
  static String CALENDAR_FACET_LABEL = "Calendar";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "3"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Determine the search term
    String query = context.getParameter("query");
    if (StringUtils.isBlank(query)) {
      return null;
    }

    // Determine the query date range (from today on... using the site's timezone start of day)
    ZoneId clientZoneId = ZoneId.of(LoadSitePropertyCommand.loadByName("site.timezone"));
    Instant instant = Instant.now();
    ZonedDateTime zdt = ZonedDateTime.ofInstant(instant, clientZoneId);
    ZonedDateTime zdtStart = zdt.toLocalDate().atStartOfDay(clientZoneId);
    Timestamp startingDateRange = Timestamp.valueOf(zdtStart.toLocalDateTime());

    // Determine the active facet filter (issue #634; calendarId is fully supported by
    // CalendarEventRepository, single-select since an event belongs to exactly one calendar)
    long selectedCalendarId = context.getParameterAsLong("calendarId", -1);

    // Search the calendar events
    CalendarEventSpecification eventSpecification = new CalendarEventSpecification();
    eventSpecification.setPublishedOnly(true);
    // issue #882: archived events are excluded from every public-facing calendar surface
    eventSpecification.setArchivedOnly(false);
    eventSpecification.setSearchTerm(query);
    eventSpecification.setStartingDateRange(startingDateRange);
    if (selectedCalendarId != -1) {
      eventSpecification.setCalendarId(selectedCalendarId);
    }
    List<CalendarEvent> calendarEventList = CalendarEventRepository.findAll(eventSpecification, constraints);
    // Not yet passing a facetKey here (issue #638's SearchAnalyticsCommand.record() overload isn't
    // on main at the time of this PR) -- a trivial follow-up once #638 merges.
    SearchAnalyticsCommand.record(context, query, "calendar", calendarEventList == null ? 0 : calendarEventList.size());

    // Facet panel: one option per calendar with a non-zero count (or the currently selected one),
    // each counted standalone (ignoring the current calendarId selection) so switching calendars
    // shows every calendar's own count, not just the selected one's (issue #634)
    List<FacetUrlCommand.FacetOption> calendarFacets = new ArrayList<>();
    List<Calendar> calendarList = CalendarRepository.findAll();
    if (calendarList != null) {
      for (Calendar calendar : calendarList) {
        boolean selected = selectedCalendarId == calendar.getId();
        CalendarEventSpecification countSpecification = new CalendarEventSpecification();
        countSpecification.setPublishedOnly(true);
        countSpecification.setArchivedOnly(false);
        countSpecification.setSearchTerm(query);
        countSpecification.setStartingDateRange(startingDateRange);
        countSpecification.setCalendarId(calendar.getId());
        long count = CalendarEventRepository.findCount(countSpecification);
        if (count > 0 || selected) {
          String url = FacetUrlCommand.buildFacetLinkUrl(context, "calendarId", String.valueOf(calendar.getId()));
          calendarFacets.add(new FacetUrlCommand.FacetOption(String.valueOf(calendar.getId()), calendar.getName(), count, selected, url));
        }
      }
    }
    context.getRequest().setAttribute("calendarFacets", calendarFacets);
    context.getRequest().setAttribute("calendarFacetLabel", CALENDAR_FACET_LABEL);

    // Active filter chip, with a URL that clears just the calendarId filter (issue #634)
    List<FacetUrlCommand.ActiveFacetFilter> activeFilters = new ArrayList<>();
    if (selectedCalendarId != -1) {
      Calendar selectedCalendar = CalendarRepository.findById(selectedCalendarId);
      String valueLabel = selectedCalendar != null ? selectedCalendar.getName() : "Selected calendar";
      activeFilters.add(new FacetUrlCommand.ActiveFacetFilter(CALENDAR_FACET_LABEL, valueLabel,
          FacetUrlCommand.buildClearFilterUrl(context, "calendarId")));
    }
    context.getRequest().setAttribute("activeFilters", activeFilters);

    // Determine if the widget is shown
    boolean showWhenEmpty = "true".equals(context.getPreferences().getOrDefault("showWhenEmpty", "true"));
    if (calendarEventList.isEmpty() && !showWhenEmpty) {
      return context;
    }

    context.getRequest().setAttribute("calendarEventList", calendarEventList);
    context.setJsp(JSP);
    return context;
  }
}
