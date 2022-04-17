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
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Cancels an order
 *
 * @author matt rajkowski
 * @created 11/20/19 9:24 PM
 */
public class OrderCancelCommand {

  private static Log LOG = LogFactory.getLog(OrderCancelCommand.class);


  public static boolean cancelOrder(Order order) throws DataException {

    // Determine the fulfillment service
    String service = LoadSitePropertyCommand.loadByName("ecommerce.orderFulfillment");
    if (StringUtils.isBlank(service) || "None".equalsIgnoreCase(service)) {
      throw new DataException("Cancellation was not processed. A fulfillment service needs to be enabled on this site.");
    }

    // Perform some validations
    if (order == null) {
      LOG.warn("Order is null");
      return false;
    }

    if (order.getShipped()) {
      LOG.debug("Order is already shipped");
      throw new DataException("The order is already marked as shipped and cannot be canceled.");
    }

    // Cancel the order
    boolean canceled = false;
    if (!order.getProcessed()) {
      // The order hasn't been processed so cancel right away
      canceled = true;
    } else if ("Boxzooka".equalsIgnoreCase(service)) {
      canceled = BoxzookaOrderCommand.cancelOrder(order);
    }
    if (canceled) {
      // Update the order info
      OrderRepository.markStatusAsCanceled(order);
      // Send an email confirmation
      OrderEmailCommand.sendCancellationConfirmationEmail(order);
      // @todo Adjust the inventory




    }
    return canceled;
  }
}
