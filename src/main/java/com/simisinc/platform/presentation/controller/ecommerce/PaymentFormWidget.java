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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.EcommerceCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.ecommerce.ProcessPaymentCommand;
import com.simisinc.platform.application.ecommerce.SquarePaymentCommand;
import com.simisinc.platform.application.ecommerce.StripePaymentCommand;
import com.simisinc.platform.domain.model.ecommerce.Cart;
import com.simisinc.platform.domain.model.ecommerce.Customer;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.domain.model.ecommerce.Payment;
import com.simisinc.platform.infrastructure.persistence.ecommerce.CustomerRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;

import java.lang.reflect.InvocationTargetException;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/18/19 11:20 PM
 */
public class PaymentFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String PAYMENT_FORM_JSP = "/ecommerce/customer-payment-form.jsp";
  static String PAYMENT_FORM_STRIPE_JSP = "/ecommerce/customer-payment-form-stripe.jsp";
  static String PAYMENT_FORM_SQUARE_JSP = "/ecommerce/customer-payment-form-square.jsp";

  /**
   * Presents the payment form to the user
   *
   * @param context
   * @return
   */
  public WidgetContext execute(WidgetContext context) {

    // Check if there is a cart with valid items
    Cart cart = context.getUserSession().getCart();
    if (cart == null || cart.getTotalItems() <= 0) {
      LOG.debug("A cart is required for payment");
      context.setRedirect("/cart");
      return context;
    }
    context.getRequest().setAttribute("cart", cart);

    // Check if there is a customer associated with the cart
    if (cart.getCustomerId() <= 0L) {
      LOG.debug("A customer id is required for payment");
      context.setRedirect("/checkout");
      return context;
    }
    Customer customer = CustomerRepository.findById(cart.getCustomerId());
    context.getRequest().setAttribute("customer", customer);

    // @todo Check if the product requires shipping
    if (cart.getShippingRateId() <= 0L) {
      LOG.debug("A shipping method is required for payment");
      context.setRedirect("/checkout/delivery-options");
      return context;
    }

    // Form bean
    Payment payment = null;
    if (context.getRequestObject() != null) {
      payment = (Payment) context.getRequestObject();
    } else {
      payment = new Payment();
    }
    payment.setCartId(cart.getId());
    context.getRequest().setAttribute("payment", payment);

    // Determine the payment form to use
    String service = LoadSitePropertyCommand.loadByName("ecommerce.paymentProcessor");
    if ("Stripe".equalsIgnoreCase(service)) {
      // Determine the publishable key to use in the client
      String stripeKey = null;
      if (EcommerceCommand.isProductionEnabled()) {
        stripeKey = LoadSitePropertyCommand.loadByName("ecommerce.stripe.production.key");
      } else {
        stripeKey = LoadSitePropertyCommand.loadByName("ecommerce.stripe.test.key");
        context.getRequest().setAttribute("testMode", "true");
      }
      context.getRequest().setAttribute("stripeKey", stripeKey);
      context.setJsp(PAYMENT_FORM_STRIPE_JSP);

    } else if ("Square".equalsIgnoreCase(service)) {
      // Determine the publishable key to use in the client
      String squareAppId = null;
      if (EcommerceCommand.isProductionEnabled()) {
        squareAppId = LoadSitePropertyCommand.loadByName("ecommerce.square.production.key");
      } else {
        squareAppId = LoadSitePropertyCommand.loadByName("ecommerce.square.test.key");
        context.getRequest().setAttribute("testMode", "true");
      }
      context.getRequest().setAttribute("squareAppId", squareAppId);
      context.setJsp(PAYMENT_FORM_SQUARE_JSP);

    } else {
      context.setJsp(PAYMENT_FORM_JSP);
    }
    return context;
  }

  /**
   * Validates and processes the payment information
   *
   * @param context
   * @return
   * @throws InvocationTargetException
   * @throws IllegalAccessException
   */
  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Check if there is a cart with valid items
    Cart cart = context.getUserSession().getCart();
    if (cart == null || cart.getTotalItems() <= 0) {
      context.setRedirect("/cart");
      return context;
    }

    // Check if there is a customer record
    if (cart.getCustomerId() <= 0) {
      context.setRedirect("/checkout");
      return context;
    }

    // Determine the payment form to use
    String service = LoadSitePropertyCommand.loadByName("ecommerce.paymentProcessor");
    if ("Stripe".equalsIgnoreCase(service)) {

      // Check the Stripe client.js value
      String stripeToken = context.getParameter("stripeToken");
      try {
        StripePaymentCommand.validateToken(cart, stripeToken);
      } catch (DataException e) {
        context.setErrorMessage(e.getMessage());
        return context;
      }

    } else if ("Square".equalsIgnoreCase(service)) {

      // Check the Square client.js value
      String squareNonce = context.getParameter("squareNonce");
      try {
        SquarePaymentCommand.validateNonce(cart, squareNonce);
      } catch (DataException e) {
        context.setErrorMessage(e.getMessage());
        return context;
      }

    } else {

      // No payment processor is defined... so just store the order
      // Populate the fields
      Payment paymentBean = new Payment();
      BeanUtils.populate(paymentBean, context.getParameterMap());
      paymentBean.setCreatedBy(context.getUserId());
      paymentBean.setModifiedBy(context.getUserId());
      Order order = null;
      try {
        // Validate the data
        ProcessPaymentCommand.validatePayment(paymentBean);
        // Verify the payment details with the processor
//        order = ProcessPaymentCommand.startPayment(paymentBean);
//        if (order == null) {
//        throw new DataException("Payment could not be processed");
//        }
      } catch (DataException de) {
        context.addSharedRequestValue("orderError", de.getMessage());
        context.setRequestObject(paymentBean);
        return context;
      }
    }

    // Move to the next step
    context.setRedirect("/checkout/order-updates");

    return context;
  }
}
