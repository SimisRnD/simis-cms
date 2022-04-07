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
 * Mailing Lists
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
}
