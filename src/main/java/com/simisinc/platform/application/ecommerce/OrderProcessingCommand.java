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
import com.simisinc.platform.application.admin.EcommerceCommand;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Timestamp;

import static com.simisinc.platform.application.ecommerce.OrderStatusCommand.PAID;

/**
 * Commands for processing an order without a payment service
 *
 * @author matt rajkowski
 * @created 4/11/20 10:29 AM
 */
public class OrderProcessingCommand {

  private static Log LOG = LogFactory.getLog(OrderProcessingCommand.class);

  public static boolean processOrder(Order order) throws DataException {

    LOG.debug("processOrder() called...");

    if (order == null) {
      LOG.debug("Order is false");
      return false;
    }

    if (order.getPaid()) {
      LOG.debug("Order is paid");
      return true;
    }

    // Prepare for the new order status
    boolean productionEnabled = EcommerceCommand.isProductionEnabled();
    int statusId = OrderStatusCommand.retrieveStatusId(PAID);

    // Processing is successful
    order.setPaymentProcessor("in-house");
    order.setStatusId(statusId);
    order.setLive(productionEnabled);
    order.setPaid(true);
    order.setPaymentDate(new Timestamp(System.currentTimeMillis()));
    order.setTotalPaid(order.getTotalAmount());
    // Update the order in the database
    if (OrderRepository.save(order) != null) {
      // Send emails to the customer and e-commerce managers
      OrderEmailCommand.sendConfirmationEmail(order);
      return true;
    }
    return false;
  }
}
