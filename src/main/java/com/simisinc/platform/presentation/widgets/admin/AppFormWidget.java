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

import java.lang.reflect.InvocationTargetException;

import com.simisinc.platform.application.AppException;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.SaveAppCommand;
import com.simisinc.platform.domain.model.App;
import com.simisinc.platform.infrastructure.persistence.AppRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/30/18 8:38 AM
 */
public class AppFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/app-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Form bean
    if (context.getRequestObject() != null) {
      context.getRequest().setAttribute("app", context.getRequestObject());
    } else {
      int appId = context.getParameterAsInt("appId");
      App app = AppRepository.findById(appId);
      context.getRequest().setAttribute("app", app);
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Read only the fields this form is meant to submit (name, summary, enabled) via explicit,
    // named parameter reads, rather than BeanUtils.populate() against the full raw parameter map --
    // that pattern is a mass-assignment-shaped risk (a crafted POST could set publicKey/privateKey/
    // enabled directly through the bean), currently harmless only because SaveAppCommand happens not
    // to read those particular fields back off the bean, which is fragile. Matches the same fix
    // already applied to WebPageFormWidget's pageXml mass-assignment gap.
    App appBean = new App();
    appBean.setId(context.getParameterAsLong("id"));
    appBean.setName(context.getParameter("name"));
    appBean.setSummary(context.getParameter("summary"));
    // Checkbox: present (any value) when checked, absent from the parameter map when unchecked.
    appBean.setEnabled(context.getParameter("enabled") != null);
    appBean.setCreatedBy(context.getUserId());

    String eventType = appBean.getId() > -1 ? "app.update" : "app.create";

    // Non-blocking duplicate-name check -- surfaced as a warning alongside the save confirmation,
    // never blocks the save itself.
    String duplicateNameWarning = SaveAppCommand.checkForDuplicateName(appBean);

    // Save the record
    App app = null;
    try {
      app = SaveAppCommand.saveApp(context, appBean);
      if (app == null) {
        throw new AppException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | AppException e) {
      AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, eventType, AuditEventCommand.FAILURE,
          "app", String.valueOf(appBean.getId()), appBean.getName(), e.getMessage());
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(appBean);
      return context;
    }

    AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, eventType, AuditEventCommand.SUCCESS,
        "app", String.valueOf(app.getId()), app.getName(), null);

    // Determine the page to return to
    context.setSuccessMessage("App was saved");
    if (duplicateNameWarning != null) {
      context.setWarningMessage(duplicateNameWarning);
    }
    context.setRedirect("/admin/apps");
    return context;
  }
}
