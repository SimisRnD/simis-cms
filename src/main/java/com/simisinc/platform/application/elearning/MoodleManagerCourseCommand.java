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

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.elearning.CourseUser;
import com.simisinc.platform.domain.model.elearning.CourseUserAggregate;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Commands for working with Moodle Courses
 *
 * @author matt rajkowski
 * @created 6/14/2022 9:34 PM
 */
public class MoodleManagerCourseCommand {

  private static Log LOG = LogFactory.getLog(MoodleManagerCourseCommand.class);

  public static List<CourseUserAggregate> retrieveManagerCourses(User user, boolean withUserCount) {
    // Determine the userId
    long remoteUserId = MoodleUserCommand.retrieveUserId(user);
    if (remoteUserId == -1) {
      return null;
    }

    // Retrieve all the courses for a user
    List<CourseUserAggregate> courseList = MoodleCourseListCommand.retrieveCoursesEnrolledForRemoteUserId(remoteUserId, withUserCount);
    if (courseList == null) {
      return null;
    }

    // Build a list of courses
    List<CourseUserAggregate> list = new ArrayList<>();

    // Look through the courses and find the user's role
    for (CourseUserAggregate courseUserAggregate : courseList) {
      // Retrieve the course's user list
      List<CourseUser> userList = MoodleCourseCommand.retrieveEnrolledUsers(courseUserAggregate.getCourse().getRemoteId());
      for (CourseUser courseUser : userList) {
        if (StringUtils.isBlank(courseUser.getRemoteUserId())) {
          continue;
        }
        if (!courseUser.getRemoteUserId().equals(String.valueOf(remoteUserId))) {
          continue;
        }
        // Teachers only
        if (courseUser.isManager()) {
          list.add(courseUserAggregate);
        }
        break;
      }
    }
    return list;
  }
}
