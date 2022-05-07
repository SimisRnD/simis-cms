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

package com.simisinc.platform.presentation.widgets.admin.ecommerce;

import com.simisinc.platform.domain.model.ecommerce.ProductCategory;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductCategoryRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/10/21 5:10 PM
 */
public class ProductCategoriesListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/product-categories-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Load the product categories
    List<ProductCategory> productCategoryList = ProductCategoryRepository.findAll();
    context.getRequest().setAttribute("productCategoryList", productCategoryList);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {

    // Check for product category to be deleted
    long productCategoryId = context.getParameterAsLong("productCategoryId", -1);
    ProductCategory record = null;
    if (context.hasRole("admin")) {
      record = ProductCategoryRepository.findById(productCategoryId);
    }
    if (record == null) {
      LOG.warn("Product Category record does not exist or no access: " + productCategoryId);
      context.setErrorMessage("Error. No access to remove product category.");
      return null;
    }

    try {
      if (ProductCategoryRepository.remove(record)) {
        context.setSuccessMessage("Product Category deleted");
      }
      return context;
    } catch (Exception e) {
      context.setErrorMessage("Error. Product Category could not be deleted.");
      // context.setRedirect("/admin/product-categories");
    }

    return context;
  }
}
