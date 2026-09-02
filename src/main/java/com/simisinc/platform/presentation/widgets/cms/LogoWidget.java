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

    // system/site/themePropertyMap are not set here. PageServlet publishes all three once per
    // request, before any widget runs, and WebContainerCommand exempts them from the per-widget
    // reset precisely so every widget's JSP -- logo.jsp included -- can read them during its own
    // turn. Re-loading and re-setting them made this widget silently authoritative over values
    // main.jsp reads after the walk is over, for no gain (issue #1799).

    // Clear attributes possibly left behind by an earlier logo widget rendered in this same
    // request (e.g. the header's, before the footer's runs) -- only setting them conditionally
    // below would otherwise let a blank preference here silently inherit a stale value.
    context.getRequest().removeAttribute("view");
    context.getRequest().removeAttribute("logoColorProperty");
    context.getRequest().removeAttribute("logoColorPropertyDark");

    // Check preferences
    String view = context.getPreferences().get("view");
    if (StringUtils.isNotBlank(view)) {
      context.getRequest().setAttribute("view", view);
    }
    String colorProperty = context.getPreferences().get("colorProperty");
    if (StringUtils.isNotBlank(colorProperty)) {
      context.getRequest().setAttribute("logoColorProperty", colorProperty);
    }
    String colorPropertyDark = context.getPreferences().get("colorPropertyDark");
    if (StringUtils.isNotBlank(colorPropertyDark)) {
      context.getRequest().setAttribute("logoColorPropertyDark", colorPropertyDark);
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
    String text = context.getPreferences().get("text");
    if (StringUtils.isNotBlank(text)) {
      context.getRequest().setAttribute("text", text);
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
