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

package com.simisinc.platform.domain.model.cms;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A sitemap change frequency
 *
 * @author matt rajkowski
 * @created 8/2/2022 6:02 PM
 */
public class SitemapChangeFrequencyOptions {

  public static final Map<String, String> map = initMap();

  private SitemapChangeFrequencyOptions() {
  }

  private static Map<String, String> initMap() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("always", "Always");
    map.put("hourly", "Hourly");
    map.put("daily", "Daily");
    map.put("weekly", "Weekly");
    map.put("monthly", "Monthly");
    map.put("yearly", "Yearly");
    map.put("never", "Never");
    return Collections.unmodifiableMap(map);
  }

}
