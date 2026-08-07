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

import com.simisinc.platform.application.cms.FormatDateCommand;
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

  protected static void addCalendarEvents(long userId, String calendarUniqueId, Date startDate, Date endDate, StringBuilder sb,
      boolean publishedOnly) {

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
    if (publishedOnly) {
      specification.setPublishedOnly(true);
    }
    // issue #882: archived events are never shown on the public calendar, regardless of the
    // viewer's publishedOnly/preview permission -- archived is a distinct "no longer relevant"
    // state, not a preview-gate concern.
    specification.setArchivedOnly(false);
    // publishedOnly already encodes "this viewer cannot preview restricted content" (CalendarAjax
    // passes !canSeeUnpublished for admin/content-manager) -- reuse it to also hide events whose
    // calendar has been switched offline, same admin/content-manager bypass as
    // CalendarEventDetailsWidget's single-event view.
    specification.setCalendarEnabledOnly(publishedOnly);
    if (calendarId > -1) {
      specification.setCalendarId(calendarId);
    }
    specification.setStartingDateRange(new Timestamp(startDate.getTime()));
    specification.setEndingDateRange(new Timestamp(endDate.getTime()));
    List<CalendarEvent> calendarEventList = CalendarEventRepository.findAll(specification, null);
    LOG.debug("Calendar events: " + startDate + " - " + endDate + " (" + calendarEventList.size() + ")");

    // Format dates in the site's configured timezone (not the JVM's default), so an all-day
    // event's calendar day and a timed event's clock time match what the site is set to show,
    // regardless of what zone the server happens to be running in.
    ZoneId siteZoneId = FormatDateCommand.getSiteZoneId();

    // Determine the results to be shown
    if (!calendarEventList.isEmpty()) {
      for (CalendarEvent calendarEvent : calendarEventList) {
        if (sb.length() > 0) {
          sb.append(",");
        }
        sb.append("{");
        sb.append("\"id\":").append(calendarEvent.getId()).append(",");
        sb.append("\"uniqueId\":\"").append(JsonCommand.toJson(calendarEvent.getUniqueId())).append("\",");
        String startDateValue = FormatDateCommand.formatIsoDate(calendarEvent.getStartDate(), siteZoneId);
        String endDateValue = FormatDateCommand.formatIsoDate(calendarEvent.getEndDate(), siteZoneId);
        if (calendarEvent.getAllDay()) {
          sb.append("\"allDay\":").append("true").append(",");
          sb.append("\"start\":\"").append(startDateValue).append("\",");
          sb.append("\"end\":\"").append(endDateValue).append("T24:00").append("\",");
        } else {
          String startDateHours = FormatDateCommand.formatIsoTime(calendarEvent.getStartDate(), siteZoneId);
          String endDateHours = FormatDateCommand.formatIsoTime(calendarEvent.getEndDate(), siteZoneId);
          String startOffset = FormatDateCommand.formatIsoOffset(calendarEvent.getStartDate(), siteZoneId);
          String endOffset = FormatDateCommand.formatIsoOffset(calendarEvent.getEndDate(), siteZoneId);
          sb.append("\"start\":\"").append(startDateValue).append("T").append(startDateHours).append(":00").append(startOffset).append("\",");
          sb.append("\"end\":\"").append(endDateValue).append("T").append(endDateHours).append(":00").append(endOffset).append("\",");
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
        // description, location, and title are concatenated into the tooltip/detail markup on the
        // client (innerHTML via .html()), so HTML-encode before JSON-encoding -- JsonCommand.toJson
        // is JSON-safe but not HTML-safe (stored DOM XSS).
        if (StringUtils.isNotEmpty(calendarEvent.getSummary())) {
          sb.append("\"description\":\"").append(JsonCommand.toJson(escapeHtml(calendarEvent.getSummary()))).append("\",");
        }
        if (StringUtils.isNotEmpty(calendarEvent.getLocation())) {
          sb.append("\"location\":\"").append(JsonCommand.toJson(escapeHtml(calendarEvent.getLocation()))).append("\",");
        }
        sb.append("\"title\":\"").append(JsonCommand.toJson(escapeHtml(calendarEvent.getTitle()))).append("\"");
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

  /**
   * Minimal HTML entity-encoding for values the client concatenates into markup (tooltip/detail).
   * Ampersand first so the entities it introduces are not themselves double-encoded.
   */
  private static String escapeHtml(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
