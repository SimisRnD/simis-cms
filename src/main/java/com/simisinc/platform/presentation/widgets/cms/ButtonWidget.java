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
import org.apache.commons.lang3.StringUtils;

/**
 * Button HTML
 *
 * @author matt rajkowski
 * @created 8/7/18 3:20 PM
 */
public class ButtonWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/button.jsp";

  public WidgetContext execute(WidgetContext context) {
    String link = context.getPreferences().get("link");
    if (StringUtils.isBlank(link)) {
      return context;
    }
    if (!link.contains("://") && !link.startsWith(context.getContextPath())) {
      link = context.getContextPath() + link;
    }
    context.getRequest().setAttribute("link", link);
    context.getRequest().setAttribute("buttonClass", context.getPreferences().getOrDefault("class", "primary"));
    context.getRequest().setAttribute("icon", context.getPreferences().getOrDefault("icon", context.getPreferences().get("rightIcon")));
    context.getRequest().setAttribute("leftIcon", context.getPreferences().get("leftIcon"));
    String title = context.getPreferences().get("title");
    if (StringUtils.isBlank(title)) {
      title = context.getPreferences().get("name");
    }
    context.getRequest().setAttribute("name", title);

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }
}
