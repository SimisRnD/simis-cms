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

import com.simisinc.platform.domain.model.ecommerce.CartItem;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.domain.model.ecommerce.OrderItem;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Generates order items from a cart
 *
 * @author matt rajkowski
 * @created 4/23/20 2:30 PM
 */
public class OrderItemCommand {

  private static Log LOG = LogFactory.getLog(OrderItemCommand.class);

  public static OrderItem generateOrderItem(Order order, CartItem cartItem) {
    OrderItem orderItem = new OrderItem();
    // Cart Item Properties
    orderItem.setOrderId(order.getId());
    orderItem.setCustomerId(order.getCustomerId());
    orderItem.setProductId(cartItem.getProductId());
    orderItem.setSkuId(cartItem.getSkuId());
    orderItem.setQuantity(cartItem.getQuantity());
    orderItem.setCurrency(cartItem.getCurrency());
    orderItem.setEachAmount(cartItem.getEachAmount());
    orderItem.setTotalAmount(cartItem.getTotalAmount());
    orderItem.setProductName(cartItem.getProductName());
    orderItem.setProductType(cartItem.getProductType());
    orderItem.setProductSku(cartItem.getProductSku());
    orderItem.setPreorder(cartItem.getPreorder());
    orderItem.setBackordered(cartItem.getBackordered());
    if (cartItem.getCreatedBy() > 0) {
      orderItem.setCreatedBy(cartItem.getCreatedBy());
    }
    if (cartItem.getModifiedBy() > 0) {
      orderItem.setModifiedBy(cartItem.getModifiedBy());
    }
    orderItem.setProductBarcode(cartItem.getProductBarcode());
    // Order Properties
    orderItem.setStatusId(order.getStatusId());
    orderItem.setPaid(order.getPaid());
    orderItem.setPaymentDate(order.getPaymentDate());
    orderItem.setProcessed(order.getProcessed());
    orderItem.setFulfillmentDate(order.getFulfillmentDate());
    orderItem.setProcessingDate(order.getProcessingDate());
    orderItem.setCanceled(order.getCanceled());
    orderItem.setCanceledDate(order.getCanceledDate());
    orderItem.setRefunded(order.getRefunded());
    orderItem.setRefundedDate(order.getRefundedDate());
    orderItem.setCreated(order.getCreated());
    return orderItem;
  }
}
