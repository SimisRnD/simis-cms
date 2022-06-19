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
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.squareup.square.models.Error;
import com.squareup.square.models.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Commands for working with Square Refunds
 *
 * @author matt rajkowski
 * @created 11/6/19 9:01 PM
 */
public class SquareRefundCommand {

  private static Log LOG = LogFactory.getLog(SquareRefundCommand.class);

  public static boolean refundPayment(Order order, BigDecimal amountToRefund) throws DataException {

    // https://developer.squareup.com/docs/payments-api/refund-payments

    LOG.debug("refundPayment() called...");

    if (StringUtils.isBlank(order.getChargeToken())) {
      LOG.debug("The order does not have a charge/payment token");
      return false;
    }

    // Prepare the refund request
    long squareCentsAmount = amountToRefund.multiply(new BigDecimal(100)).longValue();
    LOG.debug("Using square amount: " + squareCentsAmount);

    // Create the Square Refund record
    RefundPaymentRequest refundPaymentRequest =
        new RefundPaymentRequest.Builder(UUID.randomUUID().toString(), new Money(squareCentsAmount, "USD"))
            .paymentId(order.getChargeToken())
            .build();
    try {
      // Create the JSON string
      String data = new ObjectMapper()
          .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
          .writeValueAsString(refundPaymentRequest);
      // Send to Square
      JsonNode json = SquareApiClientCommand.sendSquareHttpPost("/v2/refunds", data);
      if (json == null) {
        throw new DataException("The refund could not be processed");
      }
      // Determine the response
      RefundPaymentResponse response = new ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .readerFor(RefundPaymentResponse.class)
          .readValue(json);
      if (response == null) {
        throw new DataException("The refund information could not be processed, please check your refund details");
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
        throw new DataException("The refund information could not be processed, please check the following: " + sb.toString());
      }

      // Update the order
      PaymentRefund paymentRefund = response.getRefund();
      if (LOG.isDebugEnabled()) {
        LOG.debug("Refund status: " + paymentRefund.getStatus());
      }
      // The refund was successful
      return true;
    } catch (DataException de) {
      throw de;
    } catch (Exception e) {
      LOG.error("Exception when calling PaymentsApi#refundPayment", e);
      throw new DataException("Sorry, a system error occurred contacting the processing company, please try again later");
    }
  }
}
