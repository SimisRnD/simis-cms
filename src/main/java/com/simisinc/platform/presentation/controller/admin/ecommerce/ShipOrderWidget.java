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

package com.simisinc.platform.presentation.controller.admin.ecommerce;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.EcommerceCommand;
import com.simisinc.platform.application.admin.FulfillmentOptionCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.ecommerce.OrderShippingCommand;
import com.simisinc.platform.domain.model.ecommerce.FulfillmentOption;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.domain.model.ecommerce.OrderItem;
import com.simisinc.platform.domain.model.ecommerce.Product;
import com.simisinc.platform.infrastructure.persistence.ecommerce.FulfillmentOptionRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderItemRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 10/30/19 8:37 PM
 */
public class ShipOrderWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/ship-order.jsp";

  public WidgetContext execute(WidgetContext context) {

    if (!context.hasRole("admin") && !context.hasRole("ecommerce-manager")) {
      return context;
    }

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Display if running in testMode
    context.getRequest().setAttribute("testMode", !EcommerceCommand.isProductionEnabled());

    // Determine the order
    String orderNumber = context.getParameter("order-number");
    if (StringUtils.isBlank(orderNumber)) {
      return context;
    }
    Order order = OrderRepository.findByUniqueId(orderNumber);
    if (order == null) {
      return context;
    }
    context.getRequest().setAttribute("order", order);

    // See if the order is already canceled
    if (order.getCanceled()) {
      return context;
    }

    // See if the order has been processed
    if (order.getProcessed()) {
      return context;
    }

    // See if the order has been shipped
    if (order.getShipped()) {
      return context;
    }

    // See if the order has been paid for
    if (!order.getPaid()) {
      return context;
    }

    // See if any of the order items can still be fulfilled by a service
    FulfillmentOption boxzookaFulfillmentOption = FulfillmentOptionRepository.findByCode(FulfillmentOption.BOXZOOKA);
    if (!FulfillmentOptionCommand.canBePartiallyFulfilledBy(boxzookaFulfillmentOption, order)) {
      return context;
    }

    // Check the specific items...
    List<OrderItem> orderItemList = OrderItemRepository.findItemsByOrderId(order.getId());
    for (OrderItem orderItem : orderItemList) {
      if (!orderItem.getCanceled() && !orderItem.getProcessed() && !order.getShipped() && order.getPaid()) {
        // Only need those fulfilled by service
        Product product = ProductRepository.findById(orderItem.getProductId());
        if (FulfillmentOptionCommand.canBeFulfilledBy(boxzookaFulfillmentOption, product)) {
          // Show the Feature
          context.setJsp(JSP);
          return context;
        }
      }
    }

    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Check the user permissions
    if (!context.hasRole("admin") && !context.hasRole("ecommerce-manager")) {
      return context;
    }

    // Determine the fulfillment service
    String service = LoadSitePropertyCommand.loadByName("ecommerce.orderFulfillment");
    if (StringUtils.isBlank(service) || "None".equalsIgnoreCase(service)) {
      context.setErrorMessage("The order was not shipped. A fulfillment service needs to be enabled on this site.");
      return context;
    }

    // Determine the order
    String orderNumber = context.getParameter("order-number");
    if (StringUtils.isBlank(orderNumber)) {
      return context;
    }
    Order order = OrderRepository.findByUniqueId(orderNumber);

    // Prepare to redirect
    context.setRedirect("/admin/order-details?order-number=" + order.getUniqueId());

    // See if the order has been shipped
    if (order.getProcessed()) {
      context.setErrorMessage("The order is already processing for shipping.");
      return context;
    }

    // See if the order has been shipped
    if (order.getShipped()) {
      context.setErrorMessage("The order is already marked as shipped.");
      return context;
    }

    // See if the order has been paid
    if (!order.getPaid()) {
      context.setErrorMessage("The order has not been marked as paid.");
      return context;
    }

    try {
      if (OrderShippingCommand.sendToFulfillmentService(order)) {
        context.addSharedRequestValue("orderManagementMessage", "The order has been sent for processing.");
      } else {
        context.setErrorMessage("The order has not been sent for processing.");
      }
    } catch (DataException de) {
      LOG.error(de);
      context.addSharedRequestValue("orderManagementError", de.getMessage());
    }
    return context;
  }
}
