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

import static com.simisinc.platform.application.ecommerce.OrderStatusCommand.PAID;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.ecommerce.Address;
import com.simisinc.platform.domain.model.ecommerce.Card;
import com.simisinc.platform.domain.model.ecommerce.Cart;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderRepository;
import com.squareup.square.models.CreatePaymentRequest;
import com.squareup.square.models.CreatePaymentResponse;
import com.squareup.square.models.Error;
import com.squareup.square.models.Money;

/**
 * Commands for working with Square Payments
 *
 * @author matt rajkowski
 * @created 11/6/19 9:01 PM
 */
public class SquarePaymentCommand {

  private static Log LOG = LogFactory.getLog(SquarePaymentCommand.class);

  public static void validateNonce(Cart cart, String nonceValue) throws DataException {
    // Validate the value
    if (StringUtils.isBlank(nonceValue)) {
      throw new DataException("The payment service reported an error");
    }
    LOG.debug("NONCE: " + nonceValue);
    Card card = new Card();
    card.setBrand("Payment to be processed by Square");
    CartCommand.savePaymentToken(cart, nonceValue, card);
  }

  public static boolean takePayment(Order order) throws DataException {

    // https://developer.squareup.com/docs/payments-api/take-payments

    LOG.debug("takePayment() called...");

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
      LOG.debug("The amount is 0: " + order.getTotalAmount());
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

    // Prepare for the new order status
    int statusId = OrderStatusCommand.retrieveStatusId(PAID);
    //Customer customer = CustomerRepository.findById(order.getCustomerId());

    // Prepare the charge request
    long squareCentsAmount = order.getTotalAmount().multiply(new BigDecimal(100)).longValue();
    LOG.debug("Using square amount: " + squareCentsAmount);

    // Create the Square Payment record
    CreatePaymentRequest createPaymentRequest =
        new CreatePaymentRequest.Builder(order.getPaymentToken(), UUID.randomUUID().toString(), new Money(squareCentsAmount, "USD"))
//            .orderId(squareOrderId?)
            .referenceId(order.getUniqueId())
            .buyerEmailAddress(order.getEmail())
            .build();

    try {
      // Create the JSON string
      String data = new ObjectMapper()
          .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
          .writeValueAsString(createPaymentRequest);
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
      if (response.getErrors() != null && !response.getErrors().isEmpty()) {
        StringBuilder sb = new StringBuilder();
        for (Error error : response.getErrors()) {
          if (sb.length() > 0) {
            sb.append("; ");
          }
          String errorDetail = error.getDetail();
          if (StringUtils.isNotBlank(errorDetail)) {
            if (errorDetail.contains("PAN_FAILURE")) {
              sb.append("The specified card number is invalid");
            } else {
              sb.append(errorDetail);
            }
          }
        }
        throw new DataException("The payment information could not be processed, please check the following: " + sb.toString());
      }

      // Update the order
      com.squareup.square.models.Payment payment = response.getPayment();
      if (LOG.isDebugEnabled()) {
        LOG.debug("Charge status: " + response.getPayment().getStatus());
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
      throw de;
    } catch (Exception e) {
      LOG.error("Exception when calling PaymentsApi#createPayment", e);
      throw new DataException("Sorry, a system error occurred contacting the processing company, please try again later");
    }
    return false;
  }
}
