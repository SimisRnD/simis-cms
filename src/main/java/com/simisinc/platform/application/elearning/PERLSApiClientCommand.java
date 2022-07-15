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
import com.simisinc.platform.domain.model.login.OAuthToken;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Commands for working with PERLS
 *
 * @author matt rajkowski
 * @created 6/15/2022 6:58 PM
 */
public class PERLSApiClientCommand {

  private static Log LOG = LogFactory.getLog(PERLSApiClientCommand.class);

  public static String PACKAGE_API = "/node/learn_package";
  public static String ARTICLE_API = "/node/learn_article";
  public static String COURSE_API = "/node/course";
  public static String EVENT_API = "/node/event";
  public static String FLASH_CARD_API = "/node/flash_card";
  public static String LINK_API = "/node/learn_link";
  public static String PODCAST_API = "/node/podcast";
  public static String PODCAST_EPISODE_API = "/node/podcast_episode";
  public static String QUIZ_API = "/node/quiz";
  public static String TIP_CARD_API = "/node/tip_card";
  public static String TAXONOMY_TERM_API = "/taxonomy_term/category";


  public static String sendHttpGetToString(String wsFunction, Map<String, String> parameters) {

    // Check the properties
    if (!ElearningCommand.isPERLSEnabled()) {
      LOG.debug("PERLS is not enabled");
      return null;
    }

    // Determine the mode and credentials
    String serverUrl = LoadSitePropertyCommand.loadByName("elearning.perls.url");
    String clientId = LoadSitePropertyCommand.loadByName("elearning.perls.clientId");
    String secret = LoadSitePropertyCommand.loadByName("elearning.perls.secret");

    if (StringUtils.isAnyBlank(serverUrl, clientId, secret, wsFunction)) {
      LOG.error("sendHttpGet has empty client and/or function");
      return null;
    }

    // Use the access token
    OAuthToken token = PERLSAccessTokenCommand.retrieveAccessToken();
    if (token == null) {
      LOG.error("sendHttpGet requires access token");
      return null;
    }

    // Generate the URL
    String url = serverUrl + "/jsonapi" + wsFunction;

    // Add any optional parameters
    if (parameters != null && !parameters.isEmpty()) {
      int count = 0;
      for (Map.Entry<String, String> set : parameters.entrySet()) {
        ++count;
        url += (count == 1 ? "?" : "&") + set.getKey() + "=" + set.getValue();
      }
    }

    try {
      HttpClient httpClient = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder()
          .header("Authorization", "Bearer " + token.getAccessToken())
          .uri(new URI(url)).build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response == null || StringUtils.isBlank(response.body())) {
        LOG.error("sendHttpGet: no body");
        return null;
      }

      System.out.println("CODE: " + response.statusCode());
      System.out.println("BODY: " + response.body());

      return response.body();
    } catch (Exception e) {
      LOG.error("sendHttpGet", e);
    }
    return null;
  }

  public static File sendHttpGetToFile(String wsFunction, Map<String, String> parameters, File file) {

    // Check the properties
    if (!ElearningCommand.isPERLSEnabled()) {
      LOG.debug("PERLS is not enabled");
      return null;
    }

    // Determine the mode and credentials
    String serverUrl = LoadSitePropertyCommand.loadByName("elearning.perls.url");
    String clientId = LoadSitePropertyCommand.loadByName("elearning.perls.clientId");
    String secret = LoadSitePropertyCommand.loadByName("elearning.perls.secret");

    if (StringUtils.isAnyBlank(serverUrl, clientId, secret, wsFunction)) {
      LOG.error("sendHttpGet has empty client and/or function");
      return null;
    }

    // Use the access token
    OAuthToken token = PERLSAccessTokenCommand.retrieveAccessToken();
    if (token == null) {
      LOG.error("sendHttpGet requires access token");
      return null;
    }

    // Generate the URL
    String url = serverUrl + "/jsonapi" + wsFunction;

    // Add any optional parameters
    if (parameters != null && !parameters.isEmpty()) {
      int count = 0;
      for (Map.Entry<String, String> set : parameters.entrySet()) {
        ++count;
        url += (count == 1 ? "?" : "&") + set.getKey() + "=" + set.getValue();
      }
    }

    try {
      HttpClient httpClient = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder()
          .header("Authorization", "Bearer " + token.getAccessToken())
          .uri(new URI(url)).build();

      HttpResponse<?> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(file.toPath()));
      if (response == null || file.length() <= 0) {
        LOG.error("sendHttpGet: no file");
        if (file.exists()) {
          file.delete();
        }
        return null;
      }

      System.out.println("CODE: " + response.statusCode());

      return file;
    } catch (Exception e) {
      LOG.error("sendHttpGet", e);
    }
    return null;
  }

  public static JsonNode sendHttpGetToJson(String wsFunction, Map<String, String> parameters) {

    String body = sendHttpGetToString(wsFunction, parameters);
    if (body == null) {
      return null;
    }

    try {
      JsonNode jsonNode = JsonLoader.fromString(body);

//      if (jsonNode.has("exception")) {
//        LOG.warn("Exception: " + jsonNode.get("exception"));
//        return null;
//      }

      return jsonNode;
    } catch (Exception e) {
      return null;
    }
  }
}
