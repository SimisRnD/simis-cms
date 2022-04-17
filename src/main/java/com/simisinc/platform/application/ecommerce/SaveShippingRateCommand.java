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
import com.simisinc.platform.domain.model.ecommerce.ShippingMethod;
import com.simisinc.platform.domain.model.ecommerce.ShippingRate;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingMethodRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingRateRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Validates and saves shipping rate objects
 *
 * @author matt rajkowski
 * @created 6/26/19 9:57 PM
 */
public class SaveShippingRateCommand {

  private static Log LOG = LogFactory.getLog(SaveShippingRateCommand.class);

  public static ShippingRate saveShippingRate(ShippingRate shippingRateBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();

//    if (shippingRateBean.getCreatedBy() == -1 || shippingRateBean.getModifiedBy() == -1) {
//      errorMessages.append("The user saving this record was not set");
//    }

    // Validate values
    if (StringUtils.isBlank(shippingRateBean.getCountryCode())) {
      appendMessage(errorMessages, "A country is required");
    }
    if (StringUtils.isBlank(shippingRateBean.getRegion())) {
      appendMessage(errorMessages, "A region value or * is required");
    }
    if (StringUtils.isBlank(shippingRateBean.getPostalCode())) {
      appendMessage(errorMessages, "A postal code value or * is required");
    }
    if (shippingRateBean.getShippingFee() == null) {
      appendMessage(errorMessages, "A shipping fee or 0 is required");
    }
    if (shippingRateBean.getHandlingFee() == null) {
      appendMessage(errorMessages, "A handling fee or 0 is required");
    }
    if (shippingRateBean.getShippingMethodId() == -1) {
      appendMessage(errorMessages, "A shipping method is required");
    }
    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Use the shipping method lookup names
    ShippingMethod shippingMethod = ShippingMethodRepository.findById(shippingRateBean.getShippingMethodId());
    if (shippingMethod == null) {
      throw new DataException("An error occurred");
    }
    shippingRateBean.setShippingCode(shippingMethod.getCode());

    // Transform the fields and store...
    ShippingRate shippingRate;
    if (shippingRateBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      shippingRate = ShippingRateRepository.findById(shippingRateBean.getId());
      if (shippingRate == null) {
        throw new DataException("The existing record could not be found: " + shippingRateBean.getId());
      }
    } else {
      LOG.debug("Saving a new record... ");
      shippingRate = new ShippingRate();
    }
    shippingRate.setCountryCode(shippingRateBean.getCountryCode());
    shippingRate.setRegion(shippingRateBean.getRegion());
    shippingRate.setPostalCode(shippingRateBean.getPostalCode());
    shippingRate.setMinSubTotal(shippingRateBean.getMinSubTotal());
    shippingRate.setMinWeightOz(shippingRateBean.getMinWeightOz());
    shippingRate.setShippingFee(shippingRateBean.getShippingFee());
    shippingRate.setHandlingFee(shippingRateBean.getHandlingFee());
    shippingRate.setShippingCode(shippingRateBean.getShippingCode());
    shippingRate.setShippingMethodId(shippingRateBean.getShippingMethodId());
    shippingRate.setDisplayText(shippingRateBean.getDisplayText());
    shippingRate.setExcludeSkus(shippingRateBean.getExcludeSkus());
//    shippingRate.setCreatedBy(shippingRateBean.getCreatedBy());
//    shippingRate.setModifiedBy(shippingRateBean.getModifiedBy());
    return ShippingRateRepository.save(shippingRate);
  }

  private static void appendMessage(StringBuilder errorMessages, String message) {
    if (errorMessages.length() > 0) {
      errorMessages.append("; ");
    }
    errorMessages.append(message);
  }

}
