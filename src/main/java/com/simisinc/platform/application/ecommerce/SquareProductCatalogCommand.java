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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.domain.model.ecommerce.Product;
import com.simisinc.platform.domain.model.ecommerce.ProductSku;
import com.simisinc.platform.domain.model.ecommerce.ProductSkuAttribute;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductSkuRepository;
import com.squareup.square.models.BatchUpsertCatalogObjectsRequest;
import com.squareup.square.models.BatchUpsertCatalogObjectsResponse;
import com.squareup.square.models.CatalogIdMapping;
import com.squareup.square.models.CatalogItem;
import com.squareup.square.models.CatalogItemVariation;
import com.squareup.square.models.CatalogObject;
import com.squareup.square.models.CatalogObjectBatch;
import com.squareup.square.models.Error;
import com.squareup.square.models.ListCatalogResponse;
import com.squareup.square.models.Money;

/**
 * Commands for working with Square Product Catalog
 *
 * @author matt rajkowski
 * @created 1/8/2020 7:49 AM
 */
public class SquareProductCatalogCommand {

  private static Log LOG = LogFactory.getLog(SquareProductCatalogCommand.class);

  public static boolean syncProducts() throws DataException {

    LOG.debug("syncProducts() called...");

    // Verify the service is enabled
    String service = LoadSitePropertyCommand.loadByName("ecommerce.paymentProcessor");
    if (!"Square".equalsIgnoreCase(service)) {
      LOG.warn("Square is not configured");
      return false;
    }

    // Batch Upsert all products
    // https://connect.squareup.com/v2/catalog/batch-upsert

    // Updates require a version, so retrieve the latest version
    LOG.info("Retrieving Square catalog...");
    Map<String, Long> versionMap = retrieveCatalog();

    // Send all the product changes in a batch (up to 10,000 objects and sub-objects per batch)
    List<CatalogObject> catalogObjects = new ArrayList<>();

    // for Square, take the Products, each Sku will be a Variation under the Item object
    LOG.info("Loading product repository...");
    List<Product> productList = ProductRepository.findAll();
    for (Product product : productList) {
      // Prepare the Item CatalogObject
      String squareCatalogId = product.getSquareCatalogId();
      if (StringUtils.isBlank(squareCatalogId)) {
        // Create a unique Id (#productId)
        squareCatalogId = "#" + product.getId();
      }
      CatalogObject.Builder catalogObjectBuilder = new CatalogObject.Builder("ITEM", squareCatalogId);
      if (versionMap.containsKey(squareCatalogId)) {
        catalogObjectBuilder.version(versionMap.get(squareCatalogId));
        LOG.debug("Mapped product " + product.getUniqueId() + " to version " + versionMap.get(squareCatalogId));
      } else {
        LOG.debug("Version not found for product " + product.getUniqueId());
        if (!squareCatalogId.startsWith("#")) {
          // Product no longer exists
          continue;
        }
      }
      catalogObjectBuilder.presentAtAllLocations(true);

      LOG.info("Adding product to batch: " + squareCatalogId);

      // Prepare the Item Data - CatalogItem
      CatalogItem.Builder itemDataBuilder = new CatalogItem.Builder();
      itemDataBuilder.name(product.getNameWithCaption());
      itemDataBuilder.description(HtmlCommand.text(product.getDescription()));

      // Retrieve the SKUs to be listed under the catalog item data
      List<ProductSku> productSkuList = ProductSkuRepository.findAllByProductId(product.getId());
      List<CatalogObject> variationList = new ArrayList<>();
      for (ProductSku productSku : productSkuList) {
        // Only product SKUs with prices can be sent
        if (productSku.getPrice() == null) {
          LOG.warn("Skipping productSku due to price error: " + productSku.getSku());
          continue;
        }

        // Prepare the Item Data Variations
        String squareVariationId = productSku.getSquareVariationId();
        if (StringUtils.isBlank(squareVariationId)) {
          // Create a unique Id (#productId-productSkuId)
          squareVariationId = "#" + product.getId() + "-" + productSku.getId();
        }
        CatalogObject.Builder variationBuilder = new CatalogObject.Builder("ITEM_VARIATION", squareVariationId);
        if (versionMap.containsKey(squareVariationId)) {
          variationBuilder.version(versionMap.get(squareVariationId));
          LOG.debug("Mapped productSku " + productSku.getSku() + " to version " + versionMap.get(squareVariationId));
        } else {
          LOG.debug("Version not found for productSku " + productSku.getSku());
          if (!squareVariationId.startsWith("#")) {
            // Product sku no longer exists
            continue;
          }
        }
        variationBuilder.presentAtAllLocations(true);
        // Determine the attributes for the variation name
        StringBuilder productSkuAttributes = new StringBuilder();
        if (productSku.getAttributes() != null) {
          List<ProductSkuAttribute> attributeList = productSku.getAttributes();
          for (ProductSkuAttribute skuAttribute : attributeList) {
            String value = skuAttribute.getValue();
            if (StringUtils.isNotBlank(value)) {
              if (productSkuAttributes.length() > 0) {
                productSkuAttributes.append(", ");
              }
              productSkuAttributes.append(value);
            }
          }
        }
        // Prepare the variation data
        CatalogItemVariation.Builder itemVariationDataBuilder = new CatalogItemVariation.Builder();
        itemVariationDataBuilder.itemId(squareCatalogId);
        if (productSkuAttributes.length() > 0) {
          itemVariationDataBuilder.name(productSkuAttributes.toString());
        }
        itemVariationDataBuilder.sku(productSku.getSku());
        if (StringUtils.isNotBlank(productSku.getBarcode())) {
          itemVariationDataBuilder.upc(productSku.getBarcode());
        }
        itemVariationDataBuilder.pricingType("FIXED_PRICING");
        long squareItemCentsAmount = productSku.getPrice().multiply(new BigDecimal(100)).longValue();
        itemVariationDataBuilder.priceMoney(new Money(squareItemCentsAmount, "USD"));
        variationBuilder.itemVariationData(itemVariationDataBuilder.build());
        variationList.add(variationBuilder.build());
        LOG.info("Added productSku to batch: " + squareVariationId);
      }
      // Skip if there are no SKUs with prices
      if (variationList.isEmpty()) {
        LOG.info("Skipping product, no SKUs: " + product.getName());
        continue;
      }
      // Looks good, prepare this product for the batch
      itemDataBuilder.variations(variationList);
      catalogObjectBuilder.itemData(itemDataBuilder.build());
      catalogObjects.add(catalogObjectBuilder.build());
    }

    // Prepare the batch
    List<CatalogObjectBatch> batches = new ArrayList<>();
    CatalogObjectBatch.Builder batchBuilder = new CatalogObjectBatch.Builder(catalogObjects);
    batches.add(batchBuilder.build());
    LOG.info("Preparing batch upsert... size: " + catalogObjects.size());
    BatchUpsertCatalogObjectsRequest batchUpsertCatalogObjectsRequest =
        new BatchUpsertCatalogObjectsRequest(UUID.randomUUID().toString(), batches);

    try {
      // Create the JSON string
      String data = new ObjectMapper()
          .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
          .writeValueAsString(batchUpsertCatalogObjectsRequest);

      if (LOG.isDebugEnabled()) {
        LOG.debug("Sending: " + data);
      }

      // Send to Square
      LOG.info("Sending upsert to Square...");
      JsonNode json = SquareApiClientCommand.sendSquareHttpPost("/v2/catalog/batch-upsert", data);
      if (json == null) {
        throw new DataException("Square did not provide a response");
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug("Receiving: " + json.toString());
      }

      // Determine the response
      BatchUpsertCatalogObjectsResponse response = new ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .readerFor(BatchUpsertCatalogObjectsResponse.class)
          .readValue(json);
      if (response == null) {
        throw new DataException("The product catalog information response could not be processed");
      }
      if (response.getErrors() != null && !response.getErrors().isEmpty()) {
        LOG.warn("Checking errors...");
        StringBuilder sb = new StringBuilder();
        for (Error error : response.getErrors()) {
          if (sb.length() > 0) {
            sb.append("; ");
          }
          String errorDetail = error.getDetail();
          if (StringUtils.isNotBlank(errorDetail)) {
            sb.append(errorDetail);
          }
        }
        throw new DataException("The product catalog information could not be processed, please check the following: " + sb.toString());
      }

      // Proceed if any mappings exist
      if (response.getIdMappings() == null || response.getIdMappings().isEmpty()) {
        return true;
      }

      // Update the product and product SKU Square Ids
      LOG.info("Updating product and product sku ids from square...");
      for (CatalogIdMapping idMapping : response.getIdMappings()) {
        String clientObjectId = idMapping.getClientObjectId();
        String squareObjectId = idMapping.getObjectId();
        if (!clientObjectId.startsWith("#")) {
          continue;
        }

        LOG.info("Updating Square Mapping: " + clientObjectId + "=" + squareObjectId);

        // Call update on the product or productSku (#productId-productSkuId)
        if (!clientObjectId.contains("-")) {
          // This is a product
          long productId = Long.parseLong(clientObjectId.substring(1));
          ProductRepository.updateSquareCatalogIdForProductId(productId, squareObjectId);
        } else {
          // This is a product sku
          long productSkuId = Long.parseLong(clientObjectId.substring(clientObjectId.indexOf("-") + 1));
          ProductSkuRepository.updateSquareVariationIdForProductSkuId(productSkuId, squareObjectId);
        }
      }
      return true;
    } catch (Exception e) {
      LOG.error("Exception when calling Square post batch-upsert", e);
      throw new DataException("Sorry, a system error occurred contacting the fulfillment company, please try again later");
    }
  }

