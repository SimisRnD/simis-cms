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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

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
      HttpClient client = HttpClientBuilder.create().build();
      HttpGet request = new HttpGet(url);
      HttpResponse response = client.execute(request);
      if (response == null) {
        LOG.debug("No response");
        return false;
      }

      // Check the status code
      int status = response.getStatusLine().getStatusCode();
      if (status < 200 || status >= 300) {
        LOG.debug("Received status: " + status);
        return false;
      }

      // Use the entity
      HttpEntity entity = response.getEntity();
      if (entity == null) {
        LOG.debug("No entity");
        return false;
      }

      // Save it
      try (InputStream stream = entity.getContent()) {
        try (BufferedInputStream inputStream = new BufferedInputStream(stream)) {
          try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            int inByte;
            while ((inByte = inputStream.read()) != -1) {
              outputStream.write(inByte);
            }
          }
        }
      } catch (IOException ex) {
        LOG.debug("Could not save file: " + ex.getMessage());
        throw ex;
      }
    } catch (Exception e) {
      // Clean up the file
      if (tempFile.exists()) {
        LOG.warn("Deleting an uploaded file: " + tempFile.getAbsolutePath());
        tempFile.delete();
      }
      LOG.error("downloadFile error", e);
      return false;
    }

    // Make sure a file was received
    if (tempFile.length() <= 0) {
      tempFile.delete();
      LOG.debug("File length 0");
      return false;
    }

    return true;
  }
}
