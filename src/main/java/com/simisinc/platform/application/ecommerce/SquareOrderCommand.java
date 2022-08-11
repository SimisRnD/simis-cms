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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.admin.SendEcommerceManagerEmailCommand;
import com.simisinc.platform.domain.model.ecommerce.Address;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.domain.model.ecommerce.*;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderItemRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductSkuRepository;
import com.squareup.square.models.Error;
import com.squareup.square.models.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.simisinc.platform.application.ecommerce.OrderStatusCommand.PAID;

/**
 * Commands for working with Square Orders
 *
 * @author matt rajkowski
 * @created 1/19/20 10:16 AM
 */
public class SquareOrderCommand {

  private static Log LOG = LogFactory.getLog(SquareOrderCommand.class);

  public static boolean chargeOrder(Order order) throws DataException {

    // https://developer.squareup.com/docs/orders-api/create-orders
    // https://developer.squareup.com/docs/orders-api/pay-for-orders

    LOG.debug("chargeOrder() called...");

    if (order == null) {
      LOG.debug("Order is false");
      return false;
    }

    if (order.getPaid()) {
      LOG.debug("Order is paid");
      return true;
    }

    if (order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
      // There's nothing to charge
      LOG.warn("The amount is 0: " + order.getTotalAmount());
      return false;
    }

    // Determine the processor
    String service = LoadSitePropertyCommand.loadByName("ecommerce.paymentProcessor");
    if (!"Square".equalsIgnoreCase(service)) {
      LOG.warn("Square is not configured");
      return false;
    }
    boolean productionEnabled = EcommerceCommand.isProductionEnabled();

    // Require a token
    if (StringUtils.isBlank(order.getPaymentToken())) {
      throw new DataException("The card processor did not return a nonce to use");
    }

    // Square order process: create order, pay for order
    String squareOrderId = createOrder(order);
    if (StringUtils.isNotBlank(squareOrderId)) {
      // Reference the square order and process payment
      order.setSquareOrderId(squareOrderId);
      return payForOrder(order, productionEnabled);
    } else {
      // Fallback to charge the order anyhow - a possible Catalog mismatch
      return SquarePaymentCommand.takePayment(order);
    }
  }

