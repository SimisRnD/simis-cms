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

/**
 * Methods for world cities region integration
 *
 * @author matt rajkowski
 * @created 5/25/18 4:45 PM
 */
public class ValidateGeoRegion {

  private static final String allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  private static Log LOG = LogFactory.getLog(ValidateGeoRegion.class);

  public static boolean isValidWorldCitiesRegion(String region) {
    if (StringUtils.isBlank(region)) {
      return false;
    }
    for (int i = 0; i < region.length(); i++) {
      if (allowedChars.indexOf(region.charAt(i)) == -1) {
        return false;
      }
    }
    return true;
  }

}
