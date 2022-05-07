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

import java.util.List;

import com.simisinc.platform.domain.model.ecommerce.Customer;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.ecommerce.CustomerRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.CustomerSpecification;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

import org.apache.commons.lang3.StringUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/17/19 7:23 PM
 */
public class CustomerListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/customer-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "20"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Determine criteria
    CustomerSpecification specification = new CustomerSpecification();

    String customerNumber = context.getParameter("searchCustomerNumber");
    if (StringUtils.isNotBlank(customerNumber)) {
      specification.setUniqueId(customerNumber);
    }
    String orderNumber = context.getParameter("searchOrderNumber");
    if (StringUtils.isNotBlank(orderNumber)) {
      specification.setOrderNumber(orderNumber);
    }
    String email = context.getParameter("searchEmail");
    if (StringUtils.isNotBlank(email)) {
      specification.setEmail(email);
    }
    String phoneNumber = context.getParameter("searchPhoneNumber");
    if (StringUtils.isNotBlank(phoneNumber)) {
      specification.setPhoneNumber(phoneNumber);
    }
    String name = context.getParameter("searchName");
    if (StringUtils.isNotBlank(name)) {
      specification.setName(name);
    }

    // Load the latest orders
    List<Customer> customerList = CustomerRepository.findAll(specification, constraints);
    context.getRequest().setAttribute("customerList", customerList);

    // Show the list
    context.setJsp(JSP);
    return context;
  }
}
