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
import org.apache.commons.lang3.StringUtils;

/**
 * Renders a Superset dashboard using the superset embedded sdk
 *
 * @author matt rajkowski
 * @created 7/1/22 1:18 PM
 */
public class SupersetWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public static String JSP = "/dashboard/superset-embedded.jsp";

  public static String SESSION_PREFIX = "SupersetWidget";

  public WidgetContext execute(WidgetContext context) {
    boolean enabled = ("true".equals(LoadSitePropertyCommand.loadByName("bi.enabled", "false")));
    if (!enabled) {
      LOG.debug("BI is not enabled");
      return context;
    }
    // guestToken (via AJAX request)
    String dashboardValue = context.getPreferences().get("dashboardValue");
    String dashboardEmbeddedId = context.getPreferences().get("dashboardEmbeddedId");
    String supersetDomain = LoadSitePropertyCommand.loadByName("bi.superset.url");

    context.getRequest().setAttribute("dashboardValue", dashboardValue);
    context.getRequest().setAttribute("dashboardEmbeddedId", dashboardEmbeddedId);
    context.getRequest().setAttribute("supersetDomain", supersetDomain);
    context.getRequest().setAttribute("height", context.getPreferences().getOrDefault("height", "300px"));
    boolean hideChartTitle = "true".equals(context.getPreferences().getOrDefault("hideChartTitle", "true"));
    context.getRequest().setAttribute("hideChartTitle", hideChartTitle ? "true" : "false");
    boolean hideChartControls = "true".equals(context.getPreferences().getOrDefault("hideChartControls", "true"));
    context.getRequest().setAttribute("hideChartControls", hideChartControls ? "true" : "false");

    // Use a control session value to be used by Ajax
    context.getRequest().getSession().setAttribute(SESSION_PREFIX + context.getUniqueId() + dashboardValue, context.getUniqueId());

    // Share the RLS with the Ajax request
    String rlsClause = context.getPreferences().get("clause");
    if (StringUtils.isNotBlank(rlsClause)) {
      context.getRequest().getSession().setAttribute(SESSION_PREFIX + context.getUniqueId() + dashboardValue + "-rls-clause", rlsClause);
    } else {
      context.getRequest().getSession().removeAttribute(SESSION_PREFIX + context.getUniqueId() + dashboardValue + "-rls-clause");
    }

    context.setJsp(JSP);
    return context;
  }
}
