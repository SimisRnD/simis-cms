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

package com.simisinc.platform.infrastructure.scheduler.ecommerce;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.application.ecommerce.TrackingServiceCommand;
import com.simisinc.platform.application.email.EmailCommand;
import com.simisinc.platform.application.email.EmailTemplateCommand;
import com.simisinc.platform.domain.events.ecommerce.OrderSubmittedEvent;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.domain.model.ecommerce.OrderItem;
import com.simisinc.platform.domain.model.ecommerce.ShippingMethod;
import com.simisinc.platform.domain.model.ecommerce.TrackingNumber;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderItemRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingMethodRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.TrackingNumberRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.mail.ImageHtmlEmail;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ServletContextTemplateResolver;

import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends order conformation emails
 *
 * @author matt rajkowski
 * @created 11/13/2019 8:25 PM
 */
@NoArgsConstructor
public class OrderManagementEmailJob implements JobRequest {

  public static String ORDER_OBJECT = "order";
  public static String ORDER_ITEM_LIST_OBJECT = "orderItemList";
  public static String TRACKING_NUMBER_LIST_OBJECT = "trackingNumberList";
  public static String RESEND_OBJECT = "resend";
  public static String EMAIL_TYPE = "emailType";
  public static String EMAIL_TYPE_ORDER_CONFIRMATION = "ORDER_CONFIRMATION";
  public static String EMAIL_TYPE_SHIPPING_CONFIRMATION = "SHIPPING_CONFIRMATION";
  public static String EMAIL_TYPE_CANCELLATION_CONFIRMATION = "CANCELLATION_CONFIRMATION";
  public static String EMAIL_TYPE_REFUND_CONFIRMATION = "REFUND_CONFIRMATION";

  @Getter
  @Setter
  private Order order = null;

  @Getter
  @Setter
  private List<OrderItem> orderItemList = null;

  @Getter
  @Setter
  private List<TrackingNumber> trackingNumberList = null;

  @Getter
  @Setter
  private String emailType = null;

  @Getter
  @Setter
  private boolean isResend = false;

  private static Log LOG = LogFactory.getLog(OrderManagementEmailJob.class);

  @Override
  public Class<OrderManagementEmailJobRequestHandler> getJobRequestHandler() {
    return OrderManagementEmailJobRequestHandler.class;
  }

  public static class OrderManagementEmailJobRequestHandler implements JobRequestHandler<OrderManagementEmailJob> {
    @Override
    @Job(name = "Send order management email", retries = 1)
    public void run(OrderManagementEmailJob jobRequest) {

      // Determine the objects
      Order order = jobRequest.getOrder();
      List<OrderItem> orderItemList = jobRequest.getOrderItemList();
      List<TrackingNumber> trackingNumberList = jobRequest.getTrackingNumberList();

      // Determine if the manager should be notified
      String emailType = jobRequest.getEmailType();
      if (emailType.equals(EMAIL_TYPE_ORDER_CONFIRMATION)) {
        boolean isResend = jobRequest.isResend();
        if (!isResend) {
          // Trigger events
          // @note currently emails the Admin/Managers; for integrating services
          WorkflowManager.triggerWorkflowForEvent(new OrderSubmittedEvent(order));
        }
      }

      // Prepare and send the email to the user
      try {
        sendConfirmationToUser(order, orderItemList, trackingNumberList, emailType);
      } catch (Exception e) {
        LOG.error("sendConfirmationToUser error", e);
      }
    }

