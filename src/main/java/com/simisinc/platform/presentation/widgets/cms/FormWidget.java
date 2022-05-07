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

package com.simisinc.platform.presentation.widgets.cms;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.CaptchaCommand;
import com.simisinc.platform.application.cms.FormCommand;
import com.simisinc.platform.application.cms.FormFieldCommand;
import com.simisinc.platform.domain.events.cms.FormSubmittedEvent;
import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.routines.EmailValidator;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 6/1/18 11:11 AM
 */
public class FormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  protected static Log LOG = LogFactory.getLog(FormWidget.class);

  static String JSP = "/cms/form.jsp";
  static String SUCCESS_JSP = "/cms/form-success.jsp";

  public WidgetContext execute(WidgetContext context) {

    String isSuccess = context.getSharedRequestValue(context.getUniqueId() + "formWidgetSuccess");
    if ("true".equals(isSuccess)) {
      context.getRequest().setAttribute("successTitle", context.getPreferences().get("successTitle"));
      context.getRequest().setAttribute("successMessage", context.getPreferences().getOrDefault("successMessage", "Your information has been submitted."));
      context.setJsp(SUCCESS_JSP);
      return context;
    }
    context.setJsp(JSP);

    // Preferences
    context.getRequest().setAttribute("buttonName", context.getPreferences().getOrDefault("buttonName", "Submit"));

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("subtitle", context.getPreferences().get("subtitle"));

    // Determine the captcha service
    boolean useCaptcha = "true".equals(context.getPreferences().getOrDefault("useCaptcha", "false"));
    if (useCaptcha) {
      context.getRequest().setAttribute("useCaptcha", "true");
      context.getRequest().setAttribute("googleSiteKey", LoadSitePropertyCommand.loadByName("captcha.google.sitekey"));
    }

    // Previous post had error
    FormData formData = (FormData) context.getRequestObject();
    if (formData != null) {
      if (formData.getQueryParameters() != null) {
        context.getRequest().setAttribute("queryString", formData.getQueryParameters());
      }
      context.getRequest().setAttribute("formFieldList", formData.getFormFieldList());
      return context;
    }

    // Use the fields preference to determine the item properties to be shown
    PreferenceEntriesList fieldsEntriesList = context.getPreferenceAsDataList("fields");
    if (fieldsEntriesList.isEmpty()) {
      LOG.warn("Fields preference was not found");
      return null;
    }
    String formUniqueId = context.getPreferences().get("formUniqueId");
    List<FormField> formFieldList = FormFieldCommand.parseFieldContent(formUniqueId, fieldsEntriesList);
    if (formFieldList.isEmpty()) {
      LOG.warn("No fields were found");
      return null;
    }
    context.getRequest().setAttribute("formFieldList", formFieldList);

    // Check for any query parameters to save with the form data
    if (StringUtils.isNotBlank(context.getRequest().getQueryString())) {
      context.getRequest().setAttribute("queryString", context.getRequest().getQueryString());
    }

    // Show the JSP
    return context;
  }

  public WidgetContext post(WidgetContext context) {
    // Determine the fields
    boolean isValid = true;
    int blankValues = 0;

    PreferenceEntriesList fieldsEntriesList = context.getPreferenceAsDataList("fields");
    if (fieldsEntriesList.isEmpty()) {
      LOG.warn("Fields preference was not found");
      return null;
    }
    String formUniqueId = context.getPreferences().get("formUniqueId");
    List<FormField> formFieldList = FormFieldCommand.parseFieldContent(formUniqueId, fieldsEntriesList);
    for (FormField formField : formFieldList) {
      // Determine the user's value
      String parameterValue = context.getParameter(context.getUniqueId() + formField.getName());
      if (StringUtils.isBlank(parameterValue)) {
        // Check if the field is required
        if (formField.isRequired()) {
          isValid = false;
          context.setWarningMessage(formField.getLabel() + " is required");
        }
        ++blankValues;
        continue;
      }
      parameterValue = parameterValue.trim();
      if (formField.getListOfOptions() != null) {
        formField.setUserValue(formField.getListOfOptions().get(parameterValue));
      } else {
        formField.setUserValue(parameterValue);
      }
      if ("email".equals(formField.getType())) {
        EmailValidator emailValidator = EmailValidator.getInstance(false);
        if (!emailValidator.isValid(parameterValue)) {
          isValid = false;
          context.setWarningMessage("Check the email address and try again");
        }
      }
      LOG.debug("Set userValue " + formField.getName() + "=" + formField.getUserValue());
    }
    if (isValid && blankValues == formFieldList.size()) {
      isValid = false;
      context.setWarningMessage("Check the form and try again");
    }

    // Validate the captcha
    boolean useCaptcha = "true".equals(context.getPreferences().getOrDefault("useCaptcha", "false"));
    if (useCaptcha) {
      boolean captchaSuccess = CaptchaCommand.validateRequest(context);
      if (!captchaSuccess) {
        isValid = false;
        context.setWarningMessage("The form could not be validated");
      }
    }

    // Prepare the object and handle any errors
    FormData formData = new FormData();
    formData.setFormUniqueId(formUniqueId);
    formData.setSessionId(context.getUserSession().getSessionId());
    if (context.getUserSession().isLoggedIn()) {
      formData.setCreatedBy(context.getUserId());
    }
    formData.setFormFieldList(formFieldList);
    formData.setIpAddress(context.getRequest().getRemoteAddr());
    formData.setUrl(context.getUrl() + context.getUri());
    if (context.getParameter("queryString") != null) {
      formData.setQueryParameters(context.getParameter("queryString"));
    }
    if (!isValid) {
      context.setRequestObject(formData);
      return context;
    }

    boolean checkForSpam = Boolean.parseBoolean(context.getPreferences().getOrDefault("checkForSpam", "true"));
    if (checkForSpam) {
      FormCommand.checkNotificationRules(formData);
    }

    // Store in the database
    if (FormDataRepository.save(formData) == null) {
      context.setErrorMessage("The form was not saved... try again?");
      context.setRequestObject(formData);
      return context;
    }

    // Send an alert based on the preferences (or transform for another system)
    String emailAddresses = context.getPreferences().get("emailTo");
    WorkflowManager.triggerWorkflowForEvent(new FormSubmittedEvent(formData, emailAddresses));

    // Redirect back so the message can be displayed
    context.addSharedRequestValue(context.getUniqueId() + "formWidgetSuccess", "true");

    return null;
  }
}
