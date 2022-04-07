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
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/22/19 9:35 AM
 */
public class ProductInventoryCommand {

  public static boolean isAvailable(ProductSku productSku, BigDecimal quantity) {
    return (productSku.getInventoryQty() > quantity.intValue());
  }

  public static boolean isAvailable(Product product, String sku, BigDecimal quantity) {
    for (ProductSku productSku : product.getNativeProductSKUs()) {
      if (!productSku.getSku().equalsIgnoreCase(sku)) {
        continue;
      }
      return isAvailable(productSku, quantity);
    }
    return false;
  }

  public static boolean hasMoreOnTheWay(ProductSku productSku) {
    return (productSku.getInventoryIncoming() > 0);
  }

  public static boolean hasMoreOnTheWay(Product product, String sku) {
    for (ProductSku productSku : product.getNativeProductSKUs()) {
      if (!productSku.getSku().equalsIgnoreCase(sku)) {
        continue;
      }
      return hasMoreOnTheWay(productSku);
    }
    return false;
  }

  public static String determineInventorySummary(Product product) {
    StringBuilder sb = new StringBuilder();
    for (ProductSku productSku : product.getNativeProductSKUs()) {
      String thisMessage = "";
      // Check if low
      if (productSku.getInventoryLow() > 0) {
        if (productSku.getInventoryQty() > 0 && productSku.getInventoryQty() <= productSku.getInventoryLow()) {
          thisMessage = "LOW: ";
        }
      }
      // Show the inventory
      thisMessage += String.valueOf(productSku.getInventoryQty());
      // Show if more are on the way
      if (productSku.getInventoryIncoming() > 0) {
//        thisMessage += " / +" + productSku.getInventoryIncoming();
        thisMessage += " + " + productSku.getInventoryIncoming();
      }
      appendMessage(sb, thisMessage);
    }
    return sb.toString();
  }

  private static void appendMessage(StringBuilder sb, String message) {
    if (StringUtils.isBlank(message)) {
      return;
    }
    if (sb.length() > 0) {
      sb.append(", ");
    }
    sb.append(message);
  }

}