  private static String createOrder(Order order) throws DataException {

    // https://developer.squareup.com/docs/orders-api/create-orders

    // Check for a required locationId
    // @todo can use /v2/locations to possibly find the location
    String locationId = null;
    if (EcommerceCommand.isProductionEnabled()) {
      locationId = LoadSitePropertyCommand.loadByName("ecommerce.square.production.location");
    } else {
      locationId = LoadSitePropertyCommand.loadByName("ecommerce.square.test.location");
    }

    // The top-level container for order information. Order objects include fields for line item details,
    // fulfillment details, and order summary data, including the location ID credited with the order,
    // and the total amount of taxes collected
    com.squareup.square.models.Order.Builder squareOrderBuilder =
        new com.squareup.square.models.Order.Builder(locationId)
            .referenceId(order.getUniqueId());

    List<OrderLineItem> lineItems = new ArrayList<>();
    List<OrderItem> orderItemList = OrderItemRepository.findItemsByOrderId(order.getId());
    for (OrderItem orderItem : orderItemList) {
      // Use the synchronized product info
      Product product = LoadProductCommand.loadProductMetaDataById(orderItem.getProductId());
      ProductSku productSku = ProductSkuRepository.findById(orderItem.getSkuId());
      if (product == null || productSku == null) {
        throw new DataException("Data not found issue");
      }
      if (StringUtils.isBlank(product.getSquareCatalogId()) || StringUtils.isBlank(productSku.getSquareVariationId())) {
        // When order cannot be sent, return a null orderId
        LOG.error("Data not synchronized issue: " + productSku.getSku());
        return null;
      }

      // Specify the SKU being ordered as the catalogId
      long squareItemCentsAmount = orderItem.getEachAmount().multiply(new BigDecimal(100)).longValue();
      OrderLineItem.Builder lineItemBuilder = new OrderLineItem.Builder(orderItem.getQuantity().toPlainString());
      lineItemBuilder.catalogObjectId(productSku.getSquareVariationId());
      lineItemBuilder.basePriceMoney(new Money(squareItemCentsAmount, "USD"));
      lineItems.add(lineItemBuilder.build());
    }
    squareOrderBuilder.lineItems(lineItems);

    // Add any discount
    if (order.getDiscountAmount() != null && order.getDiscountAmount().doubleValue() > 0) {
      // "uid": "ADHOC_DISCOUNT_UID",
      // "name": "Sale - $1.00 off",
      //  "amount_money": {
      //    "amount": 100,
      //    "currency": "USD"
      //  },
      //  "scope": "ORDER"
      List<OrderLineItemDiscount> discounts = new ArrayList<>();
      long squareCentsAmount = order.getDiscountAmount().multiply(new BigDecimal(100)).longValue();
      OrderLineItemDiscount.Builder discountBuilder =
          new OrderLineItemDiscount.Builder()
              .amountMoney(new Money(squareCentsAmount, "USD"))
              .type("FIXED_AMOUNT")
              .scope("ORDER");
      if (StringUtils.isNotBlank(order.getPromoCode())) {
        discountBuilder.name("Promo code " + order.getPromoCode());
      }
      discounts.add(discountBuilder.build());
      squareOrderBuilder.discounts(discounts);
    }

    // Determine any shipping charges
    List<OrderServiceCharge> serviceCharges = new ArrayList<>();
    if (order.getShippingFee().doubleValue() > 0.0) {
      long squareCentsAmount = order.getShippingFee().multiply(new BigDecimal(100)).longValue();
      OrderServiceCharge.Builder serviceChargeBuilder = new OrderServiceCharge.Builder()
          .name("Shipping")
          .amountMoney(new Money(squareCentsAmount, "USD"));
      if (SalesTaxCommand.isShippingTaxable(order.getShippingAddress())) {
        serviceChargeBuilder.calculationPhase("SUBTOTAL_PHASE");
        serviceChargeBuilder.taxable(true);
      } else {
        serviceChargeBuilder.calculationPhase("TOTAL_PHASE");
        serviceChargeBuilder.taxable(false);
      }
      serviceCharges.add(serviceChargeBuilder.build());
    }

    // Determine any handling charges
    if (order.getHandlingFee().doubleValue() > 0.0) {
      long squareCentsAmount = order.getHandlingFee().multiply(new BigDecimal(100)).longValue();
      OrderServiceCharge.Builder serviceChargeBuilder = new OrderServiceCharge.Builder()
          .name("Handling")
          .amountMoney(new Money(squareCentsAmount, "USD"));
      if (SalesTaxCommand.isHandlingTaxable(order.getShippingAddress())) {
        serviceChargeBuilder.calculationPhase("SUBTOTAL_PHASE");
        serviceChargeBuilder.taxable(true);
      } else {
        serviceChargeBuilder.calculationPhase("TOTAL_PHASE");
        serviceChargeBuilder.taxable(false);
      }
      serviceCharges.add(serviceChargeBuilder.build());
    }

    // Add any service changes
    if (!serviceCharges.isEmpty()) {
      squareOrderBuilder.serviceCharges(serviceCharges);
    }

    // Add any tax
    if (order.getTaxAmount() != null && order.getTaxAmount().doubleValue() > 0) {
      // "uid": "ADHOC_TAX_UID",
      // "name": "Virginia Sales Tax",
      //  "amount_money": {
      //    "amount": 535,
      //    "currency": "USD"
      //  },
      //  "scope": "ORDER"
      long squareCentsAmount = order.getTaxAmount().multiply(new BigDecimal(100)).longValue();
      BigDecimal taxPercentage = order.getTaxRate().multiply(new BigDecimal(100));
      OrderLineItemTax.Builder taxBuilder = new OrderLineItemTax.Builder()
          .name("Sales Tax")
          .percentage(taxPercentage.toPlainString())
          .appliedMoney(new Money(squareCentsAmount, "USD"))
          .type("ADDITIVE")
          .scope("ORDER");
      List<OrderLineItemTax> taxes = new ArrayList<>();
      taxes.add(taxBuilder.build());
      squareOrderBuilder.taxes(taxes);
    }

    // Create the Square Order record (to track order details in Square)
    CreateOrderRequest.Builder createOrderRequest = new CreateOrderRequest.Builder()
        .idempotencyKey(UUID.randomUUID().toString())
        .order(squareOrderBuilder.build());

    try {
      // Create the JSON string
      String data = new ObjectMapper()
          .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
          .writeValueAsString(createOrderRequest);

      if (LOG.isDebugEnabled()) {
        LOG.debug("Order String: " + data);
      }

      // Send to Square
      JsonNode json = SquareApiClientCommand.sendSquareHttpPost("/v2/locations/" + locationId + "/orders", data);
      if (json == null) {
        throw new DataException("The order could not be processed");
      }
      // Determine the response
      CreateOrderResponse response = new ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .readerFor(CreateOrderResponse.class)
          .readValue(json);
      if (response == null) {
        throw new DataException("The order information could not be processed, please check the order details");
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug(response.toString());
      }
      if (!response.getErrors().isEmpty()) {
        StringBuilder sb = new StringBuilder();
        for (Error error : response.getErrors()) {
          if (sb.length() > 0) {
            sb.append("; ");
          }
          String errorDetail = error.getDetail();
          if (StringUtils.isNotBlank(errorDetail)) {
            sb.append(errorDetail);
          }
        }
        throw new DataException("The order information could not be processed, please check the following: " + sb.toString());
      }

      // Use the order for the payment
      String squareOrderId = response.getOrder().getId();
      if (LOG.isDebugEnabled()) {
        LOG.debug("Square order id: " + squareOrderId);
      }
      return squareOrderId;

    } catch (DataException de) {
      LOG.error("Square Order creation skipped for #" + order.getUniqueId(), de);
      // Let admins know something is wrong
      SendEcommerceManagerEmailCommand.sendMessage("Square Order creation skipped for #" + order.getUniqueId(), null, de.getMessage());
      return null;
    } catch (Exception e) {
      LOG.error("Exception when calling OrdersApi#createOrder", e);
      throw new DataException("Sorry, a system error occurred contacting the processing company, please try again later");
    }
  }


