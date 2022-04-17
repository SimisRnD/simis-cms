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
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingCountryRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingRateRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingRateSpecification;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Finds and validates delivery options
 *
 * @author matt rajkowski
 * @created 4/24/19 10:08 PM
 */
public class ShippingRatesCommand {

  private static Log LOG = LogFactory.getLog(ShippingRatesCommand.class);

  public static List<ShippingRate> findDeliveryOptions(Customer customer, Cart cart) throws DataException {

    if (customer == null) {
      throw new DataException("Customer was not found");
    }

    // Check the address
    Address address = customer.getShippingAddress();
    if (StringUtils.isBlank(address.getCountry()) || StringUtils.isBlank(address.getState()) || StringUtils.isBlank(address.getPostalCode())) {
      throw new DataException("Customer address is incomplete");
    }

    // Use the country to determine the rates
    ShippingCountry shippingCountry = ShippingCountryRepository.findByEnabledCountry(address.getCountry());
    if (shippingCountry == null) {
      // Let the user know something about this
      LOG.warn("Country not found: " + address.getCountry());
      return null;
    }

    // Prepare to search for rates
    ShippingRateSpecification shippingRateSpecification = new ShippingRateSpecification();
    shippingRateSpecification.setAddress(shippingCountry.getCode(), address.getState(), address.getPostalCode());
    shippingRateSpecification.setOrderSubtotal(cart.getSubtotalAmount());

    // Determine if the promo has free shipping (overrides the order minimum)
    PricingRule pricingRule = CartCommand.retrievePromoCodePricingRule(cart);
    if (pricingRule != null) {
      // Check if rule always allows free shipping option
      if (pricingRule.getFreeShipping()) {
        // Remove the subtotal
        shippingRateSpecification.setOrderSubtotal(null);
      }
    }

    // @todo calculate and use total package weight for shipping rates
//    shippingRateSpecification.setPackageTotalWeightOz(cart.getTotalWeight());
    shippingRateSpecification.setEnabledOnly(true);

    // For a country, look for region specific pricing first
    shippingRateSpecification.setSpecificRegionOnly(true);
    List<ShippingRate> fullShippingRateList = ShippingRateRepository.findAll(shippingRateSpecification, null);

    // If nothing, look for the best match, like for a whole country
    if (fullShippingRateList.isEmpty()) {
      shippingRateSpecification.setSpecificRegionOnly(false);
      fullShippingRateList = ShippingRateRepository.findAll(shippingRateSpecification, null);
    }

    // Fallback to non-country specific settings, like for supporting multiple countries with the same rate
    if (fullShippingRateList.isEmpty()) {
      shippingRateSpecification.setAddress("*", "*", "*");
      fullShippingRateList = ShippingRateRepository.findAll(shippingRateSpecification, null);
    }

    // Use the cart items for valid items
    List<CartItem> cartItemList = CartItemRepository.findValidItemsByCartId(cart.getId());

    // Make a unique list of valid shipping rates
    List<ShippingRate> shippingRateList = new ArrayList<>();
    int lastMethod = -1;
    for (ShippingRate shippingRate : fullShippingRateList) {
      if (shippingRate.getShippingMethodId() != lastMethod) {
        // Found a unique entry, validate it
        boolean isExcluded = false;
        if (!StringUtils.isBlank(shippingRate.getExcludeSkus())) {
          List<String> excludeSkuList = Stream.of(shippingRate.getExcludeSkus().toUpperCase().split(Pattern.quote(",")))
              .map(String::trim)
              .collect(toList());
          for (CartItem cartItem : cartItemList) {
            if (excludeSkuList.contains(cartItem.getProductSku().toUpperCase())) {
              isExcluded = true;
              break;
            }
          }
        }
        // Add this option
        if (!isExcluded) {
          shippingRateList.add(shippingRate);
        }
      }
      lastMethod = shippingRate.getShippingMethodId();
    }

    // Sort the list (lowest to highest)
    shippingRateList.sort(Comparator.comparing(ShippingRate::getTotal));

    return shippingRateList;
  }

  public static boolean validateShippingRate(Customer customer, Cart cart, ShippingRate shippingRate) {
    try {
      List<ShippingRate> validShippingRates = findDeliveryOptions(customer, cart);
      if (!hasAvailableMethod(validShippingRates)) {
        return false;
      }
      for (ShippingRate validShippingRate : validShippingRates) {
        if (validShippingRate.getId().equals(shippingRate.getId())) {
          return true;
        }
      }
    } catch (Exception e) {
      LOG.debug("validateShippingRate error", e);
    }
    return false;
  }

  public static boolean hasAvailableMethod(List<ShippingRate> shippingRateList) {
    if (shippingRateList == null || shippingRateList.isEmpty()) {
      return false;
    }
    for (ShippingRate rate : shippingRateList) {
      if (rate.getShippingCode().equalsIgnoreCase("Restricted")) {
        return false;
      }
    }
    return true;
  }
}
