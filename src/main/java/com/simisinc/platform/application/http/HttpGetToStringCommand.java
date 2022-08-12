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

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

/**
 * Functions for working with http requests
 *
 * @author matt rajkowski
 * @created 2/7/2020 4:25 PM
 */
public class HttpGetToStringCommand {

  private static Log LOG = LogFactory.getLog(HttpGetToFileCommand.class);

  public static String execute(String url) {
    return execute(url, null);
  }

  public static String execute(String url, Map<String, String> headers) {
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
      LOG.debug("Downloading from: " + url);
      HttpClient client = HttpClientBuilder.create().build();
      HttpGet request = new HttpGet(url);

      if (headers != null) {
        for (Map.Entry<String, String> set : headers.entrySet()) {
          String name = set.getKey();
          String value = set.getValue();
          request.setHeader(name, value);
        }
      }

      HttpResponse response = client.execute(request);
      if (response == null) {
        LOG.debug("No response");
        return null;
      }

      // Check the status code
      int status = response.getStatusLine().getStatusCode();
      if (status < 200 || status >= 300) {
        LOG.debug("Received status: " + status);
        return null;
      }

      // Use the entity
      HttpEntity entity = response.getEntity();
      if (entity == null) {
        LOG.debug("No entity");
        return null;
      }

      return EntityUtils.toString(response.getEntity(), "UTF-8");
    } catch (Exception e) {
      return null;
    }
  }
}
