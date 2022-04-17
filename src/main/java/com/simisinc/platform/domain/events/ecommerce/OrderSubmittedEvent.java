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

package com.simisinc.platform.domain.events.ecommerce;

import com.simisinc.platform.application.maps.GeoIPCommand;
import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.domain.model.ecommerce.OrderItem;
import com.simisinc.platform.domain.model.ecommerce.ShippingMethod;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderItemRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingMethodRepository;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Event details for when an order is submitted
 *
 * @author matt rajkowski
 * @created 5/2/21 11:40 AM
 */
@NoArgsConstructor
public class OrderSubmittedEvent extends Event {

  public static final String ID = "order-submitted";

  private Order order = null;
  private List<OrderItem> products = null;
  private ShippingMethod shippingMethod = null;
  private String location = null;

  public OrderSubmittedEvent(Order order) {
    this.order = order;
    this.products = OrderItemRepository.findItemsByOrderId(order.getId());
    this.shippingMethod = ShippingMethodRepository.findById(order.getShippingMethodId());
    this.location = GeoIPCommand.getCityStateCountryLocation(order.getIpAddress());
  }

  @Override
  public String getDomainEventType() {
    return ID;
  }

  public void setOrder(Order order) {
    this.order = order;
  }

  public Order getOrder() {
    return order;
  }

  public void setProducts(List<OrderItem> products) {
    this.products = products;
  }

  public List<OrderItem> getProducts() {
    return products;
  }

  public void setShippingMethod(ShippingMethod shippingMethod) {
    this.shippingMethod = shippingMethod;
  }

  public ShippingMethod getShippingMethod() {
    return shippingMethod;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getLocation() {
    return location;
  }
}
