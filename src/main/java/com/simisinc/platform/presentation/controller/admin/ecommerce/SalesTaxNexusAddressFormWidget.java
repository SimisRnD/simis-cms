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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.ecommerce.ProductException;
import com.simisinc.platform.application.ecommerce.SaveSalesTaxNexusAddressCommand;
import com.simisinc.platform.domain.model.ecommerce.SalesTaxNexusAddress;
import com.simisinc.platform.infrastructure.persistence.ecommerce.SalesTaxNexusAddressRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/29/19 1:48 PM
 */
public class SalesTaxNexusAddressFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/sales-tax-nexus-address-form.jsp";

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
    SalesTaxNexusAddress address = null;
    if (context.getRequestObject() != null) {
      address = (SalesTaxNexusAddress) context.getRequestObject();
      context.getRequest().setAttribute("address", address);
    } else {
      long addressId = context.getParameterAsLong("addressId");
      if (addressId > -1) {
        address = SalesTaxNexusAddressRepository.findById(addressId);
        context.getRequest().setAttribute("address", address);
      }
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields; using nested properties
    SalesTaxNexusAddress salesTaxNexusAddressBean = new SalesTaxNexusAddress();
    BeanUtils.populate(salesTaxNexusAddressBean, context.getParameterMap());
    salesTaxNexusAddressBean.setCreatedBy(context.getUserId());
    salesTaxNexusAddressBean.setModifiedBy(context.getUserId());

    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));

    // Save the record
    SalesTaxNexusAddress address = null;
    try {
      address = SaveSalesTaxNexusAddressCommand.saveAddress(salesTaxNexusAddressBean);
      if (address == null) {
        throw new ProductException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | ProductException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(salesTaxNexusAddressBean);
      context.addSharedRequestValue("returnPage", returnPage);
      if (salesTaxNexusAddressBean.getId() > -1) {
        context.setRedirect("/admin/sales-tax-nexus-address?addressId=" + salesTaxNexusAddressBean.getId() + "&returnPage=/admin/sales-tax-nexus");
      } else {
        context.setRedirect("/admin/sales-tax-nexus-address?returnPage=/admin/sales-tax-nexus");
      }
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Address was saved");
    if (StringUtils.isNotBlank(returnPage)) {
      context.setRedirect(returnPage);
    } else {
      context.setRedirect("/admin/sales-tax-nexus");
    }
    return context;
  }
}
