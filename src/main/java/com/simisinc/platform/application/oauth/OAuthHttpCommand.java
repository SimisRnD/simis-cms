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

package com.simisinc.platform.application.oauth;

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
 * Sends http requests
 *
 * @author matt rajkowski
 * @created 4/20/22 6:19 PM
 */
public class OAuthHttpCommand {

  private static Log LOG = LogFactory.getLog(OAuthHttpCommand.class);

  public static JsonNode sendHttpGet(String endpoint, OAuthToken oAuthToken) {
    if (StringUtils.isBlank(endpoint)) {
      return null;
    }
    if (oAuthToken == null) {
      return null;
    }

    String serviceUrl = LoadSitePropertyCommand.loadByName("oauth.serviceUrl");
    String clientId = LoadSitePropertyCommand.loadByName("oauth.clientId");
    String clientSecret = LoadSitePropertyCommand.loadByName("oauth.clientSecret");
    String siteUrl = LoadSitePropertyCommand.loadByName("site.url");
    if (StringUtils.isAnyBlank(serviceUrl, clientId, clientSecret, siteUrl)) {
      LOG.warn("Service is not configured");
      return null;
    }
    String url = serviceUrl + (serviceUrl.endsWith("/") ? "" : "/") + endpoint;
    LOG.debug("Using serviceUrl: " + url);

    // Send to provider
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Bearer " + oAuthToken.getAccessToken());
    String remoteContent = HttpGetToStringCommand.execute(url, headers);

    // Check for content
    if (StringUtils.isBlank(remoteContent)) {
      LOG.error("HttpGet Remote content is empty");
      return null;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("REMOTE TEXT: " + remoteContent);
    }

    try {
      return JsonLoader.fromString(remoteContent);
    } catch (Exception e) {
      LOG.error("oauthToken error", e);
    }
    return null;
  }

  public static JsonNode sendHttpPost(String endpoint, Map<String, String> parameters) {

    if (StringUtils.isBlank(endpoint)) {
      return null;
    }

    if (parameters == null) {
      return null;
    }

    String serviceUrl = LoadSitePropertyCommand.loadByName("oauth.serviceUrl");
    String clientId = LoadSitePropertyCommand.loadByName("oauth.clientId");
    String clientSecret = LoadSitePropertyCommand.loadByName("oauth.clientSecret");
    String siteUrl = LoadSitePropertyCommand.loadByName("site.url");

    if (StringUtils.isAnyBlank(serviceUrl, clientId, clientSecret, siteUrl)) {
      LOG.warn("Service is not configured");
      return null;
    }

    String url = serviceUrl + (serviceUrl.endsWith("/") ? "" : "/") + endpoint;
    LOG.debug("Using serviceUrl: " + url);

    String redirectUri = siteUrl + (siteUrl.endsWith("/") ? "" : "/") + "oauth/callback";
    LOG.debug("Using redirectUri: " + redirectUri);

    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/x-www-form-urlencoded");

    parameters.put("client_id", clientId);
    parameters.put("client_secret", clientSecret);
    parameters.put("redirect_uri", redirectUri);

    String remoteContent = HttpPostCommand.execute(url, headers, parameters);

    // Check for content
    if (StringUtils.isBlank(remoteContent)) {
      LOG.error("HttpPost Remote content is empty");
      return null;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("REMOTE TEXT: " + remoteContent);
    }

    // Return the content as JSON
    try {
      return JsonLoader.fromString(remoteContent);
    } catch (Exception e) {
      LOG.error("HttpPost", e);
    }
    return null;
  }
}
