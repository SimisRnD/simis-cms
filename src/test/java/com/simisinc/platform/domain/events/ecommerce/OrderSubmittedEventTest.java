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

import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.domain.model.ecommerce.OrderItem;
import com.simisinc.platform.domain.model.ecommerce.ShippingMethod;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderItemRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingMethodRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/11/2022 10:30 PM
 */
class OrderSubmittedEventTest {

  @Test
  void checkEvent() {

    ShippingMethod shippingMethod = new ShippingMethod();
    shippingMethod.setId(3L);

    Order order = new Order();
    order.setId(1L);
    order.setShippingMethodId(shippingMethod.getId());

    OrderItem orderItem = new OrderItem();
    orderItem.setId(2L);

    List<OrderItem> orderItemList = new ArrayList<>();
    orderItemList.add(orderItem);

    try (MockedStatic<OrderItemRepository> orderItemRepositoryMockedStatic = mockStatic(OrderItemRepository.class)) {
      orderItemRepositoryMockedStatic.when(() -> OrderItemRepository.findItemsByOrderId(anyLong())).thenReturn(orderItemList);

      try (MockedStatic<ShippingMethodRepository> shippingMethodRepositoryMockedStatic = mockStatic(ShippingMethodRepository.class)) {
        shippingMethodRepositoryMockedStatic.when(() -> ShippingMethodRepository.findById(anyLong())).thenReturn(shippingMethod);

        OrderSubmittedEvent event = new OrderSubmittedEvent(order);
        Assertions.assertEquals(order.getId(), event.getOrder().getId());
        Assertions.assertEquals(shippingMethod.getId(), event.getShippingMethod().getId());
        Assertions.assertEquals(orderItemList.get(0).getId(), event.getProducts().get(0).getId());
        Assertions.assertTrue(event.getOccurred() <= System.currentTimeMillis());
        Assertions.assertEquals(OrderSubmittedEvent.ID, event.getDomainEventType());
      }
    }
  }
}