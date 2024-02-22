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
 * Displays a Card
 *
 * @author matt rajkowski
 * @created 4/20/18 2:23 PM
 */
public class CardWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/card.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Preferences
    context.getRequest().setAttribute("classData", context.getPreferences().get("class"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("link", context.getPreferences().get("link"));
    context.getRequest().setAttribute("linkTitle", context.getPreferences().get("linkTitle"));
    context.getRequest().setAttribute("linkIcon", context.getPreferences().get("linkIcon"));

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }
}
