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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
 * @created 7/9/2023 5:32 PM
 */
public class HttpPostCommand {

  private static Log LOG = LogFactory.getLog(HttpGetToFileCommand.class);

  public static final int POST = 1;
  public static final int PATCH = 2;
  public static final int PUT = 3;

  public static String execute(String url, Map<String, String> parameters) {
    return execute(url, parameters, POST);
  }

  public static String execute(String url, Map<String, String> headers, Map<String, String> parameters) {
    return execute(url, headers, parameters, POST);
  }

  public static String execute(String url, Map<String, String> headers, String data) {
    return execute(url, headers, data, POST);
  }

  public static String execute(String url, Map<String, String> parameters, int httpMethod) {
    return execute(url, null, parameters, httpMethod);
  }

  public static String execute(String url, Map<String, String> headers, Map<String, String> parameters,
      int httpMethod) {
    return execute(url, headers, getFormDataAsString(parameters), httpMethod);
  }

  public static String execute(String url, Map<String, String> headers, String data, int httpMethod) {
    // Validate the url
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

    // Post the parameters
    try {
      LOG.debug("Posting to: " + url);
      // Build the request
      HttpRequest.Builder builder = HttpRequest.newBuilder();
      builder.uri(URI.create(url));
      builder.timeout(Duration.ofSeconds(20));
      if (headers != null) {
        for (Map.Entry<String, String> set : headers.entrySet()) {
          String name = set.getKey();
          String value = set.getValue();
          builder.setHeader(name, value);
        }
      }
      HttpRequest request = null;

      if (PATCH == httpMethod) {
        request = builder.method("PATCH", HttpRequest.BodyPublishers.ofString(data)).build();
      } else if (PUT == httpMethod) {
        request = builder.PUT(HttpRequest.BodyPublishers.ofString(data)).build();
      } else {
        request = builder.POST(HttpRequest.BodyPublishers.ofString(data)).build();
      }

      // Create the HTTP client
      HttpClient client = HttpClient.newBuilder().build();
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
