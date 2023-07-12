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

package com.simisinc.platform.application.ecommerce;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.http.HttpGetCommand;
import com.simisinc.platform.application.http.HttpPostCommand;

/**
 * Commands for working with Square
 *
 * @author matt rajkowski
 * @created 11/6/19 9:50 PM
 */
public class SquareApiClientCommand {

  private static Log LOG = LogFactory.getLog(SquareApiClientCommand.class);

  private static String SQUARE_API_VERSION = "2022-09-21";

  public static JsonNode sendSquareHttpPost(String endpoint, String jsonString) {

    // Determine the mode and credentials
    String url;
    String key;
    if (EcommerceCommand.isProductionEnabled()) {
      url = "https://connect.squareup.com" + endpoint;
      key = LoadSitePropertyCommand.loadByName("ecommerce.square.production.secret");
    } else {
      url = "https://connect.squareupsandbox.com" + endpoint;
      key = LoadSitePropertyCommand.loadByName("ecommerce.square.test.secret");
    }

    if (StringUtils.isBlank(key)) {
      LOG.error("sendSquareHttpPost has empty key");
      return null;
    }

    // Send to Square
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Bearer " + key);
    headers.put("Accept", "application/json");
    headers.put("Content-Type", "application/json");
    headers.put("Square-Version", SQUARE_API_VERSION);

    // Check for content
    String remoteContent = HttpPostCommand.execute(url, headers, jsonString);
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
      LOG.error("sendSquareHttpPost json", e);
    }
    return null;
  }

  public static JsonNode sendSquareHttpGet(String endpoint) {

    // Determine the mode and credentials
    String url;
    String key;
    if (EcommerceCommand.isProductionEnabled()) {
      url = "https://connect.squareup.com" + endpoint;
      key = LoadSitePropertyCommand.loadByName("ecommerce.square.production.secret");
    } else {
      url = "https://connect.squareupsandbox.com" + endpoint;
      key = LoadSitePropertyCommand.loadByName("ecommerce.square.test.secret");
    }

    if (StringUtils.isBlank(key)) {
      LOG.error("sendSquareHttpGet has empty key");
      return null;
    }

    // Send to Square
    LOG.debug("GET: " + url);

    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Bearer " + key);
    headers.put("Accept", "application/json");
    headers.put("Content-Type", "application/json");
    headers.put("Square-Version", SQUARE_API_VERSION);

    String remoteContent = HttpGetCommand.execute(url, headers);

    // Check for content
    if (StringUtils.isBlank(remoteContent)) {
      LOG.error("HttpGet Remote content is empty");
      return null;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("REMOTE TEXT: " + remoteContent);
    }

    // Return the content as JSON
    try {
      return JsonLoader.fromString(remoteContent);
    } catch (Exception e) {
      LOG.error("sendSquareHttpGet", e);
    }
    return null;
  }
}
