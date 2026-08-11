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
import java.util.UUID;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.mail.ImageHtmlEmail;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.SaveFormDefinitionCommand;
import com.simisinc.platform.application.email.EmailCommand;
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

  private static Log LOG = LogFactory.getLog(FormDefinitionFormWidget.class);

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

    if ("sendTestEmail".equals(context.getParameter("action"))) {
      return sendTestEmail(context);
    }

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

  /**
   * Sends a real email to the "Email submissions to" address(es) currently typed in the field --
   * not necessarily saved yet -- so an admin can confirm right away that a wrong-yet-valid-looking
   * address (a typo of a real one) actually reaches the intended inbox. Save-time syntax validation
   * (SaveFormDefinitionCommand) can't catch that class of mistake; this is the only check that can.
   * Always re-renders the same unsaved form state rather than redirecting away from it, following
   * the same flash-object pattern the DataException branch above already relies on.
   */
  private WidgetContext sendTestEmail(WidgetContext context) {

    FormDefinition formDefinitionBean = new FormDefinition();
    try {
      BeanUtils.populate(formDefinitionBean, context.getParameterMap());
    } catch (InvocationTargetException | IllegalAccessException e) {
      LOG.error("Could not populate the form for a test email send", e);
    }
    formDefinitionBean.setEnabled(context.getParameter("enabled") != null);
    formDefinitionBean.setCheckForSpam(context.getParameter("checkForSpam") != null);

    context.setRequestObject(formDefinitionBean);
    if (formDefinitionBean.getId() > -1) {
      context.setRedirect("/admin/forms-editor?formDefinitionId=" + formDefinitionBean.getId());
    }

    String emailTo = formDefinitionBean.getEmailTo();
    if (StringUtils.isBlank(emailTo)) {
      context.setErrorMessage("Enter an address in \"Email submissions to\" first, then try again.");
      return context;
    }
    String invalidAddress = SaveFormDefinitionCommand.findInvalidEmailAddress(emailTo);
    if (invalidAddress != null) {
      context.setErrorMessage("'" + invalidAddress + "' is not a valid email address");
      return context;
    }

    String formName = StringUtils.isNotBlank(formDefinitionBean.getName()) ? formDefinitionBean.getName() : "this form";
    try {
      ImageHtmlEmail email = EmailCommand.prepareNewEmail();
      for (String address : emailTo.split(",")) {
        String trimmed = address.trim();
        if (StringUtils.isNotBlank(trimmed)) {
          email.addTo(trimmed);
        }
      }
      email.setSubject("SimIS CMS Form Test Email");
      email.setMsg("This is a test message confirming that submissions to \"" + formName
          + "\" can reach this address (" + emailTo + ").");
      email.send();
    } catch (Exception e) {
      String correlationId = UUID.randomUUID().toString();
      LOG.warn("Form test email failed to send [" + correlationId + "]", e);
      context.setErrorMessage("Test email failed (" + EmailCommand.categorizeSendFailure(e) + "). Reference: "
          + correlationId + ". Check the server logs for details.");
      return context;
    }

    context.setSuccessMessage("A test email was sent to " + emailTo);
    return context;
  }
}
