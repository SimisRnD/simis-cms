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

import com.simisinc.platform.domain.model.ecommerce.ShippingMethod;
import com.simisinc.platform.domain.model.ecommerce.ShippingRate;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingMethodRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ShippingRateRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/2/19 6:32 AM
 */
public class ShippingRatesListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/shipping-rates-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Load the shipping rates
    List<ShippingRate> shippingRateList = ShippingRateRepository.findAll(null, null);
    context.getRequest().setAttribute("shippingRateList", shippingRateList);

    // Display the shipping method lookup names
    List<ShippingMethod> shippingMethodList = ShippingMethodRepository.findAll();
    context.getRequest().setAttribute("shippingMethodList", shippingMethodList);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {

    // Check for shipping rate to be deleted
    long shippingRateId = context.getParameterAsLong("shippingRateId", -1);
    ShippingRate record = null;
    if (context.hasRole("admin")) {
      record = ShippingRateRepository.findById(shippingRateId);
    }
    if (record == null) {
      LOG.warn("Shipping rate record does not exist or no access: " + shippingRateId);
      context.setErrorMessage("Error. No access to remove shipping rate.");
      return null;
    }

    try {
      if (ShippingRateRepository.remove(record)) {
        context.setSuccessMessage("Shipping rate deleted");
      }
      return context;
    } catch (Exception e) {
      context.setErrorMessage("Error. Shipping rate could not be deleted.");
      // context.setRedirect("/admin/shipping-rates");
    }

    return context;
  }
}
