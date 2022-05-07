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
import com.simisinc.platform.domain.model.ecommerce.Product;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

/**
 * Provides product information
 *
 * @author matt rajkowski
 * @created 3/14/21 2:36 PM
 */
public class ProductImageWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/ecommerce/product-image.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Preferences
    String uniqueId = context.getPreferences().get("product");

    // Check the required preferences (a product unique id is required)
    if (StringUtils.isBlank(uniqueId)) {
      LOG.debug("Product unique id must be specified");
      return context;
    }

    // Load the product
    Product product = LoadProductCommand.loadProductByUniqueId(uniqueId);
    if (product == null || StringUtils.isBlank(product.getImageUrl())) {
      LOG.warn("Product image was not found for: " + uniqueId);
      return context;
    }
    context.getRequest().setAttribute("product", product);

    context.setJsp(JSP);
    return context;
  }
}
