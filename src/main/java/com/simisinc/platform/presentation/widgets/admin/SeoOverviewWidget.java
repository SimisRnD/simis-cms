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

package com.simisinc.platform.presentation.widgets.admin;

import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Read-only overview of the site's SEO/AEO (answer engine optimization) capabilities: what's
 * automatic vs. what an admin can configure, and where each configurable piece actually lives --
 * those settings are spread across several other admin pages (robots/crawlers, social media,
 * per-page settings, the dashboard), so this page exists purely to index them, not to duplicate
 * their controls.
 *
 * @author SimIS Inc.
 */
public class SeoOverviewWidget extends GenericWidget {

  static final long serialVersionUID = 3971882456123456789L;

  static String JSP = "/admin/seo-overview.jsp";

  public WidgetContext execute(WidgetContext context) {
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.setJsp(JSP);
    return context;
  }
}
