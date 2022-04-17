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

package com.simisinc.platform.application.admin;

import com.simisinc.platform.application.items.SaveItemCommand;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.commons.validator.routines.UrlValidator;

import java.util.List;

/**
 * Functions for validating dataset data rows
 *
 * @author matt rajkowski
 * @created 2/27/20 2:25 PM
 */
public class DatasetRecordCommand {

  private static Log LOG = LogFactory.getLog(DatasetRecordCommand.class);

  /**
   * Validates the content of the row
   *
   * @param row
   * @param fieldMappings
   * @return
   */
  public static String validateRow(String[] row, List<String> fieldMappings) {
    // Name is required
    int nameIdx = fieldMappings.indexOf("name");
    if (nameIdx == -1) {
      return "Name is a required field";
    }
    String name = row[nameIdx];
    if (StringUtils.isBlank(name) || "null".equalsIgnoreCase(name)) {
      return "Name is blank";
    }
    name = name.toLowerCase().trim();
    boolean hasValidChar = false;
    for (int i = 0; i < name.length(); i++) {
      if (SaveItemCommand.allowedChars.indexOf(name.charAt(i)) > -1) {
        hasValidChar = true;
        break;
      }
    }
    if (!hasValidChar) {
      return "Name is not valid: " + name;
    }

    // URL validation
    int urlIdx = fieldMappings.indexOf("url");
    if (urlIdx > -1) {
      String url = row[urlIdx];
      if (StringUtils.isNotBlank(url) && !"null".equalsIgnoreCase(url)) {
        String[] schemes = {"http", "https"};
        UrlValidator urlValidator = new UrlValidator(schemes);
        if (!urlValidator.isValid(url)) {
          return "URL is invalid";
        }
      }
    }

    // URL Image validation
    int imageUrlIdx = fieldMappings.indexOf("imageUrl");
    if (imageUrlIdx > -1) {
      String imageUrl = row[imageUrlIdx];
      if (StringUtils.isNotBlank(imageUrl) && !"null".equalsIgnoreCase(imageUrl)) {
        String[] schemes = {"http", "https"};
        UrlValidator urlValidator = new UrlValidator(schemes);
        if (!urlValidator.isValid(imageUrl)) {
          return "URL is invalid";
        }
      }
    }

    // Email validation
    int emailIdx = fieldMappings.indexOf("email");
    if (emailIdx > -1) {
      String email = row[emailIdx];
      if (StringUtils.isNotBlank(email) && !"null".equalsIgnoreCase(email)) {
        EmailValidator emailValidator = EmailValidator.getInstance(false);
        if (!emailValidator.isValid(email)) {
          return "Email address is invalid";
        }
      }
    }

    // Geopoints
    int geopointIdx = fieldMappings.indexOf("geopoint");
    if (geopointIdx > -1) {
      // [lon, lat]
      String value = row[geopointIdx];
      if (value != null) {
        value = value.trim();
      }
      if (StringUtils.isNotBlank(value) && !"null".equalsIgnoreCase(value)) {
        if (!value.contains(",")) {
          return "Incorrect Geopoint, requires [lon,lat]";
        }
        String longitude = value.substring(0, value.indexOf(",")).trim();
        if (longitude.startsWith("[")) {
          longitude = longitude.substring(1);
          try {
            Double.valueOf(value);
          } catch (NumberFormatException nfe) {
            return "Longitude value must be a decimal";
          }
        }
        String latitude = value.substring(value.indexOf(",") + 1).trim();
        if (latitude.endsWith("]")) {
          latitude = latitude.substring(0, latitude.indexOf("]"));
          try {
            Double.valueOf(value);
          } catch (NumberFormatException nfe) {
            return "Latitude value must be a decimal";
          }
        }
        if (longitude.length() < 1 || latitude.length() < 1) {
          LOG.warn("Geopoint looks incorrect: " + value);
          return "Incorrect Geopoint, requires [lon,lat]";
        }
      }
    }

    int latitudeIdx = fieldMappings.indexOf("latitude");
    if (latitudeIdx > -1) {
      String value = row[latitudeIdx];
      if (value != null) {
        value = value.trim();
      }
      if (StringUtils.isNotBlank(value) && !"null".equalsIgnoreCase(value)) {
        try {
          Double.valueOf(value);
        } catch (NumberFormatException nfe) {
          return "Latitude value must be a decimal";
        }
      }
    }

    int longitudeIdx = fieldMappings.indexOf("longitude");
    if (longitudeIdx > -1) {
      String value = row[longitudeIdx];
      if (value != null) {
        value = value.trim();
      }
      if (StringUtils.isNotBlank(value) && !"null".equalsIgnoreCase(value)) {
        try {
          Double.valueOf(value);
        } catch (NumberFormatException nfe) {
          return "Longitude value must be a decimal";
        }
      }
    }

    int costIdx = fieldMappings.indexOf("cost");
    if (costIdx > -1) {
      String value = row[costIdx];
      if (value != null) {
        value = value.trim();
      }
      if (StringUtils.isNotBlank(value) && !"null".equalsIgnoreCase(value)) {
        try {
          Double.valueOf(value);
        } catch (NumberFormatException nfe) {
          return "Cost value must be a number";
        }
      }
    }

    return null;
  }
}


