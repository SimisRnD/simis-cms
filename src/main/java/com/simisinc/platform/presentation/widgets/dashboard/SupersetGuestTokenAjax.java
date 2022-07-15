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
import com.simisinc.platform.application.dashboards.SupersetGuestTokenCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

import static com.simisinc.platform.presentation.widgets.dashboard.SupersetWidget.SESSION_PREFIX;

/**
 * Provides a guestToken for the dashboard request
 *
 * @author matt rajkowski
 * @created 7/4/22 10:30AM
 */
public class SupersetGuestTokenAjax extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public WidgetContext execute(WidgetContext context) {
    boolean enabled = ("true".equals(LoadSitePropertyCommand.loadByName("bi.enabled", "false")));
    if (!enabled) {
      context.setJson("{}");
      return context;
    }

    String dashboardId = context.getParameter("dashboardId");
    if (StringUtils.isBlank(dashboardId)) {
      context.setJson("{}");
      return context;
    }

    // Verify the request set a control value and optional rls statement
    String widgetUniqueId = context.getParameter("widgetUniqueId");
    if (widgetUniqueId == null) {
      LOG.debug("Missing parameter: widgetUniqueId");
      context.setJson("{}");
      return context;
    }
    String widgetUniqueIdValue = (String) context.getRequest().getSession().getAttribute(SESSION_PREFIX + widgetUniqueId + dashboardId);
    if (widgetUniqueIdValue == null || !widgetUniqueIdValue.equals(widgetUniqueId)) {
      LOG.warn("Session value did not match for widgetUniqueId: " + widgetUniqueId);
      context.setJson("{}");
      return context;
    }

    // Determine the optional rls value
    String rlsClause = (String) context.getRequest().getSession().getAttribute(SESSION_PREFIX + widgetUniqueId + dashboardId + "-rls-clause");
    LOG.debug("Using rls-clause: " + rlsClause);

    // Request the dashboard
    String guestToken = SupersetGuestTokenCommand.retrieveGuestTokenForDashboard(context.getUserSession().getUser(), dashboardId, rlsClause);
    if (StringUtils.isBlank(guestToken)) {
      context.setJson("{}");
      return context;
    }
    context.setJson("{\"guestToken\":\"" + JsonCommand.toJson(guestToken) + "\"}");
    return context;
  }
}
