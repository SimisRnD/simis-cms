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

import com.simisinc.platform.SiteProperty;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads site properties, using a cache if possible
 *
 * @author matt rajkowski
 * @created 4/23/18 3:39 PM
 */
public class LoadSitePropertyCommand {

  private static Log LOG = LogFactory.getLog(LoadSitePropertyCommand.class);


  public static Map<String, String> loadAsMap(String prefix) {
    return loadAsMap(prefix, true);
  }

  public static Map<String, String> loadNonEmptyAsMap(String prefix) {
    return loadAsMap(prefix, false);
  }

  private static Map<String, String> loadAsMap(String prefix, boolean includeEmptyValues) {
    // Use the cache
    List<SiteProperty> sitePropertyList = (List<SiteProperty>) CacheManager.getLoadingCache(CacheManager.SYSTEM_PROPERTY_PREFIX_CACHE).get(prefix);
    // Return the requested map
    Map<String, String> sitePropertyMap = new HashMap<>();
    for (SiteProperty siteProperty : sitePropertyList) {
      String value = siteProperty.getValue();
      if (includeEmptyValues || StringUtils.isNotBlank(value)) {
        sitePropertyMap.put(siteProperty.getName(), value);
      }
    }
    return sitePropertyMap;
  }

  public static String loadByName(String name, String defaultValue) {
    String value = loadByName(name);
    if (StringUtils.isBlank(value)) {
      return defaultValue;
    }
    return value;
  }

  public static String loadByName(String name) {
    String prefix = name.substring(0, name.indexOf("."));
    List<SiteProperty> sitePropertyList = (List<SiteProperty>) CacheManager.getLoadingCache(CacheManager.SYSTEM_PROPERTY_PREFIX_CACHE).get(prefix);
    if (sitePropertyList == null) {
      return null;
    }
    for (SiteProperty siteProperty : sitePropertyList) {
      if (name.equals(siteProperty.getName())) {
        return siteProperty.getValue();
      }
    }
    return null;
  }
}
