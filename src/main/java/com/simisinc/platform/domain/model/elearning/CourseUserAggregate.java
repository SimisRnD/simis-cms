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

package com.simisinc.platform.domain.model.elearning;

import com.simisinc.platform.domain.model.Entity;

/**
 * E-learning Course and Course User Aggregate
 *
 * @author matt rajkowski
 * @created 6/14/2022 7:40 AM
 */
public class CourseUserAggregate extends Entity {

  private Course course = null;
  private CourseUser courseUser = null;

  public CourseUserAggregate(Course course, CourseUser courseUser) {
    this.course = course;
    this.courseUser = courseUser;
  }

  public Course getCourse() {
    return course;
  }

  public void setCourse(Course course) {
    this.course = course;
  }

  public CourseUser getCourseUser() {
    return courseUser;
  }

  public void setCourseUser(CourseUser courseUser) {
    this.courseUser = courseUser;
  }
}
