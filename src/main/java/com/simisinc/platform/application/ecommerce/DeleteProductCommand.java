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
 * Description
 *
 * @author matt rajkowski
 * @created 3/22/19 11:38 AM
 */
public class DeleteProductCommand {

  private static Log LOG = LogFactory.getLog(DeleteProductCommand.class);

  public static boolean delete(Product productBean) {
    if (productBean.getOrderCount() > 0) {
      return false;
    }
    return ProductRepository.remove(productBean);
  }

}
