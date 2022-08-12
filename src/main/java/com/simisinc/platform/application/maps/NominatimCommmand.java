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

import static org.jobrunr.utils.resilience.RateLimiter.SECOND;
import static org.jobrunr.utils.resilience.RateLimiter.Builder.rateLimit;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.utils.resilience.RateLimiter;

import com.simisinc.platform.ApplicationInfo;
import com.simisinc.platform.application.http.HttpGetToStringCommand;
import com.simisinc.platform.domain.model.items.Item;

/**
 * Nominatim integration
 *
 * @author matt rajkowski
 * @created 6/24/21 9:00 PM
 */
public class NominatimCommmand {

  private static Log LOG = LogFactory.getLog(NominatimCommmand.class);

  private static RateLimiter rateLimit = rateLimit().at1Request().per(SECOND);

  public static Item updateGeoPoint(Item item) {
    // Skip when not enough data
    if (StringUtils.isBlank(item.getCity()) ||
        StringUtils.isBlank(item.getState()) ||
        StringUtils.isBlank(item.getStreet())) {
      LOG.debug("Skipping... not enough information");
      return item;
    }

    // Determine the address
    // https://nominatim.org/release-docs/develop/api/Search/#parameters
    //  street=<housenumber> <streetname>
    //  city=<city>
    //  county=<county>
    //  state=<state>
    //  country=<country>
    //  postalcode=<postalcode>
    StringBuilder query = new StringBuilder();
    try {
      query.append("&street=").append(URLEncoder.encode(item.getStreet(), "UTF-8"));
      query.append("&city=").append(URLEncoder.encode(item.getCity(), "UTF-8"));
      query.append("&state=").append(URLEncoder.encode(item.getState(), "UTF-8"));
      if (StringUtils.isNotBlank(item.getCountry())) {
        query.append("&country=").append(URLEncoder.encode(item.getCountry(), "UTF-8"));
      }
      if (StringUtils.isNotBlank(item.getPostalCode())) {
        query.append("&postalcode=").append(URLEncoder.encode(item.getPostalCode(), "UTF-8"));
      }
      LOG.debug("query: " + query);
    } catch (Exception e) {
      LOG.error("Could not encode the address: " + query.toString());
      return item;
    }

    // https://nominatim.openstreetmap.org/search?<params>
    String url = "https://nominatim.openstreetmap.org/search?format=json&limit=1" + query;
    try {
      // Use rate limiting
      while (!rateLimit.isAllowed()) {
        TimeUnit.MILLISECONDS.sleep(100);
      }
      // HTTP request
      Map<String, String> headers = new HashMap<>();
      headers.put("User-Agent", ApplicationInfo.PRODUCT_NAME);
      String value = HttpGetToStringCommand.execute(url, headers);
      if (value == null) {
        return item;
      }

      // [
      //  {
      //    "place_id": 139440771,
      //    "licence": "Data © OpenStreetMap contributors, ODbL 1.0. https://osm.org/copyright",
      //    "osm_type": "way",
      //    "osm_id": 204733765,
      //    "boundingbox": [
      //      "35.7655134",
      //      "35.7657604",
      //      "-78.7838912",
      //      "-78.7835035"
      //    ],
      //    "lat": "35.765636900000004",
      //    "lon": "-78.78369735",
      //    "display_name": "1148, Kildaire Farm Road, Kildaire Plaza, Cary, Wake County, North Carolina, 27511, United States",
      //    "class": "building",
      //    "type": "yes",
      //    "importance": 0.6309999999999999
      //  }
      //]

      if (LOG.isDebugEnabled()) {
        LOG.debug(value);
      }

      int latIdx = value.indexOf("\"lat\":");
      int lonIdx = value.indexOf("\"lon\":");
      if (latIdx == -1 || lonIdx == -1) {
        LOG.debug("Skipping... coordinates value not found");
        return item;
      }

      int latIdxStart = value.indexOf("\"", latIdx + 6) + 1;
      int latIdxEnd = value.indexOf("\"", latIdxStart);
      String latitude = value.substring(latIdxStart, latIdxEnd);

      int lonIdxStart = value.indexOf("\"", lonIdx + 6) + 1;
      int lonIdxEnd = value.indexOf("\"", lonIdxStart);
      String longitude = value.substring(lonIdxStart, lonIdxEnd);

      if (StringUtils.isNotBlank(latitude) && StringUtils.isNotBlank(longitude)) {
        LOG.debug("Lat/Long has been set: " + latitude + ", " + longitude);
        item.setLatitude(Double.parseDouble(latitude));
        item.setLongitude(Double.parseDouble(longitude));
      }
    } catch (Exception e) {
      // Anything could have gone wrong - limits exceeded, bad token, communication issue
      LOG.warn("Geocoder issues: " + e.getMessage());
    }
    return item;
  }

}
