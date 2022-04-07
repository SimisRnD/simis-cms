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
 * E-commerce order item
 *
 * @author matt rajkowski
 * @created 4/23/20 2:12 PM
 */
public class OrderItem extends Entity {

  private Long id = -1L;

  private long orderId = -1L;
  private long customerId = -1L;
  private long productId = -1L;
  private long skuId = -1L;

  private String currency = null;
  private BigDecimal quantity = null;
  private BigDecimal eachAmount = null;
  private BigDecimal totalAmount = null;

  private String productName = null;
  private String productType = null;
  private String productSku = null;
  private String productBarcode = null;

  private int statusId = -1;
  private boolean preorder = false;
  private boolean backordered = false;
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

  public OrderItem() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getOrderId() {
    return orderId;
  }

  public void setOrderId(long orderId) {
    this.orderId = orderId;
  }

  public long getCustomerId() {
    return customerId;
  }

  public void setCustomerId(long customerId) {
    this.customerId = customerId;
  }

  public long getProductId() {
    return productId;
  }

  public void setProductId(long productId) {
    this.productId = productId;
  }

  public long getSkuId() {
    return skuId;
  }

  public void setSkuId(long skuId) {
    this.skuId = skuId;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public void setQuantity(BigDecimal quantity) {
    this.quantity = quantity;
  }

  public BigDecimal getEachAmount() {
    return eachAmount;
  }

  public void setEachAmount(BigDecimal eachAmount) {
    this.eachAmount = eachAmount;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public void setTotalAmount(BigDecimal totalAmount) {
    this.totalAmount = totalAmount;
  }

  public String getProductName() {
    return productName;
  }

  public void setProductName(String productName) {
    this.productName = productName;
  }

  public String getProductType() {
    return productType;
  }

  public void setProductType(String productType) {
    this.productType = productType;
  }

  public String getProductSku() {
    return productSku;
  }

  public void setProductSku(String productSku) {
    this.productSku = productSku;
  }

  public String getProductBarcode() {
    return productBarcode;
  }

  public void setProductBarcode(String productBarcode) {
    this.productBarcode = productBarcode;
  }

  public int getStatusId() {
    return statusId;
  }

  public void setStatusId(int statusId) {
    this.statusId = statusId;
  }

  public boolean getPreorder() {
    return preorder;
  }

  public void setPreorder(boolean preorder) {
    this.preorder = preorder;
  }

  public boolean getBackordered() {
    return backordered;
  }

  public void setBackordered(boolean backordered) {
    this.backordered = backordered;
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
}
