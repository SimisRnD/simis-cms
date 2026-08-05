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
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventSpecification;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.time.ZoneId;
import java.util.List;

/**
 * Returns the specified event
 *
 * @author matt rajkowski
 * @created 11/27/18 8:55 AM
 */
public class CalendarEventAjax extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public WidgetContext execute(WidgetContext context) {

    long id = context.getParameterAsLong("id", -1);
    if (id == -1) {
      context.setJson("[]");
      return context;
    }

    // Access the event
    List<CalendarEvent> calendarEventList = null;
    CalendarEventSpecification specification = new CalendarEventSpecification();
    specification.setId(id);
    boolean canSeeUnpublished = context.getUserSession() != null
        && (context.getUserSession().hasRole("admin") || context.getUserSession().hasRole("content-manager"));
    if (!canSeeUnpublished) {
      specification.setPublishedOnly(true);
    }
    // issue #882: archived is a distinct "no longer relevant" state from the published/draft
    // preview permission above, so this event lookup (used by full-calendar.jsp's click handler)
    // excludes archived events unconditionally, even for an admin/content-manager viewer.
    specification.setArchivedOnly(false);
    calendarEventList = CalendarEventRepository.findAll(specification, null);
    if (calendarEventList == null || calendarEventList.isEmpty()) {
      context.setJson("[]");
      return context;
    }

    // Format dates in the site's configured timezone (not the JVM's default), so an all-day
    // event's calendar day and a timed event's clock time match what the site is set to show,
    // regardless of what zone the server happens to be running in.
    ZoneId siteZoneId = FormatDateCommand.getSiteZoneId();

    // Determine the results to be shown
    StringBuilder sb = new StringBuilder();
    for (CalendarEvent calendarEvent : calendarEventList) {
      if (sb.length() > 0) {
        sb.append(",");
      }
      sb.append("{");
      sb.append("\"id\":").append(calendarEvent.getId()).append(",");
      sb.append("\"calendarId\":").append(calendarEvent.getCalendarId()).append(",");
      if (calendarEvent.getAllDay()) {
        sb.append("\"allDay\":").append("true").append(",");
      }
      if (calendarEvent.getPublished() != null) {
        sb.append("\"published\":").append("true").append(",");
      }
      String startDate = FormatDateCommand.formatIsoDate(calendarEvent.getStartDate(), siteZoneId);
      String endDate = FormatDateCommand.formatIsoDate(calendarEvent.getEndDate(), siteZoneId);
      String startDateHours = FormatDateCommand.formatIsoTime(calendarEvent.getStartDate(), siteZoneId);
      String endDateHours = FormatDateCommand.formatIsoTime(calendarEvent.getEndDate(), siteZoneId);
      String startOffset = FormatDateCommand.formatIsoOffset(calendarEvent.getStartDate(), siteZoneId);
      String endOffset = FormatDateCommand.formatIsoOffset(calendarEvent.getEndDate(), siteZoneId);
      // 2018-12-09T16:00:00+00:00
      sb.append("\"start\":\"").append(startDate).append("T").append(startDateHours).append(":00").append(startOffset).append("\",");
      sb.append("\"end\":\"").append(endDate).append("T").append(endDateHours).append(":00").append(endOffset).append("\",");
      if (calendarEvent.getDetailsUrl() != null) {
        sb.append("\"detailsUrl\":\"").append(JsonCommand.toJson(calendarEvent.getDetailsUrl())).append("\",");
      }
      if (calendarEvent.getSignUpUrl() != null) {
        sb.append("\"signUpUrl\":\"").append(JsonCommand.toJson(calendarEvent.getSignUpUrl())).append("\",");
      }
      if (calendarEvent.getVideoUrl() != null) {
        sb.append("\"videoUrl\":\"").append(JsonCommand.toJson(calendarEvent.getVideoUrl())).append("\",");
      }
      if (calendarEvent.getTagsList() != null && calendarEvent.getTagsList().length > 0) {
        sb.append("\"tagsList\":[");
        for (int i = 0; i < calendarEvent.getTagsList().length; i++) {
          if (i > 0) {
            sb.append(",");
          }
          sb.append("\"").append(JsonCommand.toJson(calendarEvent.getTagsList()[i])).append("\"");
        }
        sb.append("],");
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
    context.setJson(sb.toString());
    return context;
  }
}
