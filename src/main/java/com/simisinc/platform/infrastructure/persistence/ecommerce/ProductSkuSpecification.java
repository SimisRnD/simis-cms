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

import com.simisinc.platform.domain.model.ecommerce.ProductSkuAttribute;
import com.simisinc.platform.presentation.controller.DataConstants;

import java.util.ArrayList;

/**
 * Properties for querying objects from the product sku repository
 *
 * @author matt rajkowski
 * @created 3/17/18 5:06 PM
 */
public class ProductSkuSpecification {

  private long id = -1L;
  private long isNotId = -1L;
  private String sku = null;
  private long productId = -1L;
  private String productUniqueId = null;
  private int showOnline = DataConstants.UNDEFINED;
  private ArrayList<ProductSkuAttribute> withProductSkuAttributeList = null;

  public ProductSkuSpecification() {
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getIsNotId() {
    return isNotId;
  }

  public void setIsNotId(long isNotId) {
    this.isNotId = isNotId;
  }

  public String getSku() {
    return sku;
  }

  public void setSku(String sku) {
    this.sku = sku;
  }

  public long getProductId() {
    return productId;
  }

  public void setProductId(long productId) {
    this.productId = productId;
  }

  public String getProductUniqueId() {
    return productUniqueId;
  }

  public void setProductUniqueId(String productUniqueId) {
    this.productUniqueId = productUniqueId;
  }

  public int getShowOnline() {
    return showOnline;
  }

  public void setShowOnline(int showOnline) {
    this.showOnline = showOnline;
  }

  public void setShowOnline(boolean showOnline) {
    this.showOnline = (showOnline ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public ArrayList<ProductSkuAttribute> getWithProductSkuAttributeList() {
    return withProductSkuAttributeList;
  }

  public void setWithProductSkuAttributeList(ArrayList<ProductSkuAttribute> withProductSkuAttributeList) {
    this.withProductSkuAttributeList = withProductSkuAttributeList;
  }
}