  public static Map<String, Long> retrieveCatalog() throws DataException {

    Map<String, Long> versionMap = new HashMap<>();

    // Retrieve the latest object versions to supply with the update
    // /v2/catalog/list?types=ITEM,ITEM_VARIATION<&cursor=>

    String cursor = "";
    while (cursor != null) {
      try {
        JsonNode json = SquareApiClientCommand.sendSquareHttpGet("/v2/catalog/list?types=ITEM,ITEM_VARIATION" + (StringUtils.isNotBlank(cursor) ? "&cursor=" + cursor : ""));
        if (json == null) {
          throw new DataException("Square did not provide a response");
        }

        if (LOG.isDebugEnabled()) {
          LOG.debug("Receiving: " + json.toString());
        }

        if (json.isEmpty()) {
          return versionMap;
        }

        // Determine the response
        ListCatalogResponse response = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readerFor(ListCatalogResponse.class)
            .readValue(json);
        if (response == null) {
          throw new DataException("The catalog information response could not be processed");
        }
        if (response.getObjects() == null) {
          throw new DataException("The catalog information response objects could not be processed");
        }

        // Check if there is more data, then it will need to be requested
        cursor = response.getCursor();
        if (StringUtils.isBlank(cursor)) {
          cursor = null;
        }

        // Make a map of the versions
        for (CatalogObject object : response.getObjects()) {
          String objectId = object.getId();
          Long version = object.getVersion();
          versionMap.put(objectId, version);
          LOG.debug("Found: " + objectId + " = " + version);
        }
      } catch (Exception e) {
        LOG.error("Exception when calling Square get catalog", e);
        throw new DataException("Sorry, a system error occurred contacting the payment service, please try again later");
      }
    }
    return versionMap;
  }
}
