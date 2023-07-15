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

package com.simisinc.platform.application.elearning;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.http.HttpPostCommand;
import com.simisinc.platform.application.oauth.OAuthAccessTokenCommand;
import com.simisinc.platform.domain.model.login.OAuthToken;

/**
 * Commands for working with PERLS
 *
 * @author matt rajkowski
 * @created 7/14/2022 1:45 PM
 */
public class PERLSAccessTokenCommand {

  private static Log LOG = LogFactory.getLog(PERLSAccessTokenCommand.class);

  public static OAuthToken retrieveAccessToken() {
    // Determine the mode and credentials
    String serverUrl = LoadSitePropertyCommand.loadByName("elearning.perls.url");
    String clientId = LoadSitePropertyCommand.loadByName("elearning.perls.clientId");
    String secret = LoadSitePropertyCommand.loadByName("elearning.perls.secret");

    if (StringUtils.isAnyBlank(serverUrl, clientId, secret)) {
      LOG.warn("Configuration is required");
      return null;
    }

    String url = serverUrl + (serverUrl.endsWith("/") ? "" : "/") + "oauth/token";

    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/x-www-form-urlencoded");

    Map<String, String> formData = new HashMap<>();
    formData.put("grant_type", "client_credentials");
    formData.put("client_id", clientId);
    formData.put("client_secret", secret);

    String remoteContent = HttpPostCommand.execute(url, headers, formData);

    if (StringUtils.isBlank(remoteContent)) {
      LOG.error("sendHttpGet: no body");
      return null;
    }

    // Check for an exception
    try {
      JsonNode jsonNode = JsonLoader.fromString(remoteContent);
      if (jsonNode.has("exception")) {
        LOG.warn("Exception: " + jsonNode.get("exception"));
        return null;
      }

      // Populate the token and the requested resource
      OAuthToken oAuthToken = new OAuthToken();
      return OAuthAccessTokenCommand.populateTokenFromJson(oAuthToken, jsonNode);
    } catch (Exception e) {
      LOG.error("sendHttpGet", e);
    }

    return null;
  }
}
