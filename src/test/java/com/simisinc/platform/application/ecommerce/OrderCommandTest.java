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

package com.simisinc.platform.application.ecommerce;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.ecommerce.Address;
import com.simisinc.platform.domain.model.ecommerce.Cart;
import com.simisinc.platform.domain.model.ecommerce.Customer;
import com.simisinc.platform.domain.model.ecommerce.ShippingRate;

/**
 * Verifies the checkout-time sales-tax re-derivation guard in OrderCommand: the grand
 * total must charge tax re-derived from the Tax service, never a stale amount stored
 * on the cart, and a mismatch must bounce the customer back to review the cart.
 *
 * @author Liz Houser
 * @created 7/23/2026
 */
class OrderCommandTest {

  private static Customer customerShippingTo(String country, String state, String postalCode) {
    Address address = new Address();
    address.setCountry(country);
    address.setState(state);
    address.setPostalCode(postalCode);
    Customer customer = new Customer();
    customer.setShippingAddress(address);
    return customer;
  }

  private static Cart cartWithReviewedTax(BigDecimal taxAmount) {
    Cart cart = new Cart();
    cart.setTaxAmount(taxAmount);
    return cart;
  }

  @Test
  void taxUnchangedChargesTheReviewedAmount() throws DataException {
    Cart cart = cartWithReviewedTax(new BigDecimal("8.25"));
    Customer customer = customerShippingTo("UNITED STATES", "VA", "22182");
    ShippingRate shippingRate = new ShippingRate();

    try (MockedStatic<SalesTaxCommand> tax = mockStatic(SalesTaxCommand.class)) {
      tax.when(() -> SalesTaxCommand.estimatedTaxRateForAddress(any())).thenReturn(new BigDecimal("0.06"));
      tax.when(() -> SalesTaxCommand.estimateTax(any(), any(), any(), any())).thenReturn(new BigDecimal("8.25"));

      BigDecimal charged = OrderCommand.determineTaxToCharge(cart, customer, shippingRate);

      Assertions.assertEquals(0, charged.compareTo(new BigDecimal("8.25")));
    }
  }

  @Test
  void taxIncreasedSinceReviewBouncesToReview() {
    Cart cart = cartWithReviewedTax(new BigDecimal("8.25"));
    Customer customer = customerShippingTo("UNITED STATES", "VA", "22182");
    ShippingRate shippingRate = new ShippingRate();

    try (MockedStatic<SalesTaxCommand> tax = mockStatic(SalesTaxCommand.class)) {
      tax.when(() -> SalesTaxCommand.estimatedTaxRateForAddress(any())).thenReturn(new BigDecimal("0.06"));
      // The tax-rate table was updated after the customer chose a shipping method
      tax.when(() -> SalesTaxCommand.estimateTax(any(), any(), any(), any())).thenReturn(new BigDecimal("9.00"));

      DataException ex = Assertions.assertThrows(DataException.class,
          () -> OrderCommand.determineTaxToCharge(cart, customer, shippingRate));
      Assertions.assertTrue(ex.getMessage().toLowerCase().contains("review the cart"));
    }
  }

  @Test
  void nexusRemovedButCartStillHasTaxBouncesToReview() {
    // The reviewed cart carries tax, but there is no longer a taxable nexus for the address
    Cart cart = cartWithReviewedTax(new BigDecimal("8.25"));
    Customer customer = customerShippingTo("UNITED STATES", "VA", "22182");
    ShippingRate shippingRate = new ShippingRate();

    try (MockedStatic<SalesTaxCommand> tax = mockStatic(SalesTaxCommand.class)) {
      tax.when(() -> SalesTaxCommand.estimatedTaxRateForAddress(any())).thenReturn(null);

      DataException ex = Assertions.assertThrows(DataException.class,
          () -> OrderCommand.determineTaxToCharge(cart, customer, shippingRate));
      Assertions.assertTrue(ex.getMessage().toLowerCase().contains("review the cart"));
      // With no rate, the amount calculation must not be attempted
      tax.verify(() -> SalesTaxCommand.estimateTax(any(), any(), any(), any()), never());
    }
  }

  @Test
  void newlyTaxableButCartHasNoTaxBouncesToReview() {
    // The cart was never taxed, but a nexus now makes the destination taxable.
    // Charging the order as-is would silently under-collect tax; bounce instead.
    Cart cart = cartWithReviewedTax(null);
    Customer customer = customerShippingTo("UNITED STATES", "VA", "22182");
    ShippingRate shippingRate = new ShippingRate();

    try (MockedStatic<SalesTaxCommand> tax = mockStatic(SalesTaxCommand.class)) {
      tax.when(() -> SalesTaxCommand.estimatedTaxRateForAddress(any())).thenReturn(new BigDecimal("0.06"));
      tax.when(() -> SalesTaxCommand.estimateTax(any(), any(), any(), any())).thenReturn(new BigDecimal("7.50"));

      DataException ex = Assertions.assertThrows(DataException.class,
          () -> OrderCommand.determineTaxToCharge(cart, customer, shippingRate));
      Assertions.assertTrue(ex.getMessage().toLowerCase().contains("review the cart"));
    }
  }

  @Test
  void noTaxApplicableAndNoneReviewedChargesZero() throws DataException {
    Cart cart = cartWithReviewedTax(null);
    Customer customer = customerShippingTo("UNITED STATES", "VA", "22182");
    ShippingRate shippingRate = new ShippingRate();

    try (MockedStatic<SalesTaxCommand> tax = mockStatic(SalesTaxCommand.class)) {
      tax.when(() -> SalesTaxCommand.estimatedTaxRateForAddress(any())).thenReturn(null);

      BigDecimal charged = OrderCommand.determineTaxToCharge(cart, customer, shippingRate);

      Assertions.assertEquals(0, charged.compareTo(BigDecimal.ZERO));
    }
  }

  @Test
  void incompleteAddressWithReviewedTaxBouncesWithoutCallingTheService() {
    // Address is missing a postal code, so tax cannot be re-derived; a cart that
    // nonetheless carries a tax amount is stale and must bounce, not charge blindly.
    Cart cart = cartWithReviewedTax(new BigDecimal("5.00"));
    Customer customer = customerShippingTo("UNITED STATES", "VA", null);
    ShippingRate shippingRate = new ShippingRate();

    try (MockedStatic<SalesTaxCommand> tax = mockStatic(SalesTaxCommand.class)) {
      DataException ex = Assertions.assertThrows(DataException.class,
          () -> OrderCommand.determineTaxToCharge(cart, customer, shippingRate));
      Assertions.assertTrue(ex.getMessage().toLowerCase().contains("review the cart"));
      tax.verify(() -> SalesTaxCommand.estimatedTaxRateForAddress(any()), never());
      tax.verify(() -> SalesTaxCommand.estimateTax(any(), any(), any(), any()), never());
    }
  }
}
