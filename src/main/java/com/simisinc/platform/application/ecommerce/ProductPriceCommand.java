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

import com.simisinc.platform.domain.model.ecommerce.Product;
import com.simisinc.platform.domain.model.ecommerce.ProductSku;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductSkuRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductSkuSpecification;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/22/19 9:33 AM
 */
public class ProductPriceCommand {

  public static void configurePriceAndStartingPrice(Product product) {
    // Load the enabled SKUs
    ProductSkuSpecification productSkuSpecification = new ProductSkuSpecification();
    productSkuSpecification.setShowOnline(true);
    productSkuSpecification.setProductId(product.getId());
    List<ProductSku> productSkuList = ProductSkuRepository.findAll(productSkuSpecification, null);
    // Determine the price to show, or range of prices to show...
    if (productSkuList == null || productSkuList.isEmpty()) {
      // no price to show
    } else {
      // Check if there is a single SKU
      if (productSkuList.size() == 1) {
        product.setPrice(productSkuList.get(0).getPrice());
        product.setSkuCount(1);
      } else {
        // Find the starting from price, by getting the lowest priced SKU
        BigDecimal lowestPrice = ProductPriceCommand.determineLowestPrice(productSkuList);
        if (lowestPrice != null) {
          // There is a range of prices
          product.setStartingFromPrice(lowestPrice);
        } else {
          // There is a single price, regardless of options
          product.setPrice(productSkuList.get(0).getPrice());
        }
        product.setSkuCount(productSkuList.size());
      }
    }
  }

  public static String determinePriceRange(Product product) {
    // Determine the range
    BigDecimal lowPrice = null;
    BigDecimal highPrice = null;
    for (ProductSku productSku : product.getNativeProductSKUs()) {
      if (productSku.getPrice() == null) {
        continue;
      }
      if (lowPrice == null) {
        lowPrice = productSku.getPrice();
        highPrice = productSku.getPrice();
        continue;
      }
      if (productSku.getPrice().compareTo(lowPrice) < 0) {
        lowPrice = productSku.getPrice();
      }
      if (productSku.getPrice().compareTo(highPrice) > 0) {
        highPrice = productSku.getPrice();
      }
    }
    // Return a string
    if (lowPrice == null || highPrice == null) {
      return null;
    }
    if (lowPrice.compareTo(highPrice) == 0) {
      return NumberFormat.getCurrencyInstance().format(lowPrice);
    }
    return NumberFormat.getCurrencyInstance().format(lowPrice) + " - " + NumberFormat.getCurrencyInstance().format(highPrice);
  }

  /**
   * For the given list, determine the lowest price (if there is one)
   */
  public static BigDecimal determineLowestPrice(List<ProductSku> productSkuList) {
    boolean difference = false;
    BigDecimal startingFrom = null;
    for (ProductSku productSku : productSkuList) {
      if (productSku.getPrice() == null) {
        continue;
      }
      if (startingFrom == null) {
        startingFrom = productSku.getPrice();
        continue;
      }
      if (productSku.getPrice().compareTo(startingFrom) < 0) {
        // Found a lower price
        startingFrom = productSku.getPrice();
        difference = true;
      } else if (productSku.getPrice().compareTo(startingFrom) > 0) {
        // Found a higher price
        difference = true;
      }
    }
    if (!difference) {
      return null;
    }
    return startingFrom;
  }
}
