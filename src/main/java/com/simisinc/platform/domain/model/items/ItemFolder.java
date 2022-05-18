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
import java.util.Arrays;
import java.util.List;

/**
 * An organization of an item's files (Web, Videos, Employees, etc.)
 *
 * @author matt rajkowski
 * @created 4/19/2021 1:00 PM
 */
public class ItemFolder extends Entity {

  private Long id = -1L;
  private long itemId = -1L;

  private String uniqueId = null;
  private String name = null;
  private String summary = null;
  private long createdBy = -1;
  private long modifiedBy = -1;
  private Timestamp created = null;
  private Timestamp modified = null;
  private boolean enabled = false;
  private int fileCount = 0;
  private boolean allowsGuests = false;
  private int guestPrivacyType = PrivacyType.UNDEFINED;
  private boolean hasAllowedGroups = false;
  private List<ItemFolderGroup> folderGroupList = null;
  private String[] privacyTypes = null;
  private boolean hasCategories = false;
  private List<ItemFolderCategory> folderCategoryList = null;

  public ItemFolder() {
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

  public boolean getEnabled() {
    return enabled;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getFileCount() {
    return fileCount;
  }

  public void setFileCount(int fileCount) {
    this.fileCount = fileCount;
  }

  public String[] getPrivacyTypes() {
    return privacyTypes;
  }

  public void setPrivacyTypes(String[] privacyTypes) {
    this.privacyTypes = privacyTypes;
  }

  public List<String> getPrivacyTypesList() {
    if (privacyTypes == null) {
      return null;
    }
    return Arrays.asList(privacyTypes);
  }

  public boolean getAllowsGuests() {
    return allowsGuests;
  }

  public void setAllowsGuests(boolean allowsGuests) {
    this.allowsGuests = allowsGuests;
  }

  public int getGuestPrivacyType() {
    return guestPrivacyType;
  }

  public void setGuestPrivacyType(int guestPrivacyType) {
    this.guestPrivacyType = guestPrivacyType;
  }

  public boolean doAllowedGroupsCheck() {
    return hasAllowedGroups || (folderGroupList != null && !folderGroupList.isEmpty());
  }

  public void setHasAllowedGroups(boolean hasAllowedGroups) {
    this.hasAllowedGroups = hasAllowedGroups;
  }

  public List<ItemFolderGroup> getFolderGroupList() {
    return folderGroupList;
  }

  public void setFolderGroupList(List<ItemFolderGroup> folderGroupList) {
    this.folderGroupList = folderGroupList;
  }

  public ItemFolderGroup getFolderGroup(Long groupId) {
    if (folderGroupList == null) {
      return null;
    }
    for (ItemFolderGroup folderGroup : folderGroupList) {
      if (folderGroup.getGroupId() == groupId) {
        return folderGroup;
      }
    }
    return null;
  }

  public boolean doCategoriesCheck() {
    return hasCategories || (folderCategoryList != null && !folderCategoryList.isEmpty());
  }

  public void setHasCategories(boolean hasCategories) {
    this.hasCategories = hasCategories;
  }

  public List<ItemFolderCategory> getFolderCategoryList() {
    return folderCategoryList;
  }

  public void setFolderCategoryList(List<ItemFolderCategory> folderCategoryList) {
    this.folderCategoryList = folderCategoryList;
  }
}
