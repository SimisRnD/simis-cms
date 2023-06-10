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

import java.lang.reflect.InvocationTargetException;

import org.apache.commons.lang3.StringUtils;

import com.sanctionco.jmail.JMail;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.ecommerce.CartCommand;
import com.simisinc.platform.domain.model.ecommerce.Cart;
import com.simisinc.platform.domain.model.ecommerce.Customer;
import com.simisinc.platform.infrastructure.persistence.ecommerce.CustomerRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/9/19 11:37 PM
 */
public class OrderUpdatesFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String ORDER_UPDATES_FORM_JSP = "/ecommerce/order-updates-form.jsp";

  /**
   * Presents the order updates form to the user
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

    // Check if there is a customer associated with the cart
    if (cart.getCustomerId() <= 0L) {
      LOG.debug("A customer id is required for order updates");
      context.setRedirect("/checkout");
      return context;
    }
    Customer customer = CustomerRepository.findById(cart.getCustomerId());
    context.getRequest().setAttribute("customer", customer);

    // Enable the account creation button for non-users
    if (!context.getUserSession().isLoggedIn()) {
      context.getRequest().setAttribute("allowRegistrations", LoadSitePropertyCommand.loadByName("site.registrations"));
    }

    // Try to populate the first/last name
    if (StringUtils.isNotBlank(cart.getFirstName())) {
      // Populate the form with the name tried here
      context.getRequest().setAttribute("userFirstName", cart.getFirstName());
      if (StringUtils.isNotBlank(cart.getLastName())) {
        context.getRequest().setAttribute("userLastName", cart.getLastName());
      }
    } else if (StringUtils.isNotBlank(customer.getFirstName())) {
      context.getRequest().setAttribute("userFirstName", customer.getFirstName());
      if (StringUtils.isNotBlank(customer.getLastName())) {
        context.getRequest().setAttribute("userLastName", customer.getLastName());
      }
    } else if (context.getUserSession().isLoggedIn()) {
      // Populate the form with the user's name
      context.getRequest().setAttribute("userFirstName", context.getUserSession().getUser().getFirstName());
      context.getRequest().setAttribute("userLastName", context.getUserSession().getUser().getLastName());
    } else if (customer.getShippingAddress() != null && StringUtils.isNotBlank(customer.getShippingAddress().getFirstName())) {
      context.getRequest().setAttribute("userFirstName", customer.getShippingAddress().getFirstName());
      if (StringUtils.isNotBlank(customer.getShippingAddress().getLastName())) {
        context.getRequest().setAttribute("userLastName", customer.getShippingAddress().getLastName());
      }
    }

    // Try to populate the email form
    if (StringUtils.isNotBlank(cart.getEmail())) {
      // Populate the form with the last email tried here
      context.getRequest().setAttribute("userEmail", cart.getEmail());
    } else if (StringUtils.isNotBlank(customer.getEmail())) {
      context.getRequest().setAttribute("userEmail", customer.getEmail());
    } else if (context.getUserSession().isLoggedIn()) {
      // Populate the form with the user's email address
      context.getRequest().setAttribute("userEmail", context.getUserSession().getUser().getEmail());
    }

    context.getRequest().setAttribute("subscribeToNewsletter", cart.getSubscribeToNewsletter() ? "true" : "false");

    // Show the shipping address form
    context.setJsp(ORDER_UPDATES_FORM_JSP);
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

    StringBuilder errorMessages = new StringBuilder();

    // Validate the name fields
    String firstName = context.getParameter("firstName");
    if (StringUtils.isBlank(firstName)) {
      appendMessage(errorMessages, "First name is required");
    } else {
      cart.setFirstName(firstName);
    }

    String lastName = context.getParameter("lastName");
    if (StringUtils.isBlank(lastName)) {
      appendMessage(errorMessages, "Last name is required");
    } else {
      cart.setLastName(lastName);
    }

    // Validate the email field
    String email = context.getParameter("email");
    if (!JMail.isValid(email)) {
      appendMessage(errorMessages, "Check the email address");
    } else {
      cart.setEmail(email);
    }

    if (errorMessages.length() > 0) {
      context.setErrorMessage("Please try again: " + errorMessages.toString());
      return context;
    }

    // Determine the post action
    String submitButton = context.getParameter("button");

    // Determine if an account needs to be created
    if ("createAccount".equals(submitButton)) {
      String password = context.getParameter("password");
      if (StringUtils.isBlank(password) || password.trim().length() < 6) {
        context.setErrorMessage("Passwords must be at least 6 characters");
        context.setRedirect("/checkout/order-updates");
        return context;
      }
      // Hang on to this until the order is done
      cart.setPassword(password);
    }

    boolean subscribeToNewsletter = context.getParameterAsBoolean("newsletter");
    cart.setSubscribeToNewsletter(subscribeToNewsletter);

    // Update the customer record
    try {
      CartCommand.saveCustomerContactInfo(cart, firstName, lastName, email);
      context.setRedirect("/checkout/order-review");
      return context;
    } catch (DataException de) {
      context.addSharedRequestValue("orderError", de.getMessage());
      return context;
    }

  }

  private static void appendMessage(StringBuilder errorMessages, String message) {
    if (errorMessages.length() > 0) {
      errorMessages.append("; ");
    }
    errorMessages.append(message);
  }
}
