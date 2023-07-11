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

package com.simisinc.platform.application.dashboards;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.http.HttpGetToStringCommand;
import com.simisinc.platform.application.http.HttpPostCommand;
import com.simisinc.platform.domain.model.login.OAuthToken;

/**
 * Commands for working with Superset
 *
 * @author matt rajkowski
 * @created 6/29/2022 7:52 AM
 */
public class SupersetApiClientCommand {

  private static Log LOG = LogFactory.getLog(SupersetApiClientCommand.class);

  public static final String API_VERSION = "/api/v1";
  public static final String POST_SECURITY_LOGIN = "/security/login";
  public static final String POST_SECURITY_REFRESH = "/security/refresh";
  public static final String POST_SECURITY_GUEST_TOKEN = "/security/guest_token/";

  public static JsonNode sendHttpPost(OAuthToken oAuthToken, String endpoint, String jsonString) {

    // Check the API properties
    boolean enabled = ("true".equals(LoadSitePropertyCommand.loadByName("bi.enabled", "false")));
    if (!enabled) {
      LOG.debug("BI is not enabled");
      return null;
    }

    // Determine the mode and credentials
    String serverUrl = LoadSitePropertyCommand.loadByName("bi.superset.url");
    String clientId = LoadSitePropertyCommand.loadByName("bi.superset.id");
    String secret = LoadSitePropertyCommand.loadByName("bi.superset.secret");

    if (StringUtils.isBlank(serverUrl)) {
      LOG.error("sendHttpPost has empty serverUrl");
      return null;
    }

    if (StringUtils.isBlank(clientId)) {
      LOG.error("sendHttpPost has empty clientId");
      return null;
    }

    if (StringUtils.isBlank(secret)) {
      LOG.error("sendHttpPost has empty secret");
      return null;
    }

    if (StringUtils.isBlank(endpoint)) {
      LOG.error("sendHttpPost has empty endpoint");
      return null;
    }

    // Generate the URL
    String url = serverUrl + API_VERSION + endpoint;

    // Send to API
    Map<String, String> headers = new HashMap<>();
    if (oAuthToken != null && StringUtils.isNotBlank(oAuthToken.getAccessToken())) {
      headers.put("Authorization", "Bearer " + oAuthToken.getAccessToken());
    }
    headers.put("Accept", "application/json");
    headers.put("Content-Type", "application/json");
    String remoteContent = HttpPostCommand.execute(url, headers, jsonString);

    // Check for content
    if (StringUtils.isBlank(remoteContent)) {
      LOG.error("HttpPost Remote content is empty");
      return null;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("REMOTE TEXT: " + remoteContent);
    }
    try {
      return JsonLoader.fromString(remoteContent);
    } catch (Exception e) {
      LOG.error("sendHttpPost", e);
    }
    return null;
  }

  public static JsonNode sendHttpGet(String endpoint, Map<String, String> parameters) {

    // Check the API properties
    boolean enabled = ("true".equals(LoadSitePropertyCommand.loadByName("bi.enabled", "false")));
    if (!enabled) {
      LOG.debug("BI is not enabled");
      return null;
    }

    // Determine the mode and credentials
    String serverUrl = LoadSitePropertyCommand.loadByName("bi.superset.url");
    String clientId = LoadSitePropertyCommand.loadByName("bi.superset.id");
    String secret = LoadSitePropertyCommand.loadByName("bi.superset.secret");

    if (StringUtils.isBlank(serverUrl)) {
      LOG.error("sendHttpGet has empty serverUrl");
      return null;
    }

    if (StringUtils.isBlank(clientId)) {
      LOG.error("sendHttpGet has empty clientId");
      return null;
    }

    if (StringUtils.isBlank(secret)) {
      LOG.error("sendHttpGet has empty secret");
      return null;
    }

    if (StringUtils.isBlank(endpoint)) {
      LOG.error("sendHttpGet has empty endpoint");
      return null;
    }

    // Generate the URL
    String url = serverUrl + API_VERSION + endpoint;

    // Add any optional parameters
    if (parameters != null && !parameters.isEmpty()) {
      boolean hasParam = url.contains("?");
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, String> set : parameters.entrySet()) {
        if (!hasParam) {
          sb.append("?");
          hasParam = true;
        } else {
          sb.append("&");
        }
        sb.append(set.getKey() + "=" + set.getValue());
      }
      url += sb.toString();
    }

    // Send
    LOG.debug("GET: " + url);
    Map<String, String> headers = new HashMap<>();
    headers.put("Accept", "application/json");
    headers.put("Content-Type", "application/json");
    String remoteContent = HttpGetToStringCommand.execute(url, headers);

    // Check for content
    if (StringUtils.isBlank(remoteContent)) {
      LOG.error("HttpGet Remote content is empty");
      return null;
    }

    // Check for an exception
    try {
      JsonNode jsonNode = JsonLoader.fromString(remoteContent);
      if (jsonNode.has("exception")) {
        LOG.warn("Exception: " + jsonNode.get("exception"));
        return null;
      }

      // Return the content as JSON
      return jsonNode;
    } catch (Exception e) {
      LOG.error("sendHttpGet", e);
    }
    return null;
  }
}
