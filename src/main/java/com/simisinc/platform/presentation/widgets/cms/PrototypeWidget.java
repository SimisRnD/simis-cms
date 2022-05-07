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

package com.simisinc.platform.presentation.widgets.cms;

import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Shows up for CMS managers
 *
 * @author matt rajkowski
 * @created 6/6/18 11:36 AM
 */
public class PrototypeWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/prototype.jsp";

  public WidgetContext execute(WidgetContext context) {

    // This widget is only for admins and content managers
    if (!context.hasRole("admin") && !context.hasRole("content-manager")) {
      return null;
    }

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Prototype values
    context.getRequest().setAttribute("html", context.getPreferences().get("html"));
    context.getRequest().setAttribute("comment", context.getPreferences().get("comment"));

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }
}
