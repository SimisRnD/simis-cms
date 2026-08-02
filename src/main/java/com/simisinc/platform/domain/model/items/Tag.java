/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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
 * Tags are attached to items and can be used for filtering items (issue #632). Unlike Category,
 * a tag has no icon/color/URL-text/uniqueId -- it's a plain label, and an item can carry any
 * number of them with no "primary" concept.
 *
 * @author SimIS
 * @created 8/2/2026
 */
public class Tag extends Entity {

  private Long id = -1L;

  private long collectionId = -1L;
  private String name = null;
  private long createdBy = -1;
  private Timestamp created = null;
  private Timestamp modified = null;
  private long itemCount = 0;

  public Tag() {
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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
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

  public Timestamp getModified() {
    return modified;
  }

  public void setModified(Timestamp modified) {
    this.modified = modified;
  }

  public long getItemCount() {
    return itemCount;
  }

  public void setItemCount(long itemCount) {
    this.itemCount = itemCount;
  }
}
