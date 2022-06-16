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
import com.simisinc.platform.domain.model.elearning.CourseUser;
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
public class MoodleCourseCommand {

  private static Log LOG = LogFactory.getLog(MoodleCourseCommand.class);
  private static final String GET_ENROLLED_USERS_API = "core_enrol_get_enrolled_users";

  public static List<CourseUser> retrieveEnrolledUsers(String remoteCourseId) {
    List<CourseUser> list = new ArrayList<>();

    // Retrieve the courses
    Map<String, String> parameters = new HashMap<>();
    parameters.put("courseid", remoteCourseId);
    JsonNode json = MoodleApiClientCommand.sendHttpGet(GET_ENROLLED_USERS_API, parameters);

    // Verify that record(s) have been returned
    if (json == null || (json.isArray() && json.isEmpty()) || !json.isArray()) {
      LOG.debug("No records found");
      return list;
    }

    // Parse the records
    Iterator<JsonNode> records = json.elements();
    while (records.hasNext()) {
      LOG.debug("Parsing record...");
      JsonNode userNode = records.next();

      // Course User information
      CourseUser courseUser = new CourseUser();
      courseUser.setRemoteCourseId(remoteCourseId);
      if (userNode.has("id")) {
        courseUser.setRemoteUserId(userNode.get("id").asText());
      }
      if (userNode.has("username")) {
        courseUser.setUsername(StringUtils.trimToNull(userNode.get("username").asText()));
      }
      if (userNode.has("email")) {
        courseUser.setEmail(StringUtils.trimToNull(userNode.get("email").asText()));
      }
      if (userNode.has("firstname")) {
        courseUser.setFirstName(StringUtils.trimToNull(userNode.get("firstname").asText()));
      }
      if (userNode.has("lastname")) {
        courseUser.setLastName(StringUtils.trimToNull(userNode.get("lastname").asText()));
      }
      if (userNode.has("firstaccess")) {
        // >0
      }
      if (userNode.has("lastaccess")) {
        // >0
      }
      // Base the decision on roles
      if (userNode.has("roles")) {
        JsonNode rolesNode = userNode.get("roles");
        if (rolesNode.isArray() && rolesNode.size() > 0) {
          Iterator<JsonNode> roles = rolesNode.elements();
          while (roles.hasNext()) {
            JsonNode thisRole = roles.next();
            if (thisRole.has("shortname")) {
              String shortName = thisRole.get("shortname").asText();
              if ("editingteacher".equals(shortName)) {
                courseUser.setTeacher(true);
              } else if ("teacher".equals(shortName)) {
                courseUser.setTeacher(true);
              } else if ("student".equals(shortName)) {
                courseUser.setEnrolled(true);
              }
            }
          }
        }
      } else {
        courseUser.setEnrolled(true);
      }
      list.add(courseUser);
    }
    return list;
  }
}
