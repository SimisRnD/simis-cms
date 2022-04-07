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

import java.lang.reflect.InvocationTargetException;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.EcommerceCommand;
import com.simisinc.platform.application.ecommerce.OrderCancelCommand;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

import org.apache.commons.lang3.StringUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 11/18/19 8:15 PM
 */
public class CancelOrderWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/cancel-order.jsp";

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

    // See if the order has been shipped
    if (order.getShipped()) {
      return context;
    }

    // See if the order has been paid for
    if (!order.getPaid()) {
      return context;
    }

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Check the user permissions
    if (!context.hasRole("admin") && !context.hasRole("ecommerce-manager")) {
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

    // Process the cancellation
    try {
      if (OrderCancelCommand.cancelOrder(order)) {
        context.addSharedRequestValue("orderManagementMessage", "The order was canceled, and an email was sent to: " + order.getEmail());
      }
    } catch (DataException de) {
      context.addSharedRequestValue("orderManagementError", "The cancellation was not processed: " + de.getMessage());
    } catch (Exception e) {
      LOG.error("CancelOrderWidget error: " + e.getMessage());
      context.addSharedRequestValue("orderManagementError", "The cancellation was not processed");
    }

    return context;
  }
}
