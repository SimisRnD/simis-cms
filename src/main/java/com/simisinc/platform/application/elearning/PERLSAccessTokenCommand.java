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

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.oauth.OAuthAccessTokenCommand;
import com.simisinc.platform.domain.model.login.OAuthToken;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

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

    Map<String, String> formData = new HashMap<>();
    formData.put("grant_type", "client_credentials");
    formData.put("client_id", clientId);
    formData.put("client_secret", secret);

    try {
      HttpClient httpClient = HttpClient.newHttpClient();

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString(getFormDataAsString(formData)))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response == null || StringUtils.isBlank(response.body())) {
        LOG.error("sendHttpGet: no body");
        return null;
      }

      System.out.println("CODE: " + response.statusCode());
      System.out.println("BODY: " + response.body());

      // Check for an exception
      JsonNode jsonNode = JsonLoader.fromString(response.body());
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

  private static String getFormDataAsString(Map<String, String> formData) {
    StringBuilder formBodyBuilder = new StringBuilder();
    for (Map.Entry<String, String> singleEntry : formData.entrySet()) {
      if (formBodyBuilder.length() > 0) {
        formBodyBuilder.append("&");
      }
      formBodyBuilder.append(URLEncoder.encode(singleEntry.getKey(), StandardCharsets.UTF_8));
      formBodyBuilder.append("=");
      formBodyBuilder.append(URLEncoder.encode(singleEntry.getValue(), StandardCharsets.UTF_8));
    }
    return formBodyBuilder.toString();
  }
}
