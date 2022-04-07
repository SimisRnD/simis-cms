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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/11/19 7:49 AM
 */
public class CartValidationCommand {

  private static Log LOG = LogFactory.getLog(CartValidationCommand.class);

  public static boolean hasCartWithItems(Cart cart) {
    return (cart != null && cart.getTotalItems() > 0);
  }

  public static void validateHasCartWithItems(Cart cart) throws DataException {
    if (!hasCartWithItems(cart)) {
      throw new DataException("A cart with items is required");
    }
  }

  public static boolean hasCustomerAssociated(Cart cart) {
    return (cart != null && cart.getCustomerId() > 0L);
  }

  public static void validateHasCustomerAssociated(Cart cart) throws DataException {
    if (!hasCustomerAssociated(cart)) {
      throw new DataException("A customer record is required");
    }
  }

  public static void validateHasShippingAddress(Customer customer) throws DataException {
    if (customer == null || !ValidateCustomerCommand.validateCustomerShippingAddress(customer.getShippingAddress(), null)) {
      throw new DataException("An address is required");
    }
  }

  public static boolean checkIfCartEntryMeetsRules(CartEntry cartEntry) {
    List<PricingRule> pricingRuleList = PricingRuleCommand.findRulesForSku(cartEntry.getProductSku().getSku());
    if (pricingRuleList == null || pricingRuleList.isEmpty()) {
      return true;
    }
    for (PricingRule pricingRule : pricingRuleList) {
      if (pricingRule.getMinimumOrderQuantity() > 0 && cartEntry.getCartItem().getQuantity().intValue() < pricingRule.getMinimumOrderQuantity()) {
        cartEntry.setErrorMessage(pricingRule.getName());
        return false;
      }
      if (pricingRule.getMaximumOrderQuantity() > 0 && cartEntry.getCartItem().getQuantity().intValue() > pricingRule.getMaximumOrderQuantity()) {
        cartEntry.setErrorMessage(pricingRule.getName());
        return false;
      }
    }
    return true;
  }

  public static boolean validateProductDestination(Customer customer, Cart cart) {
    // Does the cart contain any products in which states are excluded?
    List<CartItem> cartItemList = CartItemRepository.findValidItemsByCartId(cart.getId());
    for (CartItem cartItem : cartItemList) {
      Product product = LoadProductCommand.loadProductById(cartItem.getProductId());
      if (StringUtils.isNotBlank(product.getExcludeUsStates())) {
        // Compare the list with the shipping address
        List<String> excludedList = Stream.of(product.getExcludeUsStates().toUpperCase().split(Pattern.quote(",")))
            .map(String::trim)
            .collect(toList());
        if (excludedList.contains(customer.getShippingAddress().getState().toUpperCase())) {
          return false;
        }
      }
    }
    return true;
  }
}
