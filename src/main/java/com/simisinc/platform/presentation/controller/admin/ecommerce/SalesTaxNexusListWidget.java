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

import java.util.List;

import com.simisinc.platform.domain.model.ecommerce.SalesTaxNexusAddress;
import com.simisinc.platform.infrastructure.persistence.ecommerce.SalesTaxNexusAddressRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/29/19 12:22 PM
 */
public class SalesTaxNexusListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/sales-tax-nexus-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Load the sales tax nexus list
    List<SalesTaxNexusAddress> salesTaxNexusAddressList = SalesTaxNexusAddressRepository.findAll();
    context.getRequest().setAttribute("salesTaxNexusAddressList", salesTaxNexusAddressList);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {
    // Determine what's being deleted
    long addressId = context.getParameterAsLong("addressId");
    if (addressId > -1) {
      SalesTaxNexusAddress address = SalesTaxNexusAddressRepository.findById(addressId);
      if (address == null) {
        context.setErrorMessage("Address not found");
      } else {
        if (SalesTaxNexusAddressRepository.remove(address)) {
          context.setSuccessMessage("Address deleted");
        } else {
          context.setWarningMessage("The address could not be deleted");
        }
      }
    }
    context.setRedirect("/admin/sales-tax-nexus");
    return context;
  }
}
