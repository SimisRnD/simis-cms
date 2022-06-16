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

import com.simisinc.platform.application.cms.UrlCommand;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;

import java.util.Map;

/**
 * Functions for field definition options
 *
 * @author matt rajkowski
 * @created 6/1/2022 8:50 PM
 */
public class DatasetFieldOptionCommand {

  public static boolean hasOption(String options, String term) {
    return options.contains(term);
  }

  public static String extractValue(String options, String term) {
    int startIdx = options.indexOf(term + "(");
    if (startIdx == -1) {
      return null;
    }
    int endIdx = options.indexOf(")", startIdx);
    if (endIdx == -1) {
      return null;
    }
    String extractedValue = options.substring(startIdx + term.length() + 1, endIdx);
    if (extractedValue.startsWith("\"") && extractedValue.indexOf("\"") != extractedValue.lastIndexOf("\"")) {
      return extractedValue.substring(1, extractedValue.length() - 1);
    } else {
      return null;
    }
  }

  public static boolean isSkipped(String options, String value) {
    return isSkipped(options, value, null, -1);
  }

  public static boolean isSkipped(String options, String value, Map<String, String> uniqueColumnValueMap, int columnId) {
    if (StringUtils.isBlank(options)) {
      return false;
    }
    // Check for an equals("") value (value must equal this value to be valid)
    String equalsValue = extractValue(options, "equals");
    if (equalsValue != null && !value.equalsIgnoreCase(equalsValue)) {
      // Skip the record
      return true;
    }
    // Check for an notEquals("") value (value must not equal this value to be valid)
    String notEqualsValue = extractValue(options, "notEquals");
    if (value.equalsIgnoreCase(notEqualsValue)) {
      // Skip the record
      return true;
    }
    // Check for a contains("") value (value must contain this value to be valid)
    String containsValue = extractValue(options, "contains");
    if (containsValue != null && !value.toLowerCase().contains(containsValue.toLowerCase())) {
      // Skip the record
      return true;
    }

    // Other checks
    if (uniqueColumnValueMap != null && columnId > -1 && hasOption(options, "skipDuplicates")) {
      if (!uniqueColumnValueMap.containsKey(columnId + "-" + value)) {
        uniqueColumnValueMap.put(columnId + "-" + value, "0");
      } else {
        // Skip the record
        return true;
      }
    }

    return false;
  }

  public static String applyOptionsToField(String options, String value) {
    if (StringUtils.isBlank(options)) {
      return value;
    }
    // URI Encode
    if (options.contains("uriEncode")) {
      value = UrlCommand.encodeUri(value);
    }
    // Set Value
    if (options.contains("setValue")) {
      value = extractValue(options, "setValue");
    }
    // Case
    if (options.contains("caps") || options.contains("capitalize")) {
      value = WordUtils.capitalizeFully(value, ' ', '.', '-', '/', '\'');
      value = value.replaceAll(" And ", " and ");
    } else if (options.contains("uppercase")) {
      value = StringUtils.upperCase(value);
    } else if (options.contains("lowercase")) {
      value = StringUtils.lowerCase(value);
    }
    // Prepend
    if (options.contains("prepend")) {
      value = extractValue(options, "prepend") + value;
    }
    // Append
    if (options.contains("append")) {
      value = value + extractValue(options, "append");
    }
    return value;
  }
}


