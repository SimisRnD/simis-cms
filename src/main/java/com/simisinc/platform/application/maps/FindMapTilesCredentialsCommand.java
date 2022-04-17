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
import com.simisinc.platform.domain.model.maps.MapCredentials;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Methods for map tile service integration
 *
 * @author matt rajkowski
 * @created 4/26/18 8:16 AM
 */
public class FindMapTilesCredentialsCommand {

  private static Log LOG = LogFactory.getLog(FindMapTilesCredentialsCommand.class);

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
    }
    return new MapCredentials("openstreetmap", null);
  }

}
