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

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
public class HttpGetToFileCommand {

  private static Log LOG = LogFactory.getLog(HttpGetToFileCommand.class);

  /**
   * Downloads the remote file
   * 
   * @param url
   * @param tempFile
   * @return
   */
  public static boolean execute(String url, File tempFile) {
    return execute(url, null, tempFile);
  }

  public static boolean execute(String url, Map<String, String> headers, File tempFile) {
    // Validate the parameters
    if (StringUtils.isBlank(url)) {
      LOG.debug("No url");
      return false;
    }
    String[] schemes = { "http", "https" };
    UrlValidator urlValidator = new UrlValidator(schemes);
    if (!urlValidator.isValid(url)) {
      LOG.debug("Invalid url: " + url);
      return false;
    }
    // Download to a file
    try {
      // Build the request
      HttpRequest.Builder builder = HttpRequest.newBuilder();
      builder.uri(URI.create(url));
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
      HttpResponse<?> response = client.send(request, HttpResponse.BodyHandlers.ofFile(tempFile.toPath()));

      // Make sure a file was received
      if (response == null || tempFile.length() <= 0) {
        LOG.error("sendHttpGet: no file");
        if (tempFile.exists()) {
          tempFile.delete();
        }
        return false;
      }

      LOG.debug("CODE: " + response.statusCode());

      return true;
    } catch (Exception e) {
      // Clean up the file
      if (tempFile.exists()) {
        LOG.warn("Deleting an uploaded file: " + tempFile.getAbsolutePath());
        tempFile.delete();
      }
      LOG.error("downloadFile error", e);
      return false;
    }
  }
}
