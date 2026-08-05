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

import com.simisinc.platform.domain.model.Entity;
import com.simisinc.platform.presentation.controller.DataConstants;

import java.sql.Timestamp;

/**
 * Properties for querying objects from the web page repository
 *
 * @author matt rajkowski
 * @created 2/6/2022 12:15 PM
 */
public class WebPageSpecification extends Entity {

  private String link = null;
  private String searchTerm = null;
  private int enabled = DataConstants.UNDEFINED;
  private int draft = DataConstants.UNDEFINED;
  private int searchable = DataConstants.UNDEFINED;
  private int inSitemap = DataConstants.UNDEFINED;
  private int hasRedirect = DataConstants.UNDEFINED;
  // issue #427: mirrors CalendarEventSpecification's archivedOnly exactly -- UNDEFINED includes
  // archived rows (the default, unchanged for any caller that never sets this), TRUE returns only
  // archived rows, FALSE excludes them.
  private int archivedOnly = DataConstants.UNDEFINED;
  // issue #426 (editorial calendar): matches a page whose publishAt OR expiresAt falls within
  // [startingDateRange, endingDateRange) -- mirrors CalendarEventSpecification's
  // startingDateRange/endingDateRange naming and OR-across-two-columns shape exactly, applied here
  // to publish_at/expires_at instead of a single occurrence window.
  private Timestamp startingDateRange = null;
  private Timestamp endingDateRange = null;
  // issue #996 (editorial calendar "Drafts with no dates" feed): when true, matches a page with
  // NEITHER scheduling field set (publish_at IS NULL AND expires_at IS NULL) instead of applying
  // the startingDateRange/endingDateRange filter above -- a page with no anchor date at all can
  // never fall inside a date range, so it is otherwise invisible on every calendar view (#996).
  // Defaults to false so every pre-#996 caller is unaffected.
  private boolean undatedOnly = false;
  // issue #426: the editorial calendar's author filter. -1 (unset) matches every page, mirroring
  // every other *Specification's -1-means-unset long field (e.g. CalendarEventSpecification.calendarId).
  private long createdBy = -1L;

  public WebPageSpecification() {
  }

  public String getLink() {
    return link;
  }

  public void setLink(String link) {
    this.link = link;
  }

  /** A free-text substring match across link, title, and keywords -- distinct from {@link #getLink()}
   * (exact match) and from {@link WebPageRepository#search} (tsvector full-text, restricted to
   * enabled+searchable pages). This is for the admin list, which must find a page in any state. */
  public String getSearchTerm() {
    return searchTerm;
  }

  public void setSearchTerm(String searchTerm) {
    this.searchTerm = searchTerm;
  }

  public int getEnabled() {
    return enabled;
  }

  public void setEnabled(int enabled) {
    this.enabled = enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = (enabled ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getDraft() {
    return draft;
  }

  public void setDraft(int draft) {
    this.draft = draft;
  }

  public void setDraft(boolean draft) {
    this.draft = (draft ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getSearchable() {
    return searchable;
  }

  public void setSearchable(int searchable) {
    this.searchable = searchable;
  }

  public void setSearchable(boolean searchable) {
    this.searchable = (searchable ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getInSitemap() {
    return inSitemap;
  }

  public void setInSitemap(int inSitemap) {
    this.inSitemap = inSitemap;
  }

  public void setInSitemap(boolean inSitemap) {
    this.inSitemap = (inSitemap ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getHasRedirect() {
    return hasRedirect;
  }

  public void setHasRedirect(int hasRedirect) {
    this.hasRedirect = hasRedirect;
  }

  public void setHasRedirect(boolean hasRedirect) {
    this.hasRedirect = (hasRedirect ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getArchivedOnly() {
    return archivedOnly;
  }

  public void setArchivedOnly(int archivedOnly) {
    this.archivedOnly = archivedOnly;
  }

  public void setArchivedOnly(boolean archivedOnly) {
    this.archivedOnly = (archivedOnly ? DataConstants.TRUE : DataConstants.FALSE);
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

  public long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(long createdBy) {
    this.createdBy = createdBy;
  }

  public boolean isUndatedOnly() {
    return undatedOnly;
  }

  public void setUndatedOnly(boolean undatedOnly) {
    this.undatedOnly = undatedOnly;
  }
}
