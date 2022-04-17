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

package com.simisinc.platform.presentation.controller.cms;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/6/18 2:15 PM
 */
public class Page {

  private String name;
  private String title;
  private String keywords;
  private String description;
  private String collectionUniqueId;
  private String itemUniqueId;
  private String cssClass = null;

  private List<Section> sections = new ArrayList<Section>();
  private List<String> roles = new ArrayList<String>();
  private List<String> groups = new ArrayList<String>();

  public Page() {
  }

  public Page(String name, String collectionUniqueId, String itemUniqueId) {
    this.name = name;
    this.collectionUniqueId = collectionUniqueId;
    this.itemUniqueId = itemUniqueId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getKeywords() {
    return keywords;
  }

  public void setKeywords(String keywords) {
    this.keywords = keywords;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public List<Section> getSections() {
    return sections;
  }

  public void setSections(List<Section> sections) {
    this.sections = sections;
  }

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(List<String> roles) {
    this.roles = roles;
  }

  public List<String> getGroups() {
    return groups;
  }

  public void setGroups(List<String> groups) {
    this.groups = groups;
  }

  public String getCollectionUniqueId() {
    return collectionUniqueId;
  }

  public void setCollectionUniqueId(String collectionUniqueId) {
    this.collectionUniqueId = collectionUniqueId;
  }

  public boolean checkForCollectionUniqueId() {
    return !StringUtils.isEmpty(collectionUniqueId);
  }

  public String getItemUniqueId() {
    return itemUniqueId;
  }

  public void setItemUniqueId(String itemUniqueId) {
    this.itemUniqueId = itemUniqueId;
  }

  public boolean checkForItemUniqueId() {
    return !StringUtils.isEmpty(itemUniqueId);
  }

  public String getCssClass() {
    return cssClass;
  }

  public void setCssClass(String cssClass) {
    this.cssClass = cssClass;
  }
}
