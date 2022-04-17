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

package com.simisinc.platform.infrastructure.persistence.cms;

import com.simisinc.platform.presentation.controller.DataConstants;

import java.sql.Timestamp;

/**
 * Properties for querying objects from the calendar event repository
 *
 * @author matt rajkowski
 * @created 10/29/18 1:28 PM
 */
public class CalendarEventSpecification {

  private long id = -1L;
  private long calendarId = -1L;
  private String uniqueId = null;
  private int publishedOnly = DataConstants.UNDEFINED;
  private Timestamp startingDateRange = null;
  private Timestamp endingDateRange = null;
  private String searchTerm = null;

  public CalendarEventSpecification() {
  }

  public CalendarEventSpecification(long id) {
    this.id = id;
  }

  public CalendarEventSpecification(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getCalendarId() {
    return calendarId;
  }

  public void setCalendarId(long calendarId) {
    this.calendarId = calendarId;
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public void setUniqueId(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  public int getPublishedOnly() {
    return publishedOnly;
  }

  public void setPublishedOnly(boolean publishedOnly) {
    this.publishedOnly = (publishedOnly ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public void setPublishedOnly(int publishedOnly) {
    this.publishedOnly = publishedOnly;
  }

  public Timestamp getStartingDateRange() {
    return startingDateRange;
  }

  public void setStartingDateRange(Timestamp startingDateRange) {
    this.startingDateRange = startingDateRange;
  }

  public Timestamp getEndingDateRange() {
    return endingDateRange;
  }

  public void setEndingDateRange(Timestamp endingDateRange) {
    this.endingDateRange = endingDateRange;
  }

  public String getSearchTerm() {
    return searchTerm;
  }

  public void setSearchTerm(String searchTerm) {
    this.searchTerm = searchTerm;
  }
}
