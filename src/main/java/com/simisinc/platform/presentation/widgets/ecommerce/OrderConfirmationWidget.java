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

package com.simisinc.platform.presentation.widgets.ecommerce;

import com.simisinc.platform.application.ecommerce.LoadProductCommand;
import com.simisinc.platform.domain.model.ecommerce.*;
import com.simisinc.platform.infrastructure.persistence.ecommerce.*;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the details of an order to the user
 *
 * @author matt rajkowski
 * @created 7/14/19 11:59 AM
 */
public class OrderConfirmationWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String ORDER_CONFIRMATION_JSP = "/ecommerce/order-confirmation.jsp";

  /**
   * Displays the order confirmation exactly as the customer would see it
   *
   * @param context
   * @return
   */
  public WidgetContext execute(WidgetContext context) {

    // Common attributes
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Check for a parameter
    String orderNumber = context.getParameter("order-number");

    // See if there is an order to show on the order-details pages
    Order order = null;
    if (StringUtils.isNotBlank(orderNumber)) {
      // The request is for a specific order, only show to the user creating it or an admin
      if (context.getUserSession().isLoggedIn()) {
        order = OrderRepository.findByUniqueId(orderNumber);
        if (order == null) {
          return context;
        }
        if (order.getCreatedBy() != context.getUserId() && !context.hasRole("admin") && !context.hasRole("ecommerce-manager")) {
          return context;
        }
      }
    } else {
      // Check for the last order placed in the session
      long lastOrderId = context.getUserSession().getLastOrderId();
      if (lastOrderId > 0) {
        order = OrderRepository.findById(lastOrderId);
      }
      if (order == null) {
        context.setRedirect("/cart");
        return context;
      }
    }
    context.getRequest().setAttribute("order", order);

    // Show the order items
    List<OrderEntry> orderEntryList = new ArrayList<>();
    List<OrderItem> orderItemList = OrderItemRepository.findItemsByOrderId(order.getId());
    for (OrderItem orderItem : orderItemList) {
      ProductSku productSku = ProductSkuRepository.findById(orderItem.getSkuId());
      OrderEntry orderEntry = new OrderEntry();
      orderEntry.setOrderItem(orderItem);
      orderEntry.setProduct(LoadProductCommand.loadProductMetaDataById(productSku.getProductId()));
      orderEntry.setProductSku(productSku);
      orderEntryList.add(orderEntry);
    }
    context.getRequest().setAttribute("orderEntryList", orderEntryList);

    // Show the shipping method
    ShippingMethod shippingMethod = ShippingMethodRepository.findById(order.getShippingMethodId());
    context.getRequest().setAttribute("shippingMethod", shippingMethod);

    // Show the tracking numbers
    List<TrackingNumber> trackingNumberList = TrackingNumberRepository.findAllForOrderId(order.getId());
    context.getRequest().setAttribute("trackingNumberList", trackingNumberList);

    // HTML Content
    context.getRequest().setAttribute("calloutHtml", context.getPreferences().get("calloutHtml"));
    String introHtml = context.getPreferences().get("introHtml");
    if (introHtml != null) {
      introHtml = StringUtils.replace(introHtml, "${email}", order.getEmail());
      introHtml = StringUtils.replace(introHtml, "${orderNumber}", order.getUniqueId());
      context.getRequest().setAttribute("introHtml", introHtml);
    }

    context.setJsp(ORDER_CONFIRMATION_JSP);
    return context;
  }
}
