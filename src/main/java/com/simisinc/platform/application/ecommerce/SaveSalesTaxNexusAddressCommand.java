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
import com.simisinc.platform.domain.model.ecommerce.SalesTaxNexusAddress;
import com.simisinc.platform.infrastructure.persistence.ecommerce.SalesTaxNexusAddressRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/29/19 1:55 PM
 */
public class SaveSalesTaxNexusAddressCommand {

  private static Log LOG = LogFactory.getLog(SaveSalesTaxNexusAddressCommand.class);

  public static SalesTaxNexusAddress saveAddress(SalesTaxNexusAddress addressBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();

    if (addressBean.getCreatedBy() == -1 || addressBean.getModifiedBy() == -1) {
      errorMessages.append("The user saving this address was not set");
    }

    // Validate values
    if (StringUtils.isBlank(addressBean.getStreet())) {
      appendMessage(errorMessages, "A street address is required");
    }
    if (StringUtils.isBlank(addressBean.getCity())) {
      appendMessage(errorMessages, "A city is required");
    }
    if (StringUtils.isBlank(addressBean.getState())) {
      appendMessage(errorMessages, "A state/region is required");
    }
    if (StringUtils.isBlank(addressBean.getPostalCode())) {
      appendMessage(errorMessages, "A postal code is required");
    }
    if (StringUtils.isBlank(addressBean.getCountry())) {
      appendMessage(errorMessages, "A country is required");
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    SalesTaxNexusAddress address;
    if (addressBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      address = SalesTaxNexusAddressRepository.findById(addressBean.getId());
      if (address == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      address = new SalesTaxNexusAddress();
    }
    address.setStreet(addressBean.getStreet());
    address.setAddressLine2(addressBean.getAddressLine2());
    address.setCity(addressBean.getCity());
    address.setState(addressBean.getState());
    address.setCountry(addressBean.getCountry());
    address.setPostalCode(addressBean.getPostalCode());
    address.setCreatedBy(addressBean.getCreatedBy());
    address.setModifiedBy(addressBean.getModifiedBy());
    return SalesTaxNexusAddressRepository.save(address);
  }

  private static void appendMessage(StringBuilder errorMessages, String message) {
    if (errorMessages.length() > 0) {
      errorMessages.append("; ");
    }
    errorMessages.append(message);
  }

}
