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

import com.simisinc.platform.application.cms.FontCommand;
import com.simisinc.platform.application.cms.LoadContentCommand;
import com.simisinc.platform.application.ecommerce.*;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.domain.model.ecommerce.*;
import com.simisinc.platform.infrastructure.persistence.ecommerce.CartItemRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.CartRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductSkuRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/14/19 10:38 PM
 */
public class CartWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/ecommerce/cart.jsp";
  static String SUMMARY_JSP = "/ecommerce/cart-summary.jsp";
  static String ITEMS_JSP = "/ecommerce/cart-items.jsp";
  static String EMPTY_JSP = "/ecommerce/cart-empty.jsp";

  /**
   * Verifies that the SKU is in stock and retrieves its current price
   * Warns the customer if there are inventory limits or price changes since adding to the cart
   * Sums up all the line items for a running total
   *
   * @param context
   * @return
   */
  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Check for a global cart error
    String cartError = context.getSharedRequestValue("cartError");
    if (StringUtils.isNotBlank(cartError)) {
      context.getRequest().setAttribute("errorMessage", cartError);
    }

    // Determine the view
    String view = context.getPreferences().get("view");
    boolean showWhenEmpty = "true".equals(context.getPreferences().getOrDefault("showWhenEmpty", "true"));
    boolean showWhenNotEmpty = "true".equals(context.getPreferences().getOrDefault("showWhenNotEmpty", "true"));

    // Check the cart
    Cart cart = context.getUserSession().getCart();

    // Determine if the cart state can be shown
    boolean cartIsEmpty = (cart == null || cart.getTotalItems() == 0);
    if ((cartIsEmpty && showWhenEmpty) || (!cartIsEmpty && showWhenNotEmpty)) {
      // Show the widget
    } else {
      return context;
    }

    if (cartIsEmpty) {
      context.getRequest().setAttribute("shopUrl", context.getPreferences().getOrDefault("shopUrl", "/shop"));
      context.getRequest().setAttribute("emptyMessage", context.getPreferences().getOrDefault("emptyMessage", "Your cart is empty."));
      context.setJsp(EMPTY_JSP);
      return context;
    }
    context.getRequest().setAttribute("cart", cart);

    // Determine if there is a promo code / pricing rule
    PricingRule pricingRule = CartCommand.retrievePromoCodePricingRule(cart);
    if (pricingRule != null) {
      context.getRequest().setAttribute("pricingRule", pricingRule);
    }

    // Load the complete cart items
    List<CartItem> cartItemList = CartItemRepository.findValidItemsByCartId(cart.getId());
    context.getRequest().setAttribute("cartItemList", cartItemList);

    // Load the related products
    // Compare the cart items with the SKUs to alert the user of any changes
    // Find SKUs that are no longer available

    List<CartEntry> cartEntryList = new ArrayList<>();
    List<CartEntry> priceChangeList = new ArrayList<>();
    List<CartItem> noLongerAvailableList = new ArrayList<>();
    List<CartEntry> cartRuleNotMetList = new ArrayList<>();
    boolean preventCheckout = false;

    BigDecimal runningTotal = new BigDecimal(0);
    for (CartItem cartItem : cartItemList) {

      // Check if the product is still available
      ProductSku productSku = ProductSkuRepository.findById(cartItem.getSkuId());
      if (productSku == null || !productSku.getEnabled() || productSku.getPrice() == null) {
        // The SKU is unavailable
        noLongerAvailableList.add(cartItem);
        continue;
      }

      // Prepare the entry for display
      CartEntry cartEntry = new CartEntry();
      cartEntry.setCartItem(cartItem);
      cartEntry.setProduct(LoadProductCommand.loadProductMetaDataById(productSku.getProductId()));
      cartEntry.setProductSku(productSku);

      // Check the item meets all rules
      if (!CartValidationCommand.checkIfCartEntryMeetsRules(cartEntry)) {
        LOG.debug("A pricing rule is preventing the cart from being valid.");
        cartRuleNotMetList.add(cartEntry);
        context.getRequest().setAttribute("preventCheckout", "true");
        preventCheckout = true;
      }

      // Check the Inventory is available command
      if (!ProductInventoryCommand.isAvailable(productSku, cartEntry.getCartItem().getQuantity())) {
        if (ProductInventoryCommand.hasMoreOnTheWay(productSku)) {
          cartEntry.setStatus(ProductSku.STATUS_MORE_ON_THE_WAY);
        }
      }
      cartEntryList.add(cartEntry);

      // Track the running total
      runningTotal = runningTotal.add(productSku.getPrice().multiply(cartItem.getQuantity()));

      // See if the price changed
      if (productSku.getPrice().compareTo(cartItem.getEachAmount()) != 0) {
        priceChangeList.add(cartEntry);
      }
    }
    context.getRequest().setAttribute("cartEntryList", cartEntryList);
    context.getRequest().setAttribute("priceChangeList", priceChangeList);
    context.getRequest().setAttribute("noLongerAvailableList", noLongerAvailableList);
    context.getRequest().setAttribute("cartRuleNotMetList", cartRuleNotMetList);
    context.getRequest().setAttribute("runningTotal", runningTotal.doubleValue());

    // Determine the total price with shipping, tax, and discount
    BigDecimal grandTotal = cart.getGrandTotal();
    if (cart.getDiscount() != null && cart.getDiscount().compareTo(BigDecimal.ZERO) > 0) {
      context.getRequest().setAttribute("discount", cart.getDiscount().doubleValue());
    }
    context.getRequest().setAttribute("grandTotal", grandTotal.doubleValue());

    if ("summary".equals(view)) {
      // Just the summary is being shown (usually during checkout)
      if (preventCheckout) {
        context.setRedirect("/cart");
      }
      context.setJsp(SUMMARY_JSP);
    } else if ("items".equals(view)) {
      context.setJsp(ITEMS_JSP);
    } else {
      // Check if there is html content to include
      String[] contentList = new String[]{"card1", "card2"};
      for (String cardName : contentList) {
        String contentUniqueId = context.getPreferences().get(cardName + "uniqueId");
        if (StringUtils.isBlank(contentUniqueId)) {
          LOG.debug("Card preference not found for: " + contentUniqueId);
          continue;
        }
        Content content = LoadContentCommand.loadContentByUniqueId(contentUniqueId);
        String html = null;
        if (content != null) {
          html = content.getContent();
        }
        if (StringUtils.isBlank(html)) {
          if (context.hasRole("admin") || context.hasRole("content-manager")) {
            html = "<a class=\"button tiny radius primary\" href=\"" + context.getContextPath() + "/content-editor?uniqueId=" + contentUniqueId + "&returnPage=" + context.getUri() + "\"><i class=\"" + FontCommand.fas() + " fa-edit\"></i> Add Content Here</a>";
          }
        }
        if (StringUtils.isBlank(html)) {
          continue;
        }
        context.getRequest().setAttribute(cardName, html);
      }
      // The full cart is being shown
      context.setJsp(JSP);
    }
    return context;
  }

  /**
   * The user modified the quantity, or an item has changed price or availability,
   * or a promo code has been applied, and the cart needs updating
   *
   * @param context
   * @return
   */
  public WidgetContext post(WidgetContext context) {

    // Verify the user has a cart
    Cart sessionCart = context.getUserSession().getCart();
    if (sessionCart == null || sessionCart.getTotalItems() == 0) {
      return context;
    }

    // Load a copy from the repository to make changes
    Cart cart = LoadCartCommand.loadCartByToken(sessionCart.getToken());
    if (cart == null || cart.getTotalItems() == 0) {
      return context;
    }

    // Action: Determine if the product quantities on the form changed, or if an old product is being removed
    // Load the complete cart items, determine the changes and running total
    BigDecimal runningTotal = new BigDecimal(0);
    int totalItems = 0;
    BigDecimal totalQuantity = new BigDecimal(0);
    List<CartItem> cartItemList = CartItemRepository.findValidItemsByCartId(cart.getId());
    for (CartItem cartItem : cartItemList) {
      // Prepare changes to the cart item
      ProductSku productSku = ProductSkuRepository.findById(cartItem.getSkuId());
      if (productSku == null || !productSku.getEnabled() || productSku.getPrice() == null) {
        // Product is no longer available
        cartItem.setRemoved(true);
      } else {
        // There's an item
        totalItems += 1;
        // Set the quantity, each amount, and total amount
        int newQuantity = context.getParameterAsInt("item-" + cartItem.getId() + "-quantity");
        if (newQuantity <= 0) {
          LOG.error("The new quantity cannot be 0 or less");
          context.setErrorMessage("An error occurred");
          return context;
        }
        cartItem.setQuantity(new BigDecimal(newQuantity));
        cartItem.setEachAmount(productSku.getPrice());
        cartItem.setTotalAmount(productSku.getPrice().multiply(cartItem.getQuantity()));
        totalQuantity = totalQuantity.add(cartItem.getQuantity());
        runningTotal = runningTotal.add(cartItem.getTotalAmount());
      }
    }
    cart.setTotalItems(totalItems);
    cart.setTotalQty(totalQuantity);
    cart.setSubtotalAmount(runningTotal);

    // Reset all the taxes and stuff
    CartCommand.resetShippingAndTaxes(cart);

    // Get the cart items correct; update the cart items and the cart summary itself
    if (CartRepository.updateCart(cart, cartItemList)) {
      // Update the user's session with the modified cart
      context.getUserSession().setCart(cart);
    }

    // Action: Determine if user is adding/removing a promo code
    String promoCode = context.getParameter("promoCode");
    if (promoCode != null) {
      // The user made a change
      LOG.debug("Applying promo code...");
      CartCommand.updatePromoCode(cart, promoCode);
    } else {
      // The user didn't make a change, but need to update the promo and discount
      LOG.debug("Updating discount...");
      CartCommand.updateDiscount(cart);
    }

    // Determine where to go next
    String button = context.getParameter("button");
    if ("checkout".equalsIgnoreCase(button)) {
      context.setRedirect("/checkout");
    }

    return context;
  }

  /**
   * An action was performed on the cart: item was removed from the cart
   *
   * @param context
   * @return
   */
  public WidgetContext action(WidgetContext context) {
    // Check for a cart to work on
    Cart cart = context.getUserSession().getCart();
    if (cart == null || cart.getTotalItems() == 0) {
      return context;
    }

    // Execute the action
    String action = context.getParameter("action");
    if ("removeItem".equals(action)) {
      return removeItem(context, cart);
    }
    return context;
  }

  private WidgetContext removeItem(WidgetContext context, Cart cart) {

    // Determine a valid cart item
    long itemId = context.getParameterAsLong("itemId");
    CartItem cartItem = LoadCartItemCommand.loadCartItemById(itemId);
    if (cartItem == null || cartItem.getCartId() != cart.getId()) {
      return context;
    }

    // Remove the user's item from the cart
    if (CartCommand.removeProductFromCart(cart, cartItem)) {
      context.setSuccessMessage("Item was removed");
    }
    return context;
  }

}
