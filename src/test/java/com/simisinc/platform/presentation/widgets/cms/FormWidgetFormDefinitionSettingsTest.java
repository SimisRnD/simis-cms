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

package com.simisinc.platform.presentation.widgets.cms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.FormCommand;
import com.simisinc.platform.application.cms.FunnelEventCommand;
import com.simisinc.platform.domain.events.cms.FormSubmittedEvent;
import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.cms.FormDefinition;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormDefinitionRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormFieldRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormSubmissionFailureRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Verifies that a database-backed form's own settings (issue #409 follow-up) -- enabled,
 * useCaptcha, checkForSpam, emailTo, title, subtitle, buttonName, successTitle, successMessage --
 * actually take effect once {@code formId} is configured, rather than the widget placement's XML
 * preferences silently continuing to be the only thing that matters. Uses mockStatic on the
 * repositories (like {@code FormFieldFormWidgetTest}), so it needs no real database -- {@link
 * FormWidgetDatabaseFormIntegrationTest} covers the same {@code formId} path end-to-end against real
 * Postgres.
 *
 * @author SimIS Inc.
 */
class FormWidgetFormDefinitionSettingsTest extends WidgetBase {

  @Test
  void executeReturnsNullWhenTheDatabaseBackedFormIsDisabledForAnAnonymousVisitor() {
    preferences.put("formId", "5");
    FormDefinition disabled = new FormDefinition();
    disabled.setId(5L);
    disabled.setEnabled(false);

    try (MockedStatic<RateLimitCommand> rateLimitCommand = mockStatic(RateLimitCommand.class);
        MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class)) {
      rateLimitCommand.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), anyBoolean())).thenReturn(true);
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(5L)).thenReturn(disabled);

      WidgetContext result = new FormWidget().execute(widgetContext);

      Assertions.assertNull(result, "a disabled database-backed form must not render for the public");
    }
  }

  @Test
  void executeStillRendersADisabledDatabaseBackedFormForAnAdmin() {
    preferences.put("formId", "5");
    setRoles(widgetContext, ADMIN);
    FormDefinition disabled = new FormDefinition();
    disabled.setId(5L);
    disabled.setEnabled(false);
    FormField field = new FormField();
    field.setName("name");
    field.setLabel("Name");

    try (MockedStatic<RateLimitCommand> rateLimitCommand = mockStatic(RateLimitCommand.class);
        MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      rateLimitCommand.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), anyBoolean())).thenReturn(true);
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(5L)).thenReturn(disabled);
      formFieldRepository.when(() -> FormFieldRepository.findAllByFormDefinitionId(5L)).thenReturn(List.of(field));

      WidgetContext result = new FormWidget().execute(widgetContext);

      Assertions.assertNotNull(result, "an admin must still be able to preview a disabled form");
      Assertions.assertEquals(FormWidget.JSP, result.getJsp());
    }
  }

  @Test
  void postRejectsASubmissionToADisabledDatabaseBackedForm() {
    preferences.put("formId", "5");
    FormDefinition disabled = new FormDefinition();
    disabled.setId(5L);
    disabled.setUniqueId("disabled-form");
    disabled.setEnabled(false);

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormSubmissionFailureRepository> failureRepository = mockStatic(FormSubmissionFailureRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(5L)).thenReturn(disabled);

      WidgetContext result = new FormWidget().post(widgetContext);

      Assertions.assertNull(result, "a direct POST to a disabled database-backed form must not be accepted");
      // issue #563 follow-up -- this rejection was previously silent; must not be reachable without
      // recording it, and must not reuse REASON_MISSING_FIELD
      failureRepository.verify(() -> FormSubmissionFailureRepository.record(
          eq("disabled-form"), eq(FormSubmissionFailureRepository.REASON_FORM_UNAVAILABLE), any(), any()));
    }
  }

  @Test
  void executeReturnsNullWhenFormIdIsSetButTheFormDefinitionIsNotFound() {
    preferences.put("formId", "99");

    try (MockedStatic<RateLimitCommand> rateLimitCommand = mockStatic(RateLimitCommand.class);
        MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class)) {
      rateLimitCommand.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), anyBoolean())).thenReturn(true);
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(99L)).thenReturn(null);

      WidgetContext result = new FormWidget().execute(widgetContext);

      Assertions.assertNull(result, "a stale/bad formId must hard-fail, not silently fall back to the XML fields preference");
    }
  }

  @Test
  void postRecordsAFailureWhenFormIdIsSetButTheFormDefinitionIsNotFound() {
    // issue #563 follow-up -- a direct POST naming a formId that no longer resolves was previously
    // silent, unlike every other rejection path in FormWidget.post()
    preferences.put("formId", "99");
    preferences.put("formUniqueId", "widget-preference-slug");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormSubmissionFailureRepository> failureRepository = mockStatic(FormSubmissionFailureRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(99L)).thenReturn(null);

      WidgetContext result = new FormWidget().post(widgetContext);

      Assertions.assertNull(result, "a stale/bad formId must hard-fail, not silently fall back to the XML fields preference");
      // formDefinition never resolved, so the widget-preference formUniqueId is the only identifier
      // available -- matching how the success path resolves formUniqueId when formDefinition is null
      failureRepository.verify(() -> FormSubmissionFailureRepository.record(
          eq("widget-preference-slug"), eq(FormSubmissionFailureRepository.REASON_FORM_UNAVAILABLE), any(), any()));
    }
  }

  @Test
  void executeUsesTheFormDefinitionsOwnUseCaptchaSettingInsteadOfTheWidgetPreference() {
    preferences.put("formId", "7");
    preferences.put("useCaptcha", "false");
    FormDefinition form = new FormDefinition();
    form.setId(7L);
    form.setEnabled(true);
    form.setUseCaptcha(true);
    FormField field = new FormField();
    field.setName("name");
    field.setLabel("Name");

    try (MockedStatic<RateLimitCommand> rateLimitCommand = mockStatic(RateLimitCommand.class);
        MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class);
        MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      rateLimitCommand.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), anyBoolean())).thenReturn(true);
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(7L)).thenReturn(form);
      formFieldRepository.when(() -> FormFieldRepository.findAllByFormDefinitionId(7L)).thenReturn(List.of(field));
      property.when(() -> LoadSitePropertyCommand.loadByName(any())).thenReturn(null);

      new FormWidget().execute(widgetContext);

      Assertions.assertEquals("true", request.getAttribute("useCaptcha"),
          "the form definition's own useCaptcha=true must win even though the widget preference says false");
    }
  }

  @Test
  void postSkipsSpamCheckingWhenTheFormDefinitionsCheckForSpamIsFalseEvenIfTheWidgetPreferenceSaysTrue() {
    preferences.put("formId", "9");
    preferences.put("checkForSpam", "true");
    FormDefinition form = new FormDefinition();
    form.setId(9L);
    form.setEnabled(true);
    form.setCheckForSpam(false);
    FormField field = new FormField();
    field.setName("name");
    field.setLabel("Name");

    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "name", "First Last");

    // post()'s rate limiter only applies to anonymous submitters, and WidgetBase logs a user in by
    // default, so RateLimitCommand is never reached here -- no need to mock it.
    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class);
        MockedStatic<FormCommand> formCommand = mockStatic(FormCommand.class);
        MockedStatic<FormDataRepository> formDataRepository = mockStatic(FormDataRepository.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class);
        MockedStatic<FunnelEventCommand> funnelEventCommand = mockStatic(FunnelEventCommand.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(9L)).thenReturn(form);
      formFieldRepository.when(() -> FormFieldRepository.findAllByFormDefinitionId(9L)).thenReturn(List.of(field));
      formDataRepository.when(() -> FormDataRepository.save(any())).thenReturn(new FormData());

      WidgetContext result = new FormWidget().post(widgetContext);

      Assertions.assertNull(result, "a successful submission redirects (post() returns null)");
      formCommand.verify(() -> FormCommand.checkNotificationRules(any()), never());
    }
  }

  @Test
  void postUsesTheFormDefinitionsOwnEmailToWhenNotifyingRatherThanTheWidgetPreference() {
    preferences.put("formId", "11");
    preferences.put("emailTo", "widget-preference@example.com");
    FormDefinition form = new FormDefinition();
    form.setId(11L);
    form.setEnabled(true);
    form.setCheckForSpam(false);
    form.setEmailTo("form-definition@example.com");
    FormField field = new FormField();
    field.setName("name");
    field.setLabel("Name");

    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "name", "First Last");

    // post()'s rate limiter only applies to anonymous submitters, and WidgetBase logs a user in by
    // default, so RateLimitCommand is never reached here -- no need to mock it.
    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class);
        MockedStatic<FormDataRepository> formDataRepository = mockStatic(FormDataRepository.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class);
        MockedStatic<FunnelEventCommand> funnelEventCommand = mockStatic(FunnelEventCommand.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(11L)).thenReturn(form);
      formFieldRepository.when(() -> FormFieldRepository.findAllByFormDefinitionId(11L)).thenReturn(List.of(field));
      formDataRepository.when(() -> FormDataRepository.save(any())).thenReturn(new FormData());

      new FormWidget().post(widgetContext);

      ArgumentCaptor<FormSubmittedEvent> eventCaptor = ArgumentCaptor.forClass(FormSubmittedEvent.class);
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(eventCaptor.capture()));
      Assertions.assertEquals("form-definition@example.com", eventCaptor.getValue().getEmailAddressesTo());
    }
  }

  @Test
  void executeUsesTheFormDefinitionsOwnTitleAndSubtitleInsteadOfTheWidgetPreferences() {
    preferences.put("formId", "13");
    preferences.put("title", "Widget Preference Title");
    preferences.put("subtitle", "Widget Preference Subtitle");
    FormDefinition form = new FormDefinition();
    form.setId(13L);
    form.setEnabled(true);
    form.setTitle("Form Definition Title");
    form.setSubtitle("Form Definition Subtitle");
    FormField field = new FormField();
    field.setName("name");
    field.setLabel("Name");

    try (MockedStatic<RateLimitCommand> rateLimitCommand = mockStatic(RateLimitCommand.class);
        MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      rateLimitCommand.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), anyBoolean())).thenReturn(true);
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(13L)).thenReturn(form);
      formFieldRepository.when(() -> FormFieldRepository.findAllByFormDefinitionId(13L)).thenReturn(List.of(field));

      new FormWidget().execute(widgetContext);

      Assertions.assertEquals("Form Definition Title", request.getAttribute("title"),
          "the form definition's own title must win over the widget preference");
      Assertions.assertEquals("Form Definition Subtitle", request.getAttribute("subtitle"),
          "the form definition's own subtitle must win over the widget preference");
    }
  }

  @Test
  void executeUsesTheFormDefinitionsOwnButtonNameInsteadOfTheWidgetPreference() {
    preferences.put("formId", "14");
    preferences.put("buttonName", "Widget Preference Button");
    FormDefinition form = new FormDefinition();
    form.setId(14L);
    form.setEnabled(true);
    form.setButtonName("Send It");
    FormField field = new FormField();
    field.setName("name");
    field.setLabel("Name");

    try (MockedStatic<RateLimitCommand> rateLimitCommand = mockStatic(RateLimitCommand.class);
        MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      rateLimitCommand.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), anyBoolean())).thenReturn(true);
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(14L)).thenReturn(form);
      formFieldRepository.when(() -> FormFieldRepository.findAllByFormDefinitionId(14L)).thenReturn(List.of(field));

      new FormWidget().execute(widgetContext);

      Assertions.assertEquals("Send It", request.getAttribute("buttonName"),
          "the form definition's own button label must win over the widget preference");
    }
  }

  @Test
  void executeFallsBackToTheDefaultButtonNameWhenTheFormDefinitionsButtonNameIsBlank() {
    preferences.put("formId", "15");
    FormDefinition form = new FormDefinition();
    form.setId(15L);
    form.setEnabled(true);
    FormField field = new FormField();
    field.setName("name");
    field.setLabel("Name");

    try (MockedStatic<RateLimitCommand> rateLimitCommand = mockStatic(RateLimitCommand.class);
        MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class)) {
      rateLimitCommand.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), anyBoolean())).thenReturn(true);
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(15L)).thenReturn(form);
      formFieldRepository.when(() -> FormFieldRepository.findAllByFormDefinitionId(15L)).thenReturn(List.of(field));

      new FormWidget().execute(widgetContext);

      Assertions.assertEquals("Submit", request.getAttribute("buttonName"),
          "an admin who left the form definition's button label blank should still get the same default the XML-preference path always applied");
    }
  }

  @Test
  void executeUsesTheFormDefinitionsOwnSuccessTitleAndMessageOnTheSuccessPage() {
    preferences.put("formId", "16");
    preferences.put("successTitle", "Widget Preference Success Title");
    preferences.put("successMessage", "Widget preference success message");
    FormDefinition form = new FormDefinition();
    form.setId(16L);
    form.setEnabled(true);
    form.setSuccessTitle("Thanks!");
    form.setSuccessMessage("We'll be in touch soon.");
    widgetContext.addSharedRequestValue(widgetContext.getUniqueId() + "formWidgetSuccess", "true");

    try (MockedStatic<RateLimitCommand> rateLimitCommand = mockStatic(RateLimitCommand.class);
        MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class)) {
      rateLimitCommand.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), anyBoolean())).thenReturn(true);
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(16L)).thenReturn(form);

      WidgetContext result = new FormWidget().execute(widgetContext);

      Assertions.assertEquals(FormWidget.SUCCESS_JSP, result.getJsp());
      Assertions.assertEquals("Thanks!", request.getAttribute("successTitle"),
          "the form definition's own success title must win over the widget preference");
      Assertions.assertEquals("We'll be in touch soon.", request.getAttribute("successMessage"),
          "the form definition's own success message must win over the widget preference");
    }
  }

  @Test
  void executeFallsBackToTheDefaultSuccessMessageWhenTheFormDefinitionsSuccessMessageIsBlank() {
    preferences.put("formId", "17");
    FormDefinition form = new FormDefinition();
    form.setId(17L);
    form.setEnabled(true);
    widgetContext.addSharedRequestValue(widgetContext.getUniqueId() + "formWidgetSuccess", "true");

    try (MockedStatic<RateLimitCommand> rateLimitCommand = mockStatic(RateLimitCommand.class);
        MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class)) {
      rateLimitCommand.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), anyBoolean())).thenReturn(true);
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(17L)).thenReturn(form);

      new FormWidget().execute(widgetContext);

      Assertions.assertNull(request.getAttribute("successTitle"),
          "a blank form definition success title has no default to fall back to, matching the XML-preference path's plain get()");
      Assertions.assertEquals("Your information has been submitted.", request.getAttribute("successMessage"),
          "a blank form definition success message should still get the same default the XML-preference path always applied");
    }
  }

  @Test
  void postUsesTheFormDefinitionsOwnUniqueIdInsteadOfTheWidgetPreference() {
    preferences.put("formId", "21");
    preferences.put("formUniqueId", "widget-preference-slug");
    FormDefinition form = new FormDefinition();
    form.setId(21L);
    form.setEnabled(true);
    form.setUniqueId("form-definition-slug");
    form.setCheckForSpam(false);
    FormField field = new FormField();
    field.setName("name");
    field.setLabel("Name");

    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "name", "First Last");

    // post()'s rate limiter only applies to anonymous submitters, and WidgetBase logs a user in by
    // default, so RateLimitCommand is never reached here -- no need to mock it.
    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<FormFieldRepository> formFieldRepository = mockStatic(FormFieldRepository.class);
        MockedStatic<FormDataRepository> formDataRepository = mockStatic(FormDataRepository.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class);
        MockedStatic<FunnelEventCommand> funnelEventCommand = mockStatic(FunnelEventCommand.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(21L)).thenReturn(form);
      formFieldRepository.when(() -> FormFieldRepository.findAllByFormDefinitionId(21L)).thenReturn(List.of(field));
      formDataRepository.when(() -> FormDataRepository.save(any())).thenReturn(new FormData());

      new FormWidget().post(widgetContext);

      ArgumentCaptor<FormData> formDataCaptor = ArgumentCaptor.forClass(FormData.class);
      formDataRepository.verify(() -> FormDataRepository.save(formDataCaptor.capture()));
      Assertions.assertEquals("form-definition-slug", formDataCaptor.getValue().getFormUniqueId(),
          "form_data must be keyed by the form definition's own collision-checked uniqueId, not a "
              + "hand-typed widget preference that could collide with an unrelated form");
      funnelEventCommand.verify(() -> FunnelEventCommand.recordContactFormSubmitted(eq("form-definition-slug"), any()));
    }
  }
}
