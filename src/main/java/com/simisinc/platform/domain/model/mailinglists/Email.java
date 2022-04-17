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

package com.simisinc.platform.domain.model.mailinglists;

import com.simisinc.platform.domain.model.Entity;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

/**
 * Email record
 *
 * @author matt rajkowski
 * @created 3/24/19 8:50 PM
 */
public class Email extends Entity {

  private Long id = -1L;
  private String email = null;
  private String firstName = null;
  private String lastName = null;
  private String organization = null;
  private String source = null;
  private String ipAddress = null;
  private String sessionId = null;
  private String userAgent = null;
  private String referer = null;
  private String continent = null;
  private String countryIso = null;
  private String country = null;
  private String city = null;
  private String stateIso = null;
  private String state = null;
  private String postalCode = null;
  private String timezone = null;
  private double latitude = 0;
  private double longitude = 0;
  private int metroCode = -1;

  private long createdBy = -1;
  private long modifiedBy = -1;
  private Timestamp created = null;
  private Timestamp modified = null;
  private Timestamp lastEmailed = null;
  private Timestamp syncDate = null;

  private Timestamp subscribed = null;
  private Timestamp unsubscribed = null;
  private String unsubscribeReason = null;

  private Timestamp lastOrder = null;
  private int numberOfOrders = 0;
  private BigDecimal totalSpent = null;
  private boolean isValid = false;

  private List<String> tagList = null;

  public Email() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
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

  public String getOrganization() {
    return organization;
  }

  public void setOrganization(String organization) {
    this.organization = organization;
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

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public String getReferer() {
    return referer;
  }

  public void setReferer(String referer) {
    this.referer = referer;
  }

  public String getContinent() {
    return continent;
  }

  public void setContinent(String continent) {
    this.continent = continent;
  }

  public String getCountryIso() {
    return countryIso;
  }

  public void setCountryIso(String countryIso) {
    this.countryIso = countryIso;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getStateIso() {
    return stateIso;
  }

  public void setStateIso(String stateIso) {
    this.stateIso = stateIso;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public void setPostalCode(String postalCode) {
    this.postalCode = postalCode;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
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

  public int getMetroCode() {
    return metroCode;
  }

  public void setMetroCode(int metroCode) {
    this.metroCode = metroCode;
  }

  public long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(long createdBy) {
    this.createdBy = createdBy;
  }

  public long getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(long modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }

  public Timestamp getModified() {
    return modified;
  }

  public void setModified(Timestamp modified) {
    this.modified = modified;
  }

  public Timestamp getLastEmailed() {
    return lastEmailed;
  }

  public void setLastEmailed(Timestamp lastEmailed) {
    this.lastEmailed = lastEmailed;
  }

  public Timestamp getSyncDate() {
    return syncDate;
  }

  public void setSyncDate(Timestamp syncDate) {
    this.syncDate = syncDate;
  }

  public Timestamp getSubscribed() {
    return subscribed;
  }

  public void setSubscribed(Timestamp subscribed) {
    this.subscribed = subscribed;
  }

  public Timestamp getUnsubscribed() {
    return unsubscribed;
  }

  public void setUnsubscribed(Timestamp unsubscribed) {
    this.unsubscribed = unsubscribed;
  }

  public String getUnsubscribeReason() {
    return unsubscribeReason;
  }

  public void setUnsubscribeReason(String unsubscribeReason) {
    this.unsubscribeReason = unsubscribeReason;
  }

  public Timestamp getLastOrder() {
    return lastOrder;
  }

  public void setLastOrder(Timestamp lastOrder) {
    this.lastOrder = lastOrder;
  }

  public int getNumberOfOrders() {
    return numberOfOrders;
  }

  public void setNumberOfOrders(int numberOfOrders) {
    this.numberOfOrders = numberOfOrders;
  }

  public BigDecimal getTotalSpent() {
    return totalSpent;
  }

  public void setTotalSpent(BigDecimal totalSpent) {
    this.totalSpent = totalSpent;
  }

  public boolean isValid() {
    return isValid;
  }

  public void setValid(boolean valid) {
    isValid = valid;
  }

  public List<String> getTagList() {
    return tagList;
  }

  public void setTagList(List<String> tagList) {
    this.tagList = tagList;
  }

  public boolean hasGeoPoint() {
    return (latitude != 0 && longitude != 0);
  }
}
