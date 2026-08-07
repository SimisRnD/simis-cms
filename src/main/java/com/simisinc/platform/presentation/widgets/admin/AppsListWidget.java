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

package com.simisinc.platform.presentation.widgets.admin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.simisinc.platform.domain.model.App;
import com.simisinc.platform.infrastructure.persistence.AppRepository;
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/30/18 8:30 AM
 */
public class AppsListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/apps-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Load the list of apps. findAll() returns null (not an empty list) specifically when the
    // underlying SELECT throws a SQLException -- a DB-outage edge case, not a genuinely empty
    // table -- so this must be guarded the same way SaveAppCommand.checkForDuplicateName() already
    // guards the identical repository call, or a transient DB hiccup NPEs this loop and silently
    // blanks the whole widget instead of rendering "No apps were found".
    List<App> appList = AppRepository.findAll();
    if (appList == null) {
      appList = new ArrayList<>();
    }
    context.getRequest().setAttribute("appList", appList);

    // Determine the device/session count per app, mirroring BlogListWidget's blogPostCount pattern
    Map<Long, Long> deviceCountByAppId = new HashMap<>();
    for (App app : appList) {
      deviceCountByAppId.put(app.getId(), SessionRepository.countByAppId(app.getId()));
    }
    context.getRequest().setAttribute("deviceCountByAppId", deviceCountByAppId);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {

    // Permission is required
    if (!context.hasRole("admin")) {
      context.setWarningMessage("Must be an admin");
      return context;
    }

    // Determine what's being deleted
    long appId = context.getParameterAsLong("id");
    if (appId > -1) {
      App app = AppRepository.findById(appId);
      if (app == null) {
        context.setErrorMessage("App was not found");
      } else {
        if (AppRepository.remove(app)) {
          AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "app.delete", AuditEventCommand.SUCCESS,
              "app", String.valueOf(app.getId()), app.getName(), null);
          context.setSuccessMessage("App was deleted");
        } else {
          AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "app.delete", AuditEventCommand.FAILURE,
              "app", String.valueOf(app.getId()), app.getName(), null);
          context.setWarningMessage("App could not be deleted");
        }
      }
    }
    context.setRedirect(context.getUri());
    return context;
  }
}
