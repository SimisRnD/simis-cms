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

/**
 * The available UI tabs for a collection's items, including access and page elements
 *
 * @author matt rajkowski
 * @created 4/13/21 12:00 PM
 */
public class CollectionTab extends Entity {

  private long id = -1L;

  private long collectionId = -1L;
  private int tabOrder = 0;
  private String name = null;
  private String link = null;
  private String pageTitle = null;
  private String pageKeywords = null;
  private String pageDescription = null;
  private String pageImageUrl = null;
  private boolean draft = true;
  private boolean enabled = true;
  private String pageXml = null;
  private String roleIdList = null;

  public CollectionTab() {
  }

  public CollectionTab(Collection collection) {
    collectionId = collection.getId();
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

  public int getTabOrder() {
    return tabOrder;
  }

  public void setTabOrder(int tabOrder) {
    this.tabOrder = tabOrder;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getLink() {
    return link;
  }

  public void setLink(String link) {
    this.link = link;
  }

  public String getPageTitle() {
    return pageTitle;
  }

  public void setPageTitle(String pageTitle) {
    this.pageTitle = pageTitle;
  }

  public String getPageKeywords() {
    return pageKeywords;
  }

  public void setPageKeywords(String pageKeywords) {
    this.pageKeywords = pageKeywords;
  }

  public String getPageDescription() {
    return pageDescription;
  }

  public void setPageDescription(String pageDescription) {
    this.pageDescription = pageDescription;
  }

  public String getPageImageUrl() {
    return pageImageUrl;
  }

  public void setPageImageUrl(String pageImageUrl) {
    this.pageImageUrl = pageImageUrl;
  }

  public boolean isDraft() {
    return draft;
  }

  public boolean getDraft() {
    return draft;
  }

  public void setDraft(boolean draft) {
    this.draft = draft;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getPageXml() {
    return pageXml;
  }

  public void setPageXml(String pageXml) {
    this.pageXml = pageXml;
  }

  public String getRoleIdList() {
    return roleIdList;
  }

  public void setRoleIdList(String roleIdList) {
    this.roleIdList = roleIdList;
  }

}
