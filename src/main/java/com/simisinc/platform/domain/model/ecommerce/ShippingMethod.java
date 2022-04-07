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

/**
 * E-commerce shipping method
 *
 * @author matt rajkowski
 * @created 5/21/19 8:47 PM
 */
public class ShippingMethod extends Entity {

  private Long id = -1L;

  private int level = -1;
  private String code = null;
  private String title = null;
  private boolean enabled = false;
  private String boxzookaCode = null;

  // Helpers
  private BigDecimal shippingFee = null;
  private BigDecimal handlingFee = null;

  public ShippingMethod() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public int getLevel() {
    return level;
  }

  public void setLevel(int level) {
    this.level = level;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getBoxzookaCode() {
    return boxzookaCode;
  }

  public void setBoxzookaCode(String boxzookaCode) {
    this.boxzookaCode = boxzookaCode;
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

  public BigDecimal getShippingAndHandlingFee() {
    if (shippingFee != null && handlingFee != null) {
      return this.shippingFee.add(this.handlingFee);
    } else if (shippingFee != null) {
      return shippingFee;
    } else if (handlingFee != null) {
      return handlingFee;
    } else {
      return null;
    }
  }
}
