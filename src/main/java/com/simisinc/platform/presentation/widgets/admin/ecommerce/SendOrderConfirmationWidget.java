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

import com.simisinc.platform.application.ecommerce.EcommerceCommand;
import com.simisinc.platform.application.ecommerce.OrderEmailCommand;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 10/30/19 8:37 PM
 */
public class SendOrderConfirmationWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/send-order-confirmation.jsp";

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
    // See if the order has been paid for
    if (!order.getPaid()) {
      return context;
    }
    if (order.getCanceled()) {
      return context;
    }
    context.getRequest().setAttribute("order", order);

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
    if (order == null) {
      return context;
    }
    // See if the order has been paid for
    if (!order.getPaid()) {
      return context;
    }
    if (order.getCanceled()) {
      return context;
    }
    OrderEmailCommand.sendConfirmationEmail(order, true);
    context.addSharedRequestValue("orderManagementMessage", "The order confirmation was sent");

    // Prepare to redirect
    context.setRedirect("/admin/order-details?order-number=" + order.getUniqueId());
    return context;
  }
}
