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
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/**
 * Commands for working with Square
 *
 * @author matt rajkowski
 * @created 11/6/19 9:50 PM
 */
public class SquareApiClientCommand {

  private static Log LOG = LogFactory.getLog(SquareApiClientCommand.class);

  private static String SQUARE_API_VERSION = "2021-08-18";

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
    try (CloseableHttpClient client = HttpClients.createDefault()) {

      HttpPost httpPost = new HttpPost(url);
      httpPost.setHeader("Authorization", "Bearer " + key);
      httpPost.setHeader("Accept", "application/json");
      httpPost.setHeader("Content-Type", "application/json");
      httpPost.setHeader("Square-Version", SQUARE_API_VERSION);
      httpPost.setEntity(new StringEntity(jsonString));

      CloseableHttpResponse response = client.execute(httpPost);
      if (response == null) {
        LOG.error("HttpPost Response is empty");
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
        LOG.error("HttpPost Remote content is empty");
        return null;
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("REMOTE TEXT: " + remoteContent);
      }

      // Check for errors... HTTP/1.1 405 Method Not Allowed
      StatusLine statusLine = response.getStatusLine();
      if (statusLine.getStatusCode() > 299) {
        LOG.error("HttpPost Error for URL (" + url + "): " + statusLine.getStatusCode() + " " + statusLine.getReasonPhrase());
      }
      return JsonLoader.fromString(remoteContent);
    } catch (Exception e) {
      LOG.error("sendSquareHttpPost", e);
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
    try (CloseableHttpClient client = HttpClients.createDefault()) {

      HttpGet httpGet = new HttpGet(url);
      httpGet.setHeader("Authorization", "Bearer " + key);
      httpGet.setHeader("Accept", "application/json");
      httpGet.setHeader("Content-Type", "application/json");
      httpGet.setHeader("Square-Version", SQUARE_API_VERSION);

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
      LOG.error("sendSquareHttpGet", e);
    }
    return null;
  }
}
