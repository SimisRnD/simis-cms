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
import com.simisinc.platform.domain.model.ecommerce.*;
import com.simisinc.platform.infrastructure.persistence.ecommerce.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks and updates an order status
 *
 * @author matt rajkowski
 * @created 11/20/19 8:10 PM
 */
public class OrderShipmentStatusCommand {

  private static Log LOG = LogFactory.getLog(OrderShipmentStatusCommand.class);


  public static boolean checkAndUpdateStatus(Order order) throws DataException {

    // Determine the processor
    String service = LoadSitePropertyCommand.loadByName("ecommerce.orderFulfillment");
    if (StringUtils.isBlank(service) || "None".equalsIgnoreCase(service)) {
      throw new DataException("The order was not shipped. A fulfillment service needs to be enabled on this site.");
    }

    // Perform some validations
    if (order == null) {
      LOG.warn("Order is null");
      throw new DataException("The order record is null");
    }

    // Go through the fulfillment services and check the remote status...
    // @todo move this to a Boxzooka service command...
    FulfillmentOption boxzookaFulfillmentOption = FulfillmentOptionRepository.findByCode(FulfillmentOption.BOXZOOKA);

    // Keep track of the Boxzooka order items
    List<OrderItem> orderItemTrackedList = new ArrayList<>();

    // See if the Boxzooka order items were already processed as an order
    List<OrderItem> orderItemList = OrderItemRepository.findItemsByOrderId(order.getId());
    for (OrderItem orderItem : orderItemList) {
      Product product = ProductRepository.findById(orderItem.getProductId());
      if (orderItem.getProcessed() && !orderItem.getShipped() && FulfillmentOptionCommand.canBeFulfilledBy(boxzookaFulfillmentOption, product)) {
        orderItemTrackedList.add(orderItem);
      }
    }
    if (orderItemTrackedList.isEmpty()) {
      LOG.debug("No Boxzooka items to check");
      return false;
    }

    // There are items that need a tracking number, so check this order
    List<TrackingNumber> trackingNumberList = BoxzookaShipmentCommand.retrieveTrackingNumbers(order);

    // Determine if new tracking number info was found...
    if (trackingNumberList == null || trackingNumberList.isEmpty()) {
      LOG.debug("No Boxzooka tracking information was found");
      return false;
    }

    // Save the tracking information
    boolean hasNewTrackingNumber = false;
    for (TrackingNumber thisTrackingNumber : trackingNumberList) {
      if (!TrackingNumberRepository.exists(thisTrackingNumber)) {
        hasNewTrackingNumber = true;
        TrackingNumberRepository.save(thisTrackingNumber);
      }
    }
    // Mark the orderItems as shipped
    for (OrderItem orderItem : orderItemTrackedList) {
      OrderItemRepository.markStatusAsShipped(orderItem);
    }
    // See if the order can be marked as fully shipped
    if (OrderStatusCommand.isFullyShipped(order)) {
      if (!order.getShipped()) {
        OrderRepository.markStatusAsShipped(order);
      }
    } else {
      OrderRepository.markStatusAsPartiallyShipped(order);
    }
    if (hasNewTrackingNumber) {
      OrderEmailCommand.sendShippingConfirmationEmail(order, orderItemTrackedList, trackingNumberList);
      return true;
    }

    return false;
  }
}
