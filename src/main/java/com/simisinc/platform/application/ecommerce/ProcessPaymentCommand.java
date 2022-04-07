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
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.domain.model.ecommerce.Payment;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/8/19 9:03 AM
 */
public class ProcessPaymentCommand {

  private static Log LOG = LogFactory.getLog(ProcessPaymentCommand.class);

  /**
   * This payment processor is an offline testing mode
   *
   * @param paymentBean
   * @return
   * @throws DataException
   */
  public static boolean validatePayment(Payment paymentBean) throws DataException {
    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();

    if (StringUtils.isBlank(paymentBean.getCreditCardNumber())) {
      appendMessage(errorMessages, "A credit card number is required");
    }
    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    return false;
  }

  public static Order startPayment(Payment paymentBean) throws DataException {

    return null;
  }

  public static Order executePayment(Payment paymentBean) throws DataException {

    return null;
  }

  private static void appendMessage(StringBuilder errorMessages, String message) {
    if (errorMessages.length() > 0) {
      errorMessages.append("; ");
    }
    errorMessages.append(message);
  }
}
