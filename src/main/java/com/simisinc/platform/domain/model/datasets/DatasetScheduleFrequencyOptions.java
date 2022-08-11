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

package com.simisinc.platform.domain.model.datasets;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An interval for how often a dataset should be refreshed
 *
 * @author matt rajkowski
 * @created 8/6/2022 10:05 AM
 */
public class DatasetScheduleFrequencyOptions {

  public static final Map<String, String> map = initMap();

  private DatasetScheduleFrequencyOptions() {
  }

  private static Map<String, String> initMap() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("PT1H", "Hourly");
    map.put("P1D", "Daily");
    map.put("P7D", "Weekly");
    map.put("P14D", "Every 2 weeks");
    map.put("P1M", "Every month");
    map.put("P2M", "Every 2 months");
    map.put("P3M", "Every 3 months");
    map.put("P4M", "Every 4 months");
    map.put("P5M", "Every 5 months");
    map.put("P6M", "Every 6 months");
    map.put("P1Y", "Yearly");
    return Collections.unmodifiableMap(map);
  }
}
