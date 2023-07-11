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

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.http.HttpGetToStringCommand;

/**
 * Commands for working with Moodle
 *
 * @author matt rajkowski
 * @created 6/14/2022 9:23 PM
 */
public class MoodleApiClientCommand {

  private static Log LOG = LogFactory.getLog(MoodleApiClientCommand.class);

  public static final String GET_USERS_COURSES_API = "core_enrol_get_users_courses";
  public static final String GET_USERS_API = "core_user_get_users_by_field";
  public static final String GET_ENROLLED_USERS_API = "core_enrol_get_enrolled_users";

  public static final String GET_CALENDAR_EVENTS = "core_calendar_get_calendar_events";
  public static final String GET_CALENDAR_EVENT_BY_ID = "core_calendar_get_calendar_event_by_id";
  public static final String GET_CALENDAR_EVENTS_BY_COURSES = "core_calendar_get_action_events_by_courses";

  public static JsonNode sendHttpGet(String wsFunction, Map<String, String> parameters) {

    // Check the API properties
    if (!ElearningCommand.isMoodleEnabled()) {
      LOG.debug("Moodle is not enabled");
      return null;
    }

    // Determine the mode and credentials
    String serverUrl = LoadSitePropertyCommand.loadByName("elearning.moodle.url");
    String token = LoadSitePropertyCommand.loadByName("elearning.moodle.token");

    if (StringUtils.isBlank(serverUrl)) {
      LOG.error("sendHttpGet has empty serverUrl");
      return null;
    }

    if (StringUtils.isBlank(token)) {
      LOG.error("sendHttpGet has empty token");
      return null;
    }

    if (StringUtils.isBlank(wsFunction)) {
      LOG.error("sendHttpGet has empty wsFunction");
      return null;
    }

    // Generate the URL
    String url = serverUrl + "/webservice/rest/server.php" +
        "?wstoken=" + token +
        "&moodlewsrestformat=json" +
        "&wsfunction=" + wsFunction;

    // Add any optional parameters
    if (parameters != null && !parameters.isEmpty()) {
      for (Map.Entry<String, String> set : parameters.entrySet()) {
        url += "&" + set.getKey() + "=" + set.getValue();
      }
    }

    // Send
    LOG.debug("GET: " + url);
    String remoteContent = HttpGetToStringCommand.execute(url);

    // Check for content
    if (StringUtils.isBlank(remoteContent)) {
      LOG.error("HttpGet Remote content is empty");
      return null;
    }

    // Check for a Moodle exception
    try {
      JsonNode jsonNode = JsonLoader.fromString(remoteContent);
      if (jsonNode.has("exception")) {
        LOG.warn("Exception: " + jsonNode.get("exception"));
        return null;
      }

      // Return the content as JSON
      return jsonNode;
    } catch (Exception e) {
      LOG.error("sendHttpGet", e);
    }
    return null;
  }
}
