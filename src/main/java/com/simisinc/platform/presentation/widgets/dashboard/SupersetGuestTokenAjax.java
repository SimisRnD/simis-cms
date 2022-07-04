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

import com.simisinc.platform.application.dashboards.SupersetGuestTokenCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

/**
 * Provides a guestToken for the dashboard request
 *
 * @author matt rajkowski
 * @created 7/4/22 10:30AM
 */
public class SupersetGuestTokenAjax extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public WidgetContext execute(WidgetContext context) {
    String dashboardId = context.getParameter("dashboardId");
    if (StringUtils.isBlank(dashboardId)) {
      context.setJson("{}");
      return context;
    }

    String guestToken = SupersetGuestTokenCommand.retrieveGuestTokenForDashboard(context.getUserSession().getUser(), dashboardId);
    context.setJson("{\"guestToken\":\"" + JsonCommand.toJson(guestToken) + "\"}");
    return context;
  }
}
