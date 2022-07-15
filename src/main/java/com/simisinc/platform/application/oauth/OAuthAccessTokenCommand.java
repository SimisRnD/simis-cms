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

import com.fasterxml.jackson.databind.JsonNode;
import com.simisinc.platform.domain.model.login.OAuthToken;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;
import java.util.List;

/**
 * Configures and verifies access tokens
 *
 * @author matt rajkowski
 * @created 4/20/22 6:19 PM
 */
public class OAuthAccessTokenCommand {

  private static Log LOG = LogFactory.getLog(OAuthAccessTokenCommand.class);

  public static OAuthToken retrieveAccessToken(String state, String code) {
    if (StringUtils.isAnyBlank(state, code)) {
      LOG.warn("State and code parameters are required");
      return null;
    }
    String resource = OAuthAuthorizationCommand.resourceIfStateIsValid(state);
    if (resource == null) {
      LOG.warn("State not found in cache, may have expired");
      return null;
    }
    LOG.debug("Found resource: " + resource);

    // http://localhost:8100/realms/name/protocol/openid-connect/token
    List<NameValuePair> params = new ArrayList<>();
    params.add(new BasicNameValuePair("grant_type", "authorization_code"));
    params.add(new BasicNameValuePair("code", code));
//    params.add(new BasicNameValuePair("scope", "openid profile email"));

    JsonNode json = OAuthHttpCommand.sendHttpPost("protocol/openid-connect/token", params);
    if (json == null) {
      return null;
    }

    // Populate the token and the requested resource
    OAuthToken oAuthToken = new OAuthToken();
    oAuthToken.setResource(resource);
    return populateTokenFromJson(oAuthToken, json);
  }

  public static OAuthToken refreshAccessToken(OAuthToken oAuthToken) {
    // http://localhost:8100/realms/name/protocol/openid-connect/token
    List<NameValuePair> params = new ArrayList<>();
    params.add(new BasicNameValuePair("grant_type", "refresh_token"));
    params.add(new BasicNameValuePair("refresh_token", oAuthToken.getRefreshToken()));

    JsonNode json = OAuthHttpCommand.sendHttpPost("protocol/openid-connect/token", params);
    if (json == null) {
      return null;
    }

    return populateTokenFromJson(oAuthToken, json);
  }

  public static OAuthToken populateTokenFromJson(OAuthToken oAuthToken, JsonNode json) {
    oAuthToken.setProvider("oauth");
    oAuthToken.setTokenType("bearer");
    if (json.has("access_token")) {
      oAuthToken.setAccessToken(json.get("access_token").asText());
      oAuthToken.setExpires(null);
      if (json.has("expires_in")) {
        oAuthToken.setExpiresIn(json.get("expires_in").asInt());
      } else {
        // extend any existing expiration
        oAuthToken.setExpiresIn(oAuthToken.getExpiresIn());
      }
      // If you do not get back a new refresh token, then it means your existing refresh token will continue to work when the new access token expires
      if (json.has("refresh_token")) {
        oAuthToken.setRefreshToken(json.get("refresh_token").asText());
      }
      oAuthToken.setRefreshExpires(null);
      if (json.has("refresh_expires_in")) {
        oAuthToken.setRefreshExpiresIn(json.get("refresh_expires_in").asInt());
      } else {
        // extend any existing expiration
        oAuthToken.setRefreshExpiresIn(oAuthToken.getRefreshExpiresIn());
      }
      if (json.has("scope")) {
        oAuthToken.setScope(json.get("scope").asText());
      }
    }
    return oAuthToken;
  }
}
