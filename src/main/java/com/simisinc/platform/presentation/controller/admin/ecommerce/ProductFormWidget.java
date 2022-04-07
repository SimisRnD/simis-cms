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

package com.simisinc.platform.presentation.controller.admin.ecommerce;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.ecommerce.LoadProductCommand;
import com.simisinc.platform.application.ecommerce.ProductException;
import com.simisinc.platform.application.ecommerce.SaveProductCommand;
import com.simisinc.platform.domain.model.ecommerce.FulfillmentOption;
import com.simisinc.platform.domain.model.ecommerce.Product;
import com.simisinc.platform.infrastructure.persistence.ecommerce.FulfillmentOptionRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/18/19 4:33 PM
 */
public class ProductFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/product-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // This page can return to different places
    String returnPage = context.getSharedRequestValue("returnPage");
    if (returnPage == null) {
      returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    }
    context.getRequest().setAttribute("returnPage", returnPage);

    // Form bean
    Product product = null;
    if (context.getRequestObject() != null) {
      product = (Product) context.getRequestObject();
      context.getRequest().setAttribute("product", product);
    } else {
      long productId = context.getParameterAsLong("productId");
      if (productId > -1) {
        product = LoadProductCommand.loadProductById(productId);
        // Increase the product SKUs to show
        context.getRequest().setAttribute("product", product);
      }
    }

    List<FulfillmentOption> fulfillmentOptionList = FulfillmentOptionRepository.findAll();
    context.getRequest().setAttribute("fulfillmentOptionList", fulfillmentOptionList);

    // Show the form
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields; using nested properties
    Product productBean = new Product();
    BeanUtils.populate(productBean, context.getParameterMap());
    if (StringUtils.isNotBlank(productBean.getUniqueId())) {
      // Reformat it
      productBean.setUniqueId(productBean.getUniqueId().trim().toLowerCase());
    }
    productBean.setCreatedBy(context.getUserId());
    productBean.setModifiedBy(context.getUserId());

    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));

    // Save the record
    Product product = null;
    try {
      product = SaveProductCommand.saveProduct(productBean);
      if (product == null) {
        throw new ProductException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | ProductException e) {
      LOG.error(e);
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(productBean);
      context.addSharedRequestValue("returnPage", returnPage);
      if (productBean.getId() > -1) {
        context.setRedirect("/admin/product?productId=" + productBean.getId() + "&returnPage=/admin/products");
      } else {
        context.setRedirect("/admin/product?returnPage=/admin/products");
      }
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Product was saved");
    if (StringUtils.isNotBlank(returnPage)) {
      context.setRedirect(returnPage);
    } else {
      context.setRedirect("/admin/products");
    }
    return context;
  }
}
