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

package com.simisinc.platform.domain.model.items;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.CustomFieldCommand;
import com.simisinc.platform.application.items.ItemAddressCommand;
import com.simisinc.platform.domain.model.CustomField;
import com.simisinc.platform.domain.model.Entity;

/**
 * A specific object within a collection used as a basis for information, sharing, and collaboration
 *
 * @author matt rajkowski
 * @created 4/18/18 9:37 PM
 */
public class Item extends Entity {
  protected static Log LOG = LogFactory.getLog(Item.class);

  private Long id = -1L;

  private long datasetId = -1L;
  private long collectionId = -1L;
  private long categoryId = -1L;
  private Long[] categoryIdList = null;
  private String uniqueId = null;
  private String name = null;
  private String summary = null;
  private String description = null;
  private long createdBy = -1;
  private Timestamp created = null;
  private long modifiedBy = -1;
  private Timestamp modified = null;
  private long archivedBy = -1;
  private Timestamp archived = null;
  private long approvedBy = -1;
  private Timestamp approved = null;
  private double latitude = 0.0;
  private double longitude = 0.0;
  private String location = null;
  private String street = null;
  private String addressLine2 = null;
  private String addressLine3 = null;
  private String city = null;
  private String state = null;
  private String country = null;
  private String postalCode = null;
  private String county = null;
  private String phoneNumber = null;
  private String email = null;
  private BigDecimal cost = null;
  private Timestamp expectedDate = null;
  private Timestamp startDate = null;
  private Timestamp endDate = null;
  private Timestamp expirationDate = null;
  private String url = null;
  private String imageUrl = null;
  private String barcode = null;
  private String keywords = null;
  private long assignedTo = -1;
  private Timestamp assigned = null;
  private Map<String, CustomField> customFieldList = null;
  private String source = null;
  private String ipAddress = null;
  private String highlight = null;

  public Item() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getDatasetId() {
    return datasetId;
  }

  public void setDatasetId(long datasetId) {
    this.datasetId = datasetId;
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

  public Long[] getCategoryIdList() {
    return categoryIdList;
  }

  public void setCategoryIdList(Long[] categoryIdList) {
    this.categoryIdList = categoryIdList;
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

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(long createdBy) {
    this.createdBy = createdBy;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }

  public long getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(long modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  public Timestamp getModified() {
    return modified;
  }

  public void setModified(Timestamp modified) {
    this.modified = modified;
  }

  public long getArchivedBy() {
    return archivedBy;
  }

  public void setArchivedBy(long archivedBy) {
    this.archivedBy = archivedBy;
  }

  public Timestamp getArchived() {
    return archived;
  }

  public void setArchived(Timestamp archived) {
    this.archived = archived;
  }

  public long getApprovedBy() {
    return approvedBy;
  }

  public void setApprovedBy(long approvedBy) {
    this.approvedBy = approvedBy;
  }

  public Timestamp getApproved() {
    return approved;
  }

  public void setApproved(Timestamp approved) {
    this.approved = approved;
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

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getStreet() {
    return street;
  }

  public void setStreet(String street) {
    this.street = street;
  }

  public String getAddressLine2() {
    return addressLine2;
  }

  public void setAddressLine2(String addressLine2) {
    this.addressLine2 = addressLine2;
  }

  public String getAddressLine3() {
    return addressLine3;
  }

  public void setAddressLine3(String addressLine3) {
    this.addressLine3 = addressLine3;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public void setPostalCode(String postalCode) {
    this.postalCode = postalCode;
  }

  public String getCounty() {
    return county;
  }

  public void setCounty(String county) {
    this.county = county;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public BigDecimal getCost() {
    return cost;
  }

  public void setCost(BigDecimal cost) {
    this.cost = cost;
  }

  public Timestamp getExpectedDate() {
    return expectedDate;
  }

  public void setExpectedDate(Timestamp expectedDate) {
    this.expectedDate = expectedDate;
  }

  public Timestamp getStartDate() {
    return startDate;
  }

  public void setStartDate(Timestamp startDate) {
    this.startDate = startDate;
  }

  public Timestamp getEndDate() {
    return endDate;
  }

  public void setEndDate(Timestamp endDate) {
    this.endDate = endDate;
  }

  public Timestamp getExpirationDate() {
    return expirationDate;
  }

  public void setExpirationDate(Timestamp expirationDate) {
    this.expirationDate = expirationDate;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public String getBarcode() {
    return barcode;
  }

  public void setBarcode(String barcode) {
    this.barcode = barcode;
  }

  public String getKeywords() {
    return keywords;
  }

  public void setKeywords(String keywords) {
    this.keywords = keywords;
  }

  public long getAssignedTo() {
    return assignedTo;
  }

  public void setAssignedTo(long assignedTo) {
    this.assignedTo = assignedTo;
  }

  public Timestamp getAssigned() {
    return assigned;
  }

  public void setAssigned(Timestamp assigned) {
    this.assigned = assigned;
  }

  public boolean hasGeoPoint() {
    return (latitude != 0 && longitude != 0);
  }

  public boolean isGeocoded() {
    return hasGeoPoint();
  }

  public String getAddress() {
    return ItemAddressCommand.toText(this);
  }

  public Map<String, CustomField> getCustomFieldList() {
    return customFieldList;
  }

  public void setCustomFieldList(Map<String, CustomField> customFieldList) {
    this.customFieldList = customFieldList;
  }

  public void addCustomField(CustomField customField) {
    if (customFieldList == null) {
      customFieldList = new HashMap<>();
    }
    CustomFieldCommand.addCustomFieldToList(customFieldList, customField);
  }

  public CustomField getCustomField(String name) {
    return CustomFieldCommand.getCustomField(customFieldList, name);
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getHighlight() {
    return highlight;
  }

  public void setHighlight(String highlight) {
    this.highlight = highlight;
  }
}
