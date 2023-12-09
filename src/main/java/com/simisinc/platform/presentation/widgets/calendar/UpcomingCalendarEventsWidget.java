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

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
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
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * A widget for displaying a list of upcoming events
 *
 * @author matt rajkowski
 * @created 2/12/19 5:35 PM
 */
public class UpcomingCalendarEventsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/calendar/upcoming-events.jsp";
  static String OVERVIEW_JSP = "/calendar/upcoming-events-overview.jsp";
  static String CARDS_JSP = "/calendar/upcoming-events-cards.jsp";

  private static void insertPastEvent(List<CalendarEvent> calendarEventList, CalendarEventSpecification calSpec) {
    // Find the last event
    CalendarEventSpecification eventSpecification = new CalendarEventSpecification();
    eventSpecification.setCalendarId(calSpec.getCalendarId());
    eventSpecification.setEndingDateRange(calSpec.getStartingDateRange());
    DataConstraints constraints = new DataConstraints(1, 1);
    constraints.setColumnToSortBy("start_date", "desc");
    List<CalendarEvent> lastCalendarEventList = CalendarEventRepository.findAll(eventSpecification, constraints);
    if (!lastCalendarEventList.isEmpty()) {
      calendarEventList.add(0, lastCalendarEventList.get(0));
    } else {
      CalendarEvent today = new CalendarEvent();
      today.setTitle("No events were found");
//      today.setSummary("No events were found");
      today.setAllDay(true);
      today.setStartDate(eventSpecification.getStartingDateRange());
      today.setEndDate(eventSpecification.getStartingDateRange());
      calendarEventList.add(0, today);
    }
  }

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine the preferences
    String view = context.getPreferences().get("view");
    String showWhenEmpty = context.getPreferences().getOrDefault("showWhenEmpty", "true");
    int daysToShow = Integer.parseInt(context.getPreferences().getOrDefault("daysToShow", "-1"));
    int monthsToShow = Integer.parseInt(context.getPreferences().getOrDefault("monthsToShow", "1"));
    context.getRequest().setAttribute("showMonthName", context.getPreferences().getOrDefault("showMonthName", "true"));
    context.getRequest().setAttribute("showEventLink", context.getPreferences().getOrDefault("showEventLink", "true"));
    boolean includeLastEvent = Boolean.parseBoolean(context.getPreferences().getOrDefault("includeLastEvent", "false"));

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "-1"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Look for a specific calendar
    long calendarId = -1L;
    String calendarUniqueId = context.getParameter("calendarUniqueId");
    if (StringUtils.isNotBlank(calendarUniqueId)) {
      // Determine which calendar(s) to show
      List<Calendar> calendarList = CalendarRepository.findAll();
      for (Calendar calendar : calendarList) {
        if (calendar.getUniqueId().equals(calendarUniqueId)) {
          calendarId = calendar.getId();
          break;
        }
      }
    }

    // Determine the query date range (from today on... using the site's timezone start of day)
    ZoneId clientZoneId = ZoneId.of(LoadSitePropertyCommand.loadByName("site.timezone"));
    Instant instant = Instant.now();
    ZonedDateTime zdt = ZonedDateTime.ofInstant(instant, clientZoneId);
    ZonedDateTime zdtStart = zdt.toLocalDate().atStartOfDay(clientZoneId);

    // Build the list of upcoming events with the given specification
    CalendarEventSpecification eventSpecification = new CalendarEventSpecification();
    if (calendarId > -1) {
      eventSpecification.setCalendarId(calendarId);
    }
    eventSpecification.setStartingDateRange(Timestamp.valueOf(zdtStart.toLocalDateTime()));
    if (daysToShow > 0) {
      ZonedDateTime endDate = zdtStart.plusDays(daysToShow);
      eventSpecification.setEndingDateRange(Timestamp.valueOf(endDate.toLocalDateTime()));
    } else if (monthsToShow > 0) {
      ZonedDateTime endDate = zdtStart.plusMonths(monthsToShow);
      eventSpecification.setEndingDateRange(Timestamp.valueOf(endDate.toLocalDateTime()));
    }
    List<CalendarEvent> calendarEventList = CalendarEventRepository.findAll(eventSpecification, constraints);

    // Determine if the last event should be included
    if (includeLastEvent) {
      insertPastEvent(calendarEventList, eventSpecification);
    }

    // Determine if the widget can be shown
    if ("false".equals(showWhenEmpty) && calendarEventList.isEmpty()) {
      return null;
    } else {
      // Show an event in the UI for today, except for 'overview'
      if (StringUtils.isBlank(view) || "cards".equals(view)) {
        if (calendarEventList.isEmpty()) {
          CalendarEvent today = new CalendarEvent();
          today.setTitle("Today");
          today.setSummary("No events were found");
          today.setAllDay(true);
          today.setStartDate(eventSpecification.getStartingDateRange());
          today.setEndDate(eventSpecification.getStartingDateRange());
          calendarEventList.add(today);
        }
      }
    }
    context.getRequest().setAttribute("calendarEventList", calendarEventList);

    // Determine the view
    context.setJsp(JSP);
    if ("cards".equals(view)) {

      // Determine the number of cards to use across
      String smallCardCount = context.getPreferences().getOrDefault("smallCardCount", "3");
      String mediumCardCount = context.getPreferences().get("mediumCardCount");
      String largeCardCount = context.getPreferences().get("largeCardCount");
      if (StringUtils.isBlank(mediumCardCount)) {
        mediumCardCount = smallCardCount;
      }
      if (StringUtils.isBlank(largeCardCount)) {
        largeCardCount = mediumCardCount;
      }
      context.getRequest().setAttribute("smallCardCount", smallCardCount);
      context.getRequest().setAttribute("mediumCardCount", mediumCardCount);
      context.getRequest().setAttribute("largeCardCount", largeCardCount);
      context.getRequest().setAttribute("cardClass", context.getPreferences().get("cardClass"));
      context.getRequest().setAttribute("calendarLink", context.getPreferences().get("calendarLink"));

      List<String> titles = Stream.of(context.getPreferences().get("titles").split("\\|"))
          .map(String::trim)
          .collect(toList());
      context.getRequest().setAttribute("titles", titles);

      context.setJsp(CARDS_JSP);
    } else if ("overview".equals(view)) {
      context.setJsp(OVERVIEW_JSP);
    }
    return context;
  }
}
