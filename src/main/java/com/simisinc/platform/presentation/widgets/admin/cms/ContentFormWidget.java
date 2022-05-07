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

package com.simisinc.platform.presentation.widgets.admin.cms;

import com.simisinc.platform.application.cms.SaveContentCommand;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

import org.apache.commons.lang3.StringUtils;

/**
 * Widget for displaying a system administration form to add/update content
 *
 * @author matt rajkowski
 * @created 4/18/18 10:25 PM
 */
public class ContentFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/content-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the form
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    String uniqueId = context.getParameter("uniqueId");
    if (StringUtils.isBlank(uniqueId)) {
      context.setWarningMessage("A value is required");
      return context;
    }

    // Do some formatting
    uniqueId = uniqueId.trim().toLowerCase();
    uniqueId = StringUtils.replace(uniqueId, " ", "-");

    // Validate the characters
    for (int i = 0; i < uniqueId.length(); i++) {
      if (SaveContentCommand.allowedChars.indexOf(uniqueId.charAt(i)) == -1) {
        context.setWarningMessage("Use a-z, 0-9 and dashes");
        return context;
      }
    }

    context.setRedirect("/content-editor?uniqueId=" + uniqueId + "&returnPage=/admin/content-list");
    return context;
  }
}
