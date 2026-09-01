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

/**
 * An item within a drop-down menu
 *
 * @author matt rajkowski
 * @created 4/30/18 3:48 PM
 */
import java.util.List;

public class MenuItem extends Entity {

  private Long id = -1L;

  private Long menuTabId = -1L;
  private Integer itemOrder = 100;
  private String name = null;
  private String link = null;
  private String pageTitle = null;
  private String pageKeywords = null;
  private String pageDescription = null;
  private boolean draft = false;
  private boolean enabled = false;
  private String[] roleIdList = null;
  private String comments = null;
  /**
   * The item this one hangs under, or -1 when it sits directly beneath its tab (issue #1728).
   * Every item that existed before nesting is -1, so -1 is the unchanged, two-level behaviour.
   */
  private Long parentMenuItemId = -1L;
  /** Children of this item, populated only by the callers that render a third level. */
  private List<MenuItem> menuItemList = null;

  public MenuItem() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getMenuTabId() {
    return menuTabId;
  }

  public void setMenuTabId(Long menuTabId) {
    this.menuTabId = menuTabId;
  }

  public Integer getItemOrder() {
    return itemOrder;
  }

  public void setItemOrder(Integer itemOrder) {
    this.itemOrder = itemOrder;
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

  public Long getParentMenuItemId() {
    return parentMenuItemId;
  }

  public void setParentMenuItemId(Long parentMenuItemId) {
    this.parentMenuItemId = parentMenuItemId;
  }

  public List<MenuItem> getMenuItemList() {
    return menuItemList;
  }

  public void setMenuItemList(List<MenuItem> menuItemList) {
    this.menuItemList = menuItemList;
  }

  /** True when this item hangs under another item rather than directly under its tab. */
  public boolean hasParentMenuItem() {
    // > 0, not > -1: -1 is the unset marker, but 0 is not a valid id either -- no BIGSERIAL
    // issues it -- and treating 0 as a real parent would write a foreign key that cannot
    // resolve, producing an item nested under nothing.
    return parentMenuItemId != null && parentMenuItemId > 0;
  }

  /** True when a third level should be rendered beneath this item. */
  public boolean hasMenuItemList() {
    return menuItemList != null && !menuItemList.isEmpty();
  }
}
