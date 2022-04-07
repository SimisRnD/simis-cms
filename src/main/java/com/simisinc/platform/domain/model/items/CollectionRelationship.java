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

/**
 * Represents how one collection is related to another collection
 *
 * @author matt rajkowski
 * @created 7/26/18 1:08 PM
 */
public class CollectionRelationship extends Entity {

  private Long id = -1L;

  private long collectionId = -1L;
  private long relatedCollectionId = -1L;
  private boolean isActive = true;
  private long createdBy = -1;
  private Timestamp created = null;
  private long modifiedBy = -1;
  private Timestamp modified = null;

  public CollectionRelationship() {
  }

  public CollectionRelationship(Collection collection, Collection relatedCollection) {
    collectionId = collection.getId();
    relatedCollectionId = relatedCollection.getId();
  }

  public CollectionRelationship(long collectionId, long relatedCollectionId) {
    this.collectionId = collectionId;
    this.relatedCollectionId = relatedCollectionId;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getCollectionId() {
    return collectionId;
  }

  public void setCollectionId(long collectionId) {
    this.collectionId = collectionId;
  }

  public long getRelatedCollectionId() {
    return relatedCollectionId;
  }

  public void setRelatedCollectionId(long relatedCollectionId) {
    this.relatedCollectionId = relatedCollectionId;
  }

  public boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(boolean active) {
    isActive = active;
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
}
