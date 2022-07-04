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

package com.simisinc.platform.presentation.widgets.dashboard;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Renders a Superset dashboard using the superset embedded sdk
 *
 * @author matt rajkowski
 * @created 7/1/22 1:18 PM
 */
public class SupersetWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public static String JSP = "/dashboard/superset-embedded.jsp";

  public WidgetContext execute(WidgetContext context) {
    // guestToken (via AJAX request)
    String dashboardValue = context.getPreferences().get("dashboardValue");
    String dashboardEmbeddedId = context.getPreferences().get("dashboardEmbeddedId");
    String supersetDomain = LoadSitePropertyCommand.loadByName("bi.superset.url");

    context.getRequest().setAttribute("dashboardValue", dashboardValue);
    context.getRequest().setAttribute("dashboardEmbeddedId", dashboardEmbeddedId);
    context.getRequest().setAttribute("supersetDomain", supersetDomain);

    context.setJsp(JSP);
    return context;
  }
}