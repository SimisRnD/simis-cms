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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.ecommerce.*;
import com.simisinc.platform.infrastructure.persistence.ecommerce.CartItemRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.CartRepository;
import com.simisinc.platform.presentation.controller.UserSession;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Commands for creating and updating a user's cart
 *
 * @author matt rajkowski
 * @created 4/9/19 10:15 AM
 */
public class CartCommand {

  private static String generateCartToken() {
    return UUID.randomUUID().toString() + "-" + System.currentTimeMillis();
  }

  public static Cart createCart(UserSession userSession) {
    // Create a new cart and save it
    String cartToken = generateCartToken();
    Cart cart = new Cart(cartToken, userSession.getVisitorId(), userSession.getSessionId(), userSession.getUserId());
    cart = CartRepository.add(cart);
    userSession.setCart(cart);
    return cart;
  }

  public static boolean addSkuToCart(UserSession userSession, Product product, ProductSku productSku, BigDecimal quantity) {

    // Check if the system still thinks this cart is valid
    Cart cart = userSession.getCart();
    if (cart != null && LoadCartCommand.loadCartByToken(cart.getToken()) == null) {
      cart = null;
    }

    // If necessary, create a new cart and save it
    if (cart == null) {
      cart = createCart(userSession);
    }

    // Determine if the cart already has this SKU, and increment the quantity
    List<CartItem> cartItemList = CartItemRepository.findValidItemsByCartId(cart.getId());
    for (CartItem cartItem : cartItemList) {
      if (cartItem.getSkuId() == productSku.getId()) {
        // Prepare to update the item and cart
        BigDecimal newQuantity = cartItem.getQuantity().add(quantity);
        cartItem.setQuantity(newQuantity);
        cartItem.setTotalAmount(productSku.getPrice().multiply(cartItem.getQuantity()));
        // Adjust the cart
        cart.setTotalQty(cart.getTotalQty().add(quantity));
        cart.setSubtotalAmount(cart.getSubtotalAmount().add(productSku.getPrice().multiply(quantity)));
        // Reset all the taxes and stuff since a modification occurred
        CartCommand.resetShippingAndTaxes(cart);
        // Get the cart items correct; update the cart items and the cart summary itself
        if (CartRepository.updateCart(cart, cartItemList)) {
          // Recalculate any discount
          CartCommand.updateDiscount(cart);
          return true;
        } else {
          return false;
        }
      }
    }

    // Add the product
    if (CartRepository.addProductToCart(cart, product, productSku, quantity)) {
      // Modify the cart
      cart.setTotalItems(cart.getTotalItems() + 1);
      cart.setTotalQty(cart.getTotalQty().add(quantity));
      // Update the subtotal
      cart.setSubtotalAmount(cart.getSubtotalAmount().add(productSku.getPrice().multiply(quantity)));
      // Recalculate any discount
      CartCommand.updateDiscount(cart);
      // Reset the shipping and sales tax
      CartCommand.resetShippingAndTaxes(cart);
      return true;
    }
    return false;
  }

  public static boolean removeProductFromCart(Cart cart, CartItem cartItem) {
    // Remove the user's item from the cart
    if (CartItemRepository.removeItemFromCart(cart, cartItem)) {
      // Modify the cart
      cart.setTotalItems(cart.getTotalItems() - 1);
      cart.setTotalQty(cart.getTotalQty().subtract(cartItem.getQuantity()));
      cart.setSubtotalAmount(cart.getSubtotalAmount().subtract(cartItem.getQuantity().multiply(cartItem.getEachAmount())));
      // Recalculate any discount
      CartCommand.updateDiscount(cart);
      // Reset taxes, fees, shipping
      CartCommand.resetShippingAndTaxes(cart);
      return true;
    }
    return false;
  }

  public static PricingRule retrievePromoCodePricingRule(Cart cart) {
    if (StringUtils.isNotBlank(cart.getPromoCode())) {
      return PricingRuleCommand.findValidPromoCode(cart.getPromoCode(), null);
    }
    return null;
  }

  public static void updateDiscount(Cart cart) {
    updatePromoCode(cart, cart.getPromoCode());
  }

  public static void updatePromoCode(Cart cart, String promoCode) {
    // Reset the free items
    CartItemRepository.resetQuantityFree(cart);
    // Evaluate the promo code
    if (StringUtils.isBlank(promoCode)) {
      // Remove the code
      cart.setPromoCode(null);
      cart.setPricingRuleId(-1);
      cart.setDiscount(null);
    } else {
      // Add the code the user provided
      cart.setPromoCode(promoCode.trim().toUpperCase());
      PricingRule pricingRule = PricingRuleCommand.findValidPromoCode(cart.getPromoCode(), null);
      if (pricingRule == null) {
        // It's not valid
        cart.setPricingRuleId(-1);
        cart.setDiscount(null);
      } else {
        // It's valid
        cart.setPricingRuleId(pricingRule.getId());
        // Update the discount
        BigDecimal discount = PricingRuleCommand.calculateDiscount(pricingRule, cart);
        cart.setDiscount(discount);
      }
    }
    // Update the database
    CartRepository.updateDiscount(cart);
  }

  public static void updateCustomer(Cart cart, Customer customer) {
    cart.setCustomerId(customer.getId());
    // Update the database
    CartRepository.updateCustomer(cart);
  }

  public static void resetShippingAndTaxes(Cart cart) {
    cart.setShippingRateId(-1);
    cart.setShippingFee(null);
    cart.setHandlingFee(null);
    cart.setShippingTax(null);
    cart.setHandlingTax(null);
    cart.setTaxAmount(null);
    cart.setTaxRate(new BigDecimal(0));
    // Update the database
    CartRepository.updateShippingRateAndTaxes(cart);
  }

  public static void saveShippingAndTaxes(Cart cart, ShippingRate shippingRate, BigDecimal taxAmount, BigDecimal taxRate) {
    // @todo estimateTax needs to use a Tax object with shipping taxes, handling taxes, total taxes
    cart.setShippingRateId(shippingRate.getId());
    cart.setShippingFee(shippingRate.getShippingFee());
    cart.setHandlingFee(shippingRate.getHandlingFee());
    cart.setShippingTax(null);
    cart.setHandlingTax(null);
    cart.setTaxAmount(taxAmount);
    cart.setTaxRate(taxRate);
    // Update the database
    CartRepository.updateShippingRateAndTaxes(cart);
  }

  public static void savePaymentToken(Cart cart, String paymentToken, Card card) {
    cart.setPaymentToken(paymentToken);
    cart.setCard(card);
    // @todo consider storing in the database, though it is short lived
  }

  public static void saveCustomerContactInfo(Cart cart, String firstName, String lastName, String email) throws DataException {
    cart.setFirstName(firstName);
    cart.setLastName(lastName);
    cart.setEmail(email);
    SaveCustomerCommand.saveCustomerContactInfo(cart.getCustomerId(), firstName, lastName, email);
  }
}
