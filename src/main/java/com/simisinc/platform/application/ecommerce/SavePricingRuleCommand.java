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
import com.simisinc.platform.domain.model.ecommerce.PricingRule;
import com.simisinc.platform.infrastructure.persistence.ecommerce.PricingRuleRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 12/12/19 6:07 PM
 */
public class SavePricingRuleCommand {

  private static Log LOG = LogFactory.getLog(SavePricingRuleCommand.class);

  public static PricingRule savePricingRule(PricingRule pricingRuleBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();

    if (pricingRuleBean.getCreatedBy() == -1 || pricingRuleBean.getModifiedBy() == -1) {
      errorMessages.append("The user saving this record was not set");
    }

    // Validate values
    if (StringUtils.isBlank(pricingRuleBean.getName())) {
      appendMessage(errorMessages, "A name is required");
    }
    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    PricingRule pricingRule;
    if (pricingRuleBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      pricingRule = PricingRuleRepository.findById(pricingRuleBean.getId());
      if (pricingRule == null) {
        throw new DataException("The existing record could not be found: " + pricingRuleBean.getId());
      }
    } else {
      LOG.debug("Saving a new record... ");
      pricingRule = new PricingRule();
    }
    pricingRule.setName(pricingRuleBean.getName());
    pricingRule.setDescription(pricingRuleBean.getDescription());
    pricingRule.setFromDate(pricingRuleBean.getFromDate());
    pricingRule.setToDate(pricingRuleBean.getToDate());
    pricingRule.setPromoCode(pricingRuleBean.getPromoCode());
    pricingRule.setUsesPerCode(pricingRuleBean.getUsesPerCode());
    pricingRule.setUsesPerCustomer(pricingRuleBean.getUsesPerCustomer());
    pricingRule.setEnabled(pricingRuleBean.getEnabled());
    // Rules
    pricingRule.setCountryCode(pricingRuleBean.getCountryCode());
    pricingRule.setMinimumSubtotal(pricingRuleBean.getMinimumSubtotal());
    pricingRule.setMinimumOrderQuantity(pricingRuleBean.getMinimumOrderQuantity());
    pricingRule.setMaximumOrderQuantity(pricingRuleBean.getMaximumOrderQuantity());
    pricingRule.setItemLimit(pricingRuleBean.getItemLimit());
    pricingRule.setBuyXItems(pricingRuleBean.getBuyXItems());
    pricingRule.setGetYItemsFree(pricingRuleBean.getGetYItemsFree());
    pricingRule.setValidSkus(pricingRuleBean.getValidSkus());
    pricingRule.setInvalidSkus(pricingRuleBean.getInvalidSkus());
    pricingRule.setFreeShippingCode(pricingRuleBean.getFreeShippingCode());
    // Outcomes
    pricingRule.setSubtotalPercent(pricingRuleBean.getSubtotalPercent());
    pricingRule.setSubtractAmount(pricingRuleBean.getSubtractAmount());
    pricingRule.setFreeShipping(pricingRuleBean.getFreeShipping());
    pricingRule.setFreeProductSku(pricingRuleBean.getFreeProductSku());
    pricingRule.setCreatedBy(pricingRuleBean.getCreatedBy());
    pricingRule.setModifiedBy(pricingRuleBean.getModifiedBy());
    return PricingRuleRepository.save(pricingRule);
  }

  private static void appendMessage(StringBuilder errorMessages, String message) {
    if (errorMessages.length() > 0) {
      errorMessages.append("; ");
    }
    errorMessages.append(message);
  }

}
