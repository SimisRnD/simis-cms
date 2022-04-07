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
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * E-commerce products for display
 *
 * @author matt rajkowski
 * @created 3/17/19 2:39 PM
 */
public class Product extends Entity {

  private Long id = -1L;

  private int order = 100;
  private String uniqueId = null;
  private String name = null;
  private String description = null;
  private String caption = null;
  private boolean isGood = false;
  private boolean isService = false;
  private boolean isVirtual = false;
  private boolean isDownload = false;
  private int fulfillmentId = -1;
//  private int secondaryFulfillmentId = -1;
  private boolean taxable = false;
  private String taxCode = null;
  private Timestamp activeDate = null;
  private Timestamp deactivateOnDate = null;
  private Timestamp availableDate = null;
  private boolean shippable = false;
  private BigDecimal packageHeight = null;
  private BigDecimal packageLength = null;
  private BigDecimal packageWidth = null;
  private int packageWeightPounds = 0;
  private int packageWeightOunces = 0;
  private String imageUrl = null;
  private String productUrl = null;
  private long createdBy = -1;
  private long modifiedBy = -1;
  private Timestamp created = null;
  private Timestamp modified = null;
  private boolean enabled = false;
  private String squareCatalogId = null;
  private String excludeUsStates = null;

  private List<ProductSkuAttribute> attributes = null;
  private long orderCount = 0;

  private BigDecimal price = null;
  private BigDecimal startingFromPrice = null;
  int skuCount = 0;

  // Bean Helper
  private List<ProductSku> products = null;

  public Product() {
    products = (List) new LazyDynaList(ProductSku.class);
    attributes = (List) new LazyDynaList(ProductSkuAttribute.class);
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public int getOrder() {
    return order;
  }

  public void setOrder(int order) {
    this.order = order;
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public void setUniqueId(String uniqueId) {
    this.uniqueId = uniqueId;
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

  public String getCaption() {
    return caption;
  }

  public void setCaption(String caption) {
    this.caption = caption;
  }

  public String getNameWithCaption() {
    if (StringUtils.isNotBlank(caption)) {
      return name + " " + caption;
    }
    return name;
  }

  public boolean getIsGood() {
    return isGood;
  }

  public void setIsGood(boolean good) {
    isGood = good;
  }

  public boolean getIsService() {
    return isService;
  }

  public void setIsService(boolean service) {
    isService = service;
  }

  public boolean getIsVirtual() {
    return isVirtual;
  }

  public void setIsVirtual(boolean virtual) {
    isVirtual = virtual;
  }

  public boolean getIsDownload() {
    return isDownload;
  }

  public void setIsDownload(boolean download) {
    isDownload = download;
  }

  public int getFulfillmentId() {
    return fulfillmentId;
  }

  public void setFulfillmentId(int fulfillmentId) {
    this.fulfillmentId = fulfillmentId;
  }

  public boolean getTaxable() {
    return taxable;
  }

  public void setTaxable(boolean taxable) {
    this.taxable = taxable;
  }

  public String getTaxCode() {
    return taxCode;
  }

  public void setTaxCode(String taxCode) {
    this.taxCode = taxCode;
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

  public boolean getShippable() {
    return shippable;
  }

  public void setShippable(boolean shippable) {
    this.shippable = shippable;
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

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public String getProductUrl() {
    return productUrl;
  }

  public void setProductUrl(String productUrl) {
    this.productUrl = productUrl;
  }

  public void setType(String type) {
    isGood = false;
    isService = false;
    isVirtual = false;
    isDownload = false;
    if ("good".equals(type)) {
      isGood = true;
    } else if ("service".equals(type)) {
      isService = true;
    } else if ("virtual".equals(type)) {
      isVirtual = true;
    } else if ("download".equals(type)) {
      isDownload = true;
    }
  }

  public boolean getHasType() {
    return (isGood || isService || isVirtual || isDownload);
  }

  public String getSquareCatalogId() {
    return squareCatalogId;
  }

  public void setSquareCatalogId(String squareCatalogId) {
    this.squareCatalogId = squareCatalogId;
  }

  public String getExcludeUsStates() {
    return excludeUsStates;
  }

  public void setExcludeUsStates(String excludeUsStates) {
    this.excludeUsStates = excludeUsStates;
  }

  public List<ProductSkuAttribute> getAttributes() {
    return attributes;
  }

  public void setAttributes(List<ProductSkuAttribute> attributes) {
    this.attributes = attributes;
  }

  public long getOrderCount() {
    return orderCount;
  }

  public void setOrderCount(long orderCount) {
    this.orderCount = orderCount;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public BigDecimal getStartingFromPrice() {
    return startingFromPrice;
  }

  public void setStartingFromPrice(BigDecimal startingFromPrice) {
    this.startingFromPrice = startingFromPrice;
  }

  public int getSkuCount() {
    return skuCount;
  }

  public void setSkuCount(int skuCount) {
    this.skuCount = skuCount;
  }

  public List<ProductSku> getProducts() {
    return products;
  }

  public void setProducts(List<ProductSku> products) {
    this.products = products;
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

  public List<ProductSku> getNativeProductSKUs() {
    if (products == null) {
      return null;
    }
    if (LazyDynaList.class.isInstance(products)) {
      LazyDynaList productList = LazyDynaList.class.cast(products);
      List<ProductSku> newProductList = new ArrayList<>();
      for (Object dynaBean : productList) {
        ProductSku thisSku = (ProductSku) ((WrapDynaBean) dynaBean).getInstance();
        thisSku.setAttributes(thisSku.getNativeAttributes());
        newProductList.add(thisSku);
      }
      return newProductList;
    }
    return products;
  }

  public ProductSku getProductSKU(String sku) {
    if (products == null || sku == null) {
      return null;
    }
    for (ProductSku thisProductSku : getNativeProductSKUs()) {
      if (sku.equalsIgnoreCase(thisProductSku.getSku())) {
        return thisProductSku;
      }
    }
    return null;
  }
}
