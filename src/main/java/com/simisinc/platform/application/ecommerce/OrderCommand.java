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
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.SiteProperty;
import com.simisinc.platform.domain.model.ecommerce.*;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.persistence.SitePropertyRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.routines.EmailValidator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.simisinc.platform.application.ecommerce.OrderStatusCommand.CREATED;

/**
 * Generates an order from a cart
 *
 * @author matt rajkowski
 * @created 7/13/19 9:56 AM
 */
public class OrderCommand {

  private static final String LAST_ORDER_DATE_PROPERTY = "ecommerce.lastOrderDate";
  private static Log LOG = LogFactory.getLog(OrderCommand.class);

  public static synchronized String generateUniqueId() {
    // Use the e-commerce format
    // yymmdd-####-****
    // 190714-1005-3258

    // Use a prefix if desired
    String prefix = "";

    // Use the yymmdd
    String timezone = LoadSitePropertyCommand.loadByName("site.timezone", "America/New_York");
    DateTimeFormatter formatDate = DateTimeFormatter.ofPattern("yyMMdd");
    Instant timeStamp = Instant.now();
    ZonedDateTime orderDateTime = timeStamp.atZone(ZoneId.of(timezone));
    String date = formatDate.format(orderDateTime);

    // See if the daily sequence needs resetting
    SiteProperty lastOrderDate = SitePropertyRepository.findByName(LAST_ORDER_DATE_PROPERTY);
    if (lastOrderDate != null && !lastOrderDate.getValue().equals(date)) {
      lastOrderDate.setValue(date);
      SitePropertyRepository.save(lastOrderDate);
      DB.resetSequence("order_daily_seq", 1001);
    }

    // Get the next sequence value
    String id = "00-1";
    long sequenceId = DB.selectNextSequenceValue("order_daily_seq");
    if (sequenceId > 0) {
      id = StringUtils.leftPad(String.valueOf(sequenceId), 4, '0');
    }

    // Generate a random string
    String rand = StringUtils.leftPad(RandomStringUtils.randomNumeric(4), 4, '0');

    // Use the customer id after insert
    String uniqueId = (prefix + date + "-" + id + "-" + rand);
    LOG.debug("generateUniqueId-> order: " + uniqueId);
    return uniqueId;
  }

