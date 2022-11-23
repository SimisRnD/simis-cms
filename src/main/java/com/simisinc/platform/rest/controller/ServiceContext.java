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

package com.simisinc.platform.rest.controller;

import com.simisinc.platform.domain.model.App;
import com.simisinc.platform.domain.model.User;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Serializable;
import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/17/18 2:30 PM
 */
public class ServiceContext implements Serializable {

  final static long serialVersionUID = 215434482513634196L;

  private HttpServletRequest request = null;
  private HttpServletResponse response = null;
  private String pathParam = null;
  private String pathParam2 = null;
  private Map<String, String[]> parameterMap = null;

  private App app = null;
  private User user = null;

  public ServiceContext() {
  }

  public ServiceContext(HttpServletRequest request, HttpServletResponse response) {
    this.request = request;
    this.response = response;
  }

  public HttpServletRequest getRequest() {
    return request;
  }

  public HttpServletResponse getResponse() {
    return response;
  }

  public String getPathParam() {
    return pathParam;
  }

  public void setPathParam(String pathParam) {
    this.pathParam = pathParam;
  }

  public Map<String, String[]> getParameterMap() {
    return parameterMap;
  }

  public void setParameterMap(Map<String, String[]> parameterMap) {
    this.parameterMap = parameterMap;
  }

  public String getUrl() {
    String scheme = request.getScheme();
    String serverName = request.getServerName();
    int port = request.getServerPort();
    return scheme + "://" +
        serverName +
        (port != 80 && port != 443 ? ":" + port : "") + getContextPath();
  }

  public String getUri() {
    return request.getRequestURI();
  }

  public String getContextPath() {
    return request.getServletContext().getContextPath();
  }

  public long getUserId() {
    if (user != null) {
      return user.getId();
    }
    return -1;
  }

  public App getApp() {
    return app;
  }

  public void setApp(App app) {
    this.app = app;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public boolean hasRole(String role) {
    if (getUserId() == -1) {
      return false;
    }
    return user.hasRole(role);
  }

  public String getParameter(String name) {
    String[] values = parameterMap.get(name);
    if (values != null) {
      return values[0];
    }
    return null;
  }

  public long getParameterAsLong(String name) {
    return getParameterAsLong(name, -1L);
  }

  public long getParameterAsLong(String name, long defaultValue) {
    String value = getParameter(name);
    if (StringUtils.isNumeric(value)) {
      return Long.parseLong(value);
    }
    return defaultValue;
  }

  public int getParameterAsInt(String name) {
    return getParameterAsInt(name, -1);
  }

  public int getParameterAsInt(String name, int defaultValue) {
    String value = getParameter(name);
    if (StringUtils.isNumeric(value)) {
      return Integer.parseInt(value);
    }
    return defaultValue;
  }

  public String getPathParam2() {
    return pathParam2;
  }

  public void setPathParam2(String pathParam2) {
    this.pathParam2 = pathParam2;
  }

}
