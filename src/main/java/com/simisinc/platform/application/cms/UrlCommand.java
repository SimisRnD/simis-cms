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
import java.util.regex.Pattern;

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

  // A return page is an internal navigation target, rendered by the form JSPs into href/value
  // attributes. Constrain it to a site-relative path + optional query/fragment built from URL-safe
  // characters only: no quotes, angle brackets, backslash, colon, or whitespace, so a value can never
  // break out of the attribute it is placed in or introduce a scheme. This is the defense at the
  // source -- the sinks stay safe even where a JSP renders the value without escaping it.
  private static final Pattern SAFE_RETURN_PAGE = Pattern.compile("^/[A-Za-z0-9/?&=#%._~+,;-]*$");

  public static String getValidReturnPage(String returnPage) {
    if (StringUtils.isBlank(returnPage)) {
      return null;
    }
    // Reject protocol-relative ("//host") targets, which are not site-relative
    if (returnPage.startsWith("//")) {
      return null;
    }
    if (!SAFE_RETURN_PAGE.matcher(returnPage).matches()) {
      return null;
    }
    return returnPage;
  }

  // URL characters that cannot break out of an href/src attribute: no quotes, angle brackets,
  // backslash, backtick, or whitespace.
  private static final Pattern SAFE_URL = Pattern.compile("^[A-Za-z0-9/?&=#%._~:@!$()*+,;-]*$");

  /**
   * Returns the url when it is safe to place in an href/src attribute -- a site-relative path,
   * anchor, or an http(s)/mailto/tel absolute url with no attribute-breakout characters -- otherwise
   * null. Active schemes such as javascript: and data: are rejected, as are protocol-relative "//"
   * targets. Content-authored link values are rendered into href attributes, so this is the defense
   * at the source.
   */
  public static String sanitizeUrl(String url) {
    if (StringUtils.isBlank(url)) {
      return null;
    }
    String value = url.trim();
    if (value.startsWith("//") || !SAFE_URL.matcher(value).matches()) {
      return null;
    }
    // If the value carries a scheme, allow only the safe ones
    String lower = value.toLowerCase();
    if (lower.matches("^[a-z][a-z0-9+.-]*:.*")) {
      if (lower.startsWith("http://") || lower.startsWith("https://")
          || lower.startsWith("mailto:") || lower.startsWith("tel:")) {
        return value;
      }
      return null;
    }
    // No scheme: a site-relative path, anchor, or query
    return value;
  }
}
