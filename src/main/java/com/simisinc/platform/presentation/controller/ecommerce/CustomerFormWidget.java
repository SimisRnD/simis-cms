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
import com.simisinc.platform.domain.model.ecommerce.Address;
import com.simisinc.platform.domain.model.ecommerce.Cart;
import com.simisinc.platform.domain.model.ecommerce.Customer;
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
public class CustomerFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String CONTACT_FORM_JSP = "/ecommerce/customer-contact-form.jsp";

  /**
   * Presents the customer form to the user
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
    if (customer == null) {
      customer = new Customer();
      Address shippingAddress = new Address();
      Address billingAddress = new Address();
      if (context.getUserSession().getGeoIP() != null && context.getUserSession().getGeoIP().getStateISOCode() != null) {
        shippingAddress.setState(context.getUserSession().getGeoIP().getStateISOCode());
        billingAddress.setState(context.getUserSession().getGeoIP().getStateISOCode());
      }
      if (context.getUserSession().getGeoIP() != null && context.getUserSession().getGeoIP().getCountry() != null) {
        shippingAddress.setCountry(context.getUserSession().getGeoIP().getCountry());
        billingAddress.setCountry(context.getUserSession().getGeoIP().getCountry());
      }
      customer.setShippingAddress(shippingAddress);
      customer.setBillingAddress(billingAddress);
    }
    customer.setCartId(cart.getId());
    context.getRequest().setAttribute("customer", customer);

    // Show the contact form
    context.setJsp(CONTACT_FORM_JSP);
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

    // Retrieve the existing customer information
    Customer customerBean;
    if (cart.getCustomerId() > -1L) {
      customerBean = CustomerRepository.findById(cart.getCustomerId());
    } else {
      customerBean = new Customer();
    }

    // Populate the fields
    BeanUtils.populate(customerBean, context.getParameterMap());

    // Validate the data
    if (cart.getCustomerId() > -1L && cart.getCustomerId() != customerBean.getId()) {
      LOG.warn("CustomerId did not match: " + cart.getCustomerId() + " for form value: " + customerBean.getId());
      return context;
    }

    customerBean.setCreatedBy(context.getUserId());
    customerBean.setModifiedBy(context.getUserId());

    // Save the record
    Customer customer = null;
    try {
      // @todo make changes based on form/page
//      customer = SaveCustomerCommand.saveCustomer(customerBean);
      if (customer == null || customer.getId() == -1) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      context.addSharedRequestValue("orderError", e.getMessage());
      context.setRequestObject(customerBean);
      return context;
    }
    context.getUserSession().getCart().setCustomerId(customer.getId());

    // Move to the next step
    // If more than 1 delivery option is available then:
    // /checkout/delivery-options
    // /checkout/payment


    return context;
  }
}
