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

import com.simisinc.platform.domain.model.ecommerce.Cart;
import com.simisinc.platform.domain.model.ecommerce.CartItem;
import com.simisinc.platform.domain.model.ecommerce.PricingRule;
import com.simisinc.platform.infrastructure.persistence.ecommerce.CartItemRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.PricingRuleRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.PricingRuleSpecification;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 11/21/19 9:49 PM
 */
public class PricingRuleCommand {

  private static Log LOG = LogFactory.getLog(PricingRuleCommand.class);

  public static PricingRule findValidPromoCode(String promoCode, String countryCode) {
    if (StringUtils.isBlank(promoCode)) {
      LOG.debug("findValidPromoCode is blank");
      return null;
    }

    // Find the specified promo code
    PricingRuleSpecification specification = new PricingRuleSpecification();
    specification.setEnabled(true);
    specification.setIsValidToday(true);
    specification.setPromoCode(promoCode);
    specification.setCountryCode(countryCode);

    List<PricingRule> pricingRuleList = PricingRuleRepository.findAll(specification, null);
    if (pricingRuleList.size() == 1) {
      return pricingRuleList.get(0);
    }

    return null;
  }

  public static BigDecimal calculateDiscount(PricingRule pricingRule, Cart cart) {
    return calculateDiscount(pricingRule, cart, cart.getSubtotalAmount());
  }

  public static BigDecimal calculateDiscount(PricingRule pricingRule, Cart cart, BigDecimal subtotalAmount) {
    LOG.debug("calculateDiscount...");
    if (pricingRule == null) {
      return null;
    }
    if (subtotalAmount == null) {
      return null;
    }

    // Determine items eligible for discount (or all)
    BigDecimal eligibleAmount = subtotalAmount;

    // Use the cart items to determine eligible amounts
    List<CartItem> cartItemList = CartItemRepository.findValidItemsByCartId(cart.getId());

    // Put all the amounts in a list, then sort it; if there is a limit to the number of items, then pick the
    // top number of eligible items; if there is a minimum order quantity then use this list too
    List<BigDecimal> eligibleAmountList = new ArrayList<>();
    if (StringUtils.isBlank(pricingRule.getValidSkus())) {
      // Add all the items to the eligible amount list
      for (CartItem item : cartItemList) {
        // Add each qty separately
        for (int i = 0; i < item.getQuantity().intValue(); i++) {
          eligibleAmountList.add(item.getEachAmount());
        }
      }
    } else {
      // Add only the valid sku items to the eligible amount and list
      List<String> validSkuList = Stream.of(pricingRule.getValidSkus().toUpperCase().split(Pattern.quote(",")))
          .map(String::trim)
          .collect(toList());
      // There will be a new eligible amount based on the valid SKUs
      eligibleAmount = new BigDecimal(0);
      for (CartItem item : cartItemList) {
        if (validSkuList.contains(item.getProductSku())) {
          eligibleAmount = eligibleAmount.add(item.getTotalAmount());
          // Add each qty separately
          for (int i = 0; i < item.getQuantity().intValue(); i++) {
            eligibleAmountList.add(item.getEachAmount());
          }
        }
      }
      LOG.debug("Eligible amount from valid SKUs: " + eligibleAmount.toPlainString());
    }

    // Determine if there is a limit to the discount
    if (pricingRule.getItemLimit() > 0) {
      // Sort the items by highest price
      Collections.sort(eligibleAmountList, Collections.reverseOrder());
      // Limit the discount to the valid number of items
      BigDecimal validAmount = new BigDecimal(0);
      for (int i = 0; i < pricingRule.getItemLimit(); i++) {
        if (i < eligibleAmountList.size()) {
          validAmount = validAmount.add(eligibleAmountList.get(i));
        }
      }
      // There is a new eligible amount based on the limit
      eligibleAmount = validAmount;
      LOG.debug("Eligible amount from item limit: " + eligibleAmount.toPlainString());
    }

    // Require an eligible amount
    if (eligibleAmount.compareTo(BigDecimal.ZERO) <= 0) {
      LOG.debug("Eligible amount is 0, discount is 0");
      return null;
    }

    // Perform validations like minimum qty, etc.
    if (pricingRule.getMinimumSubtotal() != null && pricingRule.getMinimumSubtotal().compareTo(BigDecimal.ZERO) > 0) {
      // A minimum order amount is required for this discount
      if (eligibleAmount.compareTo(pricingRule.getMinimumSubtotal()) < 0) {
        LOG.debug("Minimum eligible amount not met: " + pricingRule.getMinimumSubtotal().toPlainString() + " compared to " + eligibleAmount.toPlainString());
        return null;
      }
    }

    if (pricingRule.getMinimumOrderQuantity() > 0) {
      if (eligibleAmountList.size() < pricingRule.getMinimumOrderQuantity()) {
        LOG.debug("Minimum eligible order quantity not met: " + pricingRule.getMinimumOrderQuantity() + " required compared to " + eligibleAmountList.size());
        return null;
      }
    }

    // Calculate the subtotal percent based on the eligible amount
    if (pricingRule.getSubtotalPercent() > 0) {
      // The discount is based on the eligible subtotal amount
      LOG.debug("Found subtotalPercent: " + pricingRule.getSubtotalPercent());
      double percent = pricingRule.getSubtotalPercent() / 100.0;
      BigDecimal discount = eligibleAmount.multiply(new BigDecimal(percent)).setScale(2, RoundingMode.HALF_UP);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Discount from percent is: " + discount.toPlainString());
      }
      return discount;
    }

