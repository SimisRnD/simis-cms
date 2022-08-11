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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.maps.WorldCity;
import com.simisinc.platform.domain.model.maps.ZipCode;
import com.simisinc.platform.infrastructure.persistence.maps.WorldCityRepository;
import com.simisinc.platform.infrastructure.persistence.maps.ZipCodeRepository;

/**
 * Methods to update an item's geo point
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

  public static Item updateGeoPointByRelativeLocation(Item item) {
    // In addition to CheckGeoPoint, consider using the World Cities geocode
    if (!item.hasGeoPoint() && StringUtils.isNotBlank(item.getPostalCode())) {
      // Find a zipcode geopoint, to estimate the lat/long
      ZipCode zipCode = ZipCodeRepository.findByCode(item.getPostalCode());
      if (zipCode != null && zipCode.hasGeoPoint()) {
        item.setLatitude(zipCode.getLatitude());
        item.setLongitude(zipCode.getLongitude());
      }
    }
    if (!item.hasGeoPoint() && StringUtils.isNotBlank(item.getCity()) && StringUtils.isNotBlank(item.getState())) {
      String region = item.getState();
      String country = "us";
      if (item.getCountry() != null && !"united states".equalsIgnoreCase(item.getCountry())) {
        // Leave blank or convert to 2-digit value
        region = null;
        country = null;
      }
      // Find a world cities geopoint based on country (US for now), to estimate the lat/long
      WorldCity worldCity = WorldCityRepository.findByCityRegionCountry(item.getCity(), region, country);
      if (worldCity != null) {
        item.setLatitude(worldCity.getLatitude());
        item.setLongitude(worldCity.getLongitude());
      }
    }
    return item;
  }
}
