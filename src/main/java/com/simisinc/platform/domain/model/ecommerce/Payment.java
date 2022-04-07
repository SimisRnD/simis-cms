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
 * E-commerce payment
 *
 * @author matt rajkowski
 * @created 4/30/19 8:05 AM
 */
public class Payment extends Entity {

  private Long id = -1L;

  private long customerId = -1L;
  private long orderId = -1L;
  private int paymentTypeId = -1;
  private String processor = null;
  private String reference = null;
  private String receiptNumber = null;
  private String receiptUrl = null;
  private BigDecimal amount = null;
  private BigDecimal amountRefunded = null;

  private long createdBy = -1;
  private long modifiedBy = -1;
  private Timestamp created = null;
  private Timestamp modified = null;

  // Helper
  private long cartId = -1L;
  private String creditCardNumber = null;
  private String creditCardMMYY = null;
  private String creditCardCVC = null;
  private String creditCardZip = null;

  public Payment() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getCustomerId() {
    return customerId;
  }

  public void setCustomerId(long customerId) {
    this.customerId = customerId;
  }

  public long getOrderId() {
    return orderId;
  }

  public void setOrderId(long orderId) {
    this.orderId = orderId;
  }

  public int getPaymentTypeId() {
    return paymentTypeId;
  }

  public void setPaymentTypeId(int paymentTypeId) {
    this.paymentTypeId = paymentTypeId;
  }

  public String getProcessor() {
    return processor;
  }

  public void setProcessor(String processor) {
    this.processor = processor;
  }

  public String getReference() {
    return reference;
  }

  public void setReference(String reference) {
    this.reference = reference;
  }

  public String getReceiptNumber() {
    return receiptNumber;
  }

  public void setReceiptNumber(String receiptNumber) {
    this.receiptNumber = receiptNumber;
  }

  public String getReceiptUrl() {
    return receiptUrl;
  }

  public void setReceiptUrl(String receiptUrl) {
    this.receiptUrl = receiptUrl;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public BigDecimal getAmountRefunded() {
    return amountRefunded;
  }

  public void setAmountRefunded(BigDecimal amountRefunded) {
    this.amountRefunded = amountRefunded;
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

  public long getCartId() {
    return cartId;
  }

  public void setCartId(long cartId) {
    this.cartId = cartId;
  }

  public String getCreditCardNumber() {
    return creditCardNumber;
  }

  public void setCreditCardNumber(String creditCardNumber) {
    this.creditCardNumber = creditCardNumber;
  }

  public String getCreditCardMMYY() {
    return creditCardMMYY;
  }

  public void setCreditCardMMYY(String creditCardMMYY) {
    this.creditCardMMYY = creditCardMMYY;
  }

  public String getCreditCardCVC() {
    return creditCardCVC;
  }

  public void setCreditCardCVC(String creditCardCVC) {
    this.creditCardCVC = creditCardCVC;
  }

  public String getCreditCardZip() {
    return creditCardZip;
  }

  public void setCreditCardZip(String creditCardZip) {
    this.creditCardZip = creditCardZip;
  }
}
