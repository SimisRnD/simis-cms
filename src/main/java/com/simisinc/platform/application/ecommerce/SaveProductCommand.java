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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.domain.model.ecommerce.Product;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/18/19 4:40 PM
 */
public class SaveProductCommand {

  private static final String allowedChars = "abcdefghijklmnopqrstuvwyxz0123456789";
  private static final String allowedFinalChars = "abcdefghijklmnopqrstuvwyxz0123456789-";
  private static Log LOG = LogFactory.getLog(SaveProductCommand.class);

  public static Product saveProduct(Product productBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();

    if (productBean.getCreatedBy() == -1 || productBean.getModifiedBy() == -1) {
      errorMessages.append("The user saving this product was not set");
    }

    // Validate SKUs
    ValidateProductCommand.validateProductAndSKUs(productBean, errorMessages);

    // Validate the product unique id
    if (StringUtils.isNotBlank(productBean.getUniqueId())) {
      if (!StringUtils.containsOnly(productBean.getUniqueId(), allowedFinalChars)) {
        errorMessages.append("The uniqueId contains invalid characters");
      }
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Clean the content
    String cleanedContent = HtmlCommand.cleanContent(productBean.getDescription());

    // Transform the fields and store...
    Product product;
    if (productBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      product = ProductRepository.findById(productBean.getId());
      if (product == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      product = new Product();
    }
    // @note set the uniqueId before setting the name
    product.setUniqueId(generateUniqueId(product, productBean));
    product.setOrder(productBean.getOrder());
    product.setName(productBean.getName());
    product.setDescription(cleanedContent);
    product.setCaption(productBean.getCaption());
    product.setIsGood(productBean.getIsGood());
    product.setIsService(productBean.getIsService());
    product.setIsVirtual(productBean.getIsVirtual());
    product.setIsDownload(productBean.getIsDownload());
    product.setFulfillmentId(productBean.getFulfillmentId());
    product.setTaxable(productBean.getTaxable());
    product.setTaxCode(productBean.getTaxCode());
    product.setActiveDate(productBean.getActiveDate());
    product.setDeactivateOnDate(productBean.getDeactivateOnDate());
    product.setAvailableDate(productBean.getAvailableDate());
    product.setShippable(productBean.getShippable());
    product.setPackageHeight(productBean.getPackageHeight());
    product.setPackageLength(productBean.getPackageLength());
    product.setPackageWidth(productBean.getPackageWidth());
    product.setPackageWeightPounds(productBean.getPackageWeightPounds());
    product.setPackageWeightOunces(productBean.getPackageWeightOunces());
    product.setImageUrl(productBean.getImageUrl());
    product.setProductUrl(productBean.getProductUrl());
    product.setExcludeUsStates(productBean.getExcludeUsStates());
    product.setCreatedBy(productBean.getCreatedBy());
    product.setModifiedBy(productBean.getModifiedBy());
    // Handle possible dyna list
    product.setAttributes(productBean.getNativeAttributes());
    product.setProducts(productBean.getNativeProductSKUs());
    return ProductRepository.save(product);
  }

  private static String generateUniqueId(Product previousItem, Product item) {

    // Use an existing uniqueId
    if (previousItem.getUniqueId() != null) {
      // See if the uniqueId changed...
      if (previousItem.getUniqueId().equals(item.getUniqueId())) {
        return previousItem.getUniqueId();
      }
    }

    // See if the specified one is unique
    if (StringUtils.isNotBlank(item.getUniqueId()) &&
        StringUtils.containsOnly(item.getUniqueId(), allowedFinalChars) &&
        ProductRepository.findByUniqueId(item.getUniqueId()) == null) {
      return item.getUniqueId();
    }

    // Create a new one
    StringBuilder sb = new StringBuilder();
    String name = item.getName().toLowerCase();
    final int len = name.length();
    for (int i = 0; i < len; i++) {
      char c = name.charAt(i);
      if (allowedChars.indexOf(name.charAt(i)) > -1) {
        sb.append(c);
      } else if (c == ' ') {
        sb.append("-");
      }
    }

    // Find the next available unique instance
    int count = 1;
    String originalUniqueId = sb.toString();
    String uniqueId = sb.toString();
    while (ProductRepository.findByUniqueId(uniqueId) != null) {
      ++count;
      uniqueId = originalUniqueId + "-" + count;
    }
    return uniqueId;
  }
}
