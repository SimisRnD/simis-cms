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

import org.apache.commons.beanutils.BeanUtils;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.SaveFormDefinitionCommand;
import com.simisinc.platform.domain.model.cms.FormDefinition;
import com.simisinc.platform.infrastructure.persistence.cms.FormDefinitionRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Widget to add or edit a database-backed form definition's settings (issue #409). Used both as
 * the "Add a form" sidebar callout on /admin/forms and as the settings section of
 * /admin/forms-editor, the same dual role MailingListFormWidget plays for /admin/mailing-lists'
 * sidebar and /admin/mailing-list's full edit page.
 *
 * @author SimIS Inc.
 */
public class FormDefinitionFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/form-definition-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Form bean
    FormDefinition formDefinition = (FormDefinition) context.getRequestObject();
    if (formDefinition == null) {
      long formDefinitionId = context.getParameterAsLong("formDefinitionId");
      formDefinition = FormDefinitionRepository.findById(formDefinitionId);
      if (formDefinition == null) {
        formDefinition = new FormDefinition();
      }
    }
    context.getRequest().setAttribute("formDefinition", formDefinition);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields
    FormDefinition formDefinitionBean = new FormDefinition();
    BeanUtils.populate(formDefinitionBean, context.getParameterMap());
    // An unchecked checkbox sends no request parameter at all, so BeanUtils.populate() never
    // overwrites these -- fine for a field that defaults to false (like useCaptcha), but
    // enabled/checkForSpam default to true on a fresh bean, so leaving them to populate() alone
    // means unchecking either box in the UI has no effect and always saves as true. Set both
    // explicitly from parameter presence instead.
    formDefinitionBean.setEnabled(context.getParameter("enabled") != null);
    formDefinitionBean.setCheckForSpam(context.getParameter("checkForSpam") != null);
    // createdBy is only honored by the command on insert (it preserves the original value on an
    // edit); modifiedBy always reflects whoever is saving right now
    formDefinitionBean.setCreatedBy(context.getUserId());
    formDefinitionBean.setModifiedBy(context.getUserId());

    // Save the record
    FormDefinition formDefinition;
    try {
      formDefinition = SaveFormDefinitionCommand.saveFormDefinition(formDefinitionBean);
      if (formDefinition == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(formDefinitionBean);
      if (formDefinitionBean.getId() > -1) {
        // Editing an existing form happens on /admin/forms-editor{?formDefinitionId} -- without an
        // explicit redirect here, the platform's default self-redirect on a targeted POST (see
        // WebContainerCommand) would drop that templated query parameter
        context.setRedirect("/admin/forms-editor?formDefinitionId=" + formDefinitionBean.getId());
      }
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Form was saved");
    context.setRedirect("/admin/forms-editor?formDefinitionId=" + formDefinition.getId());
    return context;
  }
}
