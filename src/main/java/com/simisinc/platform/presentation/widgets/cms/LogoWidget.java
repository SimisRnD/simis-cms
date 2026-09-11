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
import java.util.regex.Pattern;

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
    String maxWidth = cssLength(context.getPreferences().get("maxWidth"));
    if (maxWidth != null) {
      style = appendCSSValue(style, "max-width:" + maxWidth);
    }
    String maxHeight = cssLength(context.getPreferences().get("maxHeight"));
    if (maxHeight != null) {
      style = appendCSSValue(style, "max-height:" + maxHeight);
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

  /**
   * A plain CSS length, or null when the value is anything else.
   *
   * <p>These two preferences used to be concatenated into the value unchecked, which was survivable
   * while the result went into a style ATTRIBUTE -- the worst a stray character could do there was
   * produce a malformed declaration the browser drops. logo.jsp now renders them into a
   * &lt;style&gt; ELEMENT instead, because style-src has no 'unsafe-inline' and a nonce covers style
   * elements but cannot cover attributes. A stylesheet is a much wider blast
   * radius: a value carrying "}" closes the rule and everything after it becomes page-wide CSS.
   *
   * <p>Rejected rather than escaped, deliberately. There is no legitimate logo size that is not a
   * number and a unit, so anything else is a mistake or an attempt, and dropping it fails safe --
   * the logo renders at its natural size instead of the page rendering someone else's CSS.
   */
  private static final Pattern CSS_LENGTH = Pattern.compile(
      "(?i)^(auto|none|inherit|initial|unset|\\d+(\\.\\d+)?(px|rem|em|ex|ch|vh|vw|vmin|vmax|pt|pc|cm|mm|in|%))$");

  static String cssLength(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    String trimmed = value.trim();
    return CSS_LENGTH.matcher(trimmed).matches() ? trimmed : null;
  }

  private static String appendCSSValue(String existingCSS, String newCSS) {
    if (existingCSS.length() > 0) {
      return existingCSS + ";" + newCSS;
    } else {
      return newCSS;
    }
  }
}
