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

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.http.HttpDownloadFileCommand;
import com.simisinc.platform.application.http.HttpGetCommand;
import com.simisinc.platform.domain.model.login.OAuthToken;

/**
 * Commands for working with PERLS
 *
 * @author matt rajkowski
 * @created 6/15/2022 6:58 PM
 */
public class PERLSApiClientCommand {

  private static Log LOG = LogFactory.getLog(PERLSApiClientCommand.class);

  public static final String PACKAGE_API = "/node/learn_package";
  public static final String ARTICLE_API = "/node/learn_article";
  public static final String COURSE_API = "/node/course?include=field_media_image.field_media_image,field_tags&fields[file--file]=uri";
  public static final String EVENT_API = "/node/event";
  public static final String FLASH_CARD_API = "/node/flash_card";
  public static final String LINK_API = "/node/learn_link";
  public static final String PODCAST_API = "/node/podcast";
  public static final String PODCAST_EPISODE_API = "/node/podcast_episode";
  public static final String QUIZ_API = "/node/quiz";
  public static final String TIP_CARD_API = "/node/tip_card";
  public static final String TAXONOMY_TERM_API = "/taxonomy_term/category";

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

    // Retrieve the body content
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Bearer " + token.getAccessToken());

    String remoteContent = HttpGetCommand.execute(url, headers);
    if (StringUtils.isBlank(remoteContent)) {
      LOG.error("sendHttpGet: no body");
      return null;
    }

    return remoteContent;
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

    // Retrieve the file
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Bearer " + token.getAccessToken());

    boolean result = HttpDownloadFileCommand.execute(url, headers, file);
    if (result) {
      return file;
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
