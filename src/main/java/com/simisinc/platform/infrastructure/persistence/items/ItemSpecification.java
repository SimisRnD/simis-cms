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
  private long datasetId = -1L;
  private Timestamp datasetSyncTimestampThreshold = null;

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

}
