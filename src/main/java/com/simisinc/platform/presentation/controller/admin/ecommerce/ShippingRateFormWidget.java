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
import com.simisinc.platform.application.ecommerce.SaveShippingRateCommand;
import com.simisinc.platform.domain.model.ecommerce.ShippingCountry;
import com.simisinc.platform.domain.model.ecommerce.ShippingMethod;
import com.simisinc.platform.domain.model.ecommerce.ShippingRate;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingCountryRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingMethodRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingRateRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 6/26/19 9:49 PM
 */
public class ShippingRateFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/shipping-rate-form.jsp";

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
    ShippingRate shippingRate = null;
    if (context.getRequestObject() != null) {
      shippingRate = (ShippingRate) context.getRequestObject();
      context.getRequest().setAttribute("shippingRate", shippingRate);
    } else {
      long shippingRateId = context.getParameterAsLong("shippingRateId");
      if (shippingRateId > -1) {
        shippingRate = ShippingRateRepository.findById(shippingRateId);
        context.getRequest().setAttribute("shippingRate", shippingRate);
      }
    }

    // Display the available shipping countries
    List<ShippingCountry> shippingCountryList = ShippingCountryRepository.findAll();
    context.getRequest().setAttribute("shippingCountryList", shippingCountryList);

    // Display the shipping method lookup names
    List<ShippingMethod> shippingMethodList = ShippingMethodRepository.findAll();
    context.getRequest().setAttribute("shippingMethodList", shippingMethodList);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields; using nested properties
    ShippingRate shippingRateBean = new ShippingRate();
    BeanUtils.populate(shippingRateBean, context.getParameterMap());
//    shippingRateBean.setCreatedBy(context.getUserId());
//    shippingRateBean.setModifiedBy(context.getUserId());

    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));

    // Save the record
    ShippingRate shippingRate = null;
    try {
      shippingRate = SaveShippingRateCommand.saveShippingRate(shippingRateBean);
      if (shippingRate == null) {
        throw new ProductException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | ProductException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(shippingRateBean);
      context.addSharedRequestValue("returnPage", returnPage);
      if (shippingRateBean.getId() > -1) {
        context.setRedirect("/admin/shipping-rate?rateId=" + shippingRateBean.getId() + "&returnPage=/admin/shipping-rates");
      } else {
        context.setRedirect("/admin/shipping-rate?returnPage=/admin/shipping-rates");
      }
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Shipping rate was saved");
    if (StringUtils.isNotBlank(returnPage)) {
      context.setRedirect(returnPage);
    } else {
      context.setRedirect("/admin/shipping-rates");
    }
    return context;
  }
}
