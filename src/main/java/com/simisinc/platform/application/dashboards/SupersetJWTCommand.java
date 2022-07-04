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

import com.fasterxml.jackson.databind.JsonNode;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.login.OAuthToken;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Superset functions
 *
 * @author matt rajkowski
 * @created 7/1/2022 7:52 AM
 */
public class SupersetJWTCommand {
  private static Log LOG = LogFactory.getLog(SupersetJWTCommand.class);

  private static OAuthToken oAuthToken = null;


  public static OAuthToken retrieveAccessToken() {
    if (oAuthToken != null && oAuthToken.getExpires().getTime() > System.currentTimeMillis()) {
      return oAuthToken;
    }
    return updateAccessToken();
  }

  public static synchronized OAuthToken updateAccessToken() {
    // Check if an oAuthToken was updated while waiting
    if (oAuthToken != null && oAuthToken.getExpires().getTime() > System.currentTimeMillis()) {
      return oAuthToken;
    }

    String clientId = LoadSitePropertyCommand.loadByName("bi.superset.id");
    String secret = LoadSitePropertyCommand.loadByName("bi.superset.secret");
    if (StringUtils.isAnyBlank(clientId, secret)) {
      return null;
    }

    Map<String, Object> parameters = new HashMap<>();
    parameters.put("username", clientId);
    parameters.put("password", secret);
    parameters.put("provider", "db");
    parameters.put("refresh", "true");
    String jsonString = JsonCommand.createJsonNode(parameters).toString();

    JsonNode json = SupersetApiClientCommand.sendHttpPost(null, SupersetApiClientCommand.POST_SECURITY_LOGIN, jsonString);
    if (json == null) {
      return null;
    }
    if (!json.has("access_token")) {
      return null;
    }

    OAuthToken newOAuthToken = new OAuthToken();
    newOAuthToken.setAccessToken(json.get("access_token").asText());
    newOAuthToken.setExpiresIn(120);
    if (json.has("refresh_token")) {
      newOAuthToken.setRefreshToken(json.get("refresh_token").asText());
      //oAuthToken.setRefreshExpiresIn(120);
    }

    oAuthToken = newOAuthToken;
    return oAuthToken;
  }

  /*
  public static OAuthToken refreshAccessToken(OAuthToken previousOAuthToken) {
    String clientId = LoadSitePropertyCommand.loadByName("bi.superset.id");
    String secret = LoadSitePropertyCommand.loadByName("bi.superset.secret");
    if (StringUtils.isAnyBlank(clientId, secret)) {
      return null;
    }

    OAuthToken refreshToken = new OAuthToken();
    refreshToken.setAccessToken(previousOAuthToken.getRefreshToken());
    JsonNode json = SupersetApiClientCommand.sendHttpPost(refreshToken, SupersetApiClientCommand.POST_SECURITY_REFRESH, null);
    if (json == null) {
      return null;
    }
    if (!json.has("access_token")) {
      return null;
    }

    OAuthToken oAuthToken = new OAuthToken();
    oAuthToken.setAccessToken(json.get("access_token").asText());
    oAuthToken.setExpiresIn(120);
    if (json.has("refresh_token")) {
      oAuthToken.setRefreshToken(json.get("refresh_token").asText());
      //oAuthToken.setRefreshExpiresIn(120);
    }

    return oAuthToken;
  }
   */
}
