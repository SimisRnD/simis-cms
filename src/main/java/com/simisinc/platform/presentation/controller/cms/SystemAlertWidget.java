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
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/18/21 9:55 PM
 */
public class SystemAlertWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/system-alert.jsp";

  public WidgetContext execute(WidgetContext context) {
    // Check if the widget has content and can be displayed
    Map<String, String> sitePropertyMap = LoadSitePropertyCommand.loadAsMap("site");
    if (StringUtils.isBlank(sitePropertyMap.get("site.header.line1")) && !context.getUserSession().hasRole("admin")) {
      return context;
    }
    context.getRequest().setAttribute("sitePropertyMap", sitePropertyMap);

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }
}