    // Calculate subtracting an amount
    if (pricingRule.getSubtractAmount() != null && pricingRule.getSubtractAmount().compareTo(BigDecimal.ZERO) > 0) {
      LOG.debug("Found subtractAmount: " + pricingRule.getSubtractAmount());
      // Make sure discount is not more than the eligible amount
      if (pricingRule.getSubtractAmount().compareTo(eligibleAmount) <= 0) {
        // The discount is less than the eligible amount, so proceed with the discount amount
        LOG.debug("Discount is: " + pricingRule.getSubtractAmount().toPlainString());
        return pricingRule.getSubtractAmount();
      } else {
        // The discount is more than the eligible amount, so limit the discount to the eligible amount
        LOG.debug("Discount is limited to: " + eligibleAmount.toPlainString());
        return eligibleAmount;
      }
    }

    // Calculate BOGO combinations
    // @note Item limit is not enforced here, but should
    if (pricingRule.getBuyXItems() > 0 && pricingRule.getGetYItemsFree() > 0) {
      // Go through the cart and see if the item qualifies...
      // then set that the item has "Y" free (will print Y free in cart)
      // set the discount
      BigDecimal discount = new BigDecimal(0);
      int quantityVector = pricingRule.getBuyXItems() + pricingRule.getGetYItemsFree();
      for (CartItem item : cartItemList) {
        // Round down the number of free items
        if (item.getQuantity().intValue() >= quantityVector) {
          // Determine the number of complete free vectors
          int freeVector = item.getQuantity().intValue() / quantityVector;
          int numberFree = freeVector * pricingRule.getGetYItemsFree();
          BigDecimal itemDiscount = new BigDecimal(numberFree).multiply(item.getEachAmount());
          discount = discount.add(itemDiscount);
          LOG.debug("Item BOGO discount is: " + itemDiscount.toPlainString());
          item.setQuantityFree(new BigDecimal(numberFree));
        } else {
          item.setQuantityFree(new BigDecimal(0));
        }
        // Update the free quantity to value or 0
        CartItemRepository.updateQuantityFree(item);
      }
      LOG.debug("Discount is: " + discount.toPlainString());
      if (discount.compareTo(BigDecimal.ZERO) > 0) {
        return discount;
      }
    } else {
      CartItemRepository.resetQuantityFree(cart);
    }
    return null;
  }

  public static List<PricingRule> findRulesForSku(String sku) {
    if (StringUtils.isBlank(sku)) {
      return null;
    }
    return PricingRuleRepository.findAllRulesByValidSku(sku);
  }
}
