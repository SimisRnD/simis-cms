/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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
import com.simisinc.platform.application.dashboards.PowerBiEmbedCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Renders a Power BI report published via "Publish to web". The embed URL is complete and
 * public as configured by Power BI itself -- this widget only validates it before rendering,
 * it does not sign or generate anything (contrast SupersetWidget/MetabaseWidget).
 *
 * @author elizabeth houser
 */
public class PowerBiWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908895L;

  public static String JSP = "/dashboard/powerbi-embedded.jsp";

  public WidgetContext execute(WidgetContext context) {
    String embedUrl = PowerBiEmbedCommand.validateEmbedUrl(context.getPreferences().get("embedUrl"));
    if (embedUrl == null) {
      return context;
    }

    context.getRequest().setAttribute("embedUrl", embedUrl);
    // height is rendered into a style value, so require a CSS length
    context.getRequest().setAttribute("height",
        NumberCommand.filterCssLength(context.getPreferences().getOrDefault("height", "300px"), "300px"));

    context.setJsp(JSP);
    return context;
  }
}