  private static boolean payForOrder(Order order, boolean productionEnabled) throws DataException {

    // https://developer.squareup.com/docs/orders-api/pay-for-orders

    LOG.debug("payForOrder() called...");

    // Prepare for the new order status
    int statusId = OrderStatusCommand.retrieveStatusId(PAID);
    //Customer customer = CustomerRepository.findById(order.getCustomerId());

    // Prepare the charge request
    long squareCentsAmount = order.getTotalAmount().multiply(new BigDecimal(100)).longValue();
    LOG.debug("Using square amount: " + squareCentsAmount);

    // Use the shipping address
    com.squareup.square.models.Address.Builder shippingAddressBuilder = new com.squareup.square.models.Address.Builder();
    shippingAddressBuilder.addressLine1(order.getShippingAddress().getStreet());
    if (StringUtils.isNotBlank(order.getShippingAddress().getAddressLine2())) {
      shippingAddressBuilder.addressLine2(order.getShippingAddress().getAddressLine2());
    }
    if (StringUtils.isNotBlank(order.getShippingAddress().getAddressLine3())) {
      shippingAddressBuilder.addressLine3(order.getShippingAddress().getAddressLine3());
    }
    shippingAddressBuilder.locality(order.getShippingAddress().getCity());
    shippingAddressBuilder.administrativeDistrictLevel1(order.getShippingAddress().getState());
    shippingAddressBuilder.postalCode(order.getShippingAddress().getPostalCode());
    if ("UNITED STATES".equalsIgnoreCase(order.getShippingAddress().getCountry())) {
      shippingAddressBuilder.country("US");
    }
    com.squareup.square.models.Address shippingAddress = shippingAddressBuilder.build();

    // Create the Square Payment record
    CreatePaymentRequest.Builder createPaymentRequest =
        new CreatePaymentRequest.Builder(order.getPaymentToken(), UUID.randomUUID().toString(), new Money(squareCentsAmount, "USD"))
            .orderId(order.getSquareOrderId())
            .referenceId(order.getUniqueId()) //Max 40 characters
            .buyerEmailAddress(order.getEmail())
            .shippingAddress(shippingAddress);

    try {
      // Create the JSON string
      String data = new ObjectMapper()
          .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
          .writeValueAsString(createPaymentRequest);

      if (LOG.isDebugEnabled()) {
        LOG.debug("Payment String: " + data);
      }

      // Send to Square
      JsonNode json = SquareApiClientCommand.sendSquareHttpPost("/v2/payments", data);
      if (json == null) {
        throw new DataException("The payment could not be processed");
      }
      // Determine the response
      CreatePaymentResponse response = new ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .readerFor(CreatePaymentResponse.class)
          .readValue(json);
      if (response == null) {
        throw new DataException("The payment information could not be processed, please check your payment details");
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug(response.toString());
      }
      if (!response.getErrors().isEmpty()) {
        StringBuilder sb = new StringBuilder();
        for (Error error : response.getErrors()) {
          if (sb.length() > 0) {
            sb.append("; ");
          }
          String errorDetail = error.getDetail();
          if (StringUtils.isNotBlank(errorDetail)) {
            sb.append(errorDetail);
          }
        }
        throw new DataException("The payment information could not be processed, please check the following: " + sb.toString());
      }

      // Update the order
      com.squareup.square.models.Payment payment = response.getPayment();
      if (LOG.isDebugEnabled()) {
        LOG.debug("Charge status: " + payment.getStatus());
      }

      // The charge was successful, save the updated info
      order.setPaymentProcessor("square");
      order.setLive(productionEnabled);
      order.setChargeToken(payment.getId());
      boolean isPaid = "APPROVED".equalsIgnoreCase(payment.getStatus()) || "COMPLETED".equalsIgnoreCase(payment.getStatus());
      if (isPaid) {
        order.setStatusId(statusId);
        order.setPaid(true);
        order.setPaymentDate(new Timestamp(System.currentTimeMillis()));
        order.setTotalPaid(order.getTotalAmount());
      }
      if (payment.getBillingAddress() != null) {
        Address billingAddress = new Address();
        billingAddress.setCity(payment.getBillingAddress().getLocality());
        billingAddress.setCountry(payment.getBillingAddress().getCountry());
        billingAddress.setPostalCode(payment.getBillingAddress().getPostalCode());
        billingAddress.setState(payment.getBillingAddress().getAdministrativeDistrictLevel1());
        order.setBillingAddress(billingAddress);
      }
      if (payment.getCardDetails() != null) {
        com.squareup.square.models.Card card = payment.getCardDetails().getCard();
        if (card != null) {
          order.setPaymentType("card");
          order.setPaymentBrand(card.getCardBrand());
          order.setPaymentLast4(card.getLast4());
          order.setPaymentFingerprint(card.getFingerprint());
          //order.setPaymentCountry(card.getCountry());
        }
      }
      // Update the order in the database
      if (OrderRepository.save(order) != null) {
        // Send emails to the customer and e-commerce managers
        OrderEmailCommand.sendConfirmationEmail(order);
        return true;
      }
    } catch (DataException de) {
      // Let admins know something is wrong
      if (productionEnabled) {
        SendEcommerceManagerEmailCommand.sendMessage("Square Order payment error occurred #" + order.getUniqueId(), null, de.getMessage());
      }
      throw de;
    } catch (Exception e) {
      LOG.error("Exception when calling PaymentsApi#createPayment", e);
      throw new DataException("Sorry, a system error occurred contacting the processing company, please try again later");
    }
    return false;
  }
}
