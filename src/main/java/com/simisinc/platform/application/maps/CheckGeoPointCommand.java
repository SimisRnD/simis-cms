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

package com.simisinc.platform.application.maps;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.items.Item;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/26/18 8:16 AM
 */
public class CheckGeoPointCommand {

  private static Log LOG = LogFactory.getLog(CheckGeoPointCommand.class);

  public static Item updateGeoPoint(Item item) {
    // Skip already encoded items
    if (item.hasGeoPoint()) {
      LOG.debug("Skipping... has lat/long");
      return item;
    }
    // Skip when not enough data
    if (StringUtils.isBlank(item.getCity()) ||
        StringUtils.isBlank(item.getState()) ||
        StringUtils.isBlank(item.getStreet())) {
      LOG.debug("Skipping... not enough information");
      return item;
    }
    // Determine the service to use
    String geocoderService = LoadSitePropertyCommand.loadByName("maps.service.geocoder");
    if (geocoderService == null) {
      LOG.debug("Skipping... no maps.service.geocoder service");
      return item;
    }
    if ("mapbox".equalsIgnoreCase(geocoderService)) {
      String accessToken = LoadSitePropertyCommand.loadByName("maps.mapbox.accesstoken");
      if (StringUtils.isNotBlank(accessToken)) {
        return MapBoxCommand.updateGeoPoint(item);
      }
    }
    if ("nominatim".equalsIgnoreCase(geocoderService)) {
      return NominatimCommmand.updateGeoPoint(item);
    }
    return item;
  }
}
