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
import com.simisinc.platform.domain.model.Group;

/**
 * An item folder group used for configuring permissions
 *
 * @author matt rajkowski
 * @created 4/19/2021 1:00 PM
 */
public class ItemFolderGroup extends Entity {

  private Long id = -1L;

  private long itemId = -1L;
  private long folderId = -1L;
  private long groupId = -1L;
  private int privacyType = PrivacyType.UNDEFINED;
  private boolean addPermission = false;
  private boolean editPermission = false;
  private boolean deletePermission = false;

  public ItemFolderGroup() {
  }

  public ItemFolderGroup(Collection collection, Group group) {
    folderId = collection.getId();
    groupId = group.getId();
  }

  public ItemFolderGroup(long collectionId, long groupId) {
    this.folderId = collectionId;
    this.groupId = groupId;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getItemId() {
    return itemId;
  }

  public void setItemId(long itemId) {
    this.itemId = itemId;
  }

  public long getFolderId() {
    return folderId;
  }

  public void setFolderId(long folderId) {
    this.folderId = folderId;
  }

  public long getGroupId() {
    return groupId;
  }

  public void setGroupId(long groupId) {
    this.groupId = groupId;
  }

  public int getPrivacyType() {
    return privacyType;
  }

  public void setPrivacyType(int privacyType) {
    this.privacyType = privacyType;
  }

  public boolean getAddPermission() {
    return addPermission;
  }

  public void setAddPermission(boolean addPermission) {
    this.addPermission = addPermission;
  }

  public boolean getEditPermission() {
    return editPermission;
  }

  public void setEditPermission(boolean editPermission) {
    this.editPermission = editPermission;
  }

  public boolean getDeletePermission() {
    return deletePermission;
  }

  public void setDeletePermission(boolean deletePermission) {
    this.deletePermission = deletePermission;
  }
}
