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

import com.simisinc.platform.application.cms.NumberCommand;
import com.simisinc.platform.application.dashboards.MetabaseEmbedCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Renders a Metabase dashboard using static (signed) embedding. Unlike SupersetWidget, this needs
 * no client-side SDK or guest-token AJAX round trip -- the signed iframe URL is computed
 * synchronously on render.
 *
 * @author elizabeth houser
 */
public class MetabaseWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908894L;

  public static String JSP = "/dashboard/metabase-embedded.jsp";

  public WidgetContext execute(WidgetContext context) {
    String dashboardId = context.getPreferences().get("dashboardValue");

    boolean hideChartTitle = "true".equals(context.getPreferences().getOrDefault("hideChartTitle", "false"));
    String hashParameters = "bordered=true&titled=" + (hideChartTitle ? "false" : "true");

    String iframeUrl = MetabaseEmbedCommand.generateDashboardIframeUrl(dashboardId, hashParameters);
    if (iframeUrl == null) {
      return context;
    }

    context.getRequest().setAttribute("iframeUrl", iframeUrl);
    // height is rendered into a style value, so require a CSS length
    context.getRequest().setAttribute("height",
        NumberCommand.filterCssLength(context.getPreferences().getOrDefault("height", "300px"), "300px"));

    context.setJsp(JSP);
    return context;
  }
}
