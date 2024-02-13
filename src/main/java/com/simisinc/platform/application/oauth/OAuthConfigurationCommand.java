/*
 * Copyright 2024 Matt Rajkowski
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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.http.HttpGetCommand;
import com.simisinc.platform.infrastructure.cache.CacheManager;

/**
 * Configures and verifies configuration using openid-configuration
 *
 * @author matt rajkowski
 * @created 2/12/24 8:07 PM
 */
public class OAuthConfigurationCommand {

  private static Log LOG = LogFactory.getLog(OAuthConfigurationCommand.class);
  private static final String OAUTH_CONFIGURATION_CACHE_NAME = "OAUTH.CONFIGURATION.JSON";

  public static boolean isEnabled() {
    String enabled = System.getenv("OAUTH_ENABLED");
    if (StringUtils.isBlank(enabled)) {
      enabled = LoadSitePropertyCommand.loadByName("oauth.enabled");
    }
    if (!"true".equals(enabled)) {
      return false;
    }
    return true;
  }

  public static String getConfigurationUrl() {
    String configurationUrl = System.getenv("OAUTH_SERVER_URL");
    if (StringUtils.isBlank(configurationUrl)) {
      configurationUrl = LoadSitePropertyCommand.loadByName("oauth.serverUrl");
    }
    if (StringUtils.isNotBlank(configurationUrl)) {
      return (configurationUrl.endsWith("/") ? "" : "/") + ".well-known/openid-configuration";
    }
    return null;
  }

  public static String getClientId() {
    String clientId = System.getenv("OAUTH_CLIENT_ID");
    if (StringUtils.isBlank(clientId)) {
      clientId = LoadSitePropertyCommand.loadByName("oauth.clientId");
    }
    return clientId;
  }

  public static String getClientSecret() {
    String clientSecret = System.getenv("OAUTH_CLIENT_SECRET");
    if (StringUtils.isBlank(clientSecret)) {
      clientSecret = LoadSitePropertyCommand.loadByName("oauth.clientSecret");
    }
    return clientSecret;
  }

  public static String getRedirectUri() {
    String redirectUri = System.getenv("OAUTH_REDIRECT_URI");
    if (StringUtils.isBlank(redirectUri)) {
      redirectUri = "/oauth/callback";
    }
    return redirectUri;
  }

  public static String getRedirectUrl() {
    String uri = getRedirectUri();
    if (uri.startsWith("http://") || uri.startsWith("https://")) {
      return uri;
    }
    String siteUrl = LoadSitePropertyCommand.loadByName("site.url");
    if (StringUtils.isBlank(siteUrl)) {
      siteUrl = "http://localhost:8080";
    }
    return siteUrl + (siteUrl.endsWith("/") ? getRedirectUri().substring(1) : getRedirectUri());
  }

  public static String retrieveAuthEndpoint() {
    return retrieveValue("authorization_endpoint");
  }

  public static String retrieveTokenEndpoint() {
    return retrieveValue("token_endpoint");
  }

  public static String retrieveUserInfoEndpoint() {
    return retrieveValue("userinfo_endpoint");
  }

  public static String retrieveLogoutEndpoint() {
    return retrieveValue("end_session_endpoint");
  }

  public static boolean hasInvalidConfiguration() {
    if (isEnabled() == false) {
      return false;
    }
    return StringUtils.isAnyBlank(getConfigurationUrl(), getClientId(), getClientSecret(), retrieveAuthEndpoint(),
        retrieveTokenEndpoint(), retrieveUserInfoEndpoint());
  }

  private static synchronized String retrieveValue(String parameter) {
    // Cache the .well-known/openid-configuration
    JsonNode json = (JsonNode) CacheManager.getFromObjectCache(OAUTH_CONFIGURATION_CACHE_NAME);
    if (json == null) {
      String configurationUrl = getConfigurationUrl();
      if (configurationUrl == null) {
        return null;
      }
      String responseValue = HttpGetCommand.execute(configurationUrl);
      if (responseValue == null) {
        return null;
      }
      try {
        json = JsonLoader.fromString(responseValue);
        CacheManager.addToObjectCache(OAUTH_CONFIGURATION_CACHE_NAME, json);
      } catch (Exception e) {
        LOG.error("HttpPost", e);
      }
    }
    if (json.has(parameter)) {
      return json.get(parameter).asText();
    }
    return null;
  }
}
