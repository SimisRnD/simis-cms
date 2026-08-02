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

package com.simisinc.platform.domain.model.cms;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The business-KPI solution-page tags a web page can be marked with (issue #570). Stored directly
 * in web_pages.solution_type as free text -- like the sitemap change frequency options, this is a
 * fixed, common set offered as a convenience, not an enforced/foreign-keyed taxonomy.
 *
 * @author SimIS
 * @created 8/1/2026
 */
public class SolutionTypeOptions {

  public static final Map<String, String> map = initMap();

  private SolutionTypeOptions() {
  }

  private static Map<String, String> initMap() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("government-solution", "Government Solution");
    map.put("contract-past-performance", "Contract / Past Performance");
    map.put("careers", "Careers");
    return Collections.unmodifiableMap(map);
  }

}
