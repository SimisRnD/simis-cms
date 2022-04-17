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

package com.simisinc.platform.application.cms;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.routines.UrlValidator;

import java.net.URLEncoder;

/**
 * Functions for encoding and validating web content
 *
 * @author matt rajkowski
 * @created 4/27/18 12:55 PM
 */
public class UrlCommand {

  private static Log LOG = LogFactory.getLog(UrlCommand.class);

  public static String encode(String url) {
    if (StringUtils.isBlank(url)) {
      LOG.debug("URL is blank");
      return "#";
    }

    // Validate first
    String[] schemes = {"http", "https"};
    UrlValidator urlValidator = new UrlValidator(schemes);
    if (!urlValidator.isValid(url)) {
      return "#";
    }

    // @todo handle invalid urls

    return url;
  }

  public static String encodeUri(String uri) {
    if (StringUtils.isBlank(uri)) {
      LOG.debug("URL is blank");
      return "#";
    }
    try {
      return URLEncoder.encode(uri, "UTF-8")
          .replace("+", "%20")
          .replace("%21", "!")
          .replace("%27", "'")
          .replace("%28", "(")
          .replace("%29", ")")
//          .replace("%2F", "/")
          .replace("%7E", "~");
    } catch (Exception e) {
      return "#";
    }
  }

  public static boolean isUrlValid(String url) {
    String[] schemes = {"http", "https"};
    UrlValidator urlValidator = new UrlValidator(schemes);
    return urlValidator.isValid(url);
  }

  public static String getValidReturnPage(String returnPage) {
    if (StringUtils.isBlank(returnPage)) {
      return null;
    }
    if (returnPage.contains(":")) {
      return null;
    }
    if (returnPage.startsWith("/")) {
      return returnPage;
    }
    return returnPage;
  }
}
