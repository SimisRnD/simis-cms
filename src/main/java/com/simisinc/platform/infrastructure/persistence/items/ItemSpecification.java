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

package com.simisinc.platform.infrastructure.persistence.items;

import com.simisinc.platform.presentation.controller.DataConstants;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

/**
 * Properties for querying objects from the item repository
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class ItemSpecification {

  private long id = -1L;
  private long excludeId = -1L;
  private long collectionId = -1L;
  private long categoryId = -1L;
  // Issue #636: multi-select within the category facet dimension. categoryId (above) is kept as
  // the single-value field every pre-#636 caller already uses -- adding a second, list-shaped
  // field alongside it (rather than replacing categoryId) mirrors the dual-representation shape
  // WebhookSubscription.eventTypes/eventTypeList uses for the same kind of "one value historically,
  // now possibly several" change. Unlike WebhookSubscription's CSV-string-backed field, there's no
  // DB column to serialize to here -- this is an in-memory query specification -- so the list is
  // just its own field. Callers should generally not read categoryId/categoryIds directly when
  // building a WHERE clause; use getEffectiveCategoryIds() so both representations are honored.
  private List<Long> categoryIds = null;
  private long tagId = -1L;
  // Issue #632: multi-select within the tag facet dimension, mirroring categoryId/categoryIds
  // above exactly -- tagId is kept as a single-value field for any caller that only ever needs
  // one, tagIds is the list-shaped field a checkbox-group facet actually populates. Unlike
  // category, no pre-#632 caller ever set a single tagId (tags didn't exist before PR #863), but
  // the same dual-representation shape is kept for consistency with categoryId/categoryIds and in
  // case a future single-value caller appears. Use getEffectiveTagIds() when building a WHERE
  // clause so both representations are honored.
  private List<Long> tagIds = null;
  private String uniqueId = null;
  private String name = null;
  private String barcode = null;
  private long nearItemId = -1L;
  private double latitude = 0;
  private double longitude = 0;
  private int withinMeters = -1;
  private String matchesName = null;
  private String searchName = null;
  private String searchLocation = null;
  private Long forUserId = -1L;
  private Long forMemberWithUserId = -1L;
  private int hasCoordinates = DataConstants.UNDEFINED;
  private boolean approvedOnly = false;
  private boolean unapprovedOnly = false;
  // Issue #814: items have no separate active/enabled flag -- archivedBy/archived (set by
  // PageServlet's deactivateCollectionItem action) is how an item is soft-hidden. Unlike the
  // opt-out-per-caller convention used for WebPageSpecification.enabled/draft,
  // MedicineSpecification.archivedOnly, and FormDataSpecification.dismissed (all default to no
  // filtering, relying on each caller to opt out), this defaults to excluding archived items: no
  // current caller of ItemRepository's query path ever wants to browse/list archived items, so an
  // opt-out-per-caller default would just leave the door open for the next listing call site to
  // reintroduce this same bug. The few call sites that do need to reach an archived item by a
  // known id/uniqueId (single-item access checks backing edit/detail pages, and dataset cleanup
  // that must remove archived items too) opt IN explicitly via setIncludeArchived(true).
  private boolean includeArchived = false;
  private long datasetId = -1L;
  private Timestamp datasetSyncTimestampThreshold = null;
  private Timestamp dateRangeStart = null;
  private Timestamp dateRangeEnd = null;

  public ItemSpecification() {
  }

  public ItemSpecification(long id) {
    this.id = id;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getExcludeId() {
    return excludeId;
  }

  public void setExcludeId(long excludeId) {
    this.excludeId = excludeId;
  }

  public long getCollectionId() {
    return collectionId;
  }

  public void setCollectionId(long collectionId) {
    this.collectionId = collectionId;
  }

  public long getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(long categoryId) {
    this.categoryId = categoryId;
  }

  public List<Long> getCategoryIds() {
    return categoryIds;
  }

  public void setCategoryIds(List<Long> categoryIds) {
    this.categoryIds = categoryIds;
  }

  /**
   * The effective set of category ids a WHERE-clause builder should filter on (issue #636): the
   * multi-select {@code categoryIds} list when it's set and non-empty, otherwise the single legacy
   * {@code categoryId} (issue #421) when it's set, otherwise empty. This is the one method
   * ItemRepository's search WHERE clause and facet-count methods consult, so a caller that only
   * ever used the single-value setCategoryId() -- every caller that existed before #636 -- keeps
   * working unchanged.
   */
  public List<Long> getEffectiveCategoryIds() {
    if (categoryIds != null && !categoryIds.isEmpty()) {
      return categoryIds;
    }
    if (categoryId > -1) {
      return Collections.singletonList(categoryId);
    }
    return Collections.emptyList();
  }

  public long getTagId() {
    return tagId;
  }

  public void setTagId(long tagId) {
    this.tagId = tagId;
  }

  public List<Long> getTagIds() {
    return tagIds;
  }

  public void setTagIds(List<Long> tagIds) {
    this.tagIds = tagIds;
  }

  /**
   * The effective set of tag ids a WHERE-clause builder should filter on (issue #632), mirroring
   * getEffectiveCategoryIds() exactly: the multi-select {@code tagIds} list when it's set and
   * non-empty, otherwise the single {@code tagId} when it's set, otherwise empty.
   */
  public List<Long> getEffectiveTagIds() {
    if (tagIds != null && !tagIds.isEmpty()) {
      return tagIds;
    }
    if (tagId > -1) {
      return Collections.singletonList(tagId);
    }
    return Collections.emptyList();
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public void setUniqueId(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getBarcode() {
    return barcode;
  }

  public void setBarcode(String barcode) {
    this.barcode = barcode;
  }

  public long getNearItemId() {
    return nearItemId;
  }

  public void setNearItemId(long nearItemId) {
    this.nearItemId = nearItemId;
  }

  public double getLatitude() {
    return latitude;
  }

  public void setLatitude(double latitude) {
    this.latitude = latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public void setLongitude(double longitude) {
    this.longitude = longitude;
  }

  public boolean hasGeoPoint() {
    return (latitude != 0 && longitude != 0);
  }

  public int getWithinMeters() {
    return withinMeters;
  }

  public void setWithinMeters(int withinMeters) {
    this.withinMeters = withinMeters;
  }

  public String getMatchesName() {
    return matchesName;
  }

  public void setMatchesName(String matchesName) {
    this.matchesName = matchesName;
  }

  public String getSearchName() {
    return searchName;
  }

  public void setSearchName(String searchName) {
    this.searchName = searchName;
  }

  public String getSearchLocation() {
    return searchLocation;
  }

  public void setSearchLocation(String searchLocation) {
    this.searchLocation = searchLocation;
  }

  public Long getForUserId() {
    return forUserId;
  }

  public void setForUserId(Long forUserId) {
    this.forUserId = forUserId;
  }

  public Long getForMemberWithUserId() {
    return forMemberWithUserId;
  }

  public void setForMemberWithUserId(Long forMemberWithUserId) {
    this.forMemberWithUserId = forMemberWithUserId;
  }

  public boolean getApprovedOnly() {
    return approvedOnly;
  }

  public void setApprovedOnly(boolean approvedOnly) {
    this.approvedOnly = approvedOnly;
  }

  public boolean getUnapprovedOnly() {
    return unapprovedOnly;
  }

  public void setUnapprovedOnly(boolean unapprovedOnly) {
    this.unapprovedOnly = unapprovedOnly;
  }

  public boolean getIncludeArchived() {
    return includeArchived;
  }

  public void setIncludeArchived(boolean includeArchived) {
    this.includeArchived = includeArchived;
  }

  public int getHasCoordinates() {
    return hasCoordinates;
  }

  public void setHasCoordinates(int hasCoordinates) {
    this.hasCoordinates = hasCoordinates;
  }

  public void setHasCoordinates(boolean hasCoordinates) {
    this.hasCoordinates = (hasCoordinates ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public long getDatasetId() {
    return datasetId;
  }

  public void setDatasetId(long datasetId) {
    this.datasetId = datasetId;
  }

  public Timestamp getDatasetSyncTimestampThreshold() {
    return datasetSyncTimestampThreshold;
  }

  public void setDatasetSyncTimestampThreshold(Timestamp datasetSyncTimestampThreshold) {
    this.datasetSyncTimestampThreshold = datasetSyncTimestampThreshold;
  }

  public Timestamp getDateRangeStart() {
    return dateRangeStart;
  }

  public void setDateRangeStart(Timestamp dateRangeStart) {
    this.dateRangeStart = dateRangeStart;
  }

  public Timestamp getDateRangeEnd() {
    return dateRangeEnd;
  }

  public void setDateRangeEnd(Timestamp dateRangeEnd) {
    this.dateRangeEnd = dateRangeEnd;
  }

}
