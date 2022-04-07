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
public class SectionRenderInfo implements Serializable {

  static final long serialVersionUID = -8484048371911908893L;

  private List<ColumnRenderInfo> columns = new ArrayList<ColumnRenderInfo>();
  private boolean hasWidgets = false;

  // Output properties
  private String htmlId = null;
  private String cssClass = null;
  private String cssStyle = null;
  private boolean hr = false;
  private String videoBackgroundUrl = null;

  public SectionRenderInfo() {
  }

  public SectionRenderInfo(Section section) {
    this.htmlId = section.getHtmlId();
    this.cssClass = section.getCssClass();
    this.cssStyle = section.getCssStyle();
    this.hr = section.hasHr();
    this.videoBackgroundUrl = section.getVideoBackgroundUrl();
  }

  public List<ColumnRenderInfo> getColumnRenderInfoList() {
    return columns;
  }

  public boolean hasWidgets() {
    return hasWidgets;
  }

  public void setHasWidgets(boolean hasWidgets) {
    this.hasWidgets = hasWidgets;
  }

  public void addColumn(ColumnRenderInfo columnRenderInfo) {
    columns.add(columnRenderInfo);
  }

  public String getHtmlId() {
    return htmlId;
  }

  public String getCssClass() {
    return cssClass;
  }

  public String getCssStyle() {
    return cssStyle;
  }

  public boolean getHr() {
    return hr;
  }

  public String getVideoBackgroundUrl() {
    return videoBackgroundUrl;
  }
}
