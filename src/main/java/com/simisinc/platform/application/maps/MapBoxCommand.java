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
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.jobrunr.utils.resilience.RateLimiter;

import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

import static org.jobrunr.utils.resilience.RateLimiter.Builder.rateLimit;
import static org.jobrunr.utils.resilience.RateLimiter.SECOND;

/**
 * MapBox integration
 *
 * @author matt rajkowski
 * @created 4/26/18 8:16 AM
 */
public class MapBoxCommand {

  private static Log LOG = LogFactory.getLog(MapBoxCommand.class);

  private static RateLimiter rateLimit = rateLimit().atRequests(100).per(SECOND);

  public static Item updateGeoPoint(Item item) {
    // Skip when not enough data
    if (StringUtils.isBlank(item.getCity()) ||
        StringUtils.isBlank(item.getState()) ||
        StringUtils.isBlank(item.getStreet())) {
      LOG.debug("Skipping... not enough information");
      return item;
    }
    // Get the access token
    String accessToken = LoadSitePropertyCommand.loadByName("maps.mapbox.accesstoken");
    if (StringUtils.isBlank(accessToken)) {
      LOG.debug("Skipping... no MapBox token found");
      return item;
    }

    // Determine the address
    StringBuilder sb = new StringBuilder();
    sb.append(item.getStreet()).append(",");
    sb.append(item.getCity()).append(",");
    sb.append(item.getState());
    if (StringUtils.isNotBlank(item.getPostalCode())) {
      sb.append(",").append(item.getPostalCode());
    }

    // optional {country} parameter <https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2>
    // A location; a place name for forward geocoding or a coordinate pair (longitude, latitude) for reverse geocoding.
    // The {proximity} parameter biases search results within 5 miles of a specific location given in {longitude},{latitude}
    String query = null;
    try {
      query = URLEncoder.encode(sb.toString(), "UTF-8");
      LOG.debug("query: " + query);
    } catch (Exception e) {
      LOG.error("Could not encode the address: " + sb.toString());
      return item;
    }

    // https://api.mapbox.com/geocoding/v5/mapbox.places/Wawa,23321.json?limit=1&access_token=pk.eyJ1IjoibWF0dHNpbWlzIiwiYSI6ImNqZ2doZjh2bjB6NHUyd29pbm5yc3Y5cjQifQ.Qio8_yBfJeesjNHUDrLKtw
    String url = "https://api.mapbox.com/geocoding/v5/mapbox.places/" + query + ".json?limit=1&access_token=" + accessToken;
    try {
      // Use rate limiting
      while (!rateLimit.isAllowed()) {
        TimeUnit.MILLISECONDS.sleep(100);
      }
      // HTTP request
      HttpClient client = HttpClientBuilder.create().build();
      HttpGet request = new HttpGet(url);
      HttpResponse response = client.execute(request);
      HttpEntity entity = response.getEntity();
      String value = EntityUtils.toString(entity);

      if (LOG.isDebugEnabled()) {
        LOG.debug(value);
      }

      // "coordinates":[-76.349402,38.841628]
      int coordIdx = value.indexOf("\"coordinates\":");
      if (coordIdx == -1) {
        LOG.debug("Skipping... coordinates value not found");
        return item;
      }
      String longitude = value.substring(value.indexOf("[", coordIdx) + 1, value.indexOf(",", coordIdx));
      String latitude = value.substring(value.indexOf(",", coordIdx) + 1, value.indexOf("]", coordIdx));
      if (StringUtils.isNotBlank(latitude) && StringUtils.isNotBlank(longitude)) {
        LOG.debug("Lat/Long has been set: " + latitude + ", " + longitude);
        item.setLatitude(Double.valueOf(latitude));
        item.setLongitude(Double.valueOf(longitude));
      }
    } catch (Exception e) {
      // Anything could have gone wrong - limits exceeded, bad token, communication issue
      LOG.warn("Geocoder issues: " + e.getMessage());
    }
    return item;
  }

}
