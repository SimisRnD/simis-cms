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

import com.simisinc.platform.domain.model.ecommerce.Address;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Validates customer information
 *
 * @author matt rajkowski
 * @created 6/24/19 7:58 PM
 */
public class ValidateCustomerCommand {

  private static Log LOG = LogFactory.getLog(ValidateCustomerCommand.class);

  public static boolean validateCustomerShippingAddress(Address address, StringBuilder errorMessages) {

    if (errorMessages == null) {
      errorMessages = new StringBuilder();
    }

    // Check the shipping information
    if (StringUtils.isBlank(address.getFirstName()) || StringUtils.isBlank(address.getLastName())) {
      appendMessage(errorMessages, "A name is required");
    }
    if (StringUtils.isBlank(address.getStreet())) {
      appendMessage(errorMessages, "An address is required");
    }
    if (StringUtils.isBlank(address.getCity())) {
      appendMessage(errorMessages, "A city is required");
    }
    if (StringUtils.isBlank(address.getState())) {
      appendMessage(errorMessages, "A state is required");
    }
    if (StringUtils.isBlank(address.getCountry())) {
      appendMessage(errorMessages, "A country is required");
    }
    if (StringUtils.isBlank(address.getPostalCode())) {
      appendMessage(errorMessages, "A postal code is required");
    }

    return (errorMessages.length() == 0);
  }

  private static void appendMessage(StringBuilder errorMessages, String message) {
    if (errorMessages.length() > 0) {
      errorMessages.append("; ");
    }
    errorMessages.append(message);
  }

}
