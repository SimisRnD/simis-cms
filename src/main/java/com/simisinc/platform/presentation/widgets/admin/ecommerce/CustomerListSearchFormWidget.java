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

import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

import org.apache.commons.lang3.StringUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/26/19 8:38 AM
 */
public class CustomerListSearchFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/customer-list-search-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Search values
    context.getRequest().setAttribute("searchCustomerNumber", context.getRequest().getParameter("searchCustomerNumber"));
    context.getRequest().setAttribute("searchOrderNumber", context.getRequest().getParameter("searchOrderNumber"));
    context.getRequest().setAttribute("searchEmail", context.getRequest().getParameter("searchEmail"));
    context.getRequest().setAttribute("searchPhone", context.getRequest().getParameter("searchPhone"));
    context.getRequest().setAttribute("searchName", context.getRequest().getParameter("searchName"));

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    // Set search values
    StringBuilder sb = new StringBuilder();
    addSearchValue(context, sb, "customerNumber", "searchCustomerNumber");
    addSearchValue(context, sb, "orderNumber", "searchOrderNumber");
    addSearchValue(context, sb, "email", "searchEmail");
    addSearchValue(context, sb, "phone", "searchPhone");
    addSearchValue(context, sb, "name", "searchName");

    // Determine the next page to go to
    String redirectTo = context.getPreferences().get("redirectTo");
    if (redirectTo != null) {
      if (sb.length() > 0) {
        redirectTo += sb.toString();
      }
      context.setRedirect(redirectTo);
    }
    return context;
  }

  private static void addSearchValue(WidgetContext context, StringBuilder sb, String name, String setAs) {
    String value = context.getParameter(name);
    if (StringUtils.isNotBlank(value)) {
      value = value.trim();
      // Share the value with other widgets on this page
      context.addSharedRequestValue(setAs, value);
      // Update the redirect URL
      if (sb.length() == 0) {
        sb.append("?");
      } else {
        sb.append("&");
      }
      sb.append(setAs);
      sb.append("=");
      sb.append(UrlCommand.encodeUri(value));
    }
  }
}
