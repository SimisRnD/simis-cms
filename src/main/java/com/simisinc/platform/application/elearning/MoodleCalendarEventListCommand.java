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

package com.simisinc.platform.application.elearning;

import com.fasterxml.jackson.databind.JsonNode;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.application.cms.LocalDateCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.elearning.CourseUserAggregate;
import com.simisinc.platform.domain.model.elearning.Event;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

import static com.simisinc.platform.application.elearning.MoodleApiClientCommand.GET_CALENDAR_EVENTS;

/**
 * Commands for working with Moodle Calendar Events
 *
 * @author matt rajkowski
 * @created 7/11/2022 12:00 PM
 */
public class MoodleCalendarEventListCommand {

  private static Log LOG = LogFactory.getLog(MoodleCalendarEventListCommand.class);

  public static List<Event> retrieveUserCourseEvents(User user, Date startDate, Date endDate) {
    // Determine the user's Moodle id
    long userId = MoodleUserCommand.retrieveUserId(user);
    if (userId == -1) {
      LOG.debug("UserId was not found for: " + user.getEmail());
      return null;
    }
    // Determine the user's courses
    List<CourseUserAggregate> courseUserAggregateList = MoodleCourseListCommand.retrieveCoursesEnrolledForRemoteUserId(userId, false);
    // Find events for the courses
    return retrieveUserCourseEvents(courseUserAggregateList, startDate, endDate);
  }

  public static List<Event> retrieveUserCourseEvents(List<CourseUserAggregate> courseUserAggregateList, Date startDate, Date endDate) {

    if (courseUserAggregateList == null || courseUserAggregateList.isEmpty()) {
      return null;
    }

    long timeStart = (startDate.getTime() / 1000);
    long timeEnd = (endDate.getTime() / 1000);

    Map<String, String> parameters = new HashMap<>();
    parameters.put("options[timestart]", String.valueOf(timeStart));
    parameters.put("options[timeend]", String.valueOf(timeEnd));
    parameters.put("options[userevents]", "0");
    parameters.put("options[siteevents]", "0");

    // Determine the course ids value
    int i = -1;
    for (CourseUserAggregate courseUserAggregate : courseUserAggregateList) {
      parameters.put("events[courseids][" + (++i) + "]", courseUserAggregate.getCourse().getRemoteId());
    }

    // Make the request
    JsonNode json = MoodleApiClientCommand.sendHttpGet(GET_CALENDAR_EVENTS, parameters);
//    JsonNode json = MoodleApiClientCommand.sendHttpGet(GET_CALENDAR_EVENTS_BY_COURSES, parameters);

    // Verify that record(s) have been returned
    if (json == null || !json.has("events") || json.get("events").size() == 0) {
      LOG.debug("No records found");
      return null;
    }

    // Use the server url for creating links
    String serverUrl = LoadSitePropertyCommand.loadByName("elearning.moodle.url");

    // Parse the records
    List<Event> list = new ArrayList<>();
    Iterator<JsonNode> records = json.get("events").elements();
    while (records.hasNext()) {
      LOG.debug("Parsing record...");
      JsonNode eventNode = records.next();
      // Event information
      Event event = new Event();
      // Determine the courseId
      long courseId = -1;
      if (eventNode.has("courseid")) {
        courseId = eventNode.get("courseid").asLong();
      }
      // Determine the id and create a URL
      if (eventNode.has("id")) {
        long remoteId = eventNode.get("id").asLong();
        event.setRemoteId(String.valueOf(remoteId));
        // Make a call to get the event url (better way?)
        String url = MoodleCalendarEventCommand.retrieveEventById(remoteId).getUrl();
        if (StringUtils.isBlank(url)) {
          url = serverUrl + "/course/view.php?id=" + courseId;
        }
        event.setUrl(url);
      }
      if (eventNode.has("name")) {
        event.setName(StringUtils.trimToNull(eventNode.get("name").asText()));
      }
      if (eventNode.has("description")) {
        //description format (1 = HTML, 0 = MOODLE, 2 = PLAIN or 4 = MARKDOWN)
        String summary = eventNode.get("description").asText();
        event.setSummaryHtml(StringUtils.trimToNull(summary));
        event.setSummary(StringUtils.trimToNull(HtmlCommand.text(event.getSummaryHtml())));
      }
      if (eventNode.has("eventtype")) {

      }
      if (eventNode.has("timestart")) {
        long timestart = eventNode.get("timestart").asLong() * 1000;
        event.setStartDate(LocalDateCommand.convertToLocalDate(new Date(timestart)));
        event.setStartTime(LocalDateCommand.convertToLocalTime(new Date(timestart)));
      }
      if (eventNode.has("timeduration")) {

      }
      if (eventNode.has("timemodified")) {

      }
      list.add(event);
    }
    return list;
  }
}
