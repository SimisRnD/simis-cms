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
import org.apache.commons.beanutils.LazyDynaList;
import org.apache.commons.beanutils.WrapDynaBean;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * E-commerce product details
 *
 * @author matt rajkowski
 * @created 3/17/19 2:56 PM
 */
public class ProductSku extends Entity {

  public static int STATUS_UNDEFINED = -1;
  public static int STATUS_AVAILABLE = 100;
  public static int STATUS_COMING_SOON = 200;
  public static int STATUS_UNAVAILABLE = 300;
  public static int STATUS_MORE_ON_THE_WAY = 400;
  public static int STATUS_SOLD_OUT = 500;

  private Long id = -1L;
  private Long productId = -1L;
  private String sku = null;
  private String currency = null;
  private BigDecimal price = null;
  private BigDecimal strikePrice = null;
  private BigDecimal costOfGood = null;
  private String barcode = null;
  private Timestamp activeDate = null;
  private Timestamp deactivateOnDate = null;
  private Timestamp availableDate = null;
  private Integer inventoryQty = 0;
  private Integer inventoryLow = 0;
  private Integer inventoryIncoming = 0;
  private Integer minimumPurchaseQty = 0;
  private Integer maximumPurchaseQty = 0;
  private boolean allowBackorders = false;
  private BigDecimal packageHeight = null;
  private BigDecimal packageLength = null;
  private BigDecimal packageWidth = null;
  private int packageWeightPounds = 0;
  private int packageWeightOunces = 0;
  private long createdBy = -1;
  private long modifiedBy = -1;
  private Timestamp created = null;
  private Timestamp modified = null;
  private boolean enabled = false;
  private String squareVariationId = null;
  private List<ProductSkuAttribute> attributes = null;

  // Helper
  private int status = STATUS_UNDEFINED;
  private int inventoryQtyState = -1;

  public ProductSku() {
    attributes = (List) new LazyDynaList(ProductSkuAttribute.class);
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getProductId() {
    return productId;
  }

  public void setProductId(Long productId) {
    this.productId = productId;
  }

  public String getSku() {
    return sku;
  }

  public void setSku(String sku) {
    if (sku != null) {
      this.sku = sku.toUpperCase().trim();
    } else {
      this.sku = sku;
    }
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public boolean hasPrice() {
    return price != null && price.doubleValue() > 0;
  }

  public BigDecimal getStrikePrice() {
    return strikePrice;
  }

  public void setStrikePrice(BigDecimal strikePrice) {
    this.strikePrice = strikePrice;
  }

  public BigDecimal getCostOfGood() {
    return costOfGood;
  }

  public void setCostOfGood(BigDecimal costOfGood) {
    this.costOfGood = costOfGood;
  }

  public String getBarcode() {
    return barcode;
  }

  public void setBarcode(String barcode) {
    this.barcode = barcode;
  }

  public Timestamp getActiveDate() {
    return activeDate;
  }

  public void setActiveDate(Timestamp activeDate) {
    this.activeDate = activeDate;
  }

  public Timestamp getDeactivateOnDate() {
    return deactivateOnDate;
  }

  public void setDeactivateOnDate(Timestamp deactivateOnDate) {
    this.deactivateOnDate = deactivateOnDate;
  }

  public Timestamp getAvailableDate() {
    return availableDate;
  }

  public void setAvailableDate(Timestamp availableDate) {
    this.availableDate = availableDate;
  }

  public Integer getInventoryQty() {
    return inventoryQty;
  }

  public void setInventoryQty(Integer inventoryQty) {
    this.inventoryQty = inventoryQty;
  }

  public Integer getInventoryLow() {
    return inventoryLow;
  }

  public void setInventoryLow(Integer inventoryLow) {
    this.inventoryLow = inventoryLow;
  }

  public Integer getInventoryIncoming() {
    return inventoryIncoming;
  }

  public void setInventoryIncoming(Integer inventoryIncoming) {
    this.inventoryIncoming = inventoryIncoming;
  }

  public Integer getMinimumPurchaseQty() {
    return minimumPurchaseQty;
  }

  public void setMinimumPurchaseQty(Integer minimumPurchaseQty) {
    this.minimumPurchaseQty = minimumPurchaseQty;
  }

  public Integer getMaximumPurchaseQty() {
    return maximumPurchaseQty;
  }

  public void setMaximumPurchaseQty(Integer maximumPurchaseQty) {
    this.maximumPurchaseQty = maximumPurchaseQty;
  }

  public boolean getAllowBackorders() {
    return allowBackorders;
  }

  public void setAllowBackorders(boolean allowBackorders) {
    this.allowBackorders = allowBackorders;
  }

  public BigDecimal getPackageHeight() {
    return packageHeight;
  }

  public void setPackageHeight(BigDecimal packageHeight) {
    this.packageHeight = packageHeight;
  }

  public BigDecimal getPackageLength() {
    return packageLength;
  }

  public void setPackageLength(BigDecimal packageLength) {
    this.packageLength = packageLength;
  }

  public BigDecimal getPackageWidth() {
    return packageWidth;
  }

  public void setPackageWidth(BigDecimal packageWidth) {
    this.packageWidth = packageWidth;
  }

  public int getPackageWeightPounds() {
    return packageWeightPounds;
  }

  public void setPackageWeightPounds(int packageWeightPounds) {
    this.packageWeightPounds = packageWeightPounds;
  }

  public int getPackageWeightOunces() {
    return packageWeightOunces;
  }

  public void setPackageWeightOunces(int packageWeightOunces) {
    this.packageWeightOunces = packageWeightOunces;
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

  public String getSquareVariationId() {
    return squareVariationId;
  }

  public void setSquareVariationId(String squareVariationId) {
    this.squareVariationId = squareVariationId;
  }

  public List<ProductSkuAttribute> getAttributes() {
    return attributes;
  }

  public void setAttributes(List<ProductSkuAttribute> attributes) {
    this.attributes = attributes;
  }

  public List<ProductSkuAttribute> getNativeAttributes() {
    if (attributes == null) {
      return null;
    }
    if (LazyDynaList.class.isInstance(attributes)) {
      LazyDynaList attributeList = LazyDynaList.class.cast(attributes);
      List<ProductSkuAttribute> newAttributeList = new ArrayList<>();
      for (Object dynaBean : attributeList) {
        newAttributeList.add((ProductSkuAttribute) ((WrapDynaBean) dynaBean).getInstance());
      }
      return newAttributeList;
    }
    return attributes;
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public int getInventoryQtyState() {
    return inventoryQtyState;
  }

  public void setInventoryQtyState(int inventoryQtyState) {
    this.inventoryQtyState = inventoryQtyState;
  }
}
