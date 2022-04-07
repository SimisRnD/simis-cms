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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/22/19 7:34 AM
 */
public class ValidateProductCommand {

  private static final String allowedChars = "abcdefghijklmnopqrstuvwyxz";
  private static final String skuAllowedChars = "ABCDEFGHIJKLMNOPQRSTUVWYXZ-1234567890";
  private static Log LOG = LogFactory.getLog(ValidateProductCommand.class);

  public static void validateProductAndSKUs(Product productBean, StringBuilder errorMessages) {

    if (StringUtils.isBlank(productBean.getName())) {
      appendMessage(errorMessages, "A name is required");
    }
    if (productBean.getDeactivateOnDate() != null && productBean.getActiveDate() != null &&
        productBean.getDeactivateOnDate().before(productBean.getActiveDate())) {
      appendMessage(errorMessages, "Deactivation date cannot be before the active date");
    }

    // Check the products
    if (productBean.getProducts() != null) {
      for (ProductSku productSku : productBean.getNativeProductSKUs()) {
        if (StringUtils.isNotBlank(productSku.getSku())) {
          // Make sure the SKUs validate
          if (!StringUtils.containsOnly(productSku.getSku(), skuAllowedChars) ||
              productSku.getSku().trim().length() < 5 ||
              productSku.getSku().trim().length() > 20
          ) {
            appendMessage(errorMessages, "SKUs must be 5-20 characters using A-Z, 0-9, and dashes only (" + productSku.getSku() + ")");
          }
          // Check if the SKU already exists
          ProductSkuSpecification specification = new ProductSkuSpecification();
          specification.setSku(productSku.getSku());
          specification.setIsNotId(productSku.getId());
          List<ProductSku> matchingSKUList = ProductSkuRepository.findAll(specification, null);
          if (!matchingSKUList.isEmpty()) {
            appendMessage(errorMessages, "SKUs must be unique across all products (" + productSku.getSku() + ")");
          }
        }
      }
    }
  }

  private static void appendMessage(StringBuilder errorMessages, String message) {
    if (errorMessages.length() > 0) {
      errorMessages.append("; ");
    }
    errorMessages.append(message);
  }

}
