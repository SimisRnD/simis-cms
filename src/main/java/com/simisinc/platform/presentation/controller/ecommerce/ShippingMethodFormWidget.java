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
import com.simisinc.platform.application.ecommerce.CartCommand;
import com.simisinc.platform.application.ecommerce.CartValidationCommand;
import com.simisinc.platform.application.ecommerce.SalesTaxCommand;
import com.simisinc.platform.application.ecommerce.ShippingRatesCommand;
import com.simisinc.platform.domain.model.ecommerce.Address;
import com.simisinc.platform.domain.model.ecommerce.Cart;
import com.simisinc.platform.domain.model.ecommerce.Customer;
import com.simisinc.platform.domain.model.ecommerce.ShippingRate;
import com.simisinc.platform.infrastructure.persistence.ecommerce.CustomerRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingRateRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/1/19 8:00 AM
 */
public class ShippingMethodFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String SHIPPING_METHOD_FORM_JSP = "/ecommerce/customer-shipping-method-form.jsp";
  static String SHIPPING_METHOD_UNAVAILABLE_JSP = "/ecommerce/customer-shipping-method-unavailable.jsp";
  static String SHIPPING_METHOD_DESTINATION_UNAVAILABLE_JSP = "/ecommerce/customer-shipping-method-destination-unavailable.jsp";

  /**
   * Using the shipping rate information table, show the available delivery options and price for the country
   *
   * @param context
   * @return
   */
  public WidgetContext execute(WidgetContext context) {

    // Use the cart
    Cart cart = context.getUserSession().getCart();
    context.getRequest().setAttribute("cart", cart);

    try {
      // Check if there is a cart with valid items
      CartValidationCommand.validateHasCartWithItems(cart);
    } catch (DataException de) {
      context.addSharedRequestValue("cartError", de.getMessage());
      context.setRedirect("/cart");
      return context;
    }

    try {
      // Check if there is an address for the rates to use
      Customer customer = CustomerRepository.findById(cart.getCustomerId());
      CartValidationCommand.validateHasShippingAddress(customer);

      // Load the delivery options
      List<ShippingRate> shippingRateList = ShippingRatesCommand.findDeliveryOptions(customer, cart);
      context.getRequest().setAttribute("shippingRateList", shippingRateList);

      // If shipping is not found or restricted, show a message instead
      if (!ShippingRatesCommand.hasAvailableMethod(shippingRateList)) {
        context.setJsp(SHIPPING_METHOD_UNAVAILABLE_JSP);
        return context;
      }

      // Determine if the product is restricted to the destination location
      if (!CartValidationCommand.validateProductDestination(customer, cart)) {
        context.setJsp(SHIPPING_METHOD_DESTINATION_UNAVAILABLE_JSP);
        return context;
      }

    } catch (DataException de) {
      context.addSharedRequestValue("orderError", de.getMessage());
      context.setRedirect("/checkout");
      return context;
    }

    // Show the form
    context.setJsp(SHIPPING_METHOD_FORM_JSP);
    return context;
  }

  /**
   * Validates and stores the customer information, to move to the next step
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

    // Require a customer record
    Customer customer = CustomerRepository.findById(cart.getCustomerId());
    if (customer == null) {
      context.setRedirect("/checkout");
      return context;
    }

    // Require an address record
    Address address = customer.getShippingAddress();
    if (StringUtils.isBlank(address.getCountry()) || StringUtils.isBlank(address.getState()) || StringUtils.isBlank(address.getPostalCode())) {
      context.setRedirect("/checkout");
      return context;
    }

    // Determine the selection
    int shippingRateId = context.getParameterAsInt("shippingRateId", -1);
    ShippingRate shippingRate = ShippingRateRepository.findById(shippingRateId);
    if (shippingRate == null) {
      context.addSharedRequestValue("orderError", "Please choose a shipping rate");
      context.setRedirect("/checkout/delivery-options");
      return context;
    }

    // Validate the delivery method selection for this cart and address
    if (!ShippingRatesCommand.validateShippingRate(customer, cart, shippingRate)) {
      context.addSharedRequestValue("orderError", "Please choose a valid shipping rate");
      context.setRedirect("/checkout/delivery-options");
      return context;
    }

    // Check for Sales tax on the product subtotal, shipping, and handling
    // If the delivery address falls in a different state than those in which your business (or merchant of sale) has nexus, no sales tax need be collected
    BigDecimal taxRate = null;
    BigDecimal taxAmount = null;
    try {
      taxRate = SalesTaxCommand.estimatedTaxRateForAddress(address);
      if (taxRate != null) {
        taxAmount = SalesTaxCommand.estimateTax(cart, address, taxRate, shippingRate);
      }
    } catch (Exception e) {
      LOG.error("Sales tax error", e);
      context.addSharedRequestValue("orderError", "The delivery option could not be processed for this address");
      context.setRedirect("/checkout/delivery-options");
      return context;
    }

    // Save the shipping and tax information
    CartCommand.saveShippingAndTaxes(cart, shippingRate, taxAmount, taxRate);

    // Determine the next step
    if (StringUtils.isNotBlank(cart.getToken()) && cart.getCard() != null) {
      // Skip the payment method, the user was changing the delivery options
      context.setRedirect("/checkout/order-updates");
    } else {
      // Ask for a payment method
      context.setRedirect("/checkout/payment-method");
    }
    return context;
  }
}
