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

package com.simisinc.platform.infrastructure.persistence.ecommerce;

import com.simisinc.platform.presentation.controller.DataConstants;

/**
 * Properties for querying objects from the pricing rule repository
 *
 * @author matt rajkowski
 * @created 11/21/19 8:57 PM
 */
public class PricingRuleSpecification {

  private String promoCode = null;
  private int enabled = DataConstants.UNDEFINED;
  private int isValidToday = DataConstants.UNDEFINED;
  private int hasPromoCode = DataConstants.UNDEFINED;
  private String countryCode = null;
  private String includesSku = null;

  public PricingRuleSpecification() {
  }

  public String getPromoCode() {
    return promoCode;
  }

  public void setPromoCode(String promoCode) {
    this.promoCode = promoCode;
  }

  public int getEnabled() {
    return enabled;
  }

  public void setEnabled(int enabled) {
    this.enabled = enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = (enabled ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getIsValidToday() {
    return isValidToday;
  }

  public void setIsValidToday(int isValidToday) {
    this.isValidToday = isValidToday;
  }

  public void setIsValidToday(boolean isValidToday) {
    this.isValidToday = (isValidToday ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getHasPromoCode() {
    return hasPromoCode;
  }

  public void setHasPromoCode(int hasPromoCode) {
    this.hasPromoCode = hasPromoCode;
  }

  public void setHasPromoCode(boolean hasPromoCode) {
    this.hasPromoCode = (hasPromoCode ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public String getCountryCode() {
    return countryCode;
  }

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

  public String getIncludesSku() {
    return includesSku;
  }

  public void setIncludesSku(String includesSku) {
    this.includesSku = includesSku;
  }
}
