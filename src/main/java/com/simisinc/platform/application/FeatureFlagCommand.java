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

package com.simisinc.platform.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

/**
 * Lightweight per-feature toggles (issue #410), stored as {@code features.*} site properties and
 * edited from the admin UI at {@code /admin/feature-flags} (the {@code sitePropertiesEditor} widget,
 * same generic mechanism used by every other {@code /admin/*-properties} settings page).
 *
 * <p>
 * Reads go through {@link LoadSitePropertyCommand}, which is backed by
 * {@code CacheManager.SYSTEM_PROPERTY_PREFIX_CACHE} -- a 5-minute-expiry, 1-minute-refresh Caffeine
 * cache (bounded so a multi-instance deployment doesn't serve a stale flag value indefinitely on a
 * node that didn't handle the save; see issue behind commit 5653e1b4). On the instance that handled
 * the save, {@code SitePropertyRepository.saveAll()} calls
 * {@code CacheManager.invalidateKey(SYSTEM_PROPERTY_PREFIX_CACHE, "features")}, so a toggle from
 * {@code /admin/feature-flags} is visible there on the very next read. Any other instance picks up
 * the change within about a minute via the cache's own refresh, not instantly.
 * </p>
 *
 * @author elizabeth houser
 */
public class FeatureFlagCommand {

  /** The site property namespace every feature flag lives under: {@code features.<name>}. */
  public static final String PREFIX = "features";

  private FeatureFlagCommand() {
  }

  /**
   * @param name the flag's name, without the {@code features.} prefix (e.g. {@code "layout-editor"})
   * @return true only if {@code features.<name>} is set to exactly {@code "true"}; false for any
   *         other value, including a flag that was never seeded
   */
  public static boolean isEnabled(String name) {
    if (StringUtils.isBlank(name)) {
      return false;
    }
    return LoadSitePropertyCommand.loadByNameAsBoolean(PREFIX + "." + name);
  }

  /**
   * @return the currently-enabled flag names (without the {@code features.} prefix), sorted, for
   *         logging a startup summary of the feature posture
   */
  public static List<String> getActiveFlagNames() {
    List<String> activeNames = new ArrayList<>();
    Map<String, String> propertyMap = LoadSitePropertyCommand.loadNonEmptyAsMap(PREFIX);
    if (propertyMap != null) {
      for (Map.Entry<String, String> entry : propertyMap.entrySet()) {
        if ("true".equals(entry.getValue())) {
          activeNames.add(StringUtils.removeStart(entry.getKey(), PREFIX + "."));
        }
      }
    }
    Collections.sort(activeNames);
    return activeNames;
  }
}
