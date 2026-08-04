/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.ecommerce.CartCommand;
import com.simisinc.platform.application.ecommerce.CartValidationCommand;
import com.simisinc.platform.application.ecommerce.SaveCustomerCommand;
import com.simisinc.platform.application.ecommerce.ValidateCustomerCommand;
import com.simisinc.platform.domain.model.ecommerce.Cart;
import com.simisinc.platform.domain.model.ecommerce.Customer;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Issue #914 removed AddressCommand (it called USPS's legacy Web Tools API, which USPS shut down
 * in January 2026, and had no reachable admin toggle regardless). This test locks in the
 * simplified post() flow: once the required fields validate, checkout must go straight to saving
 * the customer record and redirecting to the next step, with no address-verification detour.
 */
class ShippingAddressFormWidgetTest extends WidgetBase {

  @Test
  void postSavesTheCustomerAndRedirectsWithoutAnyAddressVerificationDetour() throws Exception {
    Cart cart = new Cart();
    cart.setId(10L);
    cart.setTotalItems(1);
    widgetContext.getUserSession().setCart(cart);

    addQueryParameter(widgetContext, "firstName", "Jane");
    addQueryParameter(widgetContext, "lastName", "Doe");
    addQueryParameter(widgetContext, "street", "123 Main St");
    addQueryParameter(widgetContext, "city", "Anytown");
    addQueryParameter(widgetContext, "state", "CA");
    addQueryParameter(widgetContext, "postalCode", "90210");
    addQueryParameter(widgetContext, "country", "United States");
    addQueryParameter(widgetContext, "button", "save");

    Customer savedCustomer = new Customer();
    savedCustomer.setId(42L);

    try (MockedStatic<CartValidationCommand> cartValidation = mockStatic(CartValidationCommand.class);
        MockedStatic<ValidateCustomerCommand> validateCustomer = mockStatic(ValidateCustomerCommand.class);
        MockedStatic<SaveCustomerCommand> saveCustomer = mockStatic(SaveCustomerCommand.class);
        MockedStatic<CartCommand> cartCommand = mockStatic(CartCommand.class)) {
      cartValidation.when(() -> CartValidationCommand.validateHasCartWithItems(cart)).thenAnswer(invocation -> null);
      validateCustomer.when(() -> ValidateCustomerCommand.validateCustomerShippingAddress(any(), any())).thenReturn(true);
      saveCustomer.when(() -> SaveCustomerCommand.saveCustomerShippingAddress(any())).thenReturn(savedCustomer);

      WidgetContext result = new ShippingAddressFormWidget().post(widgetContext);

      saveCustomer.verify(() -> SaveCustomerCommand.saveCustomerShippingAddress(any()), times(1));
      cartCommand.verify(() -> CartCommand.resetShippingAndTaxes(cart), times(1));
      cartCommand.verify(() -> CartCommand.updateCustomer(cart, savedCustomer), times(1));
      assertEquals("/checkout/delivery-options", result.getRedirect());
    }
  }

  @Test
  void postDoesNotSaveWhenRequiredFieldsFailValidation() throws Exception {
    Cart cart = new Cart();
    cart.setId(10L);
    cart.setTotalItems(1);
    widgetContext.getUserSession().setCart(cart);

    addQueryParameter(widgetContext, "country", "United States");
    addQueryParameter(widgetContext, "button", "save");

    try (MockedStatic<CartValidationCommand> cartValidation = mockStatic(CartValidationCommand.class);
        MockedStatic<ValidateCustomerCommand> validateCustomer = mockStatic(ValidateCustomerCommand.class);
        MockedStatic<SaveCustomerCommand> saveCustomer = mockStatic(SaveCustomerCommand.class)) {
      cartValidation.when(() -> CartValidationCommand.validateHasCartWithItems(cart)).thenAnswer(invocation -> null);
      validateCustomer.when(() -> ValidateCustomerCommand.validateCustomerShippingAddress(any(), any()))
          .thenAnswer(invocation -> {
            StringBuilder errorMessages = invocation.getArgument(1);
            errorMessages.append("Street is required.\n");
            return false;
          });

      new ShippingAddressFormWidget().post(widgetContext);

      saveCustomer.verifyNoInteractions();
    }
  }
}
