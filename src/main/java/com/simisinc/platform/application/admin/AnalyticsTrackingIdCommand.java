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

package com.simisinc.platform.application.admin;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Validates third-party analytics tracking identifiers.
 *
 * These values (Google Analytics/Tag Manager keys, Simpli.fi and Brand CDN tag values) are rendered
 * by the master page template into public-page script src attributes and inline script. A tracking id
 * is a short token -- for example "G-XXXXXXXX", "UA-XXXXXX-X", "GTM-XXXXXXX" -- so anything outside a
 * conservative character set is not a real id and must never reach the page: escaping alone is not a
 * reliable defense there, because the values land in HTML attribute contexts where a JavaScript-string
 * escaper does not neutralize a quote. Constraining the stored value at the source closes every sink.
 *
 * @author elizabeth houser
 */
public class AnalyticsTrackingIdCommand {

  private static Log LOG = LogFactory.getLog(AnalyticsTrackingIdCommand.class);

  // A tracking id: letters, digits, dot, underscore, hyphen. No quotes, whitespace, slashes, or markup.
  private static final Pattern TRACKING_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

  private static final Set<String> TRACKING_ID_PROPERTIES = Set.of(
      "analytics.google.key",
      "analytics.google.tagmanager",
      "analytics.simplifi.value",
      "analytics.brandcdn.value",
      "analytics.brandcdn.value2");

  private AnalyticsTrackingIdCommand() {
  }

  public static boolean isTrackingIdProperty(String propertyName) {
    return propertyName != null && TRACKING_ID_PROPERTIES.contains(propertyName);
  }

  /** A blank value is allowed (the tag is simply not rendered); a non-blank value must be a well-formed id */
  public static boolean isValid(String value) {
    return StringUtils.isBlank(value) || TRACKING_ID_PATTERN.matcher(value).matches();
  }

  /**
   * Removes any tracking id from the analytics property map whose value is not well-formed, so the
   * page template only ever renders safe values -- regardless of how the value was stored.
   */
  public static void sanitize(Map<String, String> analyticsPropertyMap) {
    if (analyticsPropertyMap == null) {
      return;
    }
    for (String propertyName : TRACKING_ID_PROPERTIES) {
      String value = analyticsPropertyMap.get(propertyName);
      if (StringUtils.isNotBlank(value) && !isValid(value)) {
        LOG.warn("Ignoring malformed analytics tracking id for " + propertyName);
        analyticsPropertyMap.put(propertyName, "");
      }
    }
  }
}
