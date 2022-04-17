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

import java.math.BigDecimal;

/**
 * Properties for querying objects from the shipping rate repository
 *
 * @author matt rajkowski
 * @created 7/3/19 8:35 AM
 */
public class ShippingRateSpecification {

  private String countryCode = null;
  private String region = null;
  private String postalCode = null;

  private BigDecimal orderSubtotal = null;
  private int packageTotalWeightOz = 0;

  private boolean enabledOnly = false;
  private boolean specificRegionOnly = false;

  public ShippingRateSpecification() {
  }

  public void setAddress(String countryCode, String region, String postalCode) {
    this.countryCode = countryCode;
    this.region = region;
    this.postalCode = postalCode;
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

  public BigDecimal getOrderSubtotal() {
    return orderSubtotal;
  }

  public void setOrderSubtotal(BigDecimal orderSubtotal) {
    this.orderSubtotal = orderSubtotal;
  }

  public int getPackageTotalWeightOz() {
    return packageTotalWeightOz;
  }

  public void setPackageTotalWeightOz(int packageTotalWeightOz) {
    this.packageTotalWeightOz = packageTotalWeightOz;
  }

  public boolean getEnabledOnly() {
    return enabledOnly;
  }

  public void setEnabledOnly(boolean enabledOnly) {
    this.enabledOnly = enabledOnly;
  }

  public boolean getSpecificRegionOnly() {
    return specificRegionOnly;
  }

  public void setSpecificRegionOnly(boolean specificRegionOnly) {
    this.specificRegionOnly = specificRegionOnly;
  }
}
