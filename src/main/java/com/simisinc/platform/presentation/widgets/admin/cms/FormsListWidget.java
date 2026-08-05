/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.simisinc.platform.domain.model.cms.FormDefinition;
import com.simisinc.platform.infrastructure.persistence.cms.FormDefinitionRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormFieldRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Widget to display the list of database-backed form definitions to the system administrators
 * (issue #409), mirroring MailingListsWidget/CollectionRelationshipsListWidget's list+delete
 * shape.
 *
 * @author SimIS Inc.
 */
public class FormsListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/forms.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Load the form definitions
    List<FormDefinition> formDefinitionList = FormDefinitionRepository.findAll();
    context.getRequest().setAttribute("formDefinitionList", formDefinitionList);

    // Determine the field count for each form (mirrors BlogListWidget's per-row blogPostCount map)
    Map<Long, Integer> fieldCountMap = new HashMap<>();
    for (FormDefinition formDefinition : formDefinitionList) {
      fieldCountMap.put(formDefinition.getId(), FormFieldRepository.findAllByFormDefinitionId(formDefinition.getId()).size());
    }
    context.getRequest().setAttribute("fieldCountMap", fieldCountMap);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the list
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {
    // Determine what's being deleted
    long formDefinitionId = context.getParameterAsLong("formDefinitionId");
    if (formDefinitionId > -1) {
      FormDefinition formDefinition = FormDefinitionRepository.findById(formDefinitionId);
      if (formDefinition == null) {
        context.setErrorMessage("Form not found");
      } else if (FormDefinitionRepository.remove(formDefinition)) {
        context.setSuccessMessage("Form deleted");
      } else {
        context.setWarningMessage("Form could not be deleted");
      }
    }
    context.setRedirect("/admin/forms");
    return context;
  }
}
