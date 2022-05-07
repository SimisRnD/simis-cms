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

import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/18/21 3:44 PM
 */
public class LogoWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/logo.jsp";

  public WidgetContext execute(WidgetContext context) {

    Map<String, String> systemPropertyMap = LoadSitePropertyCommand.loadAsMap("system");
    Map<String, String> sitePropertyMap = LoadSitePropertyCommand.loadAsMap("site");
    Map<String, String> themePropertyMap = LoadSitePropertyCommand.loadAsMap("theme");

    context.getRequest().setAttribute("systemPropertyMap", systemPropertyMap);
    context.getRequest().setAttribute("sitePropertyMap", sitePropertyMap);
    context.getRequest().setAttribute("themePropertyMap", themePropertyMap);

    // Check preferences
    String view = context.getPreferences().get("view");
    if (view != null) {
      context.getRequest().setAttribute("view", view);
    }
    String style = "";
    String maxWidth = context.getPreferences().get("maxWidth");
    if (StringUtils.isNotBlank(maxWidth)) {
      style = appendCSSValue(style, "max-width:" + maxWidth.trim());
    }
    String maxHeight = context.getPreferences().get("maxHeight");
    if (StringUtils.isNotBlank(maxHeight)) {
      style = appendCSSValue(style, "max-height:" + maxHeight.trim());
    }
    if (StringUtils.isNotBlank(style)) {
      context.getRequest().setAttribute("logoStyle", style);
    }

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  private static String appendCSSValue(String existingCSS, String newCSS) {
    if (existingCSS.length() > 0) {
      return existingCSS + ";" + newCSS;
    } else {
      return newCSS;
    }
  }
}
