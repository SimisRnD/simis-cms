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
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductSpecification;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads a list of products
 *
 * @author matt rajkowski
 * @created 2/5/2021 9:42 PM
 */
public class LoadProductListCommand {

  private static Log LOG = LogFactory.getLog(LoadProductListCommand.class);

  public static List<Product> loadProductsForSale(ArrayList<String> productUniqueIdList, int limit) {
    // Find all products for sale based on conditions
    ProductSpecification specification = new ProductSpecification();
    specification.setIsForSale(true);
    if (productUniqueIdList != null && !productUniqueIdList.isEmpty()) {
      specification.setWithProductUniqueIdList(productUniqueIdList);
    }
    // Determine some result constraints
    DataConstraints constraints = new DataConstraints();
    constraints.setPageSize(limit);
    // Load the list
    List<Product> productList = ProductRepository.findAll(specification, constraints);
    // Determine the price to show, or range of prices to show...
    for (Product product : productList) {
      ProductPriceCommand.configurePriceAndStartingPrice(product);
    }
    return productList;
  }
}
