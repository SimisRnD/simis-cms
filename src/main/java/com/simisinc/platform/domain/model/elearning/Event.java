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
 * E-learning Event
 *
 * @author matt rajkowski
 * @created 7/12/2022 12:00 PM
 */
public class Event extends Entity {

  // Course properties
  private long id = -1;
  private String remoteId = null;
  private String name = null;
  private String summary = null;
  private String summaryHtml = null;
  private String format = null;
  private String url = null;
  private LocalDate startDate = null;
  private LocalTime startTime = null;
  private LocalDate endDate = null;
  private LocalTime endTime = null;
  private LocalDate lastModifiedDate = null;
  private LocalTime lastModifiedTime = null;
  private boolean visible = true;
  // Helpers
  private int enrollments = -1;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getRemoteId() {
    return remoteId;
  }

  public void setRemoteId(String remoteId) {
    this.remoteId = remoteId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getSummaryHtml() {
    return summaryHtml;
  }

  public void setSummaryHtml(String summaryHtml) {
    this.summaryHtml = summaryHtml;
  }

  public String getFormat() {
    return format;
  }

  public void setFormat(String format) {
    this.format = format;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalTime getStartTime() {
    return startTime;
  }

  public void setStartTime(LocalTime startTime) {
    this.startTime = startTime;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public LocalTime getEndTime() {
    return endTime;
  }

  public void setEndTime(LocalTime endTime) {
    this.endTime = endTime;
  }

  public LocalDate getLastModifiedDate() {
    return lastModifiedDate;
  }

  public void setLastModifiedDate(LocalDate lastModifiedDate) {
    this.lastModifiedDate = lastModifiedDate;
  }

  public LocalTime getLastModifiedTime() {
    return lastModifiedTime;
  }

  public void setLastModifiedTime(LocalTime lastModifiedTime) {
    this.lastModifiedTime = lastModifiedTime;
  }

  public boolean getVisible() {
    return visible;
  }

  public boolean isVisible() {
    return visible;
  }

  public void setVisible(boolean visible) {
    this.visible = visible;
  }

  public int getEnrollments() {
    return enrollments;
  }

  public void setEnrollments(int enrollments) {
    this.enrollments = enrollments;
  }
}
