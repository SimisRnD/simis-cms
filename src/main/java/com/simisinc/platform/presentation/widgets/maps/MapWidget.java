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

package com.simisinc.platform.presentation.widgets.maps;

import com.simisinc.platform.application.maps.FindMapTilesCredentialsCommand;
import com.simisinc.platform.domain.model.maps.MapCredentials;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

import org.apache.commons.lang3.StringUtils;

/**
 * Displays a Map based on the supplied coordinates
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class MapWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String LEAFLET_JSP = "/maps/leaflet-js_map.jsp";
  static String APPLE_MAP_JSP = "/maps/apple_map.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine the mapping service
    MapCredentials mapCredentials = FindMapTilesCredentialsCommand.getCredentials();
    if (mapCredentials == null) {
      LOG.debug("Skipping - map service not defined");
      return context;
    }
    context.getRequest().setAttribute("mapCredentials", mapCredentials);

    // Determine the geo point
    String coordinates = context.getPreferences().get("coordinates");
    String latitude = context.getPreferences().get("latitude");
    String longitude = context.getPreferences().get("longitude");

    // Check for a unified coordinates value
    if (!StringUtils.isBlank(coordinates) && coordinates.contains(",")) {
      latitude = coordinates.substring(0, coordinates.indexOf(",")).trim();
      longitude = coordinates.substring(coordinates.indexOf(",") + 1).trim();
    }

    if (StringUtils.isBlank(latitude) || StringUtils.isBlank(longitude) ||
        "-1".equals(latitude) || "-1".equals(longitude) ||
        "0.0".equals(latitude) || "0.0".equals(longitude) ||
        "0".equals(latitude) || "0".equals(longitude)) {
      LOG.debug("Skipping - no geo point");
      return context;
    }
    context.getRequest().setAttribute("latitude", latitude);
    context.getRequest().setAttribute("longitude", longitude);

    // Determine optional map info
    String mapHeight = context.getPreferences().getOrDefault("mapHeight", "290");
    context.getRequest().setAttribute("mapHeight", mapHeight);
    int mapZoomLevelValue = Integer.parseInt(context.getPreferences().getOrDefault("mapZoomLevel", "12"));
    context.getRequest().setAttribute("mapZoomLevel", String.valueOf(mapZoomLevelValue));

    // Determine optional marker info for the geo-point
    String showMarker = context.getPreferences().getOrDefault("showMarker", "true");
    context.getRequest().setAttribute("showMarker", showMarker);
    String markerTitle = context.getPreferences().get("markerTitle");
    context.getRequest().setAttribute("markerTitle", markerTitle);
    String markerText = context.getPreferences().get("markerText");
    context.getRequest().setAttribute("markerText", markerText);

    if ("apple".equals(mapCredentials.getService())) {
      context.setJsp(APPLE_MAP_JSP);
    } else {
      context.setJsp(LEAFLET_JSP);
    }
    return context;
  }
}
