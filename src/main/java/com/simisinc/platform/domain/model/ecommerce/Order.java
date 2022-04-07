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
 * E-commerce order
 *
 * @author matt rajkowski
 * @created 5/8/19 8:06 AM
 */
public class Order extends Entity {

  private Long id = -1L;
  private String uniqueId = null;
  private long cartId = -1L;
  private long customerId = -1L;
  private String squareOrderId = null;

  private String email = null;
  private String firstName = null;
  private String lastName = null;
  private String barcode = null;
  private String remoteOrderId = null;
  private String customerNote = null;
  private String trackingNumbers = null;

  private Address billingAddress = null;
  private Address shippingAddress = null;

  private int shippingMethodId = -1;
  private long shippingRateId = -1;
  private String taxId = null;
  private String promoCode = null;
  private long pricingRuleId = -1;

  private int totalItems = 0;
  private String currency = null;
  private BigDecimal subtotalAmount = new BigDecimal(0);
  private BigDecimal discountAmount = new BigDecimal(0);
  private BigDecimal shippingFee = new BigDecimal(0);
  private BigDecimal shippingTaxAmount = new BigDecimal(0);
  private BigDecimal handlingFee = new BigDecimal(0);
  private BigDecimal handlingFeeTaxAmount = new BigDecimal(0);
  private BigDecimal taxRate = new BigDecimal(0);
  private BigDecimal taxAmount = new BigDecimal(0);
  private BigDecimal totalAmount = new BigDecimal(0);
  private BigDecimal totalPaid = new BigDecimal(0);
  private BigDecimal totalPending = new BigDecimal(0);
  private BigDecimal totalRefunded = new BigDecimal(0);

  private String paymentProcessor = null;
  private String paymentToken = null;
  private String paymentType = null;
  private String paymentBrand = null;
  private String paymentLast4 = null;
  private String paymentFingerprint = null;
  private String paymentCountry = null;

  private String chargeToken = null;

  private int statusId = -1;
  private boolean hasPreOrder = false;
  private boolean hasBackOrder = false;
  private boolean live = false;
  private boolean paid = false;
  private boolean processed = false;
  private boolean shipped = false;
  private boolean canceled = false;
  private boolean refunded = false;
  private Timestamp paymentDate = null;
  private Timestamp processingDate = null;
  private Timestamp fulfillmentDate = null;
  private Timestamp shippedDate = null;
  private Timestamp canceledDate = null;
  private Timestamp refundedDate = null;

  private Timestamp created = null;
  private Timestamp modified = null;
  private long createdBy = -1L;
  private long modifiedBy = -1L;

  private String ipAddress = null;
  private String sessionId = null;
  private String countryIso = null;
  private String country = null;
  private String city = null;
  private String stateIso = null;
  private String state = null;
  private double latitude = 0;
  private double longitude = 0;

