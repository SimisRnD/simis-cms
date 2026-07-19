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

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.maps.MapCredentials;

/**
 * Methods for map tile service integration
 *
 * @author matt rajkowski
 * @created 4/26/18 8:16 AM
 */
public class FindMapTilesCredentialsCommand {

  private static Log LOG = LogFactory.getLog(FindMapTilesCredentialsCommand.class);

  // A Leaflet tile URL template, like https://tiles.example.com/{z}/{x}/{y}.png
  // Restricted to URL-safe characters (the value is rendered into page javascript):
  // no quotes, whitespace, backslashes, or angle brackets
  private static final Pattern TILE_SERVER_URL_PATTERN = Pattern
      .compile("^https?://[A-Za-z0-9\\-._~:/?&=%,+{}]+$");

  public static MapCredentials getCredentials() {
    String mapService = LoadSitePropertyCommand.loadByName("maps.service.tiles");
    if (mapService == null) {
      return null;
    }
    if ("openstreetmap".equalsIgnoreCase(mapService)) {
      return new MapCredentials(mapService, null);
    } else if ("mapbox".equalsIgnoreCase(mapService)) {
      String accessToken = LoadSitePropertyCommand.loadByName("maps.mapbox.accesstoken");
      if (accessToken != null) {
        return new MapCredentials(mapService, accessToken);
      }
    } else if ("custom".equalsIgnoreCase(mapService)) {
      // A self-hosted tile server, for deployments which do not use external map services
      String tileServerUrl = validatedTileServerUrl(LoadSitePropertyCommand.loadByName("maps.custom.tileserver.url"));
      if (tileServerUrl != null) {
        // Use the canonical value; the JSPs compare it case-sensitively
        MapCredentials mapCredentials = new MapCredentials("custom", null);
        mapCredentials.setTileServerUrl(tileServerUrl);
        return mapCredentials;
      }
      LOG.warn("maps.service.tiles is 'custom' but maps.custom.tileserver.url is blank or invalid; using openstreetmap");
    }
    return new MapCredentials("openstreetmap", null);
  }

  /** Returns the url when it is a well-formed tile url template, otherwise null */
  static String validatedTileServerUrl(String url) {
    url = StringUtils.trimToNull(url);
    if (url == null) {
      return null;
    }
    if (!TILE_SERVER_URL_PATTERN.matcher(url).matches()) {
      LOG.warn("maps.custom.tileserver.url contains unsupported characters, ignoring it");
      return null;
    }
    // Leaflet requires the tile coordinate placeholders
    if (!url.contains("{z}") || !url.contains("{x}") || !url.contains("{y}")) {
      LOG.warn("maps.custom.tileserver.url must contain {z}, {x} and {y} placeholders, ignoring it");
      return null;
    }
    return url;
  }

}
