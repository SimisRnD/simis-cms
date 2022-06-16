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
import com.simisinc.platform.application.cms.MarkdownCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.elearning.Course;
import com.simisinc.platform.domain.model.elearning.CourseUser;
import com.simisinc.platform.domain.model.elearning.CourseUserAggregate;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/**
 * Commands for working with Moodle Courses
 *
 * @author matt rajkowski
 * @created 6/14/2022 9:34 PM
 */
public class MoodleCourseListCommand {

  private static Log LOG = LogFactory.getLog(MoodleCourseListCommand.class);

  // @note API returns visible courses only https://tracker.moodle.org/browse/MDL-47229
  private static final String GET_USERS_COURSES_API = "core_enrol_get_users_courses";

  public static List<CourseUserAggregate> retrieveCoursesEnrolled(User user, boolean withUserCount) {
    // Determine the user's Moodle id
    long userId = MoodleUserCommand.retrieveUserId(user);
    if (userId == -1) {
      LOG.debug("UserId was not found for: " + user.getEmail());
      return null;
    }
    return retrieveCoursesEnrolledForRemoteUserId(userId, withUserCount);
  }

  public static List<CourseUserAggregate> retrieveCoursesEnrolledForRemoteUserId(long userId, boolean withUserCount) {
    // Retrieve the courses
    Map<String, String> parameters = new HashMap<>();
    parameters.put("userid", String.valueOf(userId));
    if (!withUserCount) {
      parameters.put("returnusercount", "0");
    }
    JsonNode json = MoodleApiClientCommand.sendHttpGet(GET_USERS_COURSES_API, parameters);

    // Verify that record(s) have been returned
    if (json == null || (json.isArray() && json.isEmpty()) || !json.isArray()) {
      LOG.debug("No records found");
      return null;
    }

    // Use the server url for creating links
    String serverUrl = LoadSitePropertyCommand.loadByName("elearning.moodle.url");

    // Parse the records
    List<CourseUserAggregate> list = new ArrayList<>();
    Iterator<JsonNode> records = json.elements();
    while (records.hasNext()) {
      LOG.debug("Parsing record...");
      JsonNode courseNode = records.next();

      // Course information
      Course course = new Course();
      if (courseNode.has("id")) {
        String remoteId = courseNode.get("id").asText();
        course.setRemoteId(remoteId);
        course.setUrl(serverUrl + "/course/view.php?id=" + remoteId);
      }
      if (courseNode.has("fullname")) {
        course.setName(StringUtils.trimToNull(courseNode.get("fullname").asText()));
      }
      if (courseNode.has("summary")) {
        //summary format (1 = HTML, 0 = MOODLE, 2 = PLAIN or 4 = MARKDOWN)
        String summary = courseNode.get("summary").asText();
        int format = courseNode.get("summaryformat").asInt(1);
        if (format == 1) {
          course.setSummaryHtml(StringUtils.trimToNull(summary));
          course.setSummary(StringUtils.trimToNull(HtmlCommand.text(course.getSummaryHtml())));
        } else if (format == 2) {
          course.setSummary(StringUtils.trimToNull(summary));
        } else if (format == 4) {
          course.setSummaryHtml(MarkdownCommand.html(summary));
          course.setSummary(StringUtils.trimToNull(HtmlCommand.text(course.getSummaryHtml())));
        }
      }
      if (courseNode.has("format")) {
        course.setFormat(StringUtils.trimToNull(courseNode.get("format").asText()));
      }
      if (withUserCount && courseNode.has("enrolledusercount")) {
        course.setEnrollments(courseNode.get("enrolledusercount").asInt(0));
      }
      if (courseNode.has("startdate")) {

      }
      if (courseNode.has("enddate")) {

      }
      if (courseNode.has("timemodified")) {

      }

      // Course User information
      CourseUser courseUser = new CourseUser();
      courseUser.setRemoteCourseId(course.getRemoteId());
      courseUser.setEnrolled(true);
      if (courseNode.has("isfavourite")) {
        courseUser.setFavorite(courseNode.get("isfavourite").asBoolean(false));
      }
      if (courseNode.has("lastaccess")) {

      }

      CourseUserAggregate courseUserAggregate = new CourseUserAggregate(course, courseUser);
      list.add(courseUserAggregate);
    }
    return list;
  }
}
