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

import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/17/21 10:30 AM
 */
public class Header {

  private String name;
  private String cssClass = null;

  private List<Section> sections = new ArrayList<Section>();
  private List<String> roles = new ArrayList<String>();

  public Header() {
  }

  public Header(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
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

  public String getCssClass() {
    return cssClass;
  }

  public void setCssClass(String cssClass) {
    this.cssClass = cssClass;
  }
}
