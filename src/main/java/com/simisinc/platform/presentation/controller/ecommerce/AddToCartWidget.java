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

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.ecommerce.CartCommand;
import com.simisinc.platform.application.ecommerce.LoadProductCommand;
import com.simisinc.platform.application.ecommerce.ProductInventoryCommand;
import com.simisinc.platform.domain.model.ecommerce.Product;
import com.simisinc.platform.domain.model.ecommerce.ProductSku;
import com.simisinc.platform.domain.model.ecommerce.ProductSkuAttribute;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductSkuRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductSkuSpecification;
import com.simisinc.platform.presentation.controller.CookieConstants;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/9/19 8:53 PM
 */
public class AddToCartWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String ADD_PRODUCT_JSP = "/ecommerce/add-product-to-cart.jsp";
  static String ADD_PRODUCT_SKU_JSP = "/ecommerce/add-product-sku-to-cart.jsp";
  static String COMING_SOON_JSP = "/ecommerce/product-coming-soon.jsp";
  static String SOLD_OUT_JSP = "/ecommerce/product-sold-out.jsp";
  static String MORE_ON_THE_WAY_JSP = "/ecommerce/product-more-on-the-way.jsp";
  static String UNAVAILABLE_JSP = "/ecommerce/product-unavailable.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Preferences
    String uniqueId = context.getPreferences().get("product");
    String sku = context.getPreferences().get("sku");
    boolean checkInventory = "true".equals(context.getPreferences().getOrDefault("checkInventory", "true"));
    context.getRequest().setAttribute("buttonName", context.getPreferences().getOrDefault("buttonName", "Add to Cart"));
    context.getRequest().setAttribute("showPrice", context.getPreferences().getOrDefault("showPrice", "false"));
    context.getRequest().setAttribute("soldOutText", context.getPreferences().getOrDefault("soldOutText", "Unavailable"));
    context.getRequest().setAttribute("unavailableText", context.getPreferences().getOrDefault("unavailableText", "Unavailable"));
    context.getRequest().setAttribute("comingSoonText", context.getPreferences().getOrDefault("comingSoonText", "Coming Soon"));
    context.getRequest().setAttribute("moreOnTheWayText", context.getPreferences().getOrDefault("moreOnTheWayText", "More On The Way"));
    context.getRequest().setAttribute("noLongerAvailableText", context.getPreferences().getOrDefault("noLongerAvailableText", "Not Available"));

    // Check the required preferences (a product unique id is required)
    if (StringUtils.isBlank(uniqueId)) {
      LOG.error("Product unique id must be specified with optional sku: " + uniqueId + "/" + sku);
      context.setJsp(UNAVAILABLE_JSP);
      return context;
    }

    // Load the product
    Product product = LoadProductCommand.loadProductByUniqueId(uniqueId);
    if (product == null) {
      LOG.warn("Product was not found for: " + uniqueId + " / " + sku);
      context.setJsp(UNAVAILABLE_JSP);
      return context;
    }

    // Load the enabled SKUs
    ProductSkuSpecification productSkuSpecification = new ProductSkuSpecification();
    productSkuSpecification.setShowOnline(true);
    productSkuSpecification.setProductUniqueId(uniqueId);
    if (StringUtils.isNotBlank(sku)) {
      productSkuSpecification.setSku(sku);
    }
    List<ProductSku> productSkuList = ProductSkuRepository.findAll(productSkuSpecification, null);

    // No products
    if (productSkuList == null || productSkuList.isEmpty()) {
      LOG.warn("Product SKU list was not found for: " + uniqueId + " / " + sku);
      context.setJsp(UNAVAILABLE_JSP);
      return context;
    }

    // A single product SKU, ready to be added to the cart
    if (productSkuList.size() == 1) {
      // The page is showing a single Product SKU
      ProductSku productSku = productSkuList.get(0);
      context.getRequest().setAttribute("product", product);
      context.getRequest().setAttribute("productSku", productSku);
    }

    if (!"true".equals(LoadSitePropertyCommand.loadByName("site.cart"))) {
      context.setJsp(COMING_SOON_JSP);
      return context;
    }

    // Check if All SKUs are: Coming Soon, Unavailable, Sold Out, On the Way; then show the message
    int allStatus = ProductSku.STATUS_UNDEFINED;
    for (ProductSku productSku : productSkuList) {
      if (allStatus == ProductSku.STATUS_UNDEFINED) {
        allStatus = productSku.getStatus();
      } else {
        if (allStatus != productSku.getStatus()) {
          allStatus = ProductSku.STATUS_UNDEFINED;
          break;
        }
      }
    }
    if (allStatus == ProductSku.STATUS_COMING_SOON) {
      context.setJsp(COMING_SOON_JSP);
      return context;
    } else if (allStatus == ProductSku.STATUS_UNAVAILABLE) {
      context.setJsp(UNAVAILABLE_JSP);
      return context;
    } else if (allStatus == ProductSku.STATUS_MORE_ON_THE_WAY) {
      context.setJsp(MORE_ON_THE_WAY_JSP);
      return context;
    } else if (allStatus == ProductSku.STATUS_SOLD_OUT) {
      context.setJsp(SOLD_OUT_JSP);
      return context;
    } else if (productSkuList.size() == 1) {
      // Default to the Add Product SKU page
      context.setJsp(ADD_PRODUCT_SKU_JSP);
      return context;
    }

    // Multiple SKUs, so a choice of attributes is required
    context.getRequest().setAttribute("product", product);
    context.getRequest().setAttribute("productAttributeList", product.getAttributes());
    context.getRequest().setAttribute("productSkuList", productSkuList);

    // Get the unique attribute names
    List<ProductSkuAttribute> attributeList = new ArrayList<>();
    Map<String, List<String>> attributeMap = new LinkedHashMap<>();
    for (ProductSkuAttribute attribute : product.getAttributes()) {
      if (attributeList.stream().map(ProductSkuAttribute::getName).noneMatch(attribute.getName()::equals)) {
        attributeList.add(attribute);
        // Now append the valid SKU attribute values
        List<String> attributeValueList = new ArrayList<>();
        attributeMap.put(attribute.getName(), attributeValueList);
        // Now go through the SKUs and keep track of the valid attributes
        for (ProductSku productSku : productSkuList) {
          for (ProductSkuAttribute productSkuAttribute : productSku.getAttributes()) {
            if (productSkuAttribute.getName().equals(attribute.getName())) {
              if (!attributeValueList.contains(productSkuAttribute.getValue())) {
                attributeValueList.add(productSkuAttribute.getValue());
              }
            }
          }
        }
      }
    }
    context.getRequest().setAttribute("attributeList", attributeList);
    context.getRequest().setAttribute("attributeMap", attributeMap);


    // For each SKU, add the attribute values if not already matching

    // Product Attribute Name
    // - Unique list of values, price
    //

    // When adding to cart, all radios must be selected,
    // there must be only one SKU matching all radios


    context.setJsp(ADD_PRODUCT_JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    // Preferences
    String uniqueId = context.getPreferences().get("product");
    String sku = context.getPreferences().get("sku");
    boolean checkInventory = "true".equals(context.getPreferences().getOrDefault("checkInventory", "true"));

    // From the request, determine the product options and quantity
    BigDecimal quantity = new BigDecimal(context.getParameterAsInt("quantity"));

    // Check the required preferences (a product unique id is required)
    if (StringUtils.isBlank(uniqueId)) {
      LOG.error("Product unique id must be specified with optional sku: " + uniqueId + "/" + sku);
      context.setErrorMessage("An error occurred, product was not found");
      return context;
    }

    // Load the product
    Product product = LoadProductCommand.loadProductByUniqueId(uniqueId);
    if (product == null) {
      LOG.warn("Product was not found for: " + uniqueId + " / " + sku);
      context.setErrorMessage("An error occurred, product was not found");
      return context;
    }

    // Based on the options, validate the decision, determine the SKU
    int index = -1;
    ArrayList<ProductSkuAttribute> productSkuAttributesList = new ArrayList<>();
    for (ProductSkuAttribute attribute : product.getAttributes()) {
      ++index;
      String attributeValue = context.getParameter("attribute" + index);
      if (StringUtils.isBlank(attributeValue)) {
        // Not all options are required (like when only 1 option is displayed)
        continue;
      }
      LOG.debug("Attribute: " + attribute.getName() + "/" + attribute.getValue() + "=" + attributeValue);
      ProductSkuAttribute selectedAttribute = new ProductSkuAttribute();
      selectedAttribute.setName(attribute.getName());
      selectedAttribute.setValue(attributeValue);
      productSkuAttributesList.add(selectedAttribute);
    }

    // Find the enabled SKU with matching options
    ProductSkuSpecification productSkuSpecification = new ProductSkuSpecification();
    productSkuSpecification.setShowOnline(true);
    productSkuSpecification.setProductUniqueId(uniqueId);
    if (StringUtils.isNotBlank(sku)) {
      productSkuSpecification.setSku(sku);
    }
    if (!productSkuAttributesList.isEmpty()) {
      productSkuSpecification.setWithProductSkuAttributeList(productSkuAttributesList);
    }
    List<ProductSku> productSkuList = ProductSkuRepository.findAll(productSkuSpecification, null);

    // Determine the SKU
    if (productSkuList.size() != 1) {
      // Could not determine the product to add
      LOG.warn("Product SKU list (" + productSkuList.size() + ") was not found for: " + uniqueId + " / " + sku);
      context.setErrorMessage("Please select from all options to add this product to the cart");
      return context;
    }

    // A single product sku was found...
    ProductSku productSku = productSkuList.get(0);
    LOG.debug("Adding product sku: " + productSku.getSku() + "; quantity available: " + productSku.getInventoryQty());

    // @todo handle preorder, backorder

    // Check if the product is available
    if (checkInventory && !ProductInventoryCommand.isAvailable(productSku, quantity)) {
      if (ProductInventoryCommand.hasMoreOnTheWay(productSku)) {
        // Add to cart
        context.setWarningMessage("This item will ship when it becomes available");
      } else {
        // Don't add to cart
        context.setWarningMessage("This item is out of stock");
        return context;
      }
    }

    // Add the sku and quantity to the cart
    if (!CartCommand.addSkuToCart(context.getUserSession(), product, productSku, quantity)) {
      context.setErrorMessage("The item could not be added due to a system error");
      return context;
    }

    // Set the cart's browser cookie
    int twoWeeksSecondsInt = 14 * 24 * 60 * 60;
    Cookie cookie = new Cookie(CookieConstants.CART_TOKEN, context.getUserSession().getCart().getToken());
    if (context.isSecure()) {
      cookie.setSecure(true);
    }
    cookie.setHttpOnly(true);
    cookie.setPath("/");
    cookie.setMaxAge(twoWeeksSecondsInt);
    context.getResponse().addCookie(cookie);

    // Redirect
    String redirectTo = context.getPreferences().get("redirectTo");
    if (redirectTo != null) {
      context.setRedirect(redirectTo);
    }
    return context;
  }
}
