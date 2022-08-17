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

import com.simisinc.platform.application.CustomFieldCommand;
import com.simisinc.platform.domain.model.CustomField;
import com.simisinc.platform.domain.model.Entity;
import org.apache.commons.lang3.StringUtils;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A common category or directory for organizing items with customization and permissions
 *
 * @author matt rajkowski
 * @created 4/18/18 9:18 PM
 */
public class Collection extends Entity {

  private Long id = -1L;

  private String name = null;
  private String uniqueId = null;
  private String description = null;
  private long createdBy = -1;
  private Timestamp created = null;
  private Timestamp modified = null;
  private long categoryCount = 0;
  private long itemCount = 0;
  private boolean allowsGuests = false;
  private int guestPrivacyType = PrivacyType.UNDEFINED;
  private boolean hasAllowedGroups = false;
  private List<CollectionGroup> collectionGroupList = null;
  private String listingsLink = null;
  private String imageUrl = null;
  private String icon = null;
  private String headerXml = null;
  private String headerTextColor = null;
  private String headerBgColor = null;
  private String menuTextColor = null;
  private String menuBgColor = null;
  private String menuBorderColor = null;
  private String menuActiveTextColor = null;
  private String menuActiveBgColor = null;
  private String menuActiveBorderColor = null;
  private String menuHoverTextColor = null;
  private String menuHoverBgColor = null;
  private String menuHoverBorderColor = null;
  private boolean showSearch = false;
  private boolean showListingsLink = false;
  private Map<String, CustomField> customFieldList = null;
  private String itemUrlText = null;

  public Collection() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public void setUniqueId(String uniqueId) {
    this.uniqueId = uniqueId;
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

  public Timestamp getModified() {
    return modified;
  }

  public void setModified(Timestamp modified) {
    this.modified = modified;
  }

  public long getCategoryCount() {
    return categoryCount;
  }

  public void setCategoryCount(long categoryCount) {
    this.categoryCount = categoryCount;
  }

  public long getItemCount() {
    return itemCount;
  }

  public void setItemCount(long itemCount) {
    this.itemCount = itemCount;
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
    return hasAllowedGroups || (collectionGroupList != null && !collectionGroupList.isEmpty());
  }

  public void setHasAllowedGroups(boolean hasAllowedGroups) {
    this.hasAllowedGroups = hasAllowedGroups;
  }

  public List<CollectionGroup> getCollectionGroupList() {
    return collectionGroupList;
  }

  public void setCollectionGroupList(List<CollectionGroup> collectionGroupList) {
    this.collectionGroupList = collectionGroupList;
  }

  public CollectionGroup getCollectionGroup(Long groupId) {
    if (collectionGroupList == null) {
      return null;
    }
    for (CollectionGroup collectionGroup : collectionGroupList) {
      if (collectionGroup.getGroupId() == groupId) {
        return collectionGroup;
      }
    }
    return null;
  }

  public String getListingsLink() {
    return listingsLink;
  }

  public void setListingsLink(String listingsLink) {
    this.listingsLink = listingsLink;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  public String getHeaderXml() {
    return headerXml;
  }

  public void setHeaderXml(String headerXml) {
    this.headerXml = headerXml;
  }

  public String getHeaderTextColor() {
    return headerTextColor;
  }

  public void setHeaderTextColor(String headerTextColor) {
    this.headerTextColor = headerTextColor;
  }

  public String getHeaderBgColor() {
    return headerBgColor;
  }

  public void setHeaderBgColor(String headerBgColor) {
    this.headerBgColor = headerBgColor;
  }

  public String getMenuTextColor() {
    return menuTextColor;
  }

  public void setMenuTextColor(String menuTextColor) {
    this.menuTextColor = menuTextColor;
  }

  public String getMenuBgColor() {
    return menuBgColor;
  }

  public void setMenuBgColor(String menuBgColor) {
    this.menuBgColor = menuBgColor;
  }

  public String getMenuBorderColor() {
    return menuBorderColor;
  }

  public void setMenuBorderColor(String menuBorderColor) {
    this.menuBorderColor = menuBorderColor;
  }

  public String getMenuActiveTextColor() {
    return menuActiveTextColor;
  }

  public void setMenuActiveTextColor(String menuActiveTextColor) {
    this.menuActiveTextColor = menuActiveTextColor;
  }

  public String getMenuActiveBgColor() {
    return menuActiveBgColor;
  }

  public void setMenuActiveBgColor(String menuActiveBgColor) {
    this.menuActiveBgColor = menuActiveBgColor;
  }

  public String getMenuActiveBorderColor() {
    return menuActiveBorderColor;
  }

  public void setMenuActiveBorderColor(String menuActiveBorderColor) {
    this.menuActiveBorderColor = menuActiveBorderColor;
  }

  public String getMenuHoverTextColor() {
    return menuHoverTextColor;
  }

  public void setMenuHoverTextColor(String menuHoverTextColor) {
    this.menuHoverTextColor = menuHoverTextColor;
  }

  public String getMenuHoverBgColor() {
    return menuHoverBgColor;
  }

  public void setMenuHoverBgColor(String menuHoverBgColor) {
    this.menuHoverBgColor = menuHoverBgColor;
  }

  public String getMenuHoverBorderColor() {
    return menuHoverBorderColor;
  }

  public void setMenuHoverBorderColor(String menuHoverBorderColor) {
    this.menuHoverBorderColor = menuHoverBorderColor;
  }

  public boolean getShowListingsLink() {
    return showListingsLink;
  }

  public void setShowListingsLink(boolean showListingsLink) {
    this.showListingsLink = showListingsLink;
  }

  public boolean getShowSearch() {
    return showSearch;
  }

  public void setShowSearch(boolean showSearch) {
    this.showSearch = showSearch;
  }

  public String createListingsLink() {
    if (StringUtils.isNotBlank(this.getListingsLink())) {
      return this.getListingsLink();
    } else {
      return "/directory/" + this.getUniqueId();
    }
  }

  public Map<String, CustomField> getCustomFieldList() {
    return customFieldList;
  }

  public void setCustomFieldList(Map<String, CustomField> customFieldList) {
    this.customFieldList = customFieldList;
  }

  public void addCustomField(CustomField customField) {
    if (customFieldList == null) {
      customFieldList = new LinkedHashMap<String, CustomField>();
    }
    CustomFieldCommand.addCustomFieldToList(customFieldList, customField);
  }

  public CustomField getCustomField(String name) {
    return CustomFieldCommand.getCustomField(customFieldList, name);
  }

  public String getItemUrlText() {
    return itemUrlText;
  }

  public void setItemUrlText(String urlText) {
    this.itemUrlText = urlText;
  }

}
