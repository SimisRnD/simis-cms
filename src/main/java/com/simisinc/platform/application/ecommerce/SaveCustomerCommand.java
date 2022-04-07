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
import com.simisinc.platform.domain.model.ecommerce.Customer;
import com.simisinc.platform.infrastructure.persistence.ecommerce.CustomerRepository;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.routines.checkdigit.CheckDigit;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.apache.commons.validator.routines.checkdigit.ModulusTenCheckDigit;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/24/19 10:08 PM
 */
public class SaveCustomerCommand {

  private static Log LOG = LogFactory.getLog(SaveCustomerCommand.class);

  public static String generateUniqueId(Customer customer) throws CheckDigitException {
    // Use the e-commerce format
    // C-###-####-*****-??
    // Use the customer id after insert
    String prefix = "C-";
    String id = StringUtils.leftPad(String.valueOf(customer.getId()), 7, '0');
    String rand = StringUtils.leftPad(RandomStringUtils.randomNumeric(5), 5, '0');

    CheckDigit routine = new ModulusTenCheckDigit(new int[]{1, 2}, true, true);
    String checkDigit = routine.calculate(id + rand);
    String uniqueId = (prefix + id.substring(0, 3) + "-" + id.substring(3) + "-" + rand + "-" + checkDigit);
    LOG.debug("generateUniqueId-> customer: " + uniqueId);
    return uniqueId;
  }

  public static void saveCustomerContactInfo(long customerId, String firstName, String lastName, String email) throws DataException {
    if (StringUtils.isBlank(firstName)) {
      throw new DataException("A first name was not set");
    }
    if (StringUtils.isBlank(lastName)) {
      throw new DataException("A last name was not set");
    }
    if (StringUtils.isBlank(email)) {
      throw new DataException("An email address was not set");
    }
    Customer customer = CustomerRepository.findById(customerId);
    if (customer == null) {
      throw new DataException("The customer record was not found");
    }
    customer.setFirstName(firstName);
    customer.setLastName(lastName);
    customer.setEmail(email);
    CustomerRepository.updateContactInfo(customer);
  }

  public static Customer saveCustomerShippingAddress(Customer customerBean) throws DataException {
    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    ValidateCustomerCommand.validateCustomerShippingAddress(customerBean.getShippingAddress(), errorMessages);
    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    Customer customer;
    if (customerBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      customer = CustomerRepository.findById(customerBean.getId());
      if (customer == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      customer = new Customer();
    }
//    customer.setEmail(customerBean.getEmail());
//    customer.setPhoneNumber(customerBean.getPhoneNumber());
    customer.setShippingAddress(customerBean.getShippingAddress());
//    customer.setBillingAddress(customerBean.getBillingAddress());
    customer.setCartId(customerBean.getCartId());
    customer.setCreatedBy(customerBean.getCreatedBy());
    customer.setModifiedBy(customerBean.getModifiedBy());
    return CustomerRepository.save(customer);
  }
}