    private static void sendConfirmationToUser(Order order, List<OrderItem> orderItemList, List<TrackingNumber> trackingNumberList, String emailType) throws Exception {

      // Prepare the email template
      ServletContext servletContext = SchedulerManager.getServletContext();
      ServletContextTemplateResolver templateResolver = new ServletContextTemplateResolver(servletContext);
      templateResolver.setTemplateMode(TemplateMode.HTML);
      templateResolver.setPrefix("/WEB-INF/email-templates/");
      templateResolver.setSuffix(".html");
      templateResolver.setCacheTTLMs(Long.valueOf(3600000L));
      templateResolver.setCacheable(true);

      TemplateEngine templateEngine = new TemplateEngine();
      templateEngine.setTemplateResolver(templateResolver);

      // Use the standard site context
      Context ctx = EmailTemplateCommand.createSiteContext();

      // Order information
      ShippingMethod shippingMethod = ShippingMethodRepository.findById(order.getShippingMethodId());

      // Order
      Map<String, Object> orderMap = new HashMap<>();
      addValue(orderMap, "uniqueId", order.getUniqueId());
      if (order.getPaymentDate() != null) {
        addValue(orderMap, "date", order.getPaymentDate());
      } else {
        addValue(orderMap, "date", order.getCreated());
      }
      addValue(orderMap, "totalAmount", order.getTotalAmount());
      addValue(orderMap, "subtotalAmount", order.getSubtotalAmount());
      addValue(orderMap, "shippingFee", order.getShippingFee());
      addValue(orderMap, "salesTax", order.getTaxAmount());
      addValue(orderMap, "discountAmount", order.getDiscountAmount());
      addValue(orderMap, "paymentBrand", order.getPaymentBrand());
      if (shippingMethod != null) {
        addValue(orderMap, "shippingMethod", shippingMethod.getTitle());
      }

      // Shipping date
      if (order.getShipped()) {
        if (order.getShippedDate() != null) {
          addValue(orderMap, "shippedDate", order.getShippedDate());
        }
      }

      // Cancellation date
      if (order.getCanceled()) {
        if (order.getCanceledDate() != null) {
          addValue(orderMap, "canceledDate", order.getCanceledDate());
        }
      }

      // Refund date and amount
      if (order.getRefunded()) {
        if (order.getRefundedDate() != null) {
          addValue(orderMap, "refundedDate", order.getRefundedDate());
        }
        if (order.getTotalRefunded() != null) {
          addValue(orderMap, "refundAmount", order.getTotalRefunded());
        }
      }
      ctx.setVariable("order", orderMap);
      ctx.setVariable("shippingAddress", order.getShippingAddress());


      // Tracking numbers to show (or all)
      if (trackingNumberList == null || trackingNumberList.isEmpty()) {
        // None were specified, so retrieve the full list
        trackingNumberList = TrackingNumberRepository.findAllForOrderId(order.getId());
      }
      if (trackingNumberList != null && !trackingNumberList.isEmpty()) {
        // Populate the tracking number link
        for (TrackingNumber trackingNumber : trackingNumberList) {
          TrackingServiceCommand.determineTrackingNumberWebLink(trackingNumber);
        }
        ctx.setVariable("trackingNumberList", trackingNumberList);
      }

      // The items to show (or all)
      List<OrderItem> masterOrderItemList = OrderItemRepository.findItemsByOrderId(order.getId());
      if (orderItemList != null && !orderItemList.isEmpty()) {
        if (orderItemList.size() != masterOrderItemList.size()) {
          ctx.setVariable("additionalTracking", "true");
        }
      } else {
        orderItemList = masterOrderItemList;
      }
      ArrayList<Map<String, Object>> productList = new ArrayList<>();
      for (OrderItem orderItem : orderItemList) {
        Map<String, Object> product = new HashMap<>();
        product.put("sku", orderItem.getProductSku());
        product.put("barcode", orderItem.getProductBarcode());
        product.put("name", orderItem.getProductName());
        product.put("quantity", orderItem.getQuantity());
        product.put("price", orderItem.getTotalAmount());
        productList.add(product);
      }
      ctx.setVariable("products", productList);

      // Prepare the email content
      String subject = null;
      String html = null;

      String siteName = LoadSitePropertyCommand.loadByName("site.name");
      if (EMAIL_TYPE_ORDER_CONFIRMATION.equals(emailType)) {
        subject = "Thanks for your order at " + siteName + "!";
        html = templateEngine.process("ecommerce/order-confirmation.html", ctx);
      } else if (EMAIL_TYPE_SHIPPING_CONFIRMATION.equals(emailType)) {
        subject = "Your " + siteName + " order is on its way!";
        html = templateEngine.process("ecommerce/order-shipped-confirmation.html", ctx);
      }
      if (EMAIL_TYPE_CANCELLATION_CONFIRMATION.equals(emailType)) {
        subject = "Your " + siteName + " order has been canceled";
        html = templateEngine.process("ecommerce/order-cancellation-confirmation.html", ctx);
      }
      if (EMAIL_TYPE_REFUND_CONFIRMATION.equals(emailType)) {
        subject = "Your " + siteName + " order has been refunded";
        html = templateEngine.process("ecommerce/order-refund-confirmation.html", ctx);
      }

      if (subject == null || html == null) {
        LOG.error("Email could not be generated for emailType: " + emailType);
        return;
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug(html);
      }

      // Send the email
      String siteUrl = LoadSitePropertyCommand.loadByName("site.url");
      String ecommerceFromName = LoadSitePropertyCommand.loadByName("ecommerce.from.name");
      String ecommerceFromEmail = LoadSitePropertyCommand.loadByName("ecommerce.from.email");

      ImageHtmlEmail email = EmailCommand.prepareNewEmail(siteUrl);
      if (StringUtils.isNotBlank(ecommerceFromEmail)) {
        if (StringUtils.isNotBlank(ecommerceFromName)) {
          email.setFrom(ecommerceFromEmail, ecommerceFromName);
        } else {
          email.setFrom(ecommerceFromEmail);
        }
      }
      email.addTo(order.getEmail());
      email.setSubject(subject);
      email.setHtmlMsg(html);
      email.setTextMsg(HtmlCommand.text(html));
      email.send();
    }

    private static void addValue(Map<String, Object> map, String name, Object value) {
      if (value == null) {
        return;
      }
      map.put(name, value);
    }
  }
}
