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

package com.simisinc.platform.application.datasets;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.text.StringTokenizer;
import org.apache.commons.text.WordUtils;

import com.simisinc.platform.application.cms.UrlCommand;

/**
 * Functions for field definition options
 *
 * @author matt rajkowski
 * @created 6/1/2022 8:50 PM
 */
public class DatasetFieldOptionCommand {

  private static Log LOG = LogFactory.getLog(DatasetFieldOptionCommand.class);

  public static boolean hasOption(String options, String term) {
    return options.contains(term);
  }

  public static String extractValue(String token, String term) {
    int startIdx = token.indexOf(term + "(");
    if (startIdx == -1) {
      return null;
    }
    int endIdx = token.indexOf(")", startIdx);
    if (endIdx == -1) {
      return null;
    }
    String extractedValue = token.substring(startIdx + term.length() + 1, endIdx);
    if (extractedValue.startsWith("\"") && extractedValue.indexOf("\"") != extractedValue.lastIndexOf("\"")) {
      return extractedValue.substring(1, extractedValue.length() - 1);
    } else {
      return null;
    }
  }

  public static String[] extractVars(String token, String term) {
    int startIdx = token.indexOf(term + "(");
    if (startIdx == -1) {
      return new String[0];
    }
    int endIdx = token.indexOf(")", startIdx);
    if (endIdx == -1) {
      return new String[0];
    }

    String input = token.substring(startIdx + term.length() + 1, endIdx);
    StringTokenizer st = StringTokenizer.getCSVInstance(input);
    return st.getTokenArray();
  }

  public static boolean isSkipped(String options, String value) {
    return isSkipped(options, value, null, -1);
  }

  public static boolean isSkipped(String options, String value, Map<String, String> uniqueColumnValueMap,
      int columnId) {
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
    if (notEqualsValue != null && value.equalsIgnoreCase(notEqualsValue)) {
      // Skip the record
      return true;
    }
    // Check for a contains("") value (value must contain this value to be valid)
    String containsValue = extractValue(options, "contains");
    if (containsValue != null && !value.toLowerCase().contains(containsValue.toLowerCase())) {
      // Skip the record
      return true;
    }
    // Check for a startsWith("") value (value must start with this value to be valid)
    String startsWithValue = extractValue(options, "startsWith");
    if (startsWithValue != null && !value.toLowerCase().startsWith(startsWithValue.toLowerCase())) {
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

    // Handle the word null
    if ("null".equals(value)) {
      value = "";
    }

    // No options, so return
    if (StringUtils.isBlank(options)) {
      return value;
    }

    // Treat as blank for the functions
    if (value == null) {
      value = "";
    }

    // Turn the string into an arraylist to retrieve functions
    StringTokenizer st = new StringTokenizer(options, ";");
    List<String> tokenList = st.getTokenList();

    // Process the functions in order
    for (String token : tokenList) {
      if ("uriEncode".equals(token)) {
        // URI Encode
        value = UrlCommand.encodeUri(value);
        if ("#".equals(value)) {
          value = "";
        }
      } else if ("validateUrl".equals(token)) {
        // Validate the URL
        if (!UrlCommand.isUrlValid(value)) {
          value = "";
        }
      } else if (token.startsWith("setValue(")) {
        // Set Value
        value = extractValue(token, "setValue");
      } else if ("caps".equals(token) || "capitalize".equals(token)) {
        // Capitalize
        value = WordUtils.capitalizeFully(value, ' ', '.', '-', '/', '\'');
        value = value.replaceAll(" And ", " and ");
      } else if ("uppercase".equals(token)) {
        // Uppercase
        value = StringUtils.upperCase(value);
      } else if ("lowercase".equals(token)) {
        // Lowercase
        value = StringUtils.lowerCase(value);
      } else if (token.startsWith("prepend(")) {
        // Prepend
        value = extractValue(token, "prepend") + value;
      } else if (token.startsWith("append(")) {
        // Append
        value = value + extractValue(token, "append");
      } else if (token.startsWith("trim(")) {
        // Trim
        value = value.trim();
      } else if (token.startsWith("replace(")) {
        // Replace value with another value
        String[] vars = extractVars(token, "replace");
        if (vars != null && vars.length == 2) {
          value = StringUtils.replace(value, vars[0], vars[1]);
        }
      } else if (token.startsWith("blank(")) {
        // Blank the matching term
        String trimToNullValue = extractValue(token, "blank");
        if (trimToNullValue != null && trimToNullValue.equals(value)) {
          value = "";
        }
      } else {
        if (LOG.isDebugEnabled()) {
          // Ignore the tokens which skip records
          if (!token.startsWith("equals(") &&
              !token.startsWith("notEquals(") &&
              !token.startsWith("contains(") &&
              !token.startsWith("startsWith(") &&
              !token.startsWith("skipDuplicates")) {
            LOG.debug("Token not found: " + token);
          }
        }
      }
    }
    return value;
  }
}
