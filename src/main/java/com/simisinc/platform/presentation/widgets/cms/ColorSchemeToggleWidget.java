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

package com.simisinc.platform.presentation.widgets.cms;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Renders a light/dark color scheme toggle for the visitor.
 *
 * <p>The widget only renders when the site property <code>theme.ui.mode</code> is set to
 * <code>user</code>. In every other mode the scheme is fixed by the administrator or follows the
 * operating system, so a control would either do nothing or contradict the configured setting, and
 * showing an inert button is worse than showing none.
 *
 * <p>The scheme itself is applied by platform-tokens.css from the data-theme attribute on the html
 * element; this widget contributes only the control and its live region.
 *
 * @author SimIS
 * @created 7/21/2026 9:00 AM
 */
public class ColorSchemeToggleWidget extends GenericWidget {

  static final long serialVersionUID = 2571863240943382178L;

  static String JSP = "/cms/color-scheme-toggle.jsp";

  public WidgetContext execute(WidgetContext context) {

    String uiMode = LoadSitePropertyCommand.loadByName("theme.ui.mode");
    if (!"user".equals(uiMode)) {
      return context;
    }

    // Optional label shown beside the icon, for placements with room for it
    String label = context.getPreferences().get("label");
    if (label != null) {
      context.getRequest().setAttribute("colorSchemeToggleLabel", label);
    }

    context.setJsp(JSP);
    return context;
  }
}
