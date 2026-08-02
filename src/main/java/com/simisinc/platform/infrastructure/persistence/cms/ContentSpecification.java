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

import java.sql.Timestamp;

/**
 * Properties for querying objects from the content repository
 *
 * @author matt rajkowski
 * @created 5/21/18 8:44 PM
 */
public class ContentSpecification {

  private long id = -1L;
  private String uniqueId = null;
  // Matches EITHER the unique id (substring) OR the body text (full-text) -- a single combined
  // search box, see ContentRepository#query for how this becomes one OR'd SQL fragment.
  private String searchTerm = null;
  private Timestamp dateModifiedAfter = null;  // modified >= this
  private Timestamp dateModifiedBefore = null; // modified < this (half-open; use the start of the day after the "to" date)
  // Character-count range, measured against LENGTH(content_text) (HTML-stripped plain text), not
  // the raw HTML -- see ContentRepository#query. -1 means "not set", matching this class's id style.
  private int minLength = -1;
  private int maxLength = -1;
  // One of ContentReviewCommand.LIST_STATUS_* (Live/Draft/Pending Review/Approved), or null for no
  // filter. Translated into a SQL WHERE fragment against draft_content/draft_status/approved_by by
  // ContentRepository#query, mirroring ContentReviewCommand#listStatusLabel's derivation.
  private String status = null;

  public ContentSpecification() {
  }

  public ContentSpecification(long id) {
    this.id = id;
  }

  public ContentSpecification(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public void setUniqueId(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  public String getSearchTerm() {
    return searchTerm;
  }

  public void setSearchTerm(String searchTerm) {
    this.searchTerm = searchTerm;
  }

  public Timestamp getDateModifiedAfter() {
    return dateModifiedAfter;
  }

  public void setDateModifiedAfter(Timestamp dateModifiedAfter) {
    this.dateModifiedAfter = dateModifiedAfter;
  }

  public Timestamp getDateModifiedBefore() {
    return dateModifiedBefore;
  }

  public void setDateModifiedBefore(Timestamp dateModifiedBefore) {
    this.dateModifiedBefore = dateModifiedBefore;
  }

  public int getMinLength() {
    return minLength;
  }

  public void setMinLength(int minLength) {
    this.minLength = minLength;
  }

  public int getMaxLength() {
    return maxLength;
  }

  public void setMaxLength(int maxLength) {
    this.maxLength = maxLength;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
