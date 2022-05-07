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

package com.simisinc.platform.application.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.domain.model.cms.Theme;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Handles JSON values, typically used for preferences and attributes
 *
 * @author matt rajkowski
 * @created 7/12/19 2:15 PM
 */
public class ThemeJSONCommand {

  private static Log LOG = LogFactory.getLog(ThemeJSONCommand.class);

  public static String createJSONString(Theme record) {
    if (record.getSiteProperties() == null) {
      LOG.debug("No fields found");
      return null;
    }

    StringBuilder sb = new StringBuilder();
    int count = 0;
    for (SiteProperty siteProperty : record.getSiteProperties()) {
      if (count > 0) {
        sb.append(",");
      }
      ++count;
      sb.append("{");
      sb.append("\"").append("id").append("\"").append(":").append(count).append(",");
      sb.append("\"").append("name").append("\"").append(":").append("\"").append(JsonCommand.toJson(siteProperty.getName())).append("\"").append(",");
      sb.append("\"").append("value").append("\"").append(":").append("\"").append(JsonCommand.toJson(siteProperty.getValue())).append("\"");
      sb.append("}");
    }
    if (sb.length() == 0) {
      LOG.debug("No values found");
      return null;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Using: " + "[" + sb.toString() + "]");
    }
    return "[" + sb.toString() + "]";
  }

  public static void populateFromJSONString(Theme record, String jsonValue) throws SQLException {
    // Convert JSON string back into values
    if (StringUtils.isBlank(jsonValue)) {
      LOG.debug("populateFromJSONString value is empty");
      return;
    }
    try {
      JsonNode config = JsonLoader.fromString(jsonValue);
      if (!config.isArray()) {
        LOG.error("populateFromJSONString value is not an array");
        return;
      }

      // Determine the values
      List<SiteProperty> sitePropertyList = new ArrayList<>();
      Iterator<JsonNode> fields = config.elements();
      while (fields.hasNext()) {
        JsonNode node = fields.next();

        SiteProperty siteProperty = new SiteProperty();
        if (node.has("name")) {
          siteProperty.setName(node.get("name").asText());
        }
        if (node.has("value")) {
          siteProperty.setValue(node.get("value").asText());
        }
        sitePropertyList.add(siteProperty);
      }
      // Store in the record
      record.setSiteProperties(sitePropertyList);
    } catch (Exception e) {
      throw new SQLException("Could not convert from JSON", e.getMessage());
    }
  }

}
