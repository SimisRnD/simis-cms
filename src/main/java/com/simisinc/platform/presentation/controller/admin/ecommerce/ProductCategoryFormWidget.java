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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.ecommerce.ProductException;
import com.simisinc.platform.application.ecommerce.SaveProductCategoryCommand;
import com.simisinc.platform.domain.model.ecommerce.ProductCategory;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductCategoryRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/10/21 5:10 PM
 */
public class ProductCategoryFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/product-category-form.jsp";

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
    ProductCategory productCategory = null;
    if (context.getRequestObject() != null) {
      productCategory = (ProductCategory) context.getRequestObject();
      context.getRequest().setAttribute("productCategory", productCategory);
    } else {
      long productCategoryId = context.getParameterAsLong("productCategoryId");
      if (productCategoryId > -1) {
        productCategory = ProductCategoryRepository.findById(productCategoryId);
        context.getRequest().setAttribute("productCategory", productCategory);
      }
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields; using nested properties
    ProductCategory productCategoryBean = new ProductCategory();
    BeanUtils.populate(productCategoryBean, context.getParameterMap());
    if (StringUtils.isNotBlank(productCategoryBean.getUniqueId())) {
      // Reformat it
      productCategoryBean.setUniqueId(productCategoryBean.getUniqueId().trim().toLowerCase());
    }
    productCategoryBean.setCreatedBy(context.getUserId());
    productCategoryBean.setModifiedBy(context.getUserId());

    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));

    // Save the record
    ProductCategory productCategory = null;
    try {
      productCategory = SaveProductCategoryCommand.save(productCategoryBean);
      if (productCategory == null) {
        throw new ProductException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | ProductException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(productCategoryBean);
      context.addSharedRequestValue("returnPage", returnPage);
      if (productCategoryBean.getId() > -1) {
        context.setRedirect("/admin/product-category?rateId=" + productCategoryBean.getId() + "&returnPage=/admin/product-categories");
      } else {
        context.setRedirect("/admin/product-category?returnPage=/admin/product-categories");
      }
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Product category was saved");
    if (StringUtils.isNotBlank(returnPage)) {
      context.setRedirect(returnPage);
    } else {
      context.setRedirect("/admin/product-categories");
    }
    return context;
  }
}
