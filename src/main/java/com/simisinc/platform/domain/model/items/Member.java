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

import com.simisinc.platform.domain.model.Entity;

import java.sql.Timestamp;
import java.util.List;

/**
 * A user belonging to an item
 *
 * @author matt rajkowski
 * @created 4/18/18 9:43 PM
 */
public class Member extends Entity {

  private Long id = -1L;

  private long userId = -1;
  private long itemId = -1;
  private long collectionId = -1;
  //  private int memberRoleId = -1;
  private long createdBy = -1;
  private Timestamp created = null;
  private long modifiedBy = -1;
  private Timestamp modified = null;
  private Timestamp requested = null;
  private long approvedBy = -1;
  private Timestamp approved = null;
  private long archivedBy = -1;
  private Timestamp archived = null;
  private Timestamp lastViewed = null;

  private List<CollectionRole> roleList = null;

  public Member() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
  }

  public long getItemId() {
    return itemId;
  }

  public void setItemId(long itemId) {
    this.itemId = itemId;
  }

  public long getCollectionId() {
    return collectionId;
  }

  public void setCollectionId(long collectionId) {
    this.collectionId = collectionId;
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

  public Timestamp getRequested() {
    return requested;
  }

  public void setRequested(Timestamp requested) {
    this.requested = requested;
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

  public Timestamp getLastViewed() {
    return lastViewed;
  }

  public void setLastViewed(Timestamp lastViewed) {
    this.lastViewed = lastViewed;
  }

  public List<CollectionRole> getRoleList() {
    return roleList;
  }

  public void setRoleList(List<CollectionRole> roleList) {
    this.roleList = roleList;
  }

  public boolean hasRole(String code) {
    if (roleList == null) {
      return false;
    }
    for (CollectionRole role : roleList) {
      if (role.getCode().equals(code)) {
        return true;
      }
    }
    return false;
  }
}