  public synchronized static Order generateOrder(Cart sessionCart) throws DataException {

    // Make sure the cart information is fully updated with the payment information
    Cart cart = CartRepository.findById(sessionCart.getId());
    if (cart == null) {
      throw new DataException("Cart error");
    }
    cart.setPaymentToken(sessionCart.getPaymentToken());
    if (sessionCart.getCreatedBy() > 0) {
      cart.setCreatedBy(sessionCart.getCreatedBy());
    }
    if (sessionCart.getModifiedBy() > 0) {
      cart.setModifiedBy(sessionCart.getModifiedBy());
    }

    // Check the required information
    LOG.debug("Using customer information...");
    Customer customer = CustomerRepository.findById(cart.getCustomerId());
    if (customer == null) {
      throw new DataException("Information error");
    }
    CartValidationCommand.validateHasCartWithItems(cart);
    CartValidationCommand.validateHasShippingAddress(customer);
    EmailValidator emailValidator = EmailValidator.getInstance(false);
    if (!emailValidator.isValid(customer.getEmail())) {
      throw new DataException("A valid email address is required");
    }
    if (!CartValidationCommand.validateProductDestination(customer, cart)) {
      throw new DataException("At least one of the items in your cart cannot be shipped to the address you entered");
    }

    // Validate the cart items and generate a running total
    BigDecimal numberOfItems = new BigDecimal(0);
    BigDecimal runningTotal = new BigDecimal(0);
    List<CartItem> cartItemList = CartItemRepository.findValidItemsByCartId(cart.getId());
    for (CartItem cartItem : cartItemList) {

      // Check if the product is still available
      ProductSku productSku = ProductSkuRepository.findById(cartItem.getSkuId());
      if (productSku == null || !productSku.getEnabled() || productSku.getPrice() == null) {
        // The SKU is unavailable
        throw new DataException("A product is no longer available, please review the cart");
      }

      // See if the price changed
      if (productSku.getPrice().compareTo(cartItem.getEachAmount()) != 0) {
        throw new DataException("A product's price changed, please review the cart");
      }
      numberOfItems = numberOfItems.add(cartItem.getQuantity());
      runningTotal = runningTotal.add(productSku.getPrice().multiply(cartItem.getQuantity()));
    }

    if (runningTotal.compareTo(cart.getSubtotalAmount()) != 0) {
      throw new DataException("A product's price changed, please review the cart");
    }

    // Determine the total price with shipping, tax, and discount
    BigDecimal grandTotal = runningTotal;

    // Determine the discount again
    PricingRule pricingRule = CartCommand.retrievePromoCodePricingRule(cart);
    BigDecimal discount = PricingRuleCommand.calculateDiscount(pricingRule, cart, runningTotal);
    if (discount != null && discount.compareTo(BigDecimal.ZERO) > 0) {
      LOG.debug("Discount calculated at checkout: " + discount.toPlainString());
      grandTotal = grandTotal.subtract(discount);
    }

    // Determine the shipping/handling fee
    if (cart.getShippingAndHandlingFee() != null) {
      grandTotal = grandTotal.add(cart.getShippingAndHandlingFee());
    }

    // Finally, determine the taxes
    if (cart.getTaxAmount() != null) {
      // @todo Use the Tax service to get the final actual amount
      grandTotal = grandTotal.add(cart.getTaxAmount());
    }

    // Include Gift Cards (set amount going to gift card, reduce amount CC)

    // Convert the cart to an order
    LOG.debug("Starting an order...");
    Order order = new Order();
    order.setCartId(cart.getId());
    order.setCustomerId(cart.getCustomerId());
    order.setTotalItems(numberOfItems.intValue());
    order.setCreatedBy(cart.getCreatedBy());
    order.setModifiedBy(cart.getModifiedBy());
    order.setSessionId(cart.getSessionId());
    // order.setCustomerNote();

    // Customer information
    order.setFirstName(customer.getFirstName());
    order.setLastName(customer.getLastName());
    order.setEmail(customer.getEmail());
    order.setBillingAddress(customer.getBillingAddress());
    order.setShippingAddress(customer.getShippingAddress());

    // Shipping information
    ShippingRate shippingRate = ShippingRateRepository.findById(cart.getShippingRateId());
    if (shippingRate != null) {
      order.setShippingRateId(shippingRate.getId());
      order.setShippingMethodId(shippingRate.getShippingMethodId());
    }

    // Payment information
    order.setPaymentToken(cart.getPaymentToken());
    if (cart.getCurrency() != null) {
      order.setCurrency(cart.getCurrency());
    }

    // Product subtotal
    order.setSubtotalAmount(runningTotal);

    // Discounts
    order.setPromoCode(cart.getPromoCode());
    order.setDiscountAmount(discount);
    if (pricingRule != null) {
      order.setPricingRuleId(pricingRule.getId());
    }

    // Shipping and handling fees
    // @todo consider re-generating
    order.setShippingFee(cart.getShippingFee());
    order.setHandlingFee(cart.getHandlingFee());

    // Taxes
    // @todo consider regenerating
    order.setTaxAmount(cart.getTaxAmount());
    order.setTaxRate(cart.getTaxRate());
//    order.setTaxId();
//    order.setShippingTaxAmount();
//    order.setHandlingFeeTaxAmount();

    // Total
    order.setTotalAmount(grandTotal);

    // Status
    int statusId = OrderStatusCommand.retrieveStatusId(CREATED);
    order.setStatusId(statusId);

    // Save the order as created (and update the cart order id)
    Order validOrder = OrderRepository.create(order, cartItemList);
    if (validOrder != null) {
      long oldOrderId = cart.getOrderId();
      cart.setOrderId(order.getId());
      if (oldOrderId != -1 && oldOrderId != order.getId()) {
        // Cleanup the old order
        LOG.info("Cleanup old order: " + oldOrderId);
        // @todo
//        OrderCommand.voidIncompleteOrder(oldOrderId);
      }
      return validOrder;
    }
    return null;
  }
}
