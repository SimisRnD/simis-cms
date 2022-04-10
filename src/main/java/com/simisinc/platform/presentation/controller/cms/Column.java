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
 * @created 4/6/18 2:20 PM
 */
public class Column implements Serializable {

  static final long serialVersionUID = -8484048371911908893L;

  // Layout and render properties
  private List<Widget> widgets = new ArrayList<Widget>();
  private List<String> roles = new ArrayList<String>();
  private List<String> groups = new ArrayList<String>();

  // Output properties
  private String htmlId = null;
  private String cssClass = null;
  private String cssStyle = null;
  private boolean sticky = false;
  private boolean hr = false;

  public Column() {
  }

  public String getHtmlId() {
    return htmlId;
  }

  public void setHtmlId(String htmlId) {
    this.htmlId = htmlId;
  }

  public String getCssClass() {
    return cssClass;
  }

  public void setCssClass(String cssClass) {
    this.cssClass = cssClass;
  }

  public String getCssStyle() {
    return cssStyle;
  }

  public void setCssStyle(String cssStyle) {
    this.cssStyle = cssStyle;
  }

  public boolean isSticky() {
    return sticky;
  }

  public void setSticky(boolean sticky) {
    this.sticky = sticky;
  }

  public boolean hasHr() {
    return hr;
  }

  public void setHr(boolean hr) {
    this.hr = hr;
  }

  public List<Widget> getWidgets() {
    return widgets;
  }

  public void setWidgets(List<Widget> widgets) {
    this.widgets = widgets;
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
}
