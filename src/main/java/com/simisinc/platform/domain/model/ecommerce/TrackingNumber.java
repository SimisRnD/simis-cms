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

import java.sql.Timestamp;

/**
 * E-commerce package tracking number
 *
 * @author matt rajkowski
 * @created 4/22/20 7:56 PM
 */
public class TrackingNumber extends Entity {

  private Long id = -1L;
  private long orderId = -1;
  private String trackingNumber = null;
  private long shippingCarrierId = -1;
  private Timestamp shipDate = null;
  private Timestamp deliveryDate = null;
  private Timestamp created = null;
  private long createdBy = -1;

  String cartItemIdList = null;
  String orderItemIdList = null;

  // Helper
  private String link = null;

  public TrackingNumber() {
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

  public String getTrackingNumber() {
    return trackingNumber;
  }

  public void setTrackingNumber(String trackingNumber) {
    this.trackingNumber = trackingNumber;
  }

  public long getShippingCarrierId() {
    return shippingCarrierId;
  }

  public void setShippingCarrierId(long shippingCarrierId) {
    this.shippingCarrierId = shippingCarrierId;
  }

  public Timestamp getShipDate() {
    return shipDate;
  }

  public void setShipDate(Timestamp shipDate) {
    this.shipDate = shipDate;
  }

  public Timestamp getDeliveryDate() {
    return deliveryDate;
  }

  public void setDeliveryDate(Timestamp deliveryDate) {
    this.deliveryDate = deliveryDate;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }

  public long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(long createdBy) {
    this.createdBy = createdBy;
  }

  public String getCartItemIdList() {
    return cartItemIdList;
  }

  public void setCartItemIdList(String cartItemIdList) {
    this.cartItemIdList = cartItemIdList;
  }

  public String getOrderItemIdList() {
    return orderItemIdList;
  }

  public void setOrderItemIdList(String orderItemIdList) {
    this.orderItemIdList = orderItemIdList;
  }

  public String getLink() {
    return link;
  }

  public void setLink(String link) {
    this.link = link;
  }
}
