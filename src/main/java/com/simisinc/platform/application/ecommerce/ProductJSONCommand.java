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

package com.simisinc.platform.application.ecommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.ecommerce.Product;
import com.simisinc.platform.domain.model.ecommerce.ProductSkuAttribute;
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
 * @created 3/17/19 4:58 PM
 */
public class ProductJSONCommand {

  private static Log LOG = LogFactory.getLog(ProductJSONCommand.class);

  public static String createJSONString(Product record) {
    if (record.getAttributes() == null) {
      LOG.debug("No attributes found");
      return null;
    }

    StringBuilder sb = new StringBuilder();
    int count = 0;
    for (ProductSkuAttribute skuAttribute : record.getAttributes()) {
      if (count > 0) {
        sb.append(",");
      }
      ++count;
      sb.append("{");
      sb.append("\"").append("id").append("\"").append(":").append(count).append(",");
      sb.append("\"").append("name").append("\"").append(":").append("\"").append(JsonCommand.toJson(skuAttribute.getName())).append("\"").append(",");
      sb.append("\"").append("value").append("\"").append(":").append("\"").append(JsonCommand.toJson(skuAttribute.getValue())).append("\"");
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

  public static void populateFromJSONString(Product record, String jsonValue) throws SQLException {
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
      List<ProductSkuAttribute> skuAttributeList = new ArrayList<>();
      Iterator<JsonNode> fields = config.elements();
      while (fields.hasNext()) {
        JsonNode node = fields.next();
        ProductSkuAttribute attribute = new ProductSkuAttribute();
        if (node.has("id")) {
          attribute.setId(node.get("id").asLong());
        }
        if (node.has("name")) {
          attribute.setName(node.get("name").asText());
        }
        if (node.has("value")) {
          attribute.setValue(node.get("value").asText());
        }
        skuAttributeList.add(attribute);
      }
      // Store in the record
      record.setAttributes(skuAttributeList);
    } catch (Exception e) {
      throw new SQLException("Could not convert from JSON", e.getMessage());
    }
  }

}
