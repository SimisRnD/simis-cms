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

package com.simisinc.platform.presentation.widgets.elearning;

import com.simisinc.platform.application.elearning.ElearningCommand;
import com.simisinc.platform.application.elearning.MoodleCourseListCommand;
import com.simisinc.platform.application.elearning.MoodleInstructorCourseCommand;
import com.simisinc.platform.application.elearning.MoodleLeanerCourseCommand;
import com.simisinc.platform.domain.model.elearning.CourseUserAggregate;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Displays courses from remote systems
 *
 * @author matt rajkowski
 * @created 6/14/2022 7:30 AM
 */
public class RemoteCourseListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/elearning/course-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Common attributes
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine preferences
    String role = context.getPreferences().getOrDefault("role", "student");
    context.getRequest().setAttribute("useItemLink", context.getPreferences().getOrDefault("useItemLink", "false"));
    context.getRequest().setAttribute("showLaunchLink", context.getPreferences().getOrDefault("showLaunchLink", "false"));
    context.getRequest().setAttribute("launchLabel", context.getPreferences().getOrDefault("launchLabel", "Launch"));
    boolean showWhenEmpty = "true".equals(context.getPreferences().getOrDefault("showWhenEmpty", "false"));

    // Retrieve the courses
    boolean moodleEnabled = ElearningCommand.isMoodleEnabled();
    List<CourseUserAggregate> courseList = new ArrayList<>();
    if ("teacher".equals(role)) {
      // Retrieve the Moodle course list for 'teaching' roles
      if (moodleEnabled) {
        List<CourseUserAggregate> moodleList = MoodleInstructorCourseCommand.retrieveTeacherCourses(context.getUserSession().getUser());
        if (moodleList != null) {
          courseList.addAll(moodleList);
        }
      }
    } else if ("any".equals(role)) {
      // Retrieve the Moodle course list for any role
      if (moodleEnabled) {
        List<CourseUserAggregate> moodleList = MoodleCourseListCommand.retrieveCoursesEnrolled(context.getUserSession().getUser(), false);
        if (moodleList != null) {
          courseList.addAll(moodleList);
        }
      }
    } else {
      // Retrieve the Moodle course list for 'student' role
      if (moodleEnabled) {
        List<CourseUserAggregate> moodleList = MoodleLeanerCourseCommand.retrieveLearnerCourses(context.getUserSession().getUser());
        if (moodleList != null) {
          courseList.addAll(moodleList);
        }
      }
    }
    // Sort the list by name
    courseList.sort(Comparator.comparing(o -> o.getCourse().getName()));

    if (!showWhenEmpty && courseList.isEmpty()) {
      return context;
    }
    context.getRequest().setAttribute("courseList", courseList);
    context.setJsp(JSP);
    return context;
  }
}