  public Order() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public void setUniqueId(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  public long getCustomerId() {
    return customerId;
  }

  public void setCustomerId(long customerId) {
    this.customerId = customerId;
  }

  public String getSquareOrderId() {
    return squareOrderId;
  }

  public void setSquareOrderId(String squareOrderId) {
    this.squareOrderId = squareOrderId;
  }

  public long getCartId() {
    return cartId;
  }

  public void setCartId(long cartId) {
    this.cartId = cartId;
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

  public String getBarcode() {
    return barcode;
  }

  public void setBarcode(String barcode) {
    this.barcode = barcode;
  }

  public String getCustomerNote() {
    return customerNote;
  }

  public void setCustomerNote(String customerNote) {
    this.customerNote = customerNote;
  }

  public String getTrackingNumbers() {
    return trackingNumbers;
  }

  public void setTrackingNumbers(String trackingNumbers) {
    this.trackingNumbers = trackingNumbers;
  }

  public Address getBillingAddress() {
    return billingAddress;
  }

  public void setBillingAddress(Address billingAddress) {
    this.billingAddress = billingAddress;
  }

  public Address getShippingAddress() {
    return shippingAddress;
  }

  public void setShippingAddress(Address shippingAddress) {
    this.shippingAddress = shippingAddress;
  }

  public int getShippingMethodId() {
    return shippingMethodId;
  }

  public void setShippingMethodId(int shippingMethodId) {
    this.shippingMethodId = shippingMethodId;
  }

  public long getShippingRateId() {
    return shippingRateId;
  }

  public void setShippingRateId(long shippingRateId) {
    this.shippingRateId = shippingRateId;
  }

  public String getTaxId() {
    return taxId;
  }

  public void setTaxId(String taxId) {
    this.taxId = taxId;
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

  public String getRemoteOrderId() {
    return remoteOrderId;
  }

  public void setRemoteOrderId(String remoteOrderId) {
    this.remoteOrderId = remoteOrderId;
  }

  public int getTotalItems() {
    return totalItems;
  }

  public void setTotalItems(int totalItems) {
    this.totalItems = totalItems;
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

  public BigDecimal getDiscountAmount() {
    return discountAmount;
  }

  public void setDiscountAmount(BigDecimal discountAmount) {
    this.discountAmount = discountAmount;
  }

  public BigDecimal getHandlingFee() {
    return handlingFee;
  }

  public void setHandlingFee(BigDecimal handlingFee) {
    this.handlingFee = handlingFee;
  }

  public BigDecimal getTaxRate() {
    return taxRate;
  }

  public void setTaxRate(BigDecimal taxPercentage) {
    this.taxRate = taxPercentage;
  }

  public BigDecimal getHandlingFeeTaxAmount() {
    return handlingFeeTaxAmount;
  }

  public void setHandlingFeeTaxAmount(BigDecimal handlingFeeTaxAmount) {
    this.handlingFeeTaxAmount = handlingFeeTaxAmount;
  }

  public BigDecimal getShippingFee() {
    return shippingFee;
  }

  public void setShippingFee(BigDecimal shippingFee) {
    this.shippingFee = shippingFee;
  }

  public BigDecimal getShippingTaxAmount() {
    return shippingTaxAmount;
  }

  public void setShippingTaxAmount(BigDecimal shippingTaxAmount) {
    this.shippingTaxAmount = shippingTaxAmount;
  }

  public BigDecimal getTaxAmount() {
    return taxAmount;
  }

  public void setTaxAmount(BigDecimal taxAmount) {
    this.taxAmount = taxAmount;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public void setTotalAmount(BigDecimal totalAmount) {
    this.totalAmount = totalAmount;
  }

  public BigDecimal getTotalPaid() {
    return totalPaid;
  }

  public void setTotalPaid(BigDecimal totalPaid) {
    this.totalPaid = totalPaid;
  }

  public BigDecimal getTotalPending() {
    return totalPending;
  }

  public void setTotalPending(BigDecimal totalPending) {
    this.totalPending = totalPending;
  }

  public BigDecimal getTotalRefunded() {
    return totalRefunded;
  }

  public void setTotalRefunded(BigDecimal totalRefunded) {
    this.totalRefunded = totalRefunded;
  }

  public String getPaymentProcessor() {
    return paymentProcessor;
  }

  public void setPaymentProcessor(String paymentProcessor) {
    this.paymentProcessor = paymentProcessor;
  }

  public String getPaymentToken() {
    return paymentToken;
  }

  public void setPaymentToken(String paymentToken) {
    this.paymentToken = paymentToken;
  }

  public String getPaymentType() {
    return paymentType;
  }

  public void setPaymentType(String paymentType) {
    this.paymentType = paymentType;
  }

  public String getPaymentBrand() {
    return paymentBrand;
  }

  public void setPaymentBrand(String paymentBrand) {
    this.paymentBrand = paymentBrand;
  }

  public String getPaymentLast4() {
    return paymentLast4;
  }

  public void setPaymentLast4(String paymentLast4) {
    this.paymentLast4 = paymentLast4;
  }

  public String getPaymentFingerprint() {
    return paymentFingerprint;
  }

  public void setPaymentFingerprint(String paymentFingerprint) {
    this.paymentFingerprint = paymentFingerprint;
  }

  public String getPaymentCountry() {
    return paymentCountry;
  }

  public void setPaymentCountry(String paymentCountry) {
    this.paymentCountry = paymentCountry;
  }

  public String getChargeToken() {
    return chargeToken;
  }

  public void setChargeToken(String chargeToken) {
    this.chargeToken = chargeToken;
  }

  public int getStatusId() {
    return statusId;
  }

  public void setStatusId(int statusId) {
    this.statusId = statusId;
  }

  public boolean getHasPreOrder() {
    return hasPreOrder;
  }

  public void setHasPreOrder(boolean hasPreOrder) {
    this.hasPreOrder = hasPreOrder;
  }

  public boolean getLive() {
    return live;
  }

  public void setLive(boolean live) {
    this.live = live;
  }

  public boolean getHasBackOrder() {
    return hasBackOrder;
  }

  public void setHasBackOrder(boolean hasBackOrder) {
    this.hasBackOrder = hasBackOrder;
  }

  public boolean getPaid() {
    return paid;
  }

  public void setPaid(boolean paid) {
    this.paid = paid;
  }

  public boolean getProcessed() {
    return processed;
  }

  public void setProcessed(boolean processed) {
    this.processed = processed;
  }

  public boolean getShipped() {
    return shipped;
  }

  public void setShipped(boolean shipped) {
    this.shipped = shipped;
  }

  public boolean getCanceled() {
    return canceled;
  }

  public void setCanceled(boolean canceled) {
    this.canceled = canceled;
  }

  public boolean getRefunded() {
    return refunded;
  }

  public void setRefunded(boolean refunded) {
    this.refunded = refunded;
  }

  public Timestamp getPaymentDate() {
    return paymentDate;
  }

  public void setPaymentDate(Timestamp paymentDate) {
    this.paymentDate = paymentDate;
  }

  public Timestamp getProcessingDate() {
    return processingDate;
  }

  public void setProcessingDate(Timestamp processingDate) {
    this.processingDate = processingDate;
  }

  public Timestamp getFulfillmentDate() {
    return fulfillmentDate;
  }

  public void setFulfillmentDate(Timestamp fulfillmentDate) {
    this.fulfillmentDate = fulfillmentDate;
  }

  public Timestamp getShippedDate() {
    return shippedDate;
  }

  public void setShippedDate(Timestamp shippedDate) {
    this.shippedDate = shippedDate;
  }

  public Timestamp getCanceledDate() {
    return canceledDate;
  }

  public void setCanceledDate(Timestamp canceledDate) {
    this.canceledDate = canceledDate;
  }

  public Timestamp getRefundedDate() {
    return refundedDate;
  }

  public void setRefundedDate(Timestamp refundedDate) {
    this.refundedDate = refundedDate;
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

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getCountryIso() {
    return countryIso;
  }

  public void setCountryIso(String countryIso) {
    this.countryIso = countryIso;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getStateIso() {
    return stateIso;
  }

  public void setStateIso(String stateIso) {
    this.stateIso = stateIso;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public double getLatitude() {
    return latitude;
  }

  public void setLatitude(double latitude) {
    this.latitude = latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public void setLongitude(double longitude) {
    this.longitude = longitude;
  }
}
