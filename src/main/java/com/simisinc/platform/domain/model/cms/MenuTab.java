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

package com.simisinc.platform.domain.model.cms;

import com.simisinc.platform.domain.model.Entity;

import java.util.List;

/**
 * The top-level tab which may or may not contain menu items
 *
 * @author matt rajkowski
 * @created 4/30/18 3:44 PM
 */
public class MenuTab extends Entity {

  private Long id = -1L;

  private Integer tabOrder = 100;
  private String name = null;
  private String link = null;
  private String pageTitle = null;
  private String pageKeywords = null;
  private String pageDescription = null;
  private boolean draft = false;
  private boolean enabled = false;
  private String[] roleIdList = null;
  private String comments = null;

  private List<MenuItem> menuItemList = null;

  public MenuTab() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Integer getTabOrder() {
    return tabOrder;
  }

  public void setTabOrder(Integer tabOrder) {
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

  public boolean isDraft() {
    return draft;
  }

  public void setDraft(boolean draft) {
    this.draft = draft;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String[] getRoleIdList() {
    return roleIdList;
  }

  public void setRoleIdList(String[] roleIdList) {
    this.roleIdList = roleIdList;
  }

  public String getComments() {
    return comments;
  }

  public void setComments(String comments) {
    this.comments = comments;
  }

  public List<MenuItem> getMenuItemList() {
    return menuItemList;
  }

  public void setMenuItemList(List<MenuItem> menuItemList) {
    this.menuItemList = menuItemList;
  }
}
