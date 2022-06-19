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

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * E-learning Course User
 *
 * @author matt rajkowski
 * @created 6/14/2022 7:40 AM
 */
public class CourseUser extends Entity {

  // User Properties
  private long courseId = -1;
  private String remoteCourseId = null;
  private String remoteUserId = null;
  private String firstName = null;
  private String lastName = null;
  private String email = null;
  private String username = null;
  private boolean manager = false;
  private boolean teacher = false;
  private boolean enrolled = false;
  private LocalDate enrollmentDate = null;
  private LocalTime enrollmentTime = null;
  private boolean completed = false;
  private double progress = 0;
  private LocalDate lastAccessDate = null;
  private LocalTime lastAccessTime = null;
  private boolean favorite = false;

  public long getCourseId() {
    return courseId;
  }

  public void setCourseId(long courseId) {
    this.courseId = courseId;
  }

  public String getRemoteCourseId() {
    return remoteCourseId;
  }

  public void setRemoteCourseId(String remoteCourseId) {
    this.remoteCourseId = remoteCourseId;
  }

  public String getRemoteUserId() {
    return remoteUserId;
  }

  public void setRemoteUserId(String remoteUserId) {
    this.remoteUserId = remoteUserId;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public boolean getManager() {
    return manager;
  }

  public boolean isManager() {
    return manager;
  }

  public void setManager(boolean manager) {
    this.manager = manager;
  }

  public boolean getTeacher() {
    return teacher;
  }

  public boolean isTeacher() {
    return teacher;
  }

  public void setTeacher(boolean teacher) {
    this.teacher = teacher;
  }

  public boolean getEnrolled() {
    return enrolled;
  }

  public boolean isEnrolled() {
    return enrolled;
  }

  public void setEnrolled(boolean enrolled) {
    this.enrolled = enrolled;
  }

  public LocalDate getEnrollmentDate() {
    return enrollmentDate;
  }

  public void setEnrollmentDate(LocalDate enrollmentDate) {
    this.enrollmentDate = enrollmentDate;
  }

  public LocalTime getEnrollmentTime() {
    return enrollmentTime;
  }

  public void setEnrollmentTime(LocalTime enrollmentTime) {
    this.enrollmentTime = enrollmentTime;
  }

  public boolean getCompleted() {
    return completed;
  }

  public boolean isCompleted() {
    return completed;
  }

  public void setCompleted(boolean completed) {
    this.completed = completed;
  }

  public double getProgress() {
    return progress;
  }

  public void setProgress(double progress) {
    this.progress = progress;
  }

  public LocalDate getLastAccessDate() {
    return lastAccessDate;
  }

  public void setLastAccessDate(LocalDate lastAccessDate) {
    this.lastAccessDate = lastAccessDate;
  }

  public LocalTime getLastAccessTime() {
    return lastAccessTime;
  }

  public void setLastAccessTime(LocalTime lastAccessTime) {
    this.lastAccessTime = lastAccessTime;
  }

  public boolean getFavorite() {
    return favorite;
  }

  public boolean isFavorite() {
    return favorite;
  }

  public void setFavorite(boolean favorite) {
    this.favorite = favorite;
  }
}
