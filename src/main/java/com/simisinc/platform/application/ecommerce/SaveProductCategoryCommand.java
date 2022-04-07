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
import com.simisinc.platform.domain.model.ecommerce.ProductCategory;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductCategoryRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/10/21 5:30 PM
 */
public class SaveProductCategoryCommand {

  private static final String allowedChars = "abcdefghijklmnopqrstuvwyxz0123456789";
  private static final String allowedFinalChars = "abcdefghijklmnopqrstuvwyxz0123456789-";
  private static Log LOG = LogFactory.getLog(SaveProductCategoryCommand.class);

  public static ProductCategory save(ProductCategory productCategoryBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();

    if (productCategoryBean.getCreatedBy() == -1 || productCategoryBean.getModifiedBy() == -1) {
      errorMessages.append("The user saving this category was not set");
    }

    if (StringUtils.isBlank(productCategoryBean.getName())) {
      errorMessages.append("A name is required");
    }

    // Validate the unique id
    if (StringUtils.isNotBlank(productCategoryBean.getUniqueId())) {
      if (!StringUtils.containsOnly(productCategoryBean.getUniqueId(), allowedFinalChars)) {
        errorMessages.append("The uniqueId contains invalid characters");
      }
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    ProductCategory productCategory;
    if (productCategoryBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      productCategory = ProductCategoryRepository.findById(productCategoryBean.getId());
      if (productCategory == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      productCategory = new ProductCategory();
    }
    // @note set the uniqueId before setting the name

    productCategory.setUniqueId(generateUniqueId(productCategory, productCategoryBean));
    productCategory.setName(productCategoryBean.getName());
    productCategory.setDescription(productCategoryBean.getDescription());
    productCategory.setDisplayOrder(productCategoryBean.getDisplayOrder());
    productCategory.setEnabled(productCategoryBean.getEnabled());
    productCategory.setCreatedBy(productCategoryBean.getCreatedBy());
    productCategory.setModifiedBy(productCategoryBean.getModifiedBy());
    return ProductCategoryRepository.save(productCategory);
  }

  private static String generateUniqueId(ProductCategory previousItem, ProductCategory item) {

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
        ProductCategoryRepository.findByUniqueId(item.getUniqueId()) == null) {
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
    while (ProductCategoryRepository.findByUniqueId(uniqueId) != null) {
      ++count;
      uniqueId = originalUniqueId + "-" + count;
    }
    return uniqueId;
  }
}
