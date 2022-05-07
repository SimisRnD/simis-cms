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
import com.simisinc.platform.application.admin.EcommerceCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.ecommerce.*;
import com.simisinc.platform.application.mailinglists.SaveEmailCommand;
import com.simisinc.platform.application.maps.GeoIPCommand;
import com.simisinc.platform.application.register.RegisterUserCommand;
import com.simisinc.platform.domain.events.cms.UserSignedUpEvent;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.ecommerce.Cart;
import com.simisinc.platform.domain.model.ecommerce.Customer;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.maps.GeoIP;
import com.simisinc.platform.infrastructure.persistence.ecommerce.CustomerRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;

import javax.security.auth.login.AccountException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/10/19 7:29 AM
 */
public class PlaceOrderWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String ORDER_REVIEW_DETAILS_JSP = "/ecommerce/place-order-button.jsp";

  /**
   * Show the place order button and process the order
   *
   * @param context
   * @return
   */
  public WidgetContext execute(WidgetContext context) {
    try {
      // Use the cart and customer information
      Cart cart = context.getUserSession().getCart();
      Customer customer = CustomerRepository.findById(cart.getCustomerId());
      // Check if there is a cart with valid items
      CartValidationCommand.validateHasCartWithItems(cart);
      CartValidationCommand.validateHasShippingAddress(customer);
      EmailValidator emailValidator = EmailValidator.getInstance(false);
      if (!emailValidator.isValid(cart.getEmail())) {
        throw new DataException("A valid email address is required");
      }
      // Determine if the product is restricted to the destination location
      if (!CartValidationCommand.validateProductDestination(customer, cart)) {
        throw new DataException("At least one of the items in your cart cannot be shipped to the address you entered");
      }
    } catch (DataException de) {
      context.setErrorMessage(de.getMessage());
      context.setRedirect("/checkout");
      return context;
    }

    context.setJsp(ORDER_REVIEW_DETAILS_JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Determine the processor
    String service = LoadSitePropertyCommand.loadByName("ecommerce.paymentProcessor");
    if (StringUtils.isBlank(service) || "None".equalsIgnoreCase(service)) {
      LOG.error("A payment processor needs to be configured");
      context.setErrorMessage("The order was not processed. A payment service needs to be enabled on this site.");
      context.setRedirect("/checkout/payment-method");
      return context;
    }

    try {
      // Convert the cart to an order
      Cart cart = context.getUserSession().getCart();
      if (context.getUserSession().isLoggedIn()) {
        cart.setCreatedBy(context.getUserId());
        cart.setModifiedBy(context.getUserId());
      }
      Order order = OrderCommand.generateOrder(cart);
      if (order == null) {
        throw new DataException("An error prevented the order from processing");
      }

      // Create a user bean so an account can be created if requested
      User userBean = null;
      if (StringUtils.isNotBlank(cart.getEmail()) && StringUtils.isNotBlank(cart.getPassword())) {
        userBean = new User();
        userBean.setFirstName(cart.getFirstName());
        userBean.setLastName(cart.getLastName());
        userBean.setEmail(cart.getEmail());
        userBean.setPassword(cart.getPassword());
      }

      // Update the user's IP and Session information
      order.setSessionId(context.getUserSession().getSessionId());
      order.setIpAddress(context.getRequest().getRemoteAddr());
      GeoIP geoIP = GeoIPCommand.getLocation(order.getIpAddress());
      if (geoIP != null) {
        order.setCountryIso(geoIP.getCountryISOCode());
        order.setCountry(geoIP.getCountry());
        order.setCity(geoIP.getCity());
        order.setStateIso(geoIP.getStateISOCode());
        order.setState(geoIP.getState());
        order.setLatitude(geoIP.getLatitude());
        order.setLongitude(geoIP.getLongitude());
      }

      // Determine if the card should be charged
      if (order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
        LOG.debug("Processing a $0 order");
        // Complete the order without payment
        if (OrderProcessingCommand.processOrder(order)) {
          // Track the last order id for the confirmation page
          context.getUserSession().setLastOrderId(order.getId());
          // Reset the cart
          context.getUserSession().setCart(null);
          context.setRedirect("/order-confirmation");
        }
      } else if ("Stripe".equalsIgnoreCase(service)) {
        // Charge the card
        if (StripePaymentCommand.chargeOrder(order)) {
          // Track the last order id for the confirmation page
          context.getUserSession().setLastOrderId(order.getId());
          // Reset the cart
          context.getUserSession().setCart(null);
          context.setRedirect("/order-confirmation");
        }
      } else if ("Square".equalsIgnoreCase(service)) {
        // Check for a Square locationId which is required for using the orders API
        String locationId = null;
        boolean productionEnabled = EcommerceCommand.isProductionEnabled();
        if (productionEnabled) {
          locationId = LoadSitePropertyCommand.loadByName("ecommerce.square.production.location");
        } else {
          locationId = LoadSitePropertyCommand.loadByName("ecommerce.square.test.location");
        }
        if (StringUtils.isNotBlank(locationId)) {
          // Use Square Order + Payment APIs
          if (SquareOrderCommand.chargeOrder(order)) {
            // Track the last order id for the confirmation page
            context.getUserSession().setLastOrderId(order.getId());
            // Reset the cart
            context.getUserSession().setCart(null);
            context.setRedirect("/order-confirmation");
          }
        } else {
          // Use Square Payment API
          LOG.warn(
              "Square Order API requires a valid locationId to be configured, skipping order creation for payment");
          if (SquarePaymentCommand.takePayment(order)) {
            // Track the last order id for the confirmation page
            context.getUserSession().setLastOrderId(order.getId());
            // Reset the cart
            context.getUserSession().setCart(null);
            context.setRedirect("/order-confirmation");
          }
        }
      } else if ("Test".equals(service)) {
        // Update the order and related cart
        order.setPaid(true);
        order.setPaymentDate(new Timestamp(System.currentTimeMillis()));
        OrderRepository.save(order);
        // Trigger confirmation email
        OrderEmailCommand.sendConfirmationEmail(order);
        // Track the last order id for the confirmation page
        context.getUserSession().setLastOrderId(order.getId());
        // Reset the cart
        context.getUserSession().setCart(null);
        context.setRedirect("/order-confirmation");
      }

      // Create a user
      if (userBean != null) {
        User user = RegisterUserCommand.registerUser(userBean);
        // Trigger events
        WorkflowManager.triggerWorkflowForEvent(new UserSignedUpEvent(user));
      }

      // Subscribe to newsletter
      if (cart.getSubscribeToNewsletter()) {

        // Populate the fields
        Email emailBean = new Email();
        emailBean.setEmail(cart.getEmail());
        emailBean.setSource("Place Order Process");
        emailBean.setSubscribed(new Timestamp(System.currentTimeMillis()));

        // Populate all the http and session info
        emailBean.setIpAddress(context.getUserSession().getIpAddress());
        emailBean.setSessionId(context.getUserSession().getSessionId());
        emailBean.setReferer(context.getUserSession().getReferer());
        emailBean.setUserAgent(context.getUserSession().getUserAgent());
        if (context.getUserSession().isLoggedIn()) {
          emailBean.setCreatedBy(context.getUserId());
          emailBean.setModifiedBy(context.getUserId());
        }
        SaveEmailCommand.saveEmail(emailBean);
      }

    } catch (AccountException ae) {
      LOG.warn("User account already exists: " + ae.getMessage());
    } catch (DataException de) {
      LOG.error(de);
      context.addSharedRequestValue("orderError", de.getMessage());
      context.setRedirect("/checkout/payment-method");
    }
    return context;
  }
}
