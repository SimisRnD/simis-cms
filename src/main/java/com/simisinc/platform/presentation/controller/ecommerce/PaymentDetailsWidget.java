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
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.lang3.StringUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/5/19 5:04 PM
 */
public class PaymentDetailsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String PAYMENT_DETAILS_JSP = "/ecommerce/customer-payment-details.jsp";

  /**
   * Show that payment info is set
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

    if (StringUtils.isBlank(cart.getToken())) {
      LOG.warn("A payment processor token is required for payment");
      context.setRedirect("/checkout");
      return context;
    }

    // Display the card summary
    if (cart.getCard() != null) {
      context.getRequest().setAttribute("card", cart.getCard());
    }

    // Show the form
    context.setJsp(PAYMENT_DETAILS_JSP);
    return context;
  }
}
