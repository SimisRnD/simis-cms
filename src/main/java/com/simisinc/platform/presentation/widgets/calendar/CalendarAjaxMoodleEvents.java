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

import com.simisinc.platform.application.elearning.ElearningCommand;
import com.simisinc.platform.application.elearning.MoodleCalendarEventListCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.elearning.Event;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * Retrieves a Moodle calendar and provides a JSON response
 *
 * @author matt rajkowski
 * @created 7/12/22 8:00 PM
 */
public class CalendarAjaxMoodleEvents {

  private static Log LOG = LogFactory.getLog(CalendarAjaxMoodleEvents.class);

  protected static void addMoodleEvents(User user, Date startDate, Date endDate, StringBuilder sb) {
    // Check the API properties
    if (!ElearningCommand.isMoodleEnabled()) {
      LOG.debug("Moodle is not enabled");
      return;
    }

    List<Event> eventList = MoodleCalendarEventListCommand.retrieveUserCourseEvents(user, startDate, endDate);
    if (eventList == null || eventList.isEmpty()) {
      return;
    }

    for (Event event : eventList) {
      if (sb.length() > 0) {
        sb.append(",");
      }
      sb.append("{");
      sb.append("\"id\":").append(event.getRemoteId()).append(",");
      sb.append("\"uniqueId\":\"").append(JsonCommand.toJson("moodle" + event.getRemoteId())).append("\",");

      String startDateValue = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(event.getStartDate());
      sb.append("\"allDay\":").append("true").append(",");
      sb.append("\"start\":\"").append(startDateValue).append("\",");
      sb.append("\"end\":\"").append(startDateValue).append("T24:00").append("\",");
      if (event.getUrl() != null) {
        sb.append("\"detailsUrl\":\"").append(JsonCommand.toJson(event.getUrl())).append("\",");
      }
      sb.append("\"title\":\"").append(JsonCommand.toJson(event.getName())).append("\"");
      sb.append("}");
    }
  }
}
