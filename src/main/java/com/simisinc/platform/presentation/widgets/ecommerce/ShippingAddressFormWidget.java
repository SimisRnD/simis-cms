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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.ecommerce.*;
import com.simisinc.platform.domain.model.ecommerce.Address;
import com.simisinc.platform.domain.model.ecommerce.Cart;
import com.simisinc.platform.domain.model.ecommerce.Customer;
import com.simisinc.platform.domain.model.ecommerce.ShippingCountry;
import com.simisinc.platform.infrastructure.persistence.ecommerce.CustomerRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingCountryRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/21/19 7:05 PM
 */
public class ShippingAddressFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String SHIPPING_ADDRESS_FORM_JSP = "/ecommerce/shipping-address-form.jsp";

  /**
   * Presents the shipping address form to the user
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

    // Form bean
    Customer customer = null;
    if (context.getRequestObject() != null) {
      customer = (Customer) context.getRequestObject();
    } else if (cart.getCustomerId() > 0) {
      customer = CustomerRepository.findById(cart.getCustomerId());
    }

    // If the user hasn't visited this page, set some defaults
    if (customer == null) {
      customer = new Customer();
      Address address = new Address();
      if (context.getUserSession().getGeoIP() != null && context.getUserSession().getGeoIP().getStateISOCode() != null) {
        address.setState(context.getUserSession().getGeoIP().getStateISOCode());
      }
      if (context.getUserSession().getGeoIP() != null && context.getUserSession().getGeoIP().getCountry() != null) {
        address.setCountry(context.getUserSession().getGeoIP().getCountry());
      }
      customer.setShippingAddress(address);
    }
    customer.setCartId(cart.getId());
    context.getRequest().setAttribute("address", customer.getShippingAddress());

    // Display the available shipping countries
    List<ShippingCountry> shippingCountryList = ShippingCountryRepository.findAll();
    context.getRequest().setAttribute("shippingCountryList", shippingCountryList);

    // Show the shipping address form
    context.setJsp(SHIPPING_ADDRESS_FORM_JSP);
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

    // Use the cart
    Cart cart = context.getUserSession().getCart();

    try {
      // Check if there is a cart with valid items
      CartValidationCommand.validateHasCartWithItems(cart);
    } catch (DataException de) {
      context.addSharedRequestValue("cartError", de.getMessage());
      context.setRedirect("/cart");
      return context;
    }

    // Retrieve the existing customer information
    Customer customerBean = null;
    if (cart.getCustomerId() > -1L) {
      customerBean = CustomerRepository.findById(cart.getCustomerId());
    }
    if (customerBean == null) {
      LOG.debug("Instantiating a new customer");
      customerBean = new Customer();
    }

    // Populate the fields
    Address addressBean = customerBean.getShippingAddress();
    if (addressBean == null) {
      addressBean = new Address();
    }
    BeanUtils.populate(addressBean, context.getParameterMap());
    if (!addressBean.getCountry().equalsIgnoreCase("United States")) {
      // Use the text field instead of the state drop-down
      addressBean.setState(context.getParameter("province"));
    }
    customerBean.setShippingAddress(addressBean);

    // Validate the data
    if (cart.getCustomerId() > -1L && cart.getCustomerId() != customerBean.getId()) {
      LOG.warn("CustomerId did not match: " + cart.getCustomerId() + " for form value: " + customerBean.getId());
      return context;
    }

    // Apply properties
    customerBean.setCartId(cart.getId());
    if (context.getUserSession().isLoggedIn()) {
      customerBean.setCreatedBy(context.getUserId());
      customerBean.setModifiedBy(context.getUserId());
    }

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    ValidateCustomerCommand.validateCustomerShippingAddress(addressBean, errorMessages);
    if (errorMessages.length() > 0) {
      LOG.debug("Shipping address validation error");
      context.addSharedRequestValue("orderError", "Please check the form and try again:\n" + errorMessages.toString());
      context.setRequestObject(customerBean);
      return context;
    }

    // Save the record
    Customer customer = null;
    try {
      LOG.debug("Saving the customer record...");
      customer = SaveCustomerCommand.saveCustomerShippingAddress(customerBean);
      if (customer == null || customer.getId() == -1) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException de) {
      context.addSharedRequestValue("orderError", de.getMessage());
      context.setRequestObject(customerBean);
      return context;
    }
    // The shipping address changed, so reset the shipping method and taxes
    CartCommand.resetShippingAndTaxes(cart);

    // The customer id changed so save the reference
    CartCommand.updateCustomer(cart, customer);

    context.setRedirect("/checkout/delivery-options");
    return context;
  }
}
