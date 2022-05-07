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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.simisinc.platform.application.admin.LoadGeoJsonFeedCommand;
import com.simisinc.platform.application.gis.portsmouth.PopulatePortsmouthDataCommand;
import com.simisinc.platform.application.maps.FindMapTilesCredentialsCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.domain.model.maps.MapCredentials;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

import org.apache.commons.lang3.StringUtils;
import org.geojson.Feature;
import org.geojson.GeoJsonObject;

/**
 * Map widget with app features
 *
 * @author matt rajkowski
 * @created 3/13/19 3:19 PM
 */
public class PropertyMapAppWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String MAP_APP_JSP = "/maps/property-map-app.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Check preferences
    context.getRequest().setAttribute("titleHtml", context.getPreferences().getOrDefault("titleHtml", null));
    String city = context.getPreferences().getOrDefault("city", null);

    // Determine the mapping service
    MapCredentials mapCredentials = FindMapTilesCredentialsCommand.getCredentials();
    if (mapCredentials == null) {
      LOG.debug("Skipping - map service not defined");
      return context;
    }
    context.getRequest().setAttribute("mapCredentials", mapCredentials);

    // Determine the map dataset
    String datasetName = context.getPreferences().get("dataset");
    if (StringUtils.isBlank(datasetName)) {
      LOG.debug("Skipping - no dataset value found");
      return context;
    }
    Dataset dataset = DatasetRepository.findByName(datasetName);
    if (dataset == null) {
      LOG.debug("Skipping - dataset not found");
      return context;
    }

    // Load the dataset values
    try {
      List<GeoJsonObject> recordList = LoadGeoJsonFeedCommand.loadRecords(dataset);
      if (recordList == null) {
        LOG.debug("Skipping - could not read records");
        return context;
      }

      // Sort the list
      recordList.sort(Comparator.comparing(o -> ((String) ((Feature) o).getProperty("SITE_ADDRE"))));

      // Prepare and calculate some things
      List<String> zoningList = new ArrayList<>();
      for (GeoJsonObject record : recordList) {
        Feature feature = (Feature) record;
        // Track unique zones
        String value = feature.getProperty("ZONING");
        if (StringUtils.isNotBlank(value)) {
          if (!zoningList.contains(value)) {
            zoningList.add(value);
          }
        }

        // Calculate distances
        if ("portsmouth".equals(city)) {
          PopulatePortsmouthDataCommand.populateFeatureData(feature);
        }
      }
      context.getRequest().setAttribute("zoningList", zoningList);
      context.getRequest().setAttribute("recordList", recordList);

    } catch (Exception e) {
      LOG.debug("Skipping - error loading data", e);
      return context;
    }


    // Determine the center geo point from data, or a preset

    String latitude = context.getPreferences().get("latitude");
    String longitude = context.getPreferences().get("longitude");
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
    String mapHeight = context.getPreferences().getOrDefault("mapHeight", "290px");
    context.getRequest().setAttribute("mapHeight", mapHeight);
    int mapZoomLevelValue = Integer.parseInt(context.getPreferences().getOrDefault("mapZoomLevel", "13"));
    context.getRequest().setAttribute("mapZoomLevel", String.valueOf(mapZoomLevelValue));


    context.setJsp(MAP_APP_JSP);
    return context;
  }
}
