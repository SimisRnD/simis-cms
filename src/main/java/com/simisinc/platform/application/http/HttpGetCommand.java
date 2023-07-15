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

package com.simisinc.platform.application.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.routines.UrlValidator;

/**
 * Functions for working with http requests
 *
 * @author matt rajkowski
 * @created 2/7/2020 4:25 PM
 */
public class HttpGetCommand {

  private static Log LOG = LogFactory.getLog(HttpDownloadFileCommand.class);

  public static final int GET = 1;
  public static final int DELETE = 2;

  public static String execute(String url) {
    return execute(url, GET);
  }

  public static String execute(String url, Map<String, String> headers) {
    return execute(url, headers, GET);
  }

  public static String execute(String url, int httpMethod) {
    return execute(url, null, httpMethod);
  }

  public static String execute(String url, Map<String, String> headers, int httpMethod) {
    // Validate the parameters
    if (StringUtils.isBlank(url)) {
      LOG.debug("No url");
      return null;
    }
    String[] schemes = { "http", "https" };
    UrlValidator urlValidator = new UrlValidator(schemes);
    if (!urlValidator.isValid(url)) {
      LOG.debug("Invalid url: " + url);
      return null;
    }

    // Download as a string
    try {
      LOG.debug("Requesting from: " + url);

      // Build the request
      HttpRequest.Builder builder = HttpRequest.newBuilder();
      builder.uri(URI.create(url));
      if (DELETE == httpMethod) {
        builder.DELETE();
      } else {
        builder.GET();
      }
      builder.timeout(Duration.ofSeconds(20));
      if (headers != null) {
        for (Map.Entry<String, String> set : headers.entrySet()) {
          String name = set.getKey();
          String value = set.getValue();
          builder.setHeader(name, value);
        }
      }
      HttpRequest request = builder.build();

      // Send the request and handle the response
      HttpClient client = HttpClient.newHttpClient();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response == null) {
        LOG.debug("No response");
        return null;
      }

      // Check the status code
      int status = response.statusCode();
      if (status < 200 || status >= 300) {
        LOG.debug("Received status: " + status);
        return null;
      }

      // Verify the content
      String content = response.body();
      if (content == null || content.length() <= 0) {
        return null;
      }
      return content;
    } catch (Exception e) {
      LOG.error("Http client exception", e);
      return null;
    }
  }
}
