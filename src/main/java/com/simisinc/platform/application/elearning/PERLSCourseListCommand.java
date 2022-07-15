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
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.application.cms.MarkdownCommand;
import com.simisinc.platform.domain.model.elearning.Course;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.simisinc.platform.application.elearning.PERLSApiClientCommand.COURSE_API;

/**
 * Commands for working with PERLS Courses
 *
 * @author matt rajkowski
 * @created 7/14/2022 1:45 PM
 */
public class PERLSCourseListCommand {

  private static Log LOG = LogFactory.getLog(PERLSCourseListCommand.class);

  public static File retrieveCourseListToFile(File file) {
    // Request the courses
    return PERLSApiClientCommand.sendHttpGetToFile(COURSE_API, null, file);
  }

  public static List<Course> retrieveCourseList() {

    // Request the courses
    JsonNode json = PERLSApiClientCommand.sendHttpGetToJson(COURSE_API, null);

    // Verify that record(s) have been returned
    if (json == null || (json.isArray() && json.isEmpty()) || !json.isArray()) {
      LOG.debug("No records found");
      return null;
    }

    // Parse the records
    List<Course> list = new ArrayList<>();
    Iterator<JsonNode> records = json.elements();
    while (records.hasNext()) {
      LOG.debug("Parsing record...");
      JsonNode courseNode = records.next();

      // Course information
      Course course = new Course();
      if (courseNode.has("id")) {
        String remoteId = courseNode.get("id").asText();
        course.setRemoteId(remoteId);
//        course.setUrl(serverUrl + "/course/view.php?id=" + remoteId);
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
      if (courseNode.has("startdate")) {

      }
      if (courseNode.has("enddate")) {

      }
      if (courseNode.has("timemodified")) {

      }

      list.add(course);

    }
    return list;
  }
}
