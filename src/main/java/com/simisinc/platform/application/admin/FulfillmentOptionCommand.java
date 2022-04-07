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

package com.simisinc.platform.application.admin;

import com.simisinc.platform.domain.model.ecommerce.FulfillmentOption;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.domain.model.ecommerce.OrderItem;
import com.simisinc.platform.domain.model.ecommerce.Product;
import com.simisinc.platform.infrastructure.persistence.ecommerce.FulfillmentOptionRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderItemRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/9/20 2:15 PM
 */
public class FulfillmentOptionCommand {

  private static Log LOG = LogFactory.getLog(FulfillmentOptionCommand.class);

  public static boolean canBeFulfilledBy(FulfillmentOption fulfillmentOption, Product product) {
    if (product.getFulfillmentId() == -1) {
      return true;
    }
    if (fulfillmentOption == null || !fulfillmentOption.getEnabled()) {
      return false;
    }
    if (fulfillmentOption.getId() == product.getFulfillmentId()) {
      return true;
    }
    return false;
  }

  public static boolean canBePartiallyFulfilledBy(FulfillmentOption fulfillmentOption, Order order) {
    if (fulfillmentOption == null || !fulfillmentOption.getEnabled()) {
      return false;
    }
    if (hasFulfillmentOverride(order)) {
      return false;
    }
    return hasProductsFulfilledByThisOption(fulfillmentOption, order);
  }

  private static boolean hasProductsFulfilledByThisOption(FulfillmentOption fulfillmentOption, Order order) {
    // Determine if any part of the order can be fulfilled by this method
    List<OrderItem> orderItemList = OrderItemRepository.findItemsByOrderId(order.getId());
    for (OrderItem orderItem : orderItemList) {
      // Only need those fulfilled by service or not specified
      Product product = ProductRepository.findById(orderItem.getProductId());
      if (FulfillmentOptionCommand.canBeFulfilledBy(fulfillmentOption, product)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasFulfillmentOverride(Order order) {
    // If 1 of the products uses IN-HOUSE, and there is an override rule, then all of products will use IN-HOUSE... so skip others
    FulfillmentOption inHouseFulfillmentOption = FulfillmentOptionRepository.findByCode(FulfillmentOption.IN_HOUSE);
    if (inHouseFulfillmentOption != null && inHouseFulfillmentOption.getEnabled() && inHouseFulfillmentOption.getOverridesOthers()) {
      return hasProductsFulfilledByThisOption(inHouseFulfillmentOption, order);
    }
    return false;
  }
}
