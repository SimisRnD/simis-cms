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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Timestamp;

/**
 * Methods for displaying the status of a product
 *
 * @author matt rajkowski
 * @created 3/22/19 10:16 AM
 */
public class ProductStatusCommand {

  private static Log LOG = LogFactory.getLog(ProductStatusCommand.class);

  public static boolean isActive(Product product) {
    // Product must have SKUs
    LOG.debug("Checking isActive...");
    if (product.getProducts() == null || product.getProducts().isEmpty()) {
      LOG.debug("Products are required");
      return false;
    }
    // At least one SKU must be active
    boolean hasActiveSku = false;
    LOG.debug("Checking for an active sku...");
    for (ProductSku sku : product.getProducts()) {
      LOG.debug("Checking sku... " + sku.getSku());
      if (sku.getEnabled()) {
        hasActiveSku = true;
        break;
      }
    }
    if (!hasActiveSku) {
      return false;
    }
    // The product cannot be expired
    LOG.debug("Checking deactivateOnDate...");
    Timestamp now = new Timestamp(System.currentTimeMillis());
    if (product.getDeactivateOnDate() != null) {
      if (product.getDeactivateOnDate().before(now)) {
        return false;
      }
    }
    // The start date must be unspecified, or in the past
    LOG.debug("Checking activeDate...");
    if (product.getActiveDate() == null) {
      return true;
    }
    LOG.debug("Evaluating activeDate...");
    if (product.getActiveDate().before(now)) {
      return true;
    }
    return false;
  }

  public static boolean isPending(Product product) {
    LOG.debug("Checking isPending...");
    if (isActive(product)) {
      return false;
    }
    if (product.getProducts() == null || product.getProducts().isEmpty()) {
      return false;
    }
    Timestamp now = new Timestamp(System.currentTimeMillis());
    if (product.getDeactivateOnDate() != null) {
      if (product.getDeactivateOnDate().before(now)) {
        return false;
      }
    }
    return (product.getActiveDate() != null && product.getActiveDate().after(now));
  }

  public static String determineStatus(Product product) {
    // Active
    if (isActive(product)) {
      return "Active";
    }
    // Pending
    if (isPending(product)) {
      return "Pending";
    }
    // Incomplete
    if (product.getProducts() == null || product.getProducts().isEmpty()) {
      return "Incomplete";
    }
    // Deactivated
    Timestamp now = new Timestamp(System.currentTimeMillis());
    if (product.getDeactivateOnDate() != null) {
      if (product.getDeactivateOnDate().before(now)) {
        return "Deactivated";
      }
    }
    // Other
    return "Not Active";
  }
}
