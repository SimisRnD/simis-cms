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

/**
 * E-commerce credit card summary
 *
 * @author matt rajkowski
 * @created 7/9/19 9:01 PM
 */
public class Card extends Entity {

  private Long id = -1L;

  private String brand = null;
  private String last4 = null;
  private Long expMonth = null;
  private Long expYear = null;
  private String country = null;
  private String fingerprint = null;

  public Card() {
  }

  public Card(com.stripe.model.Card card) {
    brand = card.getBrand();
    last4 = card.getLast4();
    expMonth = card.getExpMonth();
    expYear = card.getExpYear();
    country = card.getCountry();
    fingerprint = card.getFingerprint();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getBrand() {
    return brand;
  }

  public void setBrand(String brand) {
    this.brand = brand;
  }

  public String getLast4() {
    return last4;
  }

  public void setLast4(String last4) {
    this.last4 = last4;
  }

  public Long getExpMonth() {
    return expMonth;
  }

  public void setExpMonth(Long expMonth) {
    this.expMonth = expMonth;
  }

  public Long getExpYear() {
    return expYear;
  }

  public void setExpYear(Long expYear) {
    this.expYear = expYear;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }
}
