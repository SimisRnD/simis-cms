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

import com.simisinc.platform.application.calendar.HolidaysCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.domain.model.cms.Holiday;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * Provides the colors and values for the calendar
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class CalendarAjax extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  private static String getColor(List<Calendar> calendarList, CalendarEvent calendarEvent) {
    for (Calendar calendar : calendarList) {
      if (calendarEvent.getCalendarId().equals(calendar.getId())) {
        return calendar.getColor();
      }
    }
    return null;
  }

  public WidgetContext execute(WidgetContext context) {

    // Determine which calendar(s) to show
    List<Calendar> calendarList = CalendarRepository.findAll();

    long calendarId = -1L;
    String calendarUniqueId = context.getParameter("calendarUniqueId");
    if (StringUtils.isNotBlank(calendarUniqueId)) {
      for (Calendar calendar : calendarList) {
        if (calendar.getUniqueId().equals(calendarUniqueId)) {
          calendarId = calendar.getId();
          break;
        }
      }
    }

    // Determine which dates to show:
    String start = context.getParameter("start");
    String end = context.getParameter("end");
    Date startDate = null;
    Date endDate = null;

    List<CalendarEvent> calendarEventList = null;
    try {
      // ISO8601 date strings 2022-05-29T00:00:00-04:00 00:00    start=2013-12-01T00:00:00-05:00&end=2014-01-12T00:00:00-05:00
      startDate = start.contains("T") ? parseISO8601(start) : parseSimpleDateFormat(start);
      endDate = end.contains("T") ? parseISO8601(end) : parseSimpleDateFormat(end);
      CalendarEventSpecification specification = new CalendarEventSpecification();
      if (calendarId > -1) {
        specification.setCalendarId(calendarId);
      }
      specification.setStartingDateRange(new Timestamp(startDate.getTime()));
      specification.setEndingDateRange(new Timestamp(endDate.getTime()));
      calendarEventList = CalendarEventRepository.findAll(specification, null);
      LOG.debug("Calendar events: " + start + " - " + end + " (" + calendarEventList.size() + ")");
    } catch (Exception e) {
      LOG.error("Date/time exception: " + e.getMessage(), e);
    }

    String offset = "";
    ZoneId serverZoneId = ZoneId.systemDefault();
    if ("UTC".equals(serverZoneId.getId())) {
      offset = "+00:00";
    }

    // Determine the results to be shown
    StringBuilder sb = new StringBuilder();
    if (calendarEventList != null && !calendarEventList.isEmpty()) {
      for (CalendarEvent calendarEvent : calendarEventList) {
        if (sb.length() > 0) {
          sb.append(",");
        }
        sb.append("{");
        sb.append("\"id\":").append(calendarEvent.getId()).append(",");
        sb.append("\"uniqueId\":\"").append(JsonCommand.toJson(calendarEvent.getUniqueId())).append("\",");
        String startDateValue = new SimpleDateFormat("yyyy-MM-dd").format(calendarEvent.getStartDate());
        String endDateValue = new SimpleDateFormat("yyyy-MM-dd").format(calendarEvent.getEndDate());
        if (calendarEvent.getAllDay()) {
          sb.append("\"allDay\":").append("true").append(",");
          sb.append("\"start\":\"").append(startDateValue).append("\",");
          sb.append("\"end\":\"").append(endDateValue).append("T24:00").append("\",");
        } else {
          String startDateHours = new SimpleDateFormat("HH:mm").format(calendarEvent.getStartDate());
          String endDateHours = new SimpleDateFormat("HH:mm").format(calendarEvent.getEndDate());
          sb.append("\"start\":\"").append(startDateValue).append("T").append(startDateHours).append(":00").append(offset).append("\",");
          sb.append("\"end\":\"").append(endDateValue).append("T").append(endDateHours).append(":00").append(offset).append("\",");
        }
        if (calendarEvent.getDetailsUrl() != null) {
          sb.append("\"detailsUrl\":\"").append(JsonCommand.toJson(calendarEvent.getDetailsUrl())).append("\",");
        }
        if (calendarEvent.getSignUpUrl() != null) {
          sb.append("\"signUpUrl\":\"").append(JsonCommand.toJson(calendarEvent.getSignUpUrl())).append("\",");
        }
        String color = getColor(calendarList, calendarEvent);
        if (color != null) {
          sb.append("\"color\":\"").append(JsonCommand.toJson(color)).append("\",");
        }
        if (StringUtils.isNotEmpty(calendarEvent.getSummary())) {
          sb.append("\"description\":\"").append(JsonCommand.toJson(calendarEvent.getSummary())).append("\",");
        }
        if (StringUtils.isNotEmpty(calendarEvent.getLocation())) {
          sb.append("\"location\":\"").append(JsonCommand.toJson(calendarEvent.getLocation())).append("\",");
        }
        sb.append("\"title\":\"").append(JsonCommand.toJson(calendarEvent.getTitle())).append("\"");
        sb.append("}");
      }
    }

    // Add holidays... @todo if specified
    LocalDate startLocalDate = convertToLocalDate(startDate);
    LocalDate endLocalDate = convertToLocalDate(endDate);

    List<Holiday> holidayList = HolidaysCommand.usHolidays(startLocalDate, endLocalDate);
    int holidayId = 0;
    for (Holiday holiday : holidayList) {
      --holidayId;
      if (sb.length() > 0) {
        sb.append(",");
      }
      sb.append("{");
      sb.append("\"id\":").append(holidayId).append(",");
      sb.append("\"uniqueId\":\"").append(JsonCommand.toJson("holiday" + holidayId)).append("\",");
      sb.append("\"allDay\":").append("true").append(",");
      sb.append("\"start\":\"").append(holiday.getDate().toString()).append("\",");
      sb.append("\"end\":\"").append(holiday.getDate().toString()).append("T24:00").append("\",");
//      String color = getColor(calendarList, calendarEvent);
//      if (color != null) {
//        sb.append("\"color\":\"").append(JsonCommand.toJson(color)).append("\",");
//      }
//      if (StringUtils.isNotEmpty(calendarEvent.getLocation())) {
//        sb.append("\"location\":\"").append(JsonCommand.toJson(calendarEvent.getLocation())).append("\",");
//      }
      sb.append("\"title\":\"").append(JsonCommand.toJson(holiday.getName())).append("\"");
      sb.append("}");
    }

    if (sb.length() == 0) {
      context.setJson("[]");
      return context;
    }

    context.setJson("[" + sb + "]");
    return context;
  }

  private Date parseISO8601(String value) {
    // 2022-05-01T00:00:00-04:00
    DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_DATE_TIME;
    OffsetDateTime offsetDateTime = OffsetDateTime.parse(value, timeFormatter);
    return Date.from(Instant.from(offsetDateTime));
  }

  private Date parseSimpleDateFormat(String value) throws ParseException {
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm");
    return dateFormat.parse(value + (value.contains(" 00:00") ? "" : " 00:00"));
  }

  private LocalDate convertToLocalDate(Date dateToConvert) {
    return dateToConvert.toInstant()
        .atZone(ZoneId.systemDefault())
        .toLocalDate();
  }
}
