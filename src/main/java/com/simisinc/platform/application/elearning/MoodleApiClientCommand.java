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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.util.Map;

/**
 * Commands for working with Moodle
 *
 * @author matt rajkowski
 * @created 6/14/2022 9:23 PM
 */
public class MoodleApiClientCommand {

  private static Log LOG = LogFactory.getLog(MoodleApiClientCommand.class);

  public static JsonNode sendHttpGet(String wsFunction, Map<String, String> parameters) {

    // Check the Moodle properties
    if (!ElearningCommand.isMoodleEnabled()) {
      LOG.debug("Moodle is not enabled");
      return null;
    }

    // Determine the mode and credentials
    String serverUrl = LoadSitePropertyCommand.loadByName("elearning.moodle.url");
    String token = LoadSitePropertyCommand.loadByName("elearning.moodle.token");

    if (StringUtils.isBlank(serverUrl)) {
      LOG.error("sendHttpGet has empty serverUrl");
      return null;
    }

    if (StringUtils.isBlank(token)) {
      LOG.error("sendHttpGet has empty token");
      return null;
    }

    if (StringUtils.isBlank(wsFunction)) {
      LOG.error("sendHttpGet has empty wsFunction");
      return null;
    }

    // Generate the Moodle URL
    String url = serverUrl + "/webservice/rest/server.php" +
        "?wstoken=" + token +
        "&moodlewsrestformat=json" +
        "&wsfunction=" + wsFunction;

    // Add any optional parameters
    if (parameters != null && !parameters.isEmpty()) {
      for (Map.Entry<String, String> set : parameters.entrySet()) {
        url += "&" + set.getKey() + "=" + set.getValue();
      }
    }

    // Send
    LOG.debug("GET: " + url);
    try (CloseableHttpClient client = HttpClients.createDefault()) {

      HttpGet httpGet = new HttpGet(url);
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

      // Check the response
      StatusLine statusLine = response.getStatusLine();
      if (LOG.isDebugEnabled()) {
        LOG.debug("REMOTE TEXT (" + statusLine.getStatusCode() + "): " + remoteContent);
      }

      // Check for errors... HTTP/1.1 405 Method Not Allowed
      if (statusLine.getStatusCode() == 404) {
        // Not in the system
        LOG.debug("HttpGet 404: " + url);
        return null;
      }

      // Check for a Moodle exception
      JsonNode jsonNode = JsonLoader.fromString(remoteContent);
      if (jsonNode.has("exception")) {
        LOG.warn("Exception: " + jsonNode.get("exception"));
        return null;
      }

      // Return the content as JSON
      return jsonNode;
    } catch (Exception e) {
      LOG.error("sendHttpGet", e);
    }
    return null;
  }
}
