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

package com.simisinc.platform.presentation.controller.admin.cms;

import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataSpecification;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/24/18 7:33 PM
 */
public class FormDataListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/form-data-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "10"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage, "created", "desc");
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Determine the records to show
    FormDataSpecification specification = new FormDataSpecification();
    specification.setDismissed(false);
    specification.setProcessed(false);

    // Load the latest form data
    List<FormData> formDataList = FormDataRepository.findAll(specification, constraints);
    context.getRequest().setAttribute("formDataList", formDataList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {
    LOG.error("MUST OVERRIDE THE DEFAULT POST METHOD");
    return null;
  }

  public WidgetContext action(WidgetContext context) {
    // Find the user record
    long dataId = context.getParameterAsLong("dataId");
    FormData formData = FormDataRepository.findById(dataId);
    if (formData == null) {
      context.setErrorMessage("The form record was not found");
      return context;
    }
    // Execute the action
    context.setRedirect("/admin/form-data");
    String action = context.getParameter("action");
    if ("archive".equals(action)) {
      return archiveFormData(context, formData);
    } else if ("claim".equals(action)) {
      return claimFormData(context, formData);
    } else if ("markAsProcessed".equals(action)) {
      return markAsProcessed(context, formData);
    }
    return context;
  }

  private WidgetContext archiveFormData(WidgetContext context, FormData formData) {
    FormDataRepository.markAsArchived(formData, context.getUserId());
    return context;
  }

  private WidgetContext claimFormData(WidgetContext context, FormData formData) {
    FormDataRepository.tryToMarkAsClaimed(formData, context.getUserId());
    return context;
  }

  private WidgetContext markAsProcessed(WidgetContext context, FormData formData) {
    FormDataRepository.markAsProcessed(formData, context.getUserId());
    return context;
  }
}
