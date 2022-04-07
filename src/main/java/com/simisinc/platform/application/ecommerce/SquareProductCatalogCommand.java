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
import com.squareup.connect.models.Error;
import com.squareup.connect.models.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.util.*;

/**
 * Commands for working with Square
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
    List<CatalogObjectBatch> batches = new ArrayList<>();
    CatalogObjectBatch batch = new CatalogObjectBatch();

    // for Square, take the Products, each Sku will be a Variation under the Item object
    LOG.info("Loading product repository...");
    List<Product> productList = ProductRepository.findAll();
    for (Product product : productList) {
      // Prepare the Item CatalogObject
      CatalogObject catalogObject = new CatalogObject();
      catalogObject.setType("ITEM");
      String squareCatalogId = product.getSquareCatalogId();
      if (StringUtils.isBlank(squareCatalogId)) {
        // Create a unique Id (#productId)
        squareCatalogId = "#" + product.getId();
      }
      catalogObject.setId(squareCatalogId);
      if (versionMap.containsKey(squareCatalogId)) {
        catalogObject.setVersion(versionMap.get(squareCatalogId));
        LOG.debug("Mapped product " + product.getUniqueId() + " to version " + versionMap.get(squareCatalogId));
      } else {
        LOG.debug("Version not found for product " + product.getUniqueId());
      }
      catalogObject.setPresentAtAllLocations(true);

      LOG.info("Adding product to batch: " + squareCatalogId);

      // Prepare the Item Data - CatalogItem
      CatalogItem itemData = new CatalogItem();
      itemData.setName(product.getNameWithCaption());
      itemData.setDescription(HtmlCommand.text(product.getDescription()));

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
        CatalogObject variation = new CatalogObject();
        variation.setType("ITEM_VARIATION");
        String squareVariationId = productSku.getSquareVariationId();
        if (StringUtils.isBlank(squareVariationId)) {
          // Create a unique Id (#productId-productSkuId)
          squareVariationId = "#" + product.getId() + "-" + productSku.getId();
        }
        variation.setId(squareVariationId);
        if (versionMap.containsKey(squareVariationId)) {
          variation.setVersion(versionMap.get(squareVariationId));
          LOG.debug("Mapped productSku " + productSku.getSku() + " to version " + versionMap.get(squareVariationId));
        } else {
          LOG.debug("Version not found for productSku " + productSku.getSku());
        }
        variation.setPresentAtAllLocations(true);
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
        CatalogItemVariation itemVariationData = new CatalogItemVariation();
        itemVariationData.setItemId(squareCatalogId);
        if (productSkuAttributes.length() > 0) {
          itemVariationData.setName(productSkuAttributes.toString());
        }
        itemVariationData.setSku(productSku.getSku());
        if (StringUtils.isNotBlank(productSku.getBarcode())) {
          itemVariationData.setUpc(productSku.getBarcode());
        }
        itemVariationData.setPricingType("FIXED_PRICING");
        long squareItemCentsAmount = productSku.getPrice().multiply(new BigDecimal(100)).longValue();
        itemVariationData.setPriceMoney(new Money().amount(squareItemCentsAmount).currency("USD"));
        variation.setItemVariationData(itemVariationData);
        variationList.add(variation);
        LOG.info("Added productSku to batch: " + squareVariationId);
      }
      // Skip if there are no SKUs with prices
      if (variationList.isEmpty()) {
        LOG.info("Skipping product, no SKUs: " + product.getName());
        continue;
      }
      // Looks good, prepare this product for the batch
      itemData.setVariations(variationList);
      catalogObject.setItemData(itemData);
      batch.addObjectsItem(catalogObject);
    }
    batches.add(batch);

    // Prepare the batch
    LOG.info("Preparing batch upsert... size: " + batch.getObjects().size());
    BatchUpsertCatalogObjectsRequest batchUpsertCatalogObjectsRequest = new BatchUpsertCatalogObjectsRequest()
        .idempotencyKey(UUID.randomUUID().toString())
        .batches(batches);

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
      if (!response.getErrors().isEmpty()) {
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
