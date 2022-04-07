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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.FulfillmentOptionCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.ecommerce.FulfillmentOption;
import com.simisinc.platform.domain.model.ecommerce.Product;
import com.simisinc.platform.domain.model.ecommerce.ProductSku;
import com.simisinc.platform.domain.model.ecommerce.ProductSkuAttribute;
import com.simisinc.platform.infrastructure.persistence.ecommerce.FulfillmentOptionRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductSkuRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Commands for working with Boxzooka
 *
 * @author matt rajkowski
 * @created 11/11/19 2:21 PM
 */
public class BoxzookaProductCommand {

  private static Log LOG = LogFactory.getLog(BoxzookaProductCommand.class);

  public static boolean syncProducts() throws DataException {

    LOG.debug("syncProducts() called...");

    // Verify the service is enabled
    String service = LoadSitePropertyCommand.loadByName("ecommerce.orderFulfillment");
    if (!"Boxzooka".equalsIgnoreCase(service)) {
      LOG.warn("Boxzooka is not configured: " + service);
      return false;
    }

    // Use the fulfillment option
    FulfillmentOption boxzookaFulfillmentOption = FulfillmentOptionRepository.findByCode(FulfillmentOption.BOXZOOKA);

    Map<String, JsonNode> remoteProductMap = new LinkedHashMap<>();
    try {
      // Get the product list from Boxzooka
      JsonNode json = BoxzookaApiClientCommand.sendBoxzookaHttpGet("/v1/product");
      if (json == null) {
        throw new DataException("The product URL did not return content");
      }
      if (json.has("Error")) {
        String error = json.get("Error").asText();
        if (!error.equalsIgnoreCase("No data found")) {
          throw new DataException("Boxzooka provided an error message: " + error);
        }
      }

      // Build a list of known SKUs
      if (json.isArray()) {
        for (JsonNode product : json) {
          if (product.has("sku")) {
            LOG.debug("JSON Element: " + product.get("sku"));
            remoteProductMap.put(product.get("sku").asText(), product);
          }
        }
      }
      if (remoteProductMap.isEmpty()) {
        LOG.info("No existing products found for sync");
      }

    } catch (DataException de) {
      throw new DataException(de.getMessage());
    } catch (Exception e) {
      LOG.error("Exception when calling BXZ-Api#getAllProducts", e);
      throw new DataException("Sorry, a system error occurred contacting the fulfillment company, please try again later");
    }

    try {
      // Get a list of this system's SKUs
      List<ProductSku> productList = ProductSkuRepository.findAll(null, null);
      if (productList.isEmpty()) {
        LOG.info("There are no products in this system to send");
      }

      // Determine which data to update
      for (ProductSku productSku : productList) {

        // Load the product for reference
        Product product = ProductRepository.findById(productSku.getProductId());

        // Only need those fulfilled by Boxzooka or not specified
        if (!FulfillmentOptionCommand.canBeFulfilledBy(boxzookaFulfillmentOption, product)) {
          continue;
        }

        // Determine the values to send
        BigDecimal retailValue = null;
        if (productSku.getPrice() != null) {
          retailValue = productSku.getPrice().setScale(2, RoundingMode.HALF_UP);
        }

        // Determine the attributes and build a short description
        StringBuilder sbSize = new StringBuilder();
        StringBuilder sbColor = new StringBuilder();
        StringBuilder sbStyle = new StringBuilder();
        if (productSku.getAttributes() != null) {
          List<ProductSkuAttribute> attributeList = productSku.getAttributes();
          for (ProductSkuAttribute skuAttribute : attributeList) {
            String value = skuAttribute.getValue();
            if (StringUtils.isNotBlank(value)) {
              String attributeType = ProductAttributeCommand.getTypeForKey(product, skuAttribute.getName());
              if (StringUtils.isNotBlank(attributeType)) {
                if (attributeType.equalsIgnoreCase("size")) {
                  if (sbSize.length() > 0) {
                    sbSize.append(", ");
                  }
                  sbSize.append(value);
                } else if (attributeType.equalsIgnoreCase("color")) {
                  if (sbColor.length() > 0) {
                    sbColor.append(", ");
                  }
                  sbColor.append(value);
                } else {
                  if (sbStyle.length() > 0) {
                    sbStyle.append(", ");
                  }
                  sbStyle.append(value);
                }
              }
            }
          }
        }


        if (remoteProductMap.containsKey(productSku.getSku())) {
          // Update it
          JsonNode existingProduct = remoteProductMap.get(productSku.getSku());

          ((ObjectNode) existingProduct).put("name", product.getNameWithCaption());
          if (StringUtils.isNotBlank(product.getDescription())) {
            ((ObjectNode) existingProduct).put("description", HtmlCommand.text(product.getDescription()));
          } else {
            ((ObjectNode) existingProduct).put("description", "");
          }
          if (sbSize.length() > 0) {
            ((ObjectNode) existingProduct).put("size", sbSize.toString());
          } else {
            ((ObjectNode) existingProduct).put("size", "");
          }
          if (sbColor.length() > 0) {
            ((ObjectNode) existingProduct).put("color", sbColor.toString());
          } else {
            ((ObjectNode) existingProduct).put("color", "");
          }
          if (sbStyle.length() > 0) {
            ((ObjectNode) existingProduct).put("style", sbStyle.toString());
          } else {
            ((ObjectNode) existingProduct).put("style", "");
          }
          if (retailValue != null && retailValue.doubleValue() > 0.0) {
            ((ObjectNode) existingProduct).put("retail_value", String.valueOf(retailValue));
          } else {
            ((ObjectNode) existingProduct).put("retail_value", "");
          }

          LOG.info("Updating existing remote Sku: " + productSku.getSku());
          String data = "{\"product\": " + existingProduct.toString() + "}";
          LOG.debug("Sending existing JSON STRING:\n" + data);
          BoxzookaApiClientCommand.sendBoxzookaHttpPut("/v1/product", data);
          continue;
        }

        // Build the JSON object for insert
        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Object> productParams = new LinkedHashMap<>();
        productParams.put("sku", productSku.getSku());
        productParams.put("name", product.getNameWithCaption());
        if (StringUtils.isNotBlank(productSku.getBarcode())) {
          productParams.put("upc", productSku.getBarcode());
        } else {
          productParams.put("upc", productSku.getSku());
        }
        if (StringUtils.isNotBlank(product.getDescription())) {
          productParams.put("description", HtmlCommand.text(product.getDescription()));
        } else {
          productParams.put("description", "");
        }
        productParams.put("short_description", "");
        if (sbSize.length() > 0) {
          productParams.put("size", sbSize.toString());
        } else {
          productParams.put("size", "");
        }
        if (sbColor.length() > 0) {
          productParams.put("color", sbColor.toString());
        } else {
          productParams.put("color", "");
        }
        if (sbStyle.length() > 0) {
          productParams.put("style", sbStyle.toString());
        } else {
          productParams.put("style", "");
        }
        if (retailValue != null && retailValue.doubleValue() > 0.0) {
          productParams.put("retail_value", String.valueOf(retailValue));
        } else {
          productParams.put("retail_value", "");
        }
        productParams.put("wholesale_value", "");
        productParams.put("sale", "");
        productParams.put("buy", "");
        productParams.put("length", 0);
        productParams.put("width", 0);
        productParams.put("height", 0);
        productParams.put("weight", "");
        productParams.put("category", "");
        productParams.put("brand", "");
        productParams.put("image", "");
        productParams.put("material", "");
        productParams.put("country", "US");
        params.put("product", productParams);

        // Send it
        LOG.info("Sending new remote Sku: " + productSku.getSku());
        String data = JsonCommand.createJsonNode(params).toString();
        LOG.debug("Sending new JSON STRING:\n" + data);
        BoxzookaApiClientCommand.sendBoxzookaHttpPost("/v1/product", data);
      }
      // Complete
      return true;
    } catch (Exception e) {
      LOG.error("Exception when calling BXZ-Api#postProduct", e);
      throw new DataException("Sorry, a system error occurred contacting the fulfillment company, please try again later");
    }
  }
}
