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
public class ColumnRenderInfo implements Serializable {

  static final long serialVersionUID = -8484048371911908893L;

  private List<WidgetRenderInfo> widgets = new ArrayList<WidgetRenderInfo>();
  private boolean hasWidgets = false;

  // Output properties
  private String htmlId = null;
  private String cssClass = null;
  private String cssStyle = null;
  private boolean sticky = false;
  private boolean hr = false;

  public ColumnRenderInfo() {
  }

  public ColumnRenderInfo(Column column) {
    this.htmlId = column.getHtmlId();
    this.cssClass = column.getCssClass();
    this.cssStyle = column.getCssStyle();
    this.sticky = column.isSticky();
    this.hr = column.hasHr();
  }

  public List<WidgetRenderInfo> getWidgetRenderInfoList() {
    return widgets;
  }

  public boolean hasWidgets() {
    return hasWidgets;
  }

  public void setHasWidgets(boolean hasWidgets) {
    this.hasWidgets = hasWidgets;
  }

  public void addWidget(WidgetRenderInfo widgetRenderInfo) {
    hasWidgets = true;
    widgets.add(widgetRenderInfo);
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

  public boolean isSticky() {
    return sticky;
  }

  public boolean getHr() { return hr; }
}
