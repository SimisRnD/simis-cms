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
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
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

    // A reference name is a lookup key a page's widget XML points at, not a namespace scoped to this
    // form -- typing an existing one (e.g. accidentally re-typing "site-footer") would otherwise
    // silently open that existing, possibly load-bearing block for editing instead of creating a new
    // one, with no warning at all. This is informational rather than a hard block: editing an existing
    // named block by its known reference name is sometimes exactly what someone means to do. Staying
    // on this page (no redirect) rather than redirecting to /content-editor anyway is deliberate --
    // the framework's flash-message mechanism only survives a redirect back to this same widget, not a
    // hop to a different page, so a warning attached to a redirect to /content-editor would never
    // actually be seen. The existing block is likely visible right in the list below (or via the
    // search box above) for the admin to open deliberately instead.
    if (ContentRepository.findByUniqueId(uniqueId) != null) {
      context.setWarningMessage(
          "\"" + uniqueId + "\" already exists. Find it in the list below (or search for it above) to edit it, "
              + "or use a different reference name to create a new content block.");
      return context;
    }

    context.setRedirect("/content-editor?uniqueId=" + uniqueId + "&returnPage=/admin/content-list");
    return context;
  }
}
