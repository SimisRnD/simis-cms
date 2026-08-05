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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.domain.model.cms.FormDefinition;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.infrastructure.persistence.cms.FormDefinitionRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormFieldRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Widget to list a form's fields and persist a drag-and-drop reorder (issue #409).
 *
 * <p>The reorder mechanism mirrors SiteMapWidget/sitemap.jsp: a dragula drag handle populates a
 * hidden, comma-joined field id list on submit, POSTed as a normal form and parsed here into a
 * call to FormFieldRepository#reorderFields -- which already does the per-id database update loop
 * SaveMenuTabCommand#updateTabOrder does for menu tabs. Unlike the sitemap (tabs, each containing
 * items), a form has only one flat list of fields, so there's no second order dimension to track.
 *
 * @author SimIS Inc.
 */
public class FormFieldsListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/form-fields-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    long formDefinitionId = context.getParameterAsLong("formDefinitionId");
    FormDefinition formDefinition = FormDefinitionRepository.findById(formDefinitionId);
    if (formDefinition == null) {
      context.setErrorMessage("Form not found");
      context.setRedirect("/admin/forms");
      return context;
    }
    context.getRequest().setAttribute("formDefinition", formDefinition);

    List<FormField> fieldList = FormFieldRepository.findAllByFormDefinitionId(formDefinitionId);
    context.getRequest().setAttribute("fieldList", fieldList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    long formDefinitionId = context.getParameterAsLong("formDefinitionId");
    String fieldOrder = context.getParameter("fieldOrder");
    if (formDefinitionId > -1 && StringUtils.isNotBlank(fieldOrder)) {
      List<Long> orderedFieldIds = new ArrayList<>();
      for (String value : fieldOrder.split(",")) {
        if (StringUtils.isBlank(value)) {
          continue;
        }
        try {
          orderedFieldIds.add(Long.parseLong(value.trim()));
        } catch (NumberFormatException nfe) {
          LOG.warn("Skipping non-numeric field id in fieldOrder: " + value);
        }
      }
      FormFieldRepository.reorderFields(formDefinitionId, orderedFieldIds);
    }

    context.setRedirect("/admin/forms-editor?formDefinitionId=" + formDefinitionId);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {
    long fieldId = context.getParameterAsLong("fieldId");
    long formDefinitionId = -1;
    if (fieldId > -1) {
      FormField field = FormFieldRepository.findById(fieldId);
      if (field == null) {
        context.setErrorMessage("Field not found");
      } else {
        formDefinitionId = field.getFormDefinitionId();
        if (FormFieldRepository.remove(field)) {
          context.setSuccessMessage("Field deleted");
        } else {
          context.setWarningMessage("Field could not be deleted");
        }
      }
    }
    if (formDefinitionId == -1) {
      formDefinitionId = context.getParameterAsLong("formDefinitionId");
    }
    context.setRedirect("/admin/forms-editor?formDefinitionId=" + formDefinitionId);
    return context;
  }
}
