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

package com.simisinc.platform.presentation.widgets.userProfile;

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderSpecification;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 6/10/19 7:35 PM
 */
public class MyOrderHistoryWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/userProfile/my-order-history.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    boolean showWhenEmpty = "true".equals(context.getPreferences().getOrDefault("showWhenEmpty", "true"));

    // This widget is for a logged in user
    if (!context.getUserSession().isLoggedIn()) {
      return null;
    }
    User user = context.getUserSession().getUser();

    // Retrieve the user's orders
    OrderSpecification orderSpecification = new OrderSpecification();
    orderSpecification.setCreatedBy(user.getId());
    if (!user.hasRole("admin")) {
      orderSpecification.setShowSandbox(false);
    }
    orderSpecification.setShowIncompleteOrders(false);
    List<Order> orderList = OrderRepository.findAll(orderSpecification, null);
    context.getRequest().setAttribute("orderList", orderList);

    // Determine if the widget is shown
    if (!showWhenEmpty && (orderList == null || orderList.isEmpty())) {
      return context;
    }

    context.setJsp(JSP);
    return context;
  }
}
