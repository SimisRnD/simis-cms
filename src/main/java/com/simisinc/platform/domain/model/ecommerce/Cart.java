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
 * E-commerce cart
 *
 * @author matt rajkowski
 * @created 4/9/19 9:46 PM
 */
public class Cart extends Entity {

  private Long id = -1L;

  private String token = null;
  private long visitorId = -1L;
  private String sessionId = null;
  private long userId = -1L;

  // Customer Information
  private long customerId = -1L;
  private String email = null;
  private String firstName = null;
  private String lastName = null;

  // Discount
  private BigDecimal discount = null;
  private String promoCode = null;
  private long pricingRuleId = -1;

  // Shipping
  private long shippingRateId = -1;
  private BigDecimal shippingFee = null;
  private BigDecimal handlingFee = null;

  // Sales Tax
  private BigDecimal shippingTax = null;
  private BigDecimal handlingTax = null;
  private BigDecimal taxAmount = null;
  private BigDecimal taxRate = new BigDecimal(0);

  // Payment
  private String paymentToken = null;
  private Card card = null;

  // Order
  private long orderId = -1L;
  private Timestamp orderDate = null;

  // Cart record
  private boolean enabled = true;
  private Timestamp created = null;
  private Timestamp modified = null;
  private long createdBy = -1L;
  private long modifiedBy = -1L;
  private Timestamp expires = null;
  private int totalItems = 0;
  private BigDecimal totalQty = new BigDecimal(0);
  private String currency = null;
  private BigDecimal subtotalAmount = new BigDecimal(0);

  // Helper
  private boolean subscribeToNewsletter = true;
  private String password = null;

  public Cart() {
  }

  public Cart(String token, long visitorId, String sessionId, long userId) {
    this.token = token;
    this.visitorId = visitorId;
    this.sessionId = sessionId;
    if (userId > 0) {
      this.userId = userId;
    }
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public long getVisitorId() {
    return visitorId;
  }

  public void setVisitorId(long visitorId) {
    this.visitorId = visitorId;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
  }

  public long getCustomerId() {
    return customerId;
  }

  public void setCustomerId(long customerId) {
    this.customerId = customerId;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }
  
  public long getShippingRateId() {
    return shippingRateId;
  }

  public void setShippingRateId(long shippingRateId) {
    this.shippingRateId = shippingRateId;
  }

  public BigDecimal getShippingAndHandlingFee() {
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

  public BigDecimal getShippingTax() {
    return shippingTax;
  }

  public void setShippingTax(BigDecimal shippingTax) {
    this.shippingTax = shippingTax;
  }

  public BigDecimal getHandlingTax() {
    return handlingTax;
  }

  public void setHandlingTax(BigDecimal handlingTax) {
    this.handlingTax = handlingTax;
  }

  public BigDecimal getTaxAmount() {
    return taxAmount;
  }

  public void setTaxAmount(BigDecimal taxAmount) {
    this.taxAmount = taxAmount;
  }

  public BigDecimal getTaxRate() {
    return taxRate;
  }

  public void setTaxRate(BigDecimal taxPercentage) {
    this.taxRate = taxPercentage;
  }

  public String getPaymentToken() {
    return paymentToken;
  }

  public void setPaymentToken(String paymentToken) {
    this.paymentToken = paymentToken;
  }

  public Card getCard() {
    return card;
  }

  public void setCard(Card card) {
    this.card = card;
  }

  public long getOrderId() {
    return orderId;
  }

  public void setOrderId(long orderId) {
    this.orderId = orderId;
  }

  public Timestamp getOrderDate() {
    return orderDate;
  }

  public void setOrderDate(Timestamp orderDate) {
    this.orderDate = orderDate;
  }

  public boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
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

  public Timestamp getExpires() {
    return expires;
  }

  public void setExpires(Timestamp expires) {
    this.expires = expires;
  }

  public int getTotalItems() {
    return totalItems;
  }

  public void setTotalItems(int totalItems) {
    this.totalItems = totalItems;
  }

  public BigDecimal getTotalQty() {
    return totalQty;
  }

  public void setTotalQty(BigDecimal totalQty) {
    this.totalQty = totalQty;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public BigDecimal getSubtotalAmount() {
    return subtotalAmount;
  }

  public void setSubtotalAmount(BigDecimal subtotalAmount) {
    this.subtotalAmount = subtotalAmount;
  }

  public BigDecimal getDiscount() {
    return discount;
  }

  public void setDiscount(BigDecimal discount) {
    this.discount = discount;
  }

  public String getPromoCode() {
    return promoCode;
  }

  public void setPromoCode(String promoCode) {
    this.promoCode = promoCode;
  }

  public long getPricingRuleId() {
    return pricingRuleId;
  }

  public void setPricingRuleId(long pricingRuleId) {
    this.pricingRuleId = pricingRuleId;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public BigDecimal getGrandTotal() {
    BigDecimal grandTotal = subtotalAmount;
    if (this.getDiscount() != null && this.getDiscount().compareTo(BigDecimal.ZERO) > 0) {
      grandTotal = grandTotal.subtract(this.getDiscount());
    }
    if (this.getShippingAndHandlingFee() != null) {
      grandTotal = grandTotal.add(this.getShippingAndHandlingFee());
    }
    if (this.getTaxAmount() != null) {
      grandTotal = grandTotal.add(this.getTaxAmount());
    }
    return grandTotal;
  }

  public boolean getSubscribeToNewsletter() {
    return subscribeToNewsletter;
  }

  public void setSubscribeToNewsletter(boolean subscribeToNewsletter) {
    this.subscribeToNewsletter = subscribeToNewsletter;
  }
}
