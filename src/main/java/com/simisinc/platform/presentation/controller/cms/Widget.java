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

import com.simisinc.platform.presentation.controller.login.UserSession;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains the Widget meta-data
 *
 * @author matt rajkowski
 * @created 4/8/18 8:35 AM
 */
public class Widget implements Serializable {

  static final long serialVersionUID = -8484048371911908893L;

  // Layout and render properties
  protected String widgetName = "";
  protected String widgetClassName = "";
  private List<String> roles = new ArrayList<String>();
  protected Map<String, String> preferences = new HashMap<String, String>();

  // Output properties
  protected String htmlId = null;
  protected String cssClass = null;
  protected String cssStyle = null;
  protected boolean sticky = false;
  private boolean hr = false;

  public Widget() {
  }

  public Widget(String widgetName) {
    this.widgetName = widgetName;
  }

  public String getWidgetName() {
    return widgetName;
  }

  public void setWidgetName(String widgetName) {
    this.widgetName = widgetName;
  }

  public String getWidgetClassName() {
    return widgetClassName;
  }

  public void setWidgetClassName(String widgetClassName) {
    this.widgetClassName = widgetClassName;
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

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(List<String> roles) {
    this.roles = roles;
  }

  public boolean allowsUser(UserSession userSession) {
    if (roles.isEmpty()) {
      return true;
    }
    for (String role : roles) {
      if ("guest".equals(role) && !userSession.isLoggedIn()) {
        return true;
      }
      if ("users".equals(role) && userSession.isLoggedIn()) {
        return true;
      }
      if (userSession.hasRole(role)) {
        return true;
      }
    }
    return false;
  }

  public Map<String, String> getPreferences() {
    return preferences;
  }

  public void addPreference(String name, String value) {
    preferences.put(name, value);
  }
}
