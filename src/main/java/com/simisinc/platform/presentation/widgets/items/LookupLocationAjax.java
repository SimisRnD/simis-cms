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

package com.simisinc.platform.presentation.widgets.items;

import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.maps.WorldCity;
import com.simisinc.platform.infrastructure.persistence.maps.WorldCityRepository;
import com.simisinc.platform.infrastructure.persistence.maps.WorldCitySpecification;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Performs auto-complete queries
 *
 * @author matt rajkowski
 * @created 5/26/18 6:00 PM
 */
public class LookupLocationAjax extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public WidgetContext execute(WidgetContext context) {

    // Determine the query
    String query = context.getParameter("q");
    LOG.debug("q: " + query);
    if (query == null) {
      context.setJson("[]");
      return context;
    }
    if (StringUtils.isNumeric(query)) {
      context.setJson("[]");
      return context;
    }

    // Determine the city/region being searched
    WorldCitySpecification specification = new WorldCitySpecification();
    String city, region;
    int idx = query.indexOf(",");
    if (idx > -1) {
      city = query.substring(0, idx).trim();
      region = query.substring(idx + 1).trim();
      specification.setCity(city);
      if (region.length() == 2) {
        specification.setRegion(region);
      }
    } else {
      // No comma, so search the cities
      specification.setSearchCity(query.trim());
    }

    // Determine the results to be shown
    List<WorldCity> list = WorldCityRepository.findAll(specification, null);
    StringBuilder sb = new StringBuilder();
    boolean isFirst = true;
    for (WorldCity worldCity : list) {
      // For usability, don't show cities that are not the most populated around the world
      if (!isFirst && !"us".equals(worldCity.getCountry())) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append(",");
      }
      sb.append("\"");
      sb.append(JsonCommand.toJson(worldCity.getCity()));
      if (isFirst) {
        // Don't need to show the region for the first entry
        if ("us".equals(worldCity.getCountry())) {
          sb.append(", ").append(JsonCommand.toJson(worldCity.getRegion()));
        }
        isFirst = false;
      } else {
        sb.append(", ").append(JsonCommand.toJson(worldCity.getRegion()));
      }
      sb.append("\"");
    }
    context.setJson("[" + sb.toString() + "]");
    return context;
  }
}
