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
 * E-commerce cart item
 *
 * @author matt rajkowski
 * @created 4/15/19 9:51 PM
 */
public class CartItem extends Entity {

  private Long id = -1L;

  private long cartId = -1L;
  private long productId = -1L;
  private long skuId = -1L;

  private String currency = null;
  private BigDecimal quantity = null;
  private BigDecimal quantityFree = null;
  private BigDecimal eachAmount = null;
  private BigDecimal totalAmount = null;

  private String productName = null;
  private String productType = null;
  private String productSku = null;
  private String productBarcode = null;

  private boolean preorder = false;
  private boolean backordered = false;
  private boolean removed = false;

  private Timestamp created = null;
  private Timestamp modified = null;
  private long createdBy = -1L;
  private long modifiedBy = -1L;

  public CartItem() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getCartId() {
    return cartId;
  }

  public void setCartId(long cartId) {
    this.cartId = cartId;
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

  public BigDecimal getQuantityFree() {
    return quantityFree;
  }

  public void setQuantityFree(BigDecimal quantityFree) {
    this.quantityFree = quantityFree;
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

  public boolean getRemoved() {
    return removed;
  }

  public void setRemoved(boolean removed) {
    this.removed = removed;
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
