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

import java.sql.Timestamp;

/**
 * Mailing list member and status
 *
 * @author matt rajkowski
 * @created 3/24/19 8:45 PM
 */
public class MailingListMember extends Entity {

  private Long id = -1L;

  private long listId = -1;
  private long emailId = -1;
  private long createdBy = -1;
  private long modifiedBy = -1;
  private Timestamp created = null;
  private Timestamp modified = null;
  private Timestamp lastEmailed = null;
  private Timestamp unsubscribed = null;
  private long unsubscribedBy = -1;
  private String unsubscribeReason = null;
  private boolean isValid = false;
  private Timestamp quarantined = null;
  private String quarantineReason = null;
  private String unsubscribeToken = null;
  /** When the address owner clicked the confirm-subscription link (double opt-in). NULL means
   *  still pending, or this membership bypassed confirmation entirely (CSV import, admin
   *  manual-add). */
  private Timestamp confirmed = null;
  private String confirmToken = null;
  private Timestamp confirmTokenExpires = null;

  /** Only populated by queries that join to emails -- not a mailing_list_members column. */
  private String emailAddress = null;
  private String firstName = null;
  private String lastName = null;
  private String organization = null;
  private String ipAddress = null;
  /** The vendor's deliverability classification for this email address (e.g. ZeroBounce); see
   *  emails.validation_status. A property of the address itself, not of this one membership --
   *  quarantineReason above is the list-membership-specific consequence of a bad classification. */
  private String validationStatus = null;

  public MailingListMember() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getListId() {
    return listId;
  }

  public void setListId(long listId) {
    this.listId = listId;
  }

  public long getEmailId() {
    return emailId;
  }

  public void setEmailId(long emailId) {
    this.emailId = emailId;
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

  public Timestamp getUnsubscribed() {
    return unsubscribed;
  }

  public void setUnsubscribed(Timestamp unsubscribed) {
    this.unsubscribed = unsubscribed;
  }

  public long getUnsubscribedBy() {
    return unsubscribedBy;
  }

  public void setUnsubscribedBy(long unsubscribedBy) {
    this.unsubscribedBy = unsubscribedBy;
  }

  public String getUnsubscribeReason() {
    return unsubscribeReason;
  }

  public void setUnsubscribeReason(String unsubscribeReason) {
    this.unsubscribeReason = unsubscribeReason;
  }

  public boolean getIsValid() {
    return isValid;
  }

  public void setIsValid(boolean valid) {
    isValid = valid;
  }

  public Timestamp getQuarantined() {
    return quarantined;
  }

  public void setQuarantined(Timestamp quarantined) {
    this.quarantined = quarantined;
  }

  public String getQuarantineReason() {
    return quarantineReason;
  }

  public void setQuarantineReason(String quarantineReason) {
    this.quarantineReason = quarantineReason;
  }

  public String getUnsubscribeToken() {
    return unsubscribeToken;
  }

  public void setUnsubscribeToken(String unsubscribeToken) {
    this.unsubscribeToken = unsubscribeToken;
  }

  public Timestamp getConfirmed() {
    return confirmed;
  }

  public void setConfirmed(Timestamp confirmed) {
    this.confirmed = confirmed;
  }

  public String getConfirmToken() {
    return confirmToken;
  }

  public void setConfirmToken(String confirmToken) {
    this.confirmToken = confirmToken;
  }

  public Timestamp getConfirmTokenExpires() {
    return confirmTokenExpires;
  }

  public void setConfirmTokenExpires(Timestamp confirmTokenExpires) {
    this.confirmTokenExpires = confirmTokenExpires;
  }

  public String getEmailAddress() {
    return emailAddress;
  }

  public void setEmailAddress(String emailAddress) {
    this.emailAddress = emailAddress;
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

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getValidationStatus() {
    return validationStatus;
  }

  public void setValidationStatus(String validationStatus) {
    this.validationStatus = validationStatus;
  }
}
