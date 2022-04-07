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

import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.domain.model.ecommerce.OrderItem;
import com.simisinc.platform.domain.model.ecommerce.TrackingNumber;
import com.simisinc.platform.infrastructure.scheduler.ecommerce.OrderManagementEmailJob;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.scheduling.BackgroundJobRequest;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/24/22 9:38 PM
 */
public class OrderEmailCommand {

  private static Log LOG = LogFactory.getLog(OrderEmailCommand.class);

  public static void sendConfirmationEmail(Order order) {
    sendConfirmationEmail(order, false);
  }

  public static void sendConfirmationEmail(Order order, boolean isResend) {
    LOG.debug("Scheduling order confirmation email...");
    OrderManagementEmailJob emailJob = new OrderManagementEmailJob();
    emailJob.setEmailType(OrderManagementEmailJob.EMAIL_TYPE_ORDER_CONFIRMATION);
    emailJob.setOrder(order);
    if (isResend) {
      emailJob.setResend(true);
    }
    BackgroundJobRequest.enqueue(emailJob);
  }

  public static void sendShippingConfirmationEmail(Order order, List<OrderItem> orderItemList, List<TrackingNumber> trackingNumberList) {
    LOG.debug("Scheduling shipping confirmation email...");
    OrderManagementEmailJob emailJob = new OrderManagementEmailJob();
    emailJob.setEmailType(OrderManagementEmailJob.EMAIL_TYPE_SHIPPING_CONFIRMATION);
    emailJob.setOrder(order);
    if (orderItemList != null && !orderItemList.isEmpty()) {
      emailJob.setOrderItemList(orderItemList);
    }
    if (trackingNumberList != null && !trackingNumberList.isEmpty()) {
      emailJob.setTrackingNumberList(trackingNumberList);
    }
    BackgroundJobRequest.enqueue(emailJob);
  }

  public static void sendCancellationConfirmationEmail(Order order) {
    LOG.debug("Scheduling order cancellation email...");
    OrderManagementEmailJob emailJob = new OrderManagementEmailJob();
    emailJob.setEmailType(OrderManagementEmailJob.EMAIL_TYPE_CANCELLATION_CONFIRMATION);
    emailJob.setOrder(order);
    BackgroundJobRequest.enqueue(emailJob);
  }

  public static void sendRefundConfirmationEmail(Order order) {
    LOG.debug("Scheduling order refund email...");
    OrderManagementEmailJob emailJob = new OrderManagementEmailJob();
    emailJob.setEmailType(OrderManagementEmailJob.EMAIL_TYPE_REFUND_CONFIRMATION);
    emailJob.setOrder(order);
    BackgroundJobRequest.enqueue(emailJob);
  }
}
