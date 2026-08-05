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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.CaptchaCommand;
import com.simisinc.platform.application.cms.FunnelEventCommand;
import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormSubmissionFailureRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.SessionConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * @author matt rajkowski
 * @created 5/7/2022 8:30 AM
 */
class FormWidgetTest extends WidgetBase {

  public void initCommonPreferences() {
    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"form\">\n" +
            "  <formUniqueId>contact</formUniqueId>\n" +
            "  <useCaptcha>true</useCaptcha>\n" +
            "  <checkForSpam>true</checkForSpam>\n" +
            "  <fields>\n" +
            "    <field name=\"Your first and last name\" value=\"name\" required=\"true\" />\n" +
            "    <field name=\"Name of your organization\" value=\"organization\" />\n" +
            "    <field name=\"Your email address\" value=\"email\" type=\"email\" required=\"true\" />\n" +
            "    <!--<field name=\"Are you:\" value=\"are-you\" list=\"Patient,Doctor,Lawmaker,Other\" />-->\n" +
            "    <field name=\"An optional phone number we can contact you at\" value=\"phoneNumber\" />\n" +
            "    <field name=\"Who would you like to contact?\" value=\"who\" list=\"Sales,Marketing,Business Development,Contracts,Technical,Security,Other\" />\n" +
            "    <field name=\"How Can We Help?\" value=\"comments\" type=\"textarea\" placeholder=\"Your message\" required=\"true\" />\n" +
            "  </fields>\n" +
            "  <buttonName>Contact Me</buttonName>\n" +
            "  <successMessage><![CDATA[Thanks! We normally respond within 24-48 hours.]]></successMessage>\n" +
            "  <emailTo>inquiries@example.com</emailTo>\n" +
            "</widget>");
  }

  @Test
  void executeFormDisplay() {
    // Set widget preferences
    initCommonPreferences();
    Assertions.assertEquals(7, widgetContext.getPreferences().size());

    // Shows the form
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.sitekey")).thenReturn(null);

      try (MockedStatic<RateLimitCommand> rateLimitCommand = mockStatic(RateLimitCommand.class)) {
        rateLimitCommand.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), anyBoolean())).thenReturn(true);

        FormWidget widget = new FormWidget();
        widget.execute(widgetContext);

        // Verify the output
        Assertions.assertEquals(FormWidget.JSP, widgetContext.getJsp());

        List<FormField> formFieldList = (List) widgetContext.getRequest().getAttribute("formFieldList");
        Assertions.assertEquals(6, formFieldList.size());

        FormField formFieldName = formFieldList.get(0);
        Assertions.assertEquals("Your first and last name", formFieldName.getLabel());
        Assertions.assertEquals("name", formFieldName.getName());
        Assertions.assertNull(formFieldName.getType());
        Assertions.assertTrue(formFieldName.isRequired());

        FormField formFieldOrganization = formFieldList.get(1);
        Assertions.assertEquals("Name of your organization", formFieldOrganization.getLabel());
        Assertions.assertEquals("organization", formFieldOrganization.getName());
        Assertions.assertNull(formFieldOrganization.getType());
        Assertions.assertFalse(formFieldOrganization.isRequired());

        FormField formFieldReason = formFieldList.get(5);
        Assertions.assertEquals("How Can We Help?", formFieldReason.getLabel());
        Assertions.assertEquals("comments", formFieldReason.getName());
        Assertions.assertEquals("textarea", formFieldReason.getType());
        Assertions.assertTrue(formFieldReason.isRequired());
      }
    }
  }

  @Test
  void executeFormDisplayError() {
    // Add the form data which has error
    FormData formData = new FormData();
    widgetContext.setRequestObject(formData);

    // Show a form Error
    try (MockedStatic<RateLimitCommand> rateLimitCommand = mockStatic(RateLimitCommand.class)) {
      rateLimitCommand.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), anyBoolean())).thenReturn(true);

      FormWidget widget = new FormWidget();
      widget.execute(widgetContext);
      Assertions.assertEquals(FormWidget.JSP, widgetContext.getJsp());
    }
  }

  @Test
  void executeFormDisplaySuccess() {
    // Uses the posted success status
    widgetContext.addSharedRequestValue(widgetContext.getUniqueId() + "formWidgetSuccess", "true");

    // Show the form submit success message
    try (MockedStatic<RateLimitCommand> rateLimitCommand = mockStatic(RateLimitCommand.class)) {
      rateLimitCommand.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), anyBoolean())).thenReturn(true);
      
      FormWidget widget = new FormWidget();
      widget.execute(widgetContext);
      Assertions.assertEquals(FormWidget.SUCCESS_JSP, widgetContext.getJsp());
    }
  }

  @Test
  void postSuccess() {
    // Set widget preferences
    initCommonPreferences();
    Assertions.assertEquals(7, widgetContext.getPreferences().size());

    // Set the session values
    session.setAttribute(SessionConstants.CAPTCHA_TEXT, "G1B8A");

    // Set the request values
    addQueryParameter(widgetContext, "captcha", "G1B8A");
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "name", "First Last");
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "organization", "Organization");
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "email", "email@example.com");
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "comments", "These are my comments.");

    // Execute the widget
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.sitekey")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.secretkey")).thenReturn(null);

      try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
        formDataRepositoryMockedStatic.when(() -> FormDataRepository.save(any())).thenReturn(new FormData());

        try (MockedStatic<WorkflowManager> workflowManagerMockedStatic = mockStatic(WorkflowManager.class);
            MockedStatic<FunnelEventCommand> funnelEventCommand = mockStatic(FunnelEventCommand.class)) {

          // Execute
          FormWidget widget = new FormWidget();
          WidgetContext result = widget.post(widgetContext);

          // Verify
          Assertions.assertNull(result);
          Assertions.assertNull(widgetContext.getWarningMessage());
          Assertions.assertNull(widgetContext.getErrorMessage());
          workflowManagerMockedStatic.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()));
          Assertions.assertEquals("true", widgetContext.getSharedRequestValue(widgetContext.getUniqueId() + "formWidgetSuccess"));
          // issue #565 phase 1 -- a successful submission must be offered to the funnel tracker; the
          // tracker itself decides (via site properties) whether "contact" is the configured funnel
          funnelEventCommand.verify(() -> FunnelEventCommand.recordContactFormSubmitted(eq("contact"), any()));
        }
      }
    }
  }

  @Test
  void postError() {
    // Set widget preferences
    initCommonPreferences();
    Assertions.assertEquals(7, widgetContext.getPreferences().size());

    // Set the session values
    session.setAttribute(SessionConstants.CAPTCHA_TEXT, "G1B8A");

    // Set the request values
    addQueryParameter(widgetContext, "captcha", "G1B8A");
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "name", "First Last");
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "organization", "Organization");

    // Execute the widget
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.sitekey")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.secretkey")).thenReturn(null);

      try (MockedStatic<FormSubmissionFailureRepository> failureRepository = mockStatic(FormSubmissionFailureRepository.class)) {
        // Execute
        FormWidget widget = new FormWidget();
        WidgetContext result = widget.post(widgetContext);

        // Verify
        Assertions.assertNotNull(result);
        Assertions.assertEquals("How Can We Help? is required", widgetContext.getWarningMessage());
        Assertions.assertNull(widgetContext.getErrorMessage());
        // issue #563 -- the rejection must be recorded, with the missing-field reason winning since it's
        // the only field that failed
        failureRepository.verify(() -> FormSubmissionFailureRepository.record(
            eq("contact"), eq(FormSubmissionFailureRepository.REASON_MISSING_FIELD), any(), any()));
      }
    }
  }

  @Test
  void postInvalidEmailRecordsTheInvalidEmailReason() {
    initCommonPreferences();
    session.setAttribute(SessionConstants.CAPTCHA_TEXT, "G1B8A");

    addQueryParameter(widgetContext, "captcha", "G1B8A");
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "name", "First Last");
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "email", "not-an-email");
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "comments", "These are my comments.");

    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.sitekey")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.secretkey")).thenReturn(null);

      try (MockedStatic<FormSubmissionFailureRepository> failureRepository = mockStatic(FormSubmissionFailureRepository.class)) {
        FormWidget widget = new FormWidget();
        WidgetContext result = widget.post(widgetContext);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("Check the email address and try again", widgetContext.getWarningMessage());
        failureRepository.verify(() -> FormSubmissionFailureRepository.record(
            eq("contact"), eq(FormSubmissionFailureRepository.REASON_INVALID_EMAIL), any(), any()));
      }
    }
  }

  @Test
  void postAllBlankFieldsRecordsTheBlankReason() {
    // The REASON_BLANK branch only fires when isValid is still true after the per-field loop (see
    // FormWidget.post()) -- i.e. a form with NO required fields, entirely unfilled. initCommonPreferences()
    // has required fields, so blanking everything there trips REASON_MISSING_FIELD first; this needs its
    // own all-optional field config to reach the branch under test.
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"form\">\n" +
            "  <formUniqueId>newsletter</formUniqueId>\n" +
            "  <fields>\n" +
            "    <field name=\"Company (optional)\" value=\"company\" />\n" +
            "    <field name=\"Referral source (optional)\" value=\"referral\" />\n" +
            "  </fields>\n" +
            "</widget>");
    session.setAttribute(SessionConstants.CAPTCHA_TEXT, "G1B8A");
    addQueryParameter(widgetContext, "captcha", "G1B8A");
    // No field values at all -- both optional fields are blank

    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.sitekey")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.secretkey")).thenReturn(null);

      try (MockedStatic<FormSubmissionFailureRepository> failureRepository = mockStatic(FormSubmissionFailureRepository.class)) {
        FormWidget widget = new FormWidget();
        widget.post(widgetContext);

        Assertions.assertEquals("Check the form and try again", widgetContext.getWarningMessage());
        failureRepository.verify(() -> FormSubmissionFailureRepository.record(
            eq("newsletter"), eq(FormSubmissionFailureRepository.REASON_BLANK), any(), any()));
      }
    }
  }

  @Test
  void postCaptchaFailureRecordsTheCaptchaReason() {
    initCommonPreferences();
    // No CAPTCHA_TEXT in session, so CaptchaCommand.validateRequest will fail naturally -- but stub it
    // directly to keep this test independent of that command's own internals.
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "name", "First Last");
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "email", "email@example.com");
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "comments", "These are my comments.");

    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.sitekey")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.secretkey")).thenReturn(null);

      try (MockedStatic<CaptchaCommand> captchaCommand = mockStatic(CaptchaCommand.class)) {
        captchaCommand.when(() -> CaptchaCommand.validateRequest(any())).thenReturn(false);

        try (MockedStatic<FormSubmissionFailureRepository> failureRepository = mockStatic(FormSubmissionFailureRepository.class)) {
          FormWidget widget = new FormWidget();
          WidgetContext result = widget.post(widgetContext);

          Assertions.assertNotNull(result);
          Assertions.assertEquals("The form could not be validated", widgetContext.getWarningMessage());
          failureRepository.verify(() -> FormSubmissionFailureRepository.record(
              eq("contact"), eq(FormSubmissionFailureRepository.REASON_CAPTCHA_FAILED), any(), any()));
        }
      }
    }
  }

  @Test
  void postRateLimitedRecordsTheRateLimitedReason() {
    initCommonPreferences();
    // The post-time rate limit only applies to anonymous submitters (see FormWidget.post()); WidgetBase
    // logs a user in by default, so this must explicitly log out to reach that branch at all.
    logout(widgetContext);
    session.setAttribute(SessionConstants.CAPTCHA_TEXT, "G1B8A");
    addQueryParameter(widgetContext, "captcha", "G1B8A");
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "name", "First Last");
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "email", "email@example.com");
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "comments", "These are my comments.");

    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.sitekey")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.secretkey")).thenReturn(null);

      try (MockedStatic<RateLimitCommand> rateLimitCommand = mockStatic(RateLimitCommand.class)) {
        // Not logged in + the second, stricter post-time rate limit check fails
        rateLimitCommand.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(false);

        try (MockedStatic<FormSubmissionFailureRepository> failureRepository = mockStatic(FormSubmissionFailureRepository.class)) {
          FormWidget widget = new FormWidget();
          WidgetContext result = widget.post(widgetContext);

          Assertions.assertNotNull(result);
          Assertions.assertNotNull(widgetContext.getErrorMessage());
          failureRepository.verify(() -> FormSubmissionFailureRepository.record(
              eq("contact"), eq(FormSubmissionFailureRepository.REASON_RATE_LIMITED), any(), any()));
        }
      }
    }
  }

  private void addCheckboxGroupFieldPreferences(boolean required) {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"form\">\n" +
            "  <formUniqueId>survey</formUniqueId>\n" +
            "  <fields>\n" +
            "    <field name=\"Which departments interest you?\" value=\"departments\" type=\"checkbox\"" +
            " list=\"sales=Sales,marketing=Marketing,tech=Technical\" required=\"" + required + "\" />\n" +
            "  </fields>\n" +
            "</widget>");
  }

  @Test
  void postJoinsMultipleCheckedCheckboxGroupOptionsInListOrder() {
    // A checkbox group submits one repeated-name parameter per checked box -- getParameter() would
    // only see the first. Submitted out of list order (tech before sales) to confirm the stored
    // value follows listOfOptions order, not submission order.
    addCheckboxGroupFieldPreferences(false);
    widgetContext.getParameterMap().put(widgetContext.getUniqueId() + "departments", new String[] { "tech", "sales" });

    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.sitekey")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.secretkey")).thenReturn(null);

      try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
        ArgumentCaptor<FormData> savedFormData = ArgumentCaptor.forClass(FormData.class);
        formDataRepositoryMockedStatic.when(() -> FormDataRepository.save(savedFormData.capture())).thenReturn(new FormData());

        try (MockedStatic<WorkflowManager> workflowManagerMockedStatic = mockStatic(WorkflowManager.class);
            MockedStatic<FunnelEventCommand> funnelEventCommand = mockStatic(FunnelEventCommand.class)) {

          FormWidget widget = new FormWidget();
          WidgetContext result = widget.post(widgetContext);

          Assertions.assertNull(result);
          FormField departmentsField = savedFormData.getValue().getFormFieldList().get(0);
          Assertions.assertEquals("Sales,Technical", departmentsField.getUserValue());
        }
      }
    }
  }

  @Test
  void postJoinsSingleCheckedCheckboxGroupOptionWithoutTrailingComma() {
    addCheckboxGroupFieldPreferences(false);
    widgetContext.getParameterMap().put(widgetContext.getUniqueId() + "departments", new String[] { "marketing" });

    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.sitekey")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.secretkey")).thenReturn(null);

      try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
        ArgumentCaptor<FormData> savedFormData = ArgumentCaptor.forClass(FormData.class);
        formDataRepositoryMockedStatic.when(() -> FormDataRepository.save(savedFormData.capture())).thenReturn(new FormData());

        try (MockedStatic<WorkflowManager> workflowManagerMockedStatic = mockStatic(WorkflowManager.class);
            MockedStatic<FunnelEventCommand> funnelEventCommand = mockStatic(FunnelEventCommand.class)) {

          FormWidget widget = new FormWidget();
          widget.post(widgetContext);

          FormField departmentsField = savedFormData.getValue().getFormFieldList().get(0);
          Assertions.assertEquals("Marketing", departmentsField.getUserValue());
        }
      }
    }
  }

  @Test
  void postDeduplicatesRepeatedCheckboxGroupValues() {
    addCheckboxGroupFieldPreferences(false);
    widgetContext.getParameterMap().put(widgetContext.getUniqueId() + "departments", new String[] { "sales", "sales" });

    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.sitekey")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.secretkey")).thenReturn(null);

      try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
        ArgumentCaptor<FormData> savedFormData = ArgumentCaptor.forClass(FormData.class);
        formDataRepositoryMockedStatic.when(() -> FormDataRepository.save(savedFormData.capture())).thenReturn(new FormData());

        try (MockedStatic<WorkflowManager> workflowManagerMockedStatic = mockStatic(WorkflowManager.class);
            MockedStatic<FunnelEventCommand> funnelEventCommand = mockStatic(FunnelEventCommand.class)) {

          FormWidget widget = new FormWidget();
          widget.post(widgetContext);

          FormField departmentsField = savedFormData.getValue().getFormFieldList().get(0);
          Assertions.assertEquals("Sales", departmentsField.getUserValue());
        }
      }
    }
  }

  @Test
  void postRequiredCheckboxGroupWithNoneCheckedIsRejected() {
    addCheckboxGroupFieldPreferences(true);
    // No parameter submitted at all for the group -- equivalent to every box left unchecked

    try (MockedStatic<FormSubmissionFailureRepository> failureRepository = mockStatic(FormSubmissionFailureRepository.class)) {
      FormWidget widget = new FormWidget();
      WidgetContext result = widget.post(widgetContext);

      Assertions.assertNotNull(result);
      Assertions.assertEquals("Which departments interest you? is required", widgetContext.getWarningMessage());
      failureRepository.verify(() -> FormSubmissionFailureRepository.record(
          eq("survey"), eq(FormSubmissionFailureRepository.REASON_MISSING_FIELD), any(), any()));
    }
  }

  @Test
  void postCapturesCheckedSingleToggleCheckboxValue() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"form\">\n" +
            "  <formUniqueId>subscribe</formUniqueId>\n" +
            "  <fields>\n" +
            "    <field name=\"Subscribe to updates\" value=\"subscribe\" type=\"checkbox\" required=\"true\" />\n" +
            "  </fields>\n" +
            "</widget>");
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "subscribe", "true");

    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      ArgumentCaptor<FormData> savedFormData = ArgumentCaptor.forClass(FormData.class);
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.save(savedFormData.capture())).thenReturn(new FormData());

      try (MockedStatic<WorkflowManager> workflowManagerMockedStatic = mockStatic(WorkflowManager.class);
          MockedStatic<FunnelEventCommand> funnelEventCommand = mockStatic(FunnelEventCommand.class)) {

        FormWidget widget = new FormWidget();
        WidgetContext result = widget.post(widgetContext);

        Assertions.assertNull(result);
        FormField subscribeField = savedFormData.getValue().getFormFieldList().get(0);
        Assertions.assertEquals("true", subscribeField.getUserValue());
      }
    }
  }

  @Test
  void postRequiredSingleToggleCheckboxUncheckedIsRejected() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"form\">\n" +
            "  <formUniqueId>subscribe</formUniqueId>\n" +
            "  <fields>\n" +
            "    <field name=\"Subscribe to updates\" value=\"subscribe\" type=\"checkbox\" required=\"true\" />\n" +
            "  </fields>\n" +
            "</widget>");
    // Unchecked -- the browser submits nothing for this parameter at all

    try (MockedStatic<FormSubmissionFailureRepository> failureRepository = mockStatic(FormSubmissionFailureRepository.class)) {
      FormWidget widget = new FormWidget();
      WidgetContext result = widget.post(widgetContext);

      Assertions.assertNotNull(result);
      Assertions.assertEquals("Subscribe to updates is required", widgetContext.getWarningMessage());
      failureRepository.verify(() -> FormSubmissionFailureRepository.record(
          eq("subscribe"), eq(FormSubmissionFailureRepository.REASON_MISSING_FIELD), any(), any()));
    }
  }

  @Test
  void rateLimitError() {
    // Show a form Error
    try (MockedStatic<RateLimitCommand> rateLimitCommand = mockStatic(RateLimitCommand.class)) {
      rateLimitCommand.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), anyBoolean())).thenReturn(false);

      FormWidget widget = new FormWidget();
      widget.execute(widgetContext);
      Assertions.assertEquals(FormWidget.RATE_LIMITED_JSP, widgetContext.getJsp());
    }
  }
}