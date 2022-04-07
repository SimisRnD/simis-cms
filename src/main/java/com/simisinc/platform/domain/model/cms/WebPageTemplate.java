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
 * A snapshot, with optional variables, for creating new web pages
 *
 * @author matt rajkowski
 * @created 5/7/18 7:33 AM
 */
public class WebPageTemplate extends Entity {

  private Long id = -1L;
  private Long uniqueId = null;
  private Integer templateOrder = 10;
  private String name = null;
  private String category = null;
  private String description = null;
  private String imagePath = null;
  private String pageXml = null;
  private String css = null;
//  private List<WebPageTemplateRule> ruleList = null;

  public WebPageTemplate() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUniqueId() {
    return uniqueId;
  }

  public void setUniqueId(Long uniqueId) {
    this.uniqueId = uniqueId;
  }

  public Integer getTemplateOrder() {
    return templateOrder;
  }

  public void setTemplateOrder(Integer templateOrder) {
    this.templateOrder = templateOrder;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getImagePath() {
    return imagePath;
  }

  public void setImagePath(String imagePath) {
    this.imagePath = imagePath;
  }

  public String getPageXml() {
    return pageXml;
  }

  public void setPageXml(String pageXml) {
    this.pageXml = pageXml;
  }

  public String getCss() {
    return css;
  }

  public void setCss(String css) {
    this.css = css;
  }
}
