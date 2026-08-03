/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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
import java.util.TreeMap;

/**
 * Which admin settings page owns a {@code site_properties} root prefix (the segment before the
 * first dot) -- shared by {@code IntegrationsHubWidget} (issue #454) and {@code
 * IntegrationRegistryWidget} (issue #455) so the mapping only lives in one place. Derived from
 * {@code admin-layout.xml}'s {@code sitePropertiesEditor} prefix registrations. A prefix with no
 * entry here (currently just {@code oauth}) has no admin editor at all.
 */
public class SitePropertySettingsPageCommand {

  private static final Map<String, String> PREFIX_TO_PAGE = new TreeMap<>();
  static {
    PREFIX_TO_PAGE.put("ecommerce", "/admin/ecommerce-properties");
    PREFIX_TO_PAGE.put("mail", "/admin/mail-properties");
    PREFIX_TO_PAGE.put("captcha", "/admin/captcha-properties");
    PREFIX_TO_PAGE.put("mailing-list", "/admin/mailing-list-properties");
    PREFIX_TO_PAGE.put("bi", "/admin/bi-properties");
    PREFIX_TO_PAGE.put("social", "/admin/social-media-settings");
    PREFIX_TO_PAGE.put("elearning", "/admin/elearning-properties");
  }

  private SitePropertySettingsPageCommand() {
    // Static utility, not instantiated
  }

  /** @return the settings page that owns this root prefix, or null when there is no admin editor for it */
  public static String findPageUrl(String prefix) {
    return PREFIX_TO_PAGE.get(prefix);
  }

  /** @return the segment before the first dot, matching SitePropertyRepository.saveAll's cache-key convention */
  public static String rootPrefixOf(String propertyName) {
    if (propertyName == null) {
      return null;
    }
    int dot = propertyName.indexOf('.');
    return dot > 0 ? propertyName.substring(0, dot) : propertyName;
  }
}
