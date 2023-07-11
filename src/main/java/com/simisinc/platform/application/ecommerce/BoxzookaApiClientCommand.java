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
import com.simisinc.platform.application.http.HttpDeleteCommand;
import com.simisinc.platform.application.http.HttpGetToStringCommand;
import com.simisinc.platform.application.http.HttpPostCommand;
import com.simisinc.platform.application.http.HttpPutCommand;
import com.simisinc.platform.domain.model.ecommerce.Order;

/**
 * Commands for working with Boxzooka
 *
 * @author matt rajkowski
 * @created 11/7/19 8:01 PM
 */
public class BoxzookaApiClientCommand {

  private static Log LOG = LogFactory.getLog(BoxzookaApiClientCommand.class);

  private static String PRODUCTION_URL = "https://api.boxzooka.com";
  private static String SANDBOX_URL = "https://sandbox.boxzooka.com";

  public static String externalOrderIdFromOrder(Order order) {
    // Shortens the orderId to be used with Boxzooka
    return order.getUniqueId().replaceAll("-", "");
  }

  public static JsonNode sendBoxzookaHttpPost(String endpoint, String jsonString) {
    return sendBoxzookaHttpRequest("POST", endpoint, jsonString);
  }

  public static JsonNode sendBoxzookaHttpPut(String endpoint, String jsonString) {
    return sendBoxzookaHttpRequest("PUT", endpoint, jsonString);
  }

  public static JsonNode sendBoxzookaHttpRequest(String method, String endpoint, String jsonString) {

    // Determine the mode and credentials
    String customerId = LoadSitePropertyCommand.loadByName("ecommerce.boxzooka.production.id");
    String key = LoadSitePropertyCommand.loadByName("ecommerce.boxzooka.production.secret");
    String url;
    if (EcommerceCommand.isProductionEnabled()) {
      url = PRODUCTION_URL + endpoint;
    } else {
      url = SANDBOX_URL + endpoint;
    }

    if (StringUtils.isBlank(key) || StringUtils.isBlank(customerId)) {
      LOG.error("sendBoxzookaHttpRequest has empty key or customerId");
      return null;
    }

    // Send to Boxzooka
    LOG.debug(method + ": " + url);

    Map<String, String> headers = new HashMap<>();
    headers.put("token", key);
    headers.put("customer", customerId);
    headers.put("Content-Type", "application/json");
    headers.put("Accept", "application/json");

    String remoteContent = null;
    if ("PUT".equals(method)) {
      remoteContent = HttpPutCommand.execute(url, headers, jsonString);
    } else {
      remoteContent = HttpPostCommand.execute(url, headers, jsonString);
    }

    // Check for content
    if (StringUtils.isBlank(remoteContent)) {
      LOG.error("sendBoxzookaHttpRequest Remote content is empty");
      return null;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("REMOTE TEXT: " + remoteContent);
    }

    try {
      return JsonLoader.fromString(remoteContent);
    } catch (Exception e) {
      LOG.error("sendBoxzookaHttpRequest", e);
    }
    return null;
  }

  public static JsonNode sendBoxzookaHttpGet(String endpoint) {

    // Determine the mode and credentials
    String url;
    String key = LoadSitePropertyCommand.loadByName("ecommerce.boxzooka.production.secret");
    if (EcommerceCommand.isProductionEnabled()) {
      url = PRODUCTION_URL + endpoint;
    } else {
      url = SANDBOX_URL + endpoint;
    }
    String customerId = LoadSitePropertyCommand.loadByName("ecommerce.boxzooka.production.id");

    if (StringUtils.isBlank(key) || StringUtils.isBlank(customerId)) {
      LOG.error("sendBoxzookaHttpGet has empty key or customerId");
      return null;
    }

    // Send to Boxzooka
    LOG.debug("GET: " + url);

    Map<String, String> headers = new HashMap<>();
    headers.put("token", key);
    headers.put("customer", customerId);
    headers.put("Accept", "application/json");
    headers.put("Content-Type", "application/json");

    String remoteContent = HttpGetToStringCommand.execute(url, headers);

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
      LOG.error("sendBoxzookaHttpGet", e);
    }
    return null;
  }

  public static JsonNode sendBoxzookaHttpDelete(String endpoint) {

    // Determine the mode and credentials
    String url;
    String key = LoadSitePropertyCommand.loadByName("ecommerce.boxzooka.production.secret");
    if (EcommerceCommand.isProductionEnabled()) {
      url = PRODUCTION_URL + endpoint;
    } else {
      url = SANDBOX_URL + endpoint;
    }
    String customerId = LoadSitePropertyCommand.loadByName("ecommerce.boxzooka.production.id");

    if (StringUtils.isBlank(key) || StringUtils.isBlank(customerId)) {
      LOG.error("sendBoxzookaHttpGet has empty key or customerId");
      return null;
    }

    // Send to Boxzooka
    LOG.debug("DELETE: " + url);

    Map<String, String> headers = new HashMap<>();
    headers.put("token", key);
    headers.put("customer", customerId);
    headers.put("Accept", "application/json");
    headers.put("Content-Type", "application/json");

    String remoteContent = HttpDeleteCommand.execute(url, headers);

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
      LOG.error("sendBoxzookaHttpGet", e);
    }
    return null;
  }
}
