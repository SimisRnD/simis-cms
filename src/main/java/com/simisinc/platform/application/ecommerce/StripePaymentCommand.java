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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.ecommerce.*;
import com.simisinc.platform.infrastructure.persistence.ecommerce.CustomerRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderRepository;
import com.stripe.Stripe;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Token;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import static com.simisinc.platform.application.ecommerce.OrderStatusCommand.PAID;

/**
 * Commands for working with Stripe Payments
 *
 * @author matt rajkowski
 * @created 7/5/19 4:35 PM
 */
public class StripePaymentCommand {

  private static Log LOG = LogFactory.getLog(StripePaymentCommand.class);

  public static void configureService() {
    // Set your secret key: remember to change this to your live secret key in production
    // See your keys here: https://dashboard.stripe.com/account/apikeys
    String key;
    if (EcommerceCommand.isProductionEnabled()) {
      key = LoadSitePropertyCommand.loadByName("ecommerce.stripe.production.secret");
    } else {
      key = LoadSitePropertyCommand.loadByName("ecommerce.stripe.test.secret");
    }
    if (StringUtils.isNotBlank(key)) {
      Stripe.apiKey = key;
    } else {
      LOG.warn("Stripe API key is not configured");
    }
  }

  public static void validateToken(Cart cart, String tokenValue) throws DataException {
    // https://stripe.com/docs/api/tokens/retrieve
    try {
      // Validate the token
      configureService();
      Token token = Token.retrieve(tokenValue);
      // Process the response
      Card card = null;
      if ("card".equals(token.getType())) {
        card = new Card(token.getCard());
      } else {
        card = new Card();
        card.setBrand("Payment to be processed by Stripe");
      }
      // Update the cart
      CartCommand.savePaymentToken(cart, tokenValue, card);
    } catch (Exception e) {
      LOG.warn(e);
      throw new DataException("The payment service reported an error");
    }
  }

  public static boolean chargeOrder(Order order) throws DataException {

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
      LOG.debug("The amount is 0: " + order.getTotalAmount());
      return false;
    }

    // Determine the processor
    String service = LoadSitePropertyCommand.loadByName("ecommerce.paymentProcessor");
    if (!"Stripe".equalsIgnoreCase(service)) {
      LOG.warn("Stripe is not configured");
      return false;
    }

    // Require a token
    if (StringUtils.isBlank(order.getPaymentToken())) {
      throw new DataException("The card processor did not return a token to use");
    }

    // Prepare for the new order status
    int statusId = OrderStatusCommand.retrieveStatusId(PAID);
    Customer customer = CustomerRepository.findById(order.getCustomerId());

    // Make sure stripe is configured
    configureService();

    // Prepare the charge request
    long stripeCentsAmount = order.getTotalAmount().multiply(new BigDecimal(100)).longValue();
    LOG.debug("Using stripe amount: " + stripeCentsAmount);

    Map<String, Object> params = new HashMap<>();
    params.put("amount", stripeCentsAmount);
    params.put("currency", "usd");
    //      params.put("description", "Example charge");
    params.put("source", order.getPaymentToken());
    // 10 chars, appears on statement after NAME* COSMETICS
    //      params.put("statement_descriptor", "COSMETICS");

    Map<String, String> metadata = new HashMap<>();
    metadata.put("order_id", String.valueOf(order.getUniqueId()));
    if (customer != null) {
      metadata.put("customer_id", String.valueOf(customer.getUniqueId()));
    }
    params.put("metadata", metadata);

    try {
      // Make the charge request
      LOG.debug("Charging the customer...");
      Charge charge = Charge.create(params);
      if (charge.getLivemode() && charge.getPaid()) {
        LOG.info("Charge paid for order: " + order.getId());
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug(charge.toString());
      }
      // The charge was successful, save the updated info
      order.setPaymentProcessor("stripe");
      order.setStatusId(statusId);
      order.setChargeToken(charge.getId());
      order.setLive(charge.getLivemode());
      order.setPaid(charge.getPaid());
      order.setPaymentDate(new Timestamp(System.currentTimeMillis()));
      order.setTotalPaid(order.getTotalAmount());
      if (charge.getBillingDetails() != null && order.getBillingAddress() == null) {
        Address billingAddress = new Address();
        billingAddress.setCity(charge.getBillingDetails().getAddress().getCity());
        billingAddress.setCountry(charge.getBillingDetails().getAddress().getCountry());
        billingAddress.setPostalCode(charge.getBillingDetails().getAddress().getPostalCode());
        billingAddress.setState(charge.getBillingDetails().getAddress().getState());
        order.setBillingAddress(billingAddress);
      }
      if (charge.getPaymentMethodDetails() != null) {
        Charge.PaymentMethodDetails paymentMethodDetails = charge.getPaymentMethodDetails();
        if ("card".equals(paymentMethodDetails.getType())) {
          Charge.PaymentMethodDetails.Card card = paymentMethodDetails.getCard();
          if (card != null) {
            order.setPaymentType("card");
            order.setPaymentBrand(card.getBrand());
            order.setPaymentLast4(card.getLast4());
            order.setPaymentFingerprint(card.getFingerprint());
            order.setPaymentCountry(card.getCountry());
          }
        }
      }
      // Update the order in the database
      if (OrderRepository.save(order) != null) {
        // Send emails to the customer and e-commerce managers
        OrderEmailCommand.sendConfirmationEmail(order);
        return true;
      }
    } catch (AuthenticationException e) {
      // Authentication with API failed. Log error.
      LOG.error("Authentication error", e);
      throw new DataException("Sorry, a system error occurred contacting the processing company, please try again later");
    } catch (InvalidRequestException e) {
      // Invalid parameters, log error.
      LOG.error("Invalid parameters", e);
      throw new DataException("Sorry, a system error occurred contacting the processing company, please try again later");
    } catch (CardException e) {
      // A card error
      // Code                  Details
      // incorrect_number      The card number is incorrect
      // invalid_number        The card number is not a valid credit card number
      // invalid_expiry_month  The card's expiration month is invalid
      // invalid_expiry_year   The card's expiration year is invalid
      // invalid_cvc           The card's security code is invalid
      // expired_card          The card has expired
      // incorrect_cvc         The card's security code is incorrect
      // card_declined         The card was declined.
      // missing               There is no card on a customer that is being charged.
      // processing_error      An error occurred while processing the card.

      LOG.info("Card exception: order = " + order.getId() + "; " + e.getCode() + " = " + e.getParam());

      String errorCode = e.getCode();
      String errorMessage = "An error occurred while processing the card, without further information";
      if ("invalid_number".equals(errorCode) || "incorrect_number".equals(errorCode)) {
        errorMessage = "The card number is incorrect";
      } else if ("invalid_cvc".equals(errorCode) || "incorrect_cvc".equals(errorCode)) {
        errorMessage = "The CVC is incorrect";
      } else if ("invalid_expiry_month".equals(errorCode) || "invalid_expiry_year".equals(errorCode)) {
        errorMessage = "The expiration is incorrect";
      } else if ("expired_card".equals(errorCode)) {
        errorMessage = "The card has expired";
      } else if ("card_declined".equals(errorCode)) {
        errorMessage = "The card was declined";
      }
      throw new DataException(errorMessage);
    } catch (StripeException e) {
      LOG.error("Charge error", e);
      throw new DataException(e.getMessage());
    }
    return false;
  }

  public static boolean refundPayment(Order order, BigDecimal amountToRefund) throws DataException {
    throw new DataException("Service is not implemented");
  }

}
