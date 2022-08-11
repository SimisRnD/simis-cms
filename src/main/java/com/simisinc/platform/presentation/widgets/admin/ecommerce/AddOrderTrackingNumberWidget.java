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
import com.simisinc.platform.application.ecommerce.EcommerceCommand;
import com.simisinc.platform.application.ecommerce.LoadProductCommand;
import com.simisinc.platform.application.ecommerce.OrderEmailCommand;
import com.simisinc.platform.application.ecommerce.OrderStatusCommand;
import com.simisinc.platform.application.ecommerce.SaveTrackingNumberCommand;
import com.simisinc.platform.domain.model.ecommerce.*;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderItemRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductSkuRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingCarrierRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/22/20 1:43 PM
 */
public class AddOrderTrackingNumberWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/add-tracking-number.jsp";

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

    List<ShippingCarrier> shippingCarrierList = ShippingCarrierRepository.findAll();
    context.getRequest().setAttribute("shippingCarrierList", shippingCarrierList);

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

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Check the user permissions
    if (!context.hasRole("admin") && !context.hasRole("ecommerce-manager")) {
      return context;
    }

    // Validate the parameters
    String orderNumber = context.getParameter("order-number");
    if (StringUtils.isBlank(orderNumber)) {
      return context;
    }
    Order order = OrderRepository.findByUniqueId(orderNumber);
    if (order == null) {
      return context;
    }

    boolean sendCustomerEmail = context.getParameterAsBoolean("sendCustomerEmail");

    long shippingCarrierId = context.getParameterAsLong("shippingCarrierId");

    // Determine tracking number(s)
    String trackingNumberValue = context.getParameter("trackingNumber");
    List<String> trackingNumberList = Stream.of(trackingNumberValue.split(Pattern.quote(",")))
        .map(String::trim)
        .collect(toList());
    if (trackingNumberList.isEmpty()) {
      context.addSharedRequestValue("orderManagementError", "A tracking number is required");
      return context;
    }

    // Determine the products included with this tracking number
    List<OrderItem> orderItemList = OrderItemRepository.findItemsByOrderId(order.getId());
    List<OrderItem> orderItemTrackedList = new ArrayList<>();
    List<Long> orderItemIdList = new ArrayList<>();
    for (int i = 0; i < orderItemList.size(); i++) {
      long orderItemId = context.getParameterAsLong("orderItemTrackingNumber" + i);
      if (orderItemId > -1) {
        // Match the orderItem
        for (OrderItem orderItem : orderItemList) {
          if (orderItem.getId() == orderItemId) {
            orderItemIdList.add(orderItemId);
            orderItemTrackedList.add(orderItem);
          }
        }
      }
    }

    // Prepare to redirect
    context.setRedirect("/admin/order-details?order-number=" + order.getUniqueId());

    try {
      boolean saved = true;
      // Process the tracking numbers, keeping track of the objects for emailing
      List<TrackingNumber> trackingNumberObjectList = new ArrayList<>();
      for (String trackingNumber : trackingNumberList) {
        TrackingNumber trackingNumberBean = new TrackingNumber();
        trackingNumberBean.setOrderId(order.getId());
        trackingNumberBean.setTrackingNumber(trackingNumber);
        trackingNumberBean.setShippingCarrierId(shippingCarrierId);
        trackingNumberBean.setCreatedBy(context.getUserId());
        if (!orderItemIdList.isEmpty()) {
          trackingNumberBean.setOrderItemIdList(StringUtils.join(orderItemIdList, ","));
        }
        LOG.info("Adding tracking number: " + trackingNumberBean.getTrackingNumber());
        if (SaveTrackingNumberCommand.save(trackingNumberBean) == null) {
          saved = false;
        } else {
          trackingNumberObjectList.add(trackingNumberBean);
        }
      }
      if (saved) {
        // Mark the items as processed and shipped
        if (!orderItemTrackedList.isEmpty()) {
          for (OrderItem orderItem : orderItemTrackedList) {
            OrderItemRepository.markStatusAsPreparing(orderItem);
            OrderItemRepository.markStatusAsShipped(orderItem);
          }
        }
        // Determine if all the products have been sent, update the order status (Shipped) or (Partially Shipped)
        if (OrderStatusCommand.isFullyShipped(order)) {
          OrderRepository.markStatusAsPreparing(order);
          OrderRepository.markStatusAsShipped(order);
        } else {
          OrderRepository.markStatusAsPartiallyPrepared(order);
          OrderRepository.markStatusAsPartiallyShipped(order);
        }
        // Optionally send email to the customer
        if (sendCustomerEmail) {
          OrderEmailCommand.sendShippingConfirmationEmail(order, orderItemTrackedList, trackingNumberObjectList);
          context.addSharedRequestValue("orderManagementMessage", "The tracking number was added, and an email was sent to: " + order.getEmail());
        } else {
          context.addSharedRequestValue("orderManagementMessage", "The tracking number was added, no email sent");
        }
        return context;
      } else {
        context.addSharedRequestValue("orderManagementError", "The tracking number could not be added, a system error occurred");
      }
    } catch (DataException de) {
      context.addSharedRequestValue("orderManagementError", "The tracking number could not be added: " + de.getMessage());
    } catch (Exception e) {
      LOG.error("AddOrderTrackingNumberWidget error: " + e.getMessage());
      context.addSharedRequestValue("orderManagementError", "The tracking number could not be added, a system error occurred");
    }
    return context;
  }
}
