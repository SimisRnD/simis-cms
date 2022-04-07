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

package com.simisinc.platform.presentation.controller.ecommerce;

import com.simisinc.platform.domain.model.ecommerce.Cart;
import com.simisinc.platform.domain.model.ecommerce.ShippingRate;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingRateRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/28/19 8:00 AM
 */
public class ShippingMethodDetailsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String SHIPPING_METHOD_DETAILS_JSP = "/ecommerce/customer-shipping-method-details.jsp";

  /**
   * Show the selected delivery option
   *
   * @param context
   * @return
   */
  public WidgetContext execute(WidgetContext context) {

    // Check if there is a cart with valid items
    Cart cart = context.getUserSession().getCart();
    if (cart == null || cart.getTotalItems() <= 0) {
      context.setRedirect("/cart");
      return context;
    }

    // Check if there is a customer associated with the cart
    if (cart.getCustomerId() <= 0L) {
      LOG.debug("A customer id is required for payment");
      context.setRedirect("/checkout");
      return context;
    }

    // Retrieve the shipping information
    long shippingRateId = cart.getShippingRateId();
    ShippingRate shippingRate = ShippingRateRepository.findById(shippingRateId);
    context.getRequest().setAttribute("shippingRate", shippingRate);

    // Show the form
    context.setJsp(SHIPPING_METHOD_DETAILS_JSP);
    return context;
  }
}
