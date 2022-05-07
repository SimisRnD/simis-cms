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

package com.simisinc.platform.presentation.controller;

import com.simisinc.platform.presentation.widgets.cms.PreferenceEntriesList;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/6/18 2:22 PM
 */
public class WidgetContext implements Serializable {

  final static long serialVersionUID = 215434482513634196L;

  private HttpServletRequest request = null;
  private HttpServletResponse response = null;
  private String uniqueId = null;
  private Map<String, String> preferences = null;
  private Map<String, String[]> parameterMap = null;
  private Map<String, String> coreData = null;

  private boolean maximized = false;
  private boolean embedded = false;

  private String pageTitle = null;
  private String pageDescription = null;
  private String pageKeywords = null;

  private String jsp = null;
  private String html = null;
  private String json = null;
  private String redirect = null;
  private boolean handledResponse = false;

  private String message = null;
  private String successMessage = null;
  private String warningMessage = null;
  private String errorMessage = null;
  private Object requestObject = null;
  private Map<String, String> sharedRequestValueMap = null;

  private UserSession userSession = null;

  public WidgetContext() {
  }

  public WidgetContext(HttpServletRequest request, HttpServletResponse response, String widgetUniqueId) {
    this.request = request;
    this.response = response;
    this.uniqueId = widgetUniqueId;
  }

  public HttpServletRequest getRequest() {
    return request;
  }

  public HttpServletResponse getResponse() {
    return response;
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public Map<String, String> getPreferences() {
    return preferences;
  }

  public void setPreferences(Map<String, String> preferences) {
    this.preferences = preferences;
  }

  public PreferenceEntriesList getPreferenceAsDataList(String preference) {
    String data = getPreferences().get(preference);
    if (StringUtils.isBlank(data)) {
      return new PreferenceEntriesList();
    }
    return new PreferenceEntriesList(data);
  }

  public Map<String, String[]> getParameterMap() {
    return parameterMap;
  }

  public void setParameterMap(Map<String, String[]> parameterMap) {
    this.parameterMap = parameterMap;
  }

  public Map<String, String> getCoreData() {
    return coreData;
  }

  public void setCoreData(Map<String, String> coreData) {
    this.coreData = coreData;
  }

  public boolean isMaximized() {
    return maximized;
  }

  public void setMaximized(boolean maximized) {
    this.maximized = maximized;
  }

  public boolean isEmbedded() {
    return embedded;
  }

  public void setEmbedded(boolean embedded) {
    this.embedded = embedded;
  }

  public String getPageTitle() {
    return pageTitle;
  }

  public void setPageTitle(String pageTitle) {
    this.pageTitle = pageTitle;
  }

  public String getPageDescription() {
    return pageDescription;
  }

  public void setPageDescription(String pageDescription) {
    this.pageDescription = pageDescription;
  }

  public String getPageKeywords() {
    return pageKeywords;
  }

  public void setPageKeywords(String pageKeywords) {
    this.pageKeywords = pageKeywords;
  }

  public String getJsp() {
    return jsp;
  }

  public void setJsp(String jsp) {
    this.jsp = jsp;
  }

  public boolean hasJsp() {
    return jsp != null;
  }

  public String getHtml() {
    return html;
  }

  public void setHtml(String html) {
    this.html = html;
  }

  public boolean hasHtml() {
    return html != null;
  }

  public String getJson() {
    return json;
  }

  public void setJson(String json) {
    this.json = json;
  }

  public boolean hasJson() {
    return json != null;
  }

  public String getRedirect() {
    return redirect;
  }

  public void setRedirect(String redirect) {
    this.redirect = redirect;
  }

  public boolean hasRedirect() {
    return redirect != null;
  }

  public boolean handledResponse() {
    return handledResponse;
  }

  public void setHandledResponse(boolean handledResponse) {
    this.handledResponse = handledResponse;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getSuccessMessage() {
    return successMessage;
  }

  public void setSuccessMessage(String successMessage) {
    this.successMessage = successMessage;
  }

  public String getWarningMessage() {
    return warningMessage;
  }

  public void setWarningMessage(String warningMessage) {
    this.warningMessage = warningMessage;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Object getRequestObject() {
    return requestObject;
  }

  public void setRequestObject(Object requestObject) {
    this.requestObject = requestObject;
  }

  public boolean isSecure() {
    return request.isSecure();
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
    if (coreData.containsKey("userId")) {
      return Long.parseLong(coreData.get("userId"));
    }
    return -1;
  }

  public UserSession getUserSession() {
    return userSession;
  }

  public void setUserSession(UserSession userSession) {
    this.userSession = userSession;
  }

  public boolean hasRole(String role) {
    if (getUserId() == -1) {
      return false;
    }
    return getUserSession().hasRole(role);
  }

  public String getParameter(String name) {
    return getParameter(name, null);
  }

  public String getParameter(String name, String defaultValue) {
    String[] values = parameterMap.get(name);
    if (values != null) {
      return values[0];
    }
    return defaultValue;
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
    if (value != null) {
      value = value.trim();
    }
    if (StringUtils.isNumeric(value)) {
      return Integer.parseInt(value);
    }
    return defaultValue;
  }

  public boolean getParameterAsBoolean(String name) {
    String value = getParameter(name);
    if (value != null) {
      return ("on".equals(value) || "true".equals(value) || "yes".equals(value));
    }
    return false;
  }

  public void addSharedRequestValue(String name, String value) {
    if (value == null) {
      return;
    }
    if (sharedRequestValueMap == null) {
      sharedRequestValueMap = new HashMap<>();
    }
    sharedRequestValueMap.put(name, value);
  }

  public Map<String, String> getSharedRequestValueMap() {
    return sharedRequestValueMap;
  }

  public void setSharedRequestValueMap(Map<String, String> sharedRequestValueMap) {
    this.sharedRequestValueMap = sharedRequestValueMap;
  }

  public String getSharedRequestValue(String name) {
    if (sharedRequestValueMap == null) {
      return null;
    }
    return sharedRequestValueMap.get(name);
  }
}
