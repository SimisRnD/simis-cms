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

package com.simisinc.platform.presentation.widgets.admin.ecommerce;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.EcommerceCommand;
import com.simisinc.platform.application.admin.FulfillmentOptionCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.ecommerce.OrderShipmentStatusCommand;
import com.simisinc.platform.domain.model.ecommerce.FulfillmentOption;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.domain.model.ecommerce.OrderItem;
import com.simisinc.platform.domain.model.ecommerce.Product;
import com.simisinc.platform.infrastructure.persistence.ecommerce.FulfillmentOptionRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderItemRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 11/18/19 11:31 PM
 */
public class CheckOrderStatusWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/check-order-tracking.jsp";

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

    // See if this service should be checked
    FulfillmentOption boxzookaFulfillmentOption = FulfillmentOptionRepository.findByCode(FulfillmentOption.BOXZOOKA);
    if (!FulfillmentOptionCommand.canBePartiallyFulfilledBy(boxzookaFulfillmentOption, order)) {
      return context;
    }

    // See if this service has shipped any part of the order
    List<OrderItem> orderItemList = OrderItemRepository.findItemsByOrderId(order.getId());
    for (OrderItem orderItem : orderItemList) {
      if (!orderItem.getCanceled() && orderItem.getProcessed() && !order.getShipped()) {
        // Only need at least 1 fulfilled by service
        Product product = ProductRepository.findById(orderItem.getProductId());
        if (FulfillmentOptionCommand.canBeFulfilledBy(boxzookaFulfillmentOption, product)) {
          // Show the Feature
          context.setJsp(JSP);
          return context;
        }
      }
    }

    // No need to show
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

    // Update the tracking information, send an email if there is new tracking info
    try {
      if (OrderShipmentStatusCommand.checkAndUpdateStatus(order)) {
        context.addSharedRequestValue("orderManagementMessage", "The order was shipped, and an email was sent to: " + order.getEmail());
      } else {
        context.addSharedRequestValue("orderManagementError", "An updated shipping status was not found");
      }
    } catch (DataException de) {
      context.addSharedRequestValue("orderManagementError", "The status reported an error: " + de.getMessage());
    } catch (Exception e) {
      LOG.error("CheckOrderStatusWidget error: " + e.getMessage());
      context.addSharedRequestValue("orderManagementError", "The status was not found");
    }

    return context;
  }
}
