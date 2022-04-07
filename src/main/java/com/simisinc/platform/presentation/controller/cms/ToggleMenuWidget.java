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

package com.simisinc.platform.presentation.controller.cms;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 2/4/2021 12:00 PM
 */
public class ToggleMenuWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/toggle-menu.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Check preferences
    String view = context.getPreferences().get("view");
    if (view != null) {
      context.getRequest().setAttribute("view", view);
    }

    Map<String, String> sitePropertyMap = LoadSitePropertyCommand.loadAsMap("site");
    context.getRequest().setAttribute("sitePropertyMap", sitePropertyMap);

    Map<String, String> themePropertyMap = LoadSitePropertyCommand.loadAsMap("theme");
    context.getRequest().setAttribute("themePropertyMap", themePropertyMap);

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }
}
