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

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.admin.EcommerceCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.ecommerce.Order;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

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
    try (CloseableHttpClient client = HttpClients.createDefault()) {
      HttpEntityEnclosingRequestBase request;
      if ("PUT".equals(method)) {
        request = new HttpPut(url);
      } else {
        request = new HttpPost(url);
      }
      request.setHeader("token", key);
      request.setHeader("customer", customerId);
      request.setHeader("Content-Type", "application/json");
      request.setHeader("Accept", "application/json");
      request.setEntity(new StringEntity(jsonString));

      CloseableHttpResponse response = client.execute(request);
      if (response == null) {
        LOG.error("sendBoxzookaHttpRequest Response is empty");
        return null;
      }

      HttpEntity entity = response.getEntity();
      if (entity == null) {
        LOG.error("sendBoxzookaHttpRequest Entity is null");
        return null;
      }

      // Check for content
      String remoteContent = EntityUtils.toString(entity);
      if (StringUtils.isBlank(remoteContent)) {
        LOG.error("sendBoxzookaHttpRequest Remote content is empty");
        return null;
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("REMOTE TEXT: " + remoteContent);
      }

      // Check for errors... HTTP/1.1 405 Method Not Allowed
      StatusLine statusLine = response.getStatusLine();
      if (statusLine.getStatusCode() > 299) {
        LOG.error("sendBoxzookaHttpRequest Error for URL (" + url + "): " + statusLine.getStatusCode() + " " + statusLine.getReasonPhrase());
      }
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
    try (CloseableHttpClient client = HttpClients.createDefault()) {

      HttpGet httpGet = new HttpGet(url);
      httpGet.setHeader("token", key);
      httpGet.setHeader("customer", customerId);
      httpGet.setHeader("Accept", "application/json");
      httpGet.setHeader("Content-Type", "application/json");

      CloseableHttpResponse response = client.execute(httpGet);
      if (response == null) {
        LOG.error("HttpGet Response is empty");
        return null;
      }

      HttpEntity entity = response.getEntity();
      if (entity == null) {
        LOG.error("HttpEntity is null");
        return null;
      }

      // Check for content
      String remoteContent = EntityUtils.toString(entity);
      if (StringUtils.isBlank(remoteContent)) {
        LOG.error("HttpGet Remote content is empty");
        return null;
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("REMOTE TEXT: " + remoteContent);
      }

      // Check for errors... HTTP/1.1 405 Method Not Allowed
      StatusLine statusLine = response.getStatusLine();
      if (statusLine.getStatusCode() == 404) {
        // Not in the system
        LOG.debug("HttpGet 404: " + url);
        return null;
      }

      // Return the content as JSON
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
    try (CloseableHttpClient client = HttpClients.createDefault()) {

      HttpDelete httpDelete = new HttpDelete(url);
      httpDelete.setHeader("token", key);
      httpDelete.setHeader("customer", customerId);
      httpDelete.setHeader("Accept", "application/json");
      httpDelete.setHeader("Content-Type", "application/json");

      CloseableHttpResponse response = client.execute(httpDelete);
      if (response == null) {
        LOG.error("HttpGet Response is empty");
        return null;
      }

      HttpEntity entity = response.getEntity();
      if (entity == null) {
        LOG.error("HttpPost Entity is null");
        return null;
      }

      // Check for content
      String remoteContent = EntityUtils.toString(entity);
      if (StringUtils.isBlank(remoteContent)) {
        LOG.error("HttpGet Remote content is empty");
        return null;
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("REMOTE TEXT: " + remoteContent);
      }

      // Check for errors... HTTP/1.1 405 Method Not Allowed
      StatusLine statusLine = response.getStatusLine();
      if (statusLine.getStatusCode() == 404) {
        // Not in the system
        LOG.debug("HttpGet 404: " + url);
        return null;
      }

      // Return the content as JSON
      return JsonLoader.fromString(remoteContent);
    } catch (Exception e) {
      LOG.error("sendBoxzookaHttpGet", e);
    }
    return null;
  }
}
