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
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Loads product objects
 *
 * @author matt rajkowski
 * @created 3/18/19 4:35 PM
 */
public class LoadProductCommand {

  private static Log LOG = LogFactory.getLog(LoadProductCommand.class);

  public static Product loadProductById(long productId) {
    return ProductRepository.findById(productId);
  }

  public static Product loadProductByUniqueId(String uniqueId) {
    return ProductRepository.findByUniqueId(uniqueId);
  }

  public static Product loadProductBySku(String sku) {
    return ProductRepository.findBySku(sku);
  }

  public static Product loadProductMetaDataById(long productId) {
    return ProductRepository.findById(productId, false);
  }
}
