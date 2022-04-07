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

package com.simisinc.platform.application.json;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.text.StringEscapeUtils;

import java.util.List;
import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 9/9/19 10:47 AM
 */
public class JsonCommand {

  private static Log LOG = LogFactory.getLog(JsonCommand.class);

  public static String toJson(String value) {
    if (StringUtils.isBlank(value)) {
      return "";
    }
    // return value.replaceAll("\"", "\\\\\"").trim();
    return StringEscapeUtils.escapeJson(value);
  }

  public static StringBuilder createJsonNode(Map<String, Object> pairs) {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    boolean isFirst = true;
    for (String name : pairs.keySet()) {
      // Make sure the value exists
      Object value = pairs.get(name);
      if (value == null) {
        continue;
      }
      // @note Consider empty values
//      if (value instanceof String) {
//        if (StringUtils.isBlank(String.valueOf(value))) {
//          continue;
//        }
//      }
      // Append the name
      if (!isFirst) {
        sb.append(", ");
      } else {
        isFirst = false;
      }
      sb.append("\"").append(name).append("\":");
      // Determine how the value is appended
      if (value instanceof List) {
        sb.append("[");
        boolean isFirstMap = true;
        for (Object map : (List) value) {
          if (!isFirstMap) {
            sb.append(", ");
          } else {
            isFirstMap = false;
          }
          sb.append(createJsonNode((Map) map));
        }
        sb.append("]");
      } else if (value instanceof String) {
        sb.append("\"").append(toJson((String) value)).append("\"");
      } else if (value instanceof Map) {
        sb.append(createJsonNode((Map) value));
      } else {
        sb.append(value);
      }
    }
    sb.append("}");
    return sb;
  }
}
