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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/8/18 2:15 PM
 */
public class PageRenderInfo implements ContainerRenderInfo, Serializable {

  static final long serialVersionUID = -8484048371911908893L;

  private List<SectionRenderInfo> sections = new ArrayList<SectionRenderInfo>();
  private boolean hasWidgets = false;
  private String targetWidget = null;

  // Output properties
  private String name;
  private String title;
  private String keywords;
  private String description;
  private String imageUrl;
  private String pagePath;
  private String cssClass = null;

  public PageRenderInfo() {
  }

  public PageRenderInfo(Page page, String pagePath) {
    this.name = page.getName();
    this.title = page.getTitle();
    this.keywords = page.getKeywords();
    this.description = page.getDescription();
    this.cssClass = page.getCssClass();
    this.pagePath = pagePath;
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

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public boolean hasWidgets() {
    return hasWidgets;
  }

  public void setHasWidgets(boolean hasWidgets) {
    this.hasWidgets = hasWidgets;
  }

  public String getTargetWidget() {
    return targetWidget;
  }

  public void setTargetWidget(String targetWidget) {
    this.targetWidget = targetWidget;
  }

  public List<SectionRenderInfo> getSectionRenderInfoList() {
    return sections;
  }

  public void addSection(SectionRenderInfo sectionRenderInfo) {
    sections.add(sectionRenderInfo);
  }

  public String getPagePath() {
    return pagePath;
  }

  public void setPagePath(String pagePath) {
    this.pagePath = pagePath;
  }

  public String getCssClass() {
    return cssClass;
  }

}
