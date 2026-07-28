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

package com.simisinc.platform.presentation.widgets.ecommerce;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.ecommerce.CartCommand;
import com.simisinc.platform.application.ecommerce.LoadCartItemCommand;
import com.simisinc.platform.domain.model.ecommerce.Cart;
import com.simisinc.platform.domain.model.ecommerce.CartItem;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

/**
 * removeItemViaPostCallsRepository guards a real regression: the cart's remove link submits via a real HTTP
 * POST (issue #358 moved state-changing actions off GET query strings), so WebContainerContext routes the
 * request to post(), not action() -- action()'s "removeItem" dispatch was correct but unreachable, and post()
 * never checked the action parameter, so it fell through to the unconditional quantity-update logic instead.
 * That logic reads an "item-{id}-quantity" parameter that a removeItem request never sends, so it defaulted
 * to -1, tripped the "quantity cannot be 0 or less" guard, and replaced the click with a generic
 * "An error occurred" message -- the item was never removed. This test calls post() directly, the same method
 * a real request now reaches, so it fails if that dispatch gap reopens.
 */
class CartWidgetTest extends WidgetBase {

  @Test
  void removeItemViaPostCallsRepository() {
    Cart cart = new Cart();
    cart.setId(10L);
    cart.setTotalItems(1);
    widgetContext.getUserSession().setCart(cart);

    CartItem cartItem = new CartItem();
    cartItem.setId(42L);
    cartItem.setCartId(10L);

    addQueryParameter(widgetContext, "action", "removeItem");
    addQueryParameter(widgetContext, "itemId", "42");

    try (MockedStatic<LoadCartItemCommand> loadCartItem = mockStatic(LoadCartItemCommand.class);
        MockedStatic<CartCommand> cartCommand = mockStatic(CartCommand.class)) {
      loadCartItem.when(() -> LoadCartItemCommand.loadCartItemById(anyLong())).thenReturn(cartItem);
      cartCommand.when(() -> CartCommand.removeProductFromCart(cart, cartItem)).thenReturn(true);

      WidgetContext result = new CartWidget().post(widgetContext);

      cartCommand.verify(() -> CartCommand.removeProductFromCart(cart, cartItem), times(1));
      Assertions.assertEquals("Item was removed", result.getSuccessMessage());
    }
  }
}
