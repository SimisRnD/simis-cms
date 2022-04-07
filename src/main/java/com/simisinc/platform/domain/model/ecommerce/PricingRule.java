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
import java.sql.Timestamp;

/**
 * E-commerce pricing rules for promo codes, etc.
 *
 * @author matt rajkowski
 * @created 11/21/19 8:52 PM
 */
public class PricingRule extends Entity {

  private Long id = -1L;
  private String name = null;
  private String description = null;
  private String errorMessage = null;
  private Timestamp fromDate = null;
  private Timestamp toDate = null;
  private String promoCode = null;
  private int usesPerCode = 0;
  private int usesPerCustomer = 0;
  private int timesUsed = 0;
  private long createdBy = -1;
  private long modifiedBy = -1;
  private Timestamp created = null;
  private Timestamp modified = null;
  private boolean enabled = false;

  // Rules
  private String countryCode = null;
  private BigDecimal minimumSubtotal = null;
  private int minimumOrderQuantity = 0;
  private int maximumOrderQuantity = 0;
  private int itemLimit = 0;
  private String validSkus = null;
  private String invalidSkus = null;
  private String freeShippingCode = null;
  private int buyXItems = 0;
  private int getYItemsFree = 0;

  // Outcomes
  private int subtotalPercent = 0;
  private BigDecimal subtractAmount = null;
  private boolean freeShipping = false;
  private String freeProductSku = null;

  public PricingRule() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Timestamp getFromDate() {
    return fromDate;
  }

  public void setFromDate(Timestamp fromDate) {
    this.fromDate = fromDate;
  }

  public Timestamp getToDate() {
    return toDate;
  }

  public void setToDate(Timestamp toDate) {
    this.toDate = toDate;
  }

  public String getPromoCode() {
    return promoCode;
  }

  public void setPromoCode(String promoCode) {
    this.promoCode = promoCode;
  }

  public int getUsesPerCode() {
    return usesPerCode;
  }

  public void setUsesPerCode(int usesPerCode) {
    this.usesPerCode = usesPerCode;
  }

  public int getUsesPerCustomer() {
    return usesPerCustomer;
  }

  public void setUsesPerCustomer(int usesPerCustomer) {
    this.usesPerCustomer = usesPerCustomer;
  }

  public int getTimesUsed() {
    return timesUsed;
  }

  public void setTimesUsed(int timesUsed) {
    this.timesUsed = timesUsed;
  }

  public long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(long createdBy) {
    this.createdBy = createdBy;
  }

  public long getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(long modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }

  public Timestamp getModified() {
    return modified;
  }

  public void setModified(Timestamp modified) {
    this.modified = modified;
  }

  public boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getCountryCode() {
    return countryCode;
  }

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

  public BigDecimal getMinimumSubtotal() {
    return minimumSubtotal;
  }

  public void setMinimumSubtotal(BigDecimal minimumSubtotal) {
    this.minimumSubtotal = minimumSubtotal;
  }

  public int getMinimumOrderQuantity() {
    return minimumOrderQuantity;
  }

  public void setMinimumOrderQuantity(int minimumOrderQuantity) {
    this.minimumOrderQuantity = minimumOrderQuantity;
  }

  public int getMaximumOrderQuantity() {
    return maximumOrderQuantity;
  }

  public void setMaximumOrderQuantity(int maximumOrderQuantity) {
    this.maximumOrderQuantity = maximumOrderQuantity;
  }

  public int getItemLimit() {
    return itemLimit;
  }

  public void setItemLimit(int itemLimit) {
    this.itemLimit = itemLimit;
  }

  public String getValidSkus() {
    return validSkus;
  }

  public void setValidSkus(String validSkus) {
    this.validSkus = validSkus;
  }

  public String getInvalidSkus() {
    return invalidSkus;
  }

  public void setInvalidSkus(String invalidSkus) {
    this.invalidSkus = invalidSkus;
  }

  public String getFreeShippingCode() {
    return freeShippingCode;
  }

  public void setFreeShippingCode(String freeShippingCode) {
    this.freeShippingCode = freeShippingCode;
  }

  public int getBuyXItems() {
    return buyXItems;
  }

  public void setBuyXItems(int buyXItems) {
    this.buyXItems = buyXItems;
  }

  public int getGetYItemsFree() {
    return getYItemsFree;
  }

  public void setGetYItemsFree(int getYItemsFree) {
    this.getYItemsFree = getYItemsFree;
  }

  public int getSubtotalPercent() {
    return subtotalPercent;
  }

  public void setSubtotalPercent(int subtotalPercent) {
    this.subtotalPercent = subtotalPercent;
  }

  public BigDecimal getSubtractAmount() {
    return subtractAmount;
  }

  public void setSubtractAmount(BigDecimal subtractAmount) {
    this.subtractAmount = subtractAmount;
  }

  public boolean getFreeShipping() {
    return freeShipping;
  }

  public void setFreeShipping(boolean freeShipping) {
    this.freeShipping = freeShipping;
  }

  public String getFreeProductSku() {
    return freeProductSku;
  }

  public void setFreeProductSku(String freeProductSku) {
    this.freeProductSku = freeProductSku;
  }
}
