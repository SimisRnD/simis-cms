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

import java.math.BigDecimal;

/**
 * Refunds payment for part or all of an order
 *
 * @author matt rajkowski
 * @created 11/20/19 8:10 PM
 */
public class OrderRefundCommand {

  private static Log LOG = LogFactory.getLog(OrderRefundCommand.class);


  public static boolean refundPayment(Order order, BigDecimal amountToRefund) throws DataException {

    // Determine the processor
    String service = LoadSitePropertyCommand.loadByName("ecommerce.paymentProcessor");
    if (StringUtils.isBlank(service) || "None".equalsIgnoreCase(service)) {
      throw new DataException("Refund was not processed. A payment service needs to be enabled on this site.");
    }

    // Perform some validations
    if (!order.getPaid()) {
      throw new DataException("The order does not have a payment");
    }
    if (order.getLive()) {
      // Only live orders will have a payment value
      if (order.getTotalPaid().doubleValue() <= 0.0) {
        throw new DataException("The amount paid is 0");
      }
    }
    if (amountToRefund == null || amountToRefund.doubleValue() <= 0.0) {
      throw new DataException("The amount to refund must be greater than 0");
    }
    if (amountToRefund.doubleValue() > order.getTotalAmount().subtract(order.getTotalRefunded()).doubleValue()) {
      throw new DataException("Cannot refund more than the total amount of the order, minus any refunds already made");
    }
    // Refund the payment
    boolean refunded = false;
    if ("Stripe".equalsIgnoreCase(service)) {
      refunded = StripePaymentCommand.refundPayment(order, amountToRefund);
    } else if ("Square".equalsIgnoreCase(service)) {
      refunded = SquareRefundCommand.refundPayment(order, amountToRefund);
    }
    if (refunded) {
      // Update the order info
      OrderRepository.markStatusAsRefunded(order, amountToRefund);
      // Send an email confirmation
      OrderEmailCommand.sendRefundConfirmationEmail(order);
    }
    return refunded;
  }
}
