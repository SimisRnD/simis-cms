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

import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static com.simisinc.platform.presentation.widgets.calendar.CalendarAjaxEvents.addCalendarEvents;
import static com.simisinc.platform.presentation.widgets.calendar.CalendarAjaxHolidays.addHolidays;
import static com.simisinc.platform.presentation.widgets.calendar.CalendarAjaxMoodleEvents.addMoodleEvents;

/**
 * Provides the colors and values for the calendar
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class CalendarAjax extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public WidgetContext execute(WidgetContext context) {

    // Check for parameters
    String calendarUniqueId = context.getParameter("calendarUniqueId");
    boolean showEvents = context.getParameterAsBoolean("showEvents", false);
    boolean showHolidays = context.getParameterAsBoolean("showHolidays", false);
    boolean showMoodleEvents = context.getParameterAsBoolean("showMoodleEvents", false);

    // Determine which dates to show:
    String start = context.getParameter("start");
    String end = context.getParameter("end");

    // JSON data
    StringBuilder sb = new StringBuilder();

    try {
      // ISO8601 date strings 2022-05-29T00:00:00-04:00 00:00    start=2013-12-01T00:00:00-05:00&end=2014-01-12T00:00:00-05:00
      Date startDate = start.contains("T") ? parseISO8601(start) : parseSimpleDateFormat(start);
      Date endDate = end.contains("T") ? parseISO8601(end) : parseSimpleDateFormat(end);

      if (showEvents) {
        addCalendarEvents(context.getUserId(), calendarUniqueId, startDate, endDate, sb);
      }

      if (showHolidays) {
        addHolidays(startDate, endDate, sb);
      }

      if (showMoodleEvents) {
        addMoodleEvents(context.getUserSession().getUser(), startDate, endDate, sb);
      }

    } catch (Exception e) {
      LOG.error("Date/time exception: " + e.getMessage(), e);
    }

    if (sb.length() == 0) {
      context.setJson("[]");
      return context;
    }

    context.setJson("[" + sb + "]");
    return context;
  }

  private static Date parseISO8601(String value) {
    // 2022-05-01T00:00:00-04:00
    DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_DATE_TIME;
    OffsetDateTime offsetDateTime = OffsetDateTime.parse(value, timeFormatter);
    return Date.from(Instant.from(offsetDateTime));
  }

  private static Date parseSimpleDateFormat(String value) throws ParseException {
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm");
    return dateFormat.parse(value + (value.contains(" 00:00") ? "" : " 00:00"));
  }
}

