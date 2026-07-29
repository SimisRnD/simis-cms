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

package com.simisinc.platform.presentation.widgets.ecommerce;

import java.util.List;

import com.simisinc.platform.application.ecommerce.LoadProductCommand;
import com.simisinc.platform.application.ecommerce.ProductPriceCommand;
import com.simisinc.platform.domain.model.ecommerce.Product;
import com.simisinc.platform.domain.model.ecommerce.ProductSku;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

/**
 * Provides product information
 *
 * @author matt rajkowski
 * @created 8/9/19 3:54 PM
 */
public class ProductNameWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/ecommerce/product-name.jsp";
  static String ERROR_JSP = "/ecommerce/product-name-error.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("combineCaption", context.getPreferences().getOrDefault("combineCaption", "false"));

    // Preferences
    String uniqueId = context.getPreferences().get("product");
    boolean showPrice = "true".equals(context.getPreferences().getOrDefault("showPrice", "false"));
    context.getRequest().setAttribute("showPrice", showPrice ? "true" : "false");

    // Check the required preferences (a product unique id is required)
    if (StringUtils.isBlank(uniqueId)) {
      LOG.error("Product unique id must be specified with optional sku: " + uniqueId);
      context.setJsp(ERROR_JSP);
      return context;
    }

    // Load the product
    Product product = LoadProductCommand.loadProductByUniqueId(uniqueId);
    if (product == null) {
      LOG.warn("Product was not found for: " + uniqueId);
      context.setJsp(ERROR_JSP);
      return context;
    }
    context.getRequest().setAttribute("product", product);

    // Determine the price to show, or range of prices to show... computed unconditionally
    // (not just when showPrice is true) because the Product schema fields below need it
    // regardless of whether this particular widget instance displays the price on the page
    ProductPriceCommand.configurePriceAndStartingPrice(product);
    context.setJsp(JSP);

    setProductSchemaFields(context, product);
    return context;
  }

  /**
   * Bridges this product's data into WidgetContext for the Product JSON-LD schema (issue #403).
   * There's no URL routing to a specific product the way there is for a blog post or an
   * Item/Collection, so this widget -- wherever a real ecommerce product page happens to place
   * it -- is the only place this data is available at all.
   */
  static void setProductSchemaFields(WidgetContext context, Product product) {
    context.setProductName(product.getName());
    if (StringUtils.isNotBlank(product.getDescription())) {
      context.setProductDescription(product.getDescription());
    }
    if (StringUtils.isNotBlank(product.getImageUrl())) {
      context.setProductImageUrl(product.getImageUrl());
    }
    if (product.getPrice() != null) {
      context.setProductPrice(product.getPrice());
    } else if (product.getStartingFromPrice() != null) {
      context.setProductLowPrice(product.getStartingFromPrice());
    }
    if (product.getSkuCount() > 0) {
      context.setProductOfferCount(product.getSkuCount());
    }

    List<ProductSku> productSkuList = product.getNativeProductSKUs();
    context.setProductCurrency(determineCurrency(productSkuList));
    String availability = determineAvailability(productSkuList);
    if (availability != null) {
      context.setProductAvailability(availability);
    }
  }

  /**
   * ProductSku.currency is a real DB column but is not currently set anywhere in the admin UI or
   * Square sync -- every price display in this codebase hardcodes USD instead of reading it. This
   * prefers it when a SKU happens to have it set, and falls back to USD to match that existing
   * behavior rather than introducing a third, different default.
   */
  static String determineCurrency(List<ProductSku> productSkuList) {
    if (productSkuList != null) {
      for (ProductSku productSku : productSkuList) {
        if (StringUtils.isNotBlank(productSku.getCurrency())) {
          return productSku.getCurrency();
        }
      }
    }
    return "USD";
  }

  /**
   * Maps this product's enabled SKUs to a single schema.org availability URL. A product with
   * multiple SKUs (size/color variants) is considered available if ANY enabled variant can
   * currently be bought -- a customer landing on the page can still complete a purchase, even if
   * one specific variant is sold out. Precedence (most to least purchasable): a SKU actually in
   * stock, then one with more on the way (backorderable now), then one not yet released, then
   * out of stock/disabled if nothing else qualifies.
   */
  static String determineAvailability(List<ProductSku> productSkuList) {
    if (productSkuList == null || productSkuList.isEmpty()) {
      return null;
    }
    boolean hasAvailable = false;
    boolean hasMoreOnTheWay = false;
    boolean hasComingSoon = false;
    for (ProductSku productSku : productSkuList) {
      if (!productSku.getEnabled()) {
        continue;
      }
      if (productSku.getStatus() == ProductSku.STATUS_AVAILABLE) {
        hasAvailable = true;
      } else if (productSku.getStatus() == ProductSku.STATUS_MORE_ON_THE_WAY) {
        hasMoreOnTheWay = true;
      } else if (productSku.getStatus() == ProductSku.STATUS_COMING_SOON) {
        hasComingSoon = true;
      }
    }
    if (hasAvailable) {
      return "https://schema.org/InStock";
    }
    if (hasMoreOnTheWay) {
      return "https://schema.org/BackOrder";
    }
    if (hasComingSoon) {
      return "https://schema.org/PreOrder";
    }
    return "https://schema.org/OutOfStock";
  }
}
