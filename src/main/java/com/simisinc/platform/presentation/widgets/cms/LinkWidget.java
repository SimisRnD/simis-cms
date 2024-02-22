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

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

/**
 * Displays content with a link
 *
 * @author matt rajkowski
 * @created 1/18/21 3:44 PM
 */
public class LinkWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/link.jsp";

  public WidgetContext execute(WidgetContext context) {
    // Determine if the link can be shown
    String propertyRule = context.getPreferences().get("property");
    if (StringUtils.isNotBlank(propertyRule)) {
      if (!propertyRule.contains("=")) {
        return context;
      }
      String[] property = propertyRule.split("=");
      String siteProperty = LoadSitePropertyCommand.loadByName(property[0].trim());
      if (StringUtils.isBlank(siteProperty)) {
        return context;
      }
      if (!siteProperty.equalsIgnoreCase(property[1].trim())) {
        return context;
      }
    }

    // Configure the link based on preferences
    String link = context.getPreferences().get("link");
    if (StringUtils.isBlank(link)) {
      return context;
    }
    if (!link.contains("://") && !link.startsWith(context.getContextPath())) {
      link = context.getContextPath() + link;
    }
    context.getRequest().setAttribute("link", link);
    String linkClass = context.getPreferences().get("linkClass");
    linkClass = context.getPreferences().getOrDefault("class", linkClass);
    
    context.getRequest().setAttribute("linkClass", linkClass);
    context.getRequest().setAttribute("name", context.getPreferences().get("name"));
    context.getRequest().setAttribute("icon", context.getPreferences().getOrDefault("icon", context.getPreferences().get("rightIcon")));
    context.getRequest().setAttribute("leftIcon", context.getPreferences().get("leftIcon"));
    context.getRequest().setAttribute("target", context.getPreferences().getOrDefault("target", (link.contains("://") ? "_blank" : null)));

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }
}
