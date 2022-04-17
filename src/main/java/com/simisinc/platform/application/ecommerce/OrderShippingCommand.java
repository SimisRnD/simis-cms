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
import com.simisinc.platform.application.admin.FulfillmentOptionCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.ecommerce.FulfillmentOption;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.infrastructure.persistence.ecommerce.FulfillmentOptionRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Submits an order to a fulfillment service
 *
 * @author matt rajkowski
 * @created 12/15/19 11:26 AM
 */
public class OrderShippingCommand {

  private static Log LOG = LogFactory.getLog(OrderShippingCommand.class);

  public static boolean sendToFulfillmentService(Order order) throws DataException {

    // Determine the processor (this is not so relevant since multiple fulfillment services can be used)
    String service = LoadSitePropertyCommand.loadByName("ecommerce.orderFulfillment");
    if (StringUtils.isBlank(service) || "None".equalsIgnoreCase(service)) {
      throw new DataException("The order was not shipped. A fulfillment service needs to be enabled on this site.");
    }

    // Perform some validations
    if (order == null) {
      LOG.warn("Order is null");
      throw new DataException("The order record is null");
    }
    if (order.getProcessed()) {
      return false;
    }
    if (order.getShipped()) {
      return false;
    }
    if (!order.getPaid()) {
      return false;
    }

    // See if the order should be sent to Boxzooka...
    if ("Boxzooka".equalsIgnoreCase(service)) {
      FulfillmentOption boxzookaFulfillmentOption = FulfillmentOptionRepository.findByCode(FulfillmentOption.BOXZOOKA);
      if (FulfillmentOptionCommand.canBePartiallyFulfilledBy(boxzookaFulfillmentOption, order)) {
        if (BoxzookaOrderCommand.postNewOrder(order)) {
          LOG.debug("Sent to Boxzooka successfully");
          return true;
        }
      }
    }
    return false;
  }
}
