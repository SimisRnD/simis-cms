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

import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Retrieves calendars and provides a JSON response
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class CalendarAjaxEvents {

  private static Log LOG = LogFactory.getLog(CalendarAjaxEvents.class);

  protected static void addCalendarEvents(long userId, String calendarUniqueId, Date startDate, Date endDate, StringBuilder sb) {

    // Determine which calendar(s) to show
    List<Calendar> calendarList = CalendarRepository.findAll();
    long calendarId = -1L;
    if (StringUtils.isNotBlank(calendarUniqueId)) {
      for (Calendar calendar : calendarList) {
        if (calendar.getUniqueId().equals(calendarUniqueId)) {
          calendarId = calendar.getId();
          break;
        }
      }
    }

    // Load the events
    CalendarEventSpecification specification = new CalendarEventSpecification();
    if (calendarId > -1) {
      specification.setCalendarId(calendarId);
    }
    specification.setStartingDateRange(new Timestamp(startDate.getTime()));
    specification.setEndingDateRange(new Timestamp(endDate.getTime()));
    List<CalendarEvent> calendarEventList = CalendarEventRepository.findAll(specification, null);
    LOG.debug("Calendar events: " + startDate + " - " + endDate + " (" + calendarEventList.size() + ")");

    String offset = "";
    ZoneId serverZoneId = ZoneId.systemDefault();
    if ("UTC".equals(serverZoneId.getId())) {
      offset = "+00:00";
    }

    // Determine the results to be shown
    if (!calendarEventList.isEmpty()) {
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
  }

  private static String getColor(List<Calendar> calendarList, CalendarEvent calendarEvent) {
    for (Calendar calendar : calendarList) {
      if (calendarEvent.getCalendarId().equals(calendar.getId())) {
        return calendar.getColor();
      }
    }
    return null;
  }
}
