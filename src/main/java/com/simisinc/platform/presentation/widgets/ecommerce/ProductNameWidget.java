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

import com.simisinc.platform.application.ecommerce.LoadProductCommand;
import com.simisinc.platform.application.ecommerce.ProductPriceCommand;
import com.simisinc.platform.domain.model.ecommerce.Product;
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

    if (showPrice) {
      // Determine the price to show, or range of prices to show...
      ProductPriceCommand.configurePriceAndStartingPrice(product);
    }
    context.setJsp(JSP);
    return context;
  }
}
