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

package com.simisinc.platform.domain.model.ecommerce;

import com.simisinc.platform.domain.model.Entity;

import java.math.BigDecimal;

/**
 * E-commerce shipping rate
 *
 * @author matt rajkowski
 * @created 5/2/19 6:32 AM
 */
public class ShippingRate extends Entity {

  private Long id = -1L;
  private String countryCode = null;
  private String region = null;
  private String postalCode = null;
  private BigDecimal minSubTotal = null;
  private int minWeightOz = 0;
  private BigDecimal shippingFee = null;
  private BigDecimal handlingFee = null;
  private int shippingMethodId = -1;
  private String shippingCode = null;
  private String description = null;
  private String displayText = null;
  private String excludeSkus = null;

  public ShippingRate() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getCountryCode() {
    return countryCode;
  }

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public void setPostalCode(String postalCode) {
    this.postalCode = postalCode;
  }

  public BigDecimal getMinSubTotal() {
    return minSubTotal;
  }

  public void setMinSubTotal(BigDecimal minSubTotal) {
    this.minSubTotal = minSubTotal;
  }

  public int getMinWeightOz() {
    return minWeightOz;
  }

  public void setMinWeightOz(int minWeightOz) {
    this.minWeightOz = minWeightOz;
  }

  public BigDecimal getShippingFee() {
    return shippingFee;
  }

  public void setShippingFee(BigDecimal shippingFee) {
    this.shippingFee = shippingFee;
  }

  public BigDecimal getHandlingFee() {
    return handlingFee;
  }

  public void setHandlingFee(BigDecimal handlingFee) {
    this.handlingFee = handlingFee;
  }

  public int getShippingMethodId() {
    return shippingMethodId;
  }

  public void setShippingMethodId(int shippingMethodId) {
    this.shippingMethodId = shippingMethodId;
  }

  public String getShippingCode() {
    return shippingCode;
  }

  public void setShippingCode(String shippingCode) {
    this.shippingCode = shippingCode;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getDisplayText() {
    return displayText;
  }

  public void setDisplayText(String displayText) {
    this.displayText = displayText;
  }

  public String getExcludeSkus() {
    return excludeSkus;
  }

  public void setExcludeSkus(String excludeSkus) {
    this.excludeSkus = excludeSkus;
  }

  public BigDecimal getTotal() {
    if (shippingFee != null && handlingFee != null) {
      return shippingFee.add(handlingFee);
    }
    if (shippingFee != null) {
      return shippingFee;
    }
    if (handlingFee != null) {
      return handlingFee;
    }
    return null;
  }
}
