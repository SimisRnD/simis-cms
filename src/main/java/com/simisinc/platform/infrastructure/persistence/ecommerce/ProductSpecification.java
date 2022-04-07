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

import com.simisinc.platform.presentation.controller.DataConstants;

import java.util.ArrayList;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/17/18 4:48 PM
 */
public class ProductSpecification {

  private long id = -1L;
  private String productUniqueId = null;
  private ArrayList<String> withProductUniqueIdList = null;
  private int isForSale = DataConstants.UNDEFINED;

  public ProductSpecification() {
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getProductUniqueId() {
    return productUniqueId;
  }

  public void setProductUniqueId(String productUniqueId) {
    this.productUniqueId = productUniqueId;
  }

  public ArrayList<String> getWithProductUniqueIdList() {
    return withProductUniqueIdList;
  }

  public void setWithProductUniqueIdList(ArrayList<String> withProductUniqueIdList) {
    this.withProductUniqueIdList = withProductUniqueIdList;
  }

  public int getIsForSale() {
    return isForSale;
  }

  public void setIsForSale(int isForSale) {
    this.isForSale = isForSale;
  }

  public void setIsForSale(boolean isForSale) {
    this.isForSale = (isForSale ? DataConstants.TRUE : DataConstants.FALSE);
  }
}
