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
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.login.OAuthToken;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.util.List;

/**
 * Sends http requests
 *
 * @author matt rajkowski
 * @created 4/20/22 6:19 PM
 */
public class OAuthHttpCommand {

  private static Log LOG = LogFactory.getLog(OAuthHttpCommand.class);

  public static JsonNode sendHttpGet(String endpoint, OAuthToken oAuthToken) {
    if (StringUtils.isBlank(endpoint)) {
      return null;
    }
    if (oAuthToken == null) {
      return null;
    }

    String serviceUrl = LoadSitePropertyCommand.loadByName("oauth.serviceUrl");
    String clientId = LoadSitePropertyCommand.loadByName("oauth.clientId");
    String clientSecret = LoadSitePropertyCommand.loadByName("oauth.clientSecret");
    String siteUrl = LoadSitePropertyCommand.loadByName("site.url");
    if (StringUtils.isAnyBlank(serviceUrl, clientId, clientSecret, siteUrl)) {
      LOG.warn("Service is not configured");
      return null;
    }
    String url = serviceUrl + (serviceUrl.endsWith("/") ? "" : "/") + endpoint;
    LOG.debug("Using serviceUrl: " + url);

    // Send to provider
    try (CloseableHttpClient client = HttpClients.createDefault()) {

      HttpGet httpGet = new HttpGet(url);
      httpGet.setHeader("Authorization", "Bearer " + oAuthToken.getAccessToken());

      CloseableHttpResponse response = client.execute(httpGet);
      if (response == null) {
        LOG.error("HttpGet Response is empty");
        return null;
      }

      HttpEntity entity = response.getEntity();
      if (entity == null) {
        LOG.error("HttpGet Entity is null");
        return null;
      }

      // Check for content
      String remoteContent = EntityUtils.toString(entity);
      if (StringUtils.isBlank(remoteContent)) {
        LOG.error("HttpGet Remote content is empty");
        if (response.getStatusLine() != null) {
          // 401 The request was denied due to an invalid or missing access token
          // 403 The request was denied due to the bearer access token having insufficient privileges
          LOG.debug("HttpGet status: " + response.getStatusLine().getStatusCode() + " " + response.getStatusLine().getReasonPhrase());
        }
        return null;
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("REMOTE TEXT: " + remoteContent);
      }

      // Check for errors... HTTP/1.1 405 Method Not Allowed
      StatusLine statusLine = response.getStatusLine();
      if (statusLine.getStatusCode() > 299) {
        LOG.error("HttpGet Error for URL (" + url + "): " + statusLine.getStatusCode() + " " + statusLine.getReasonPhrase());
        return null;
      }
      return JsonLoader.fromString(remoteContent);
    } catch (Exception e) {
      LOG.error("oauthToken error", e);
    }
    return null;
  }

  public static JsonNode sendHttpPost(String endpoint, List<NameValuePair> params) {

    if (StringUtils.isBlank(endpoint)) {
      return null;
    }

    if (params == null) {
      return null;
    }

    String serviceUrl = LoadSitePropertyCommand.loadByName("oauth.serviceUrl");
    String clientId = LoadSitePropertyCommand.loadByName("oauth.clientId");
    String clientSecret = LoadSitePropertyCommand.loadByName("oauth.clientSecret");
    String siteUrl = LoadSitePropertyCommand.loadByName("site.url");

    if (StringUtils.isAnyBlank(serviceUrl, clientId, clientSecret, siteUrl)) {
      LOG.warn("Service is not configured");
      return null;
    }

    String url = serviceUrl + (serviceUrl.endsWith("/") ? "" : "/") + endpoint;
    LOG.debug("Using serviceUrl: " + url);

    String redirectUri = siteUrl + (siteUrl.endsWith("/") ? "" : "/") + "oauth/callback";
    LOG.debug("Using redirectUri: " + redirectUri);

    params.add(new BasicNameValuePair("client_id", clientId));
    params.add(new BasicNameValuePair("client_secret", clientSecret));
    params.add(new BasicNameValuePair("redirect_uri", redirectUri));

    try (CloseableHttpClient client = HttpClients.createDefault()) {

      HttpPost httpPost = new HttpPost(url);
      httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
      httpPost.setEntity(new UrlEncodedFormEntity(params));

      CloseableHttpResponse response = client.execute(httpPost);
      if (response == null) {
        LOG.error("HttpPost Response is empty");
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
        return null;
      }

      // Return the content as JSON
      return JsonLoader.fromString(remoteContent);
    } catch (Exception e) {
      LOG.error("HttpPost", e);
    }
    return null;
  }
}
