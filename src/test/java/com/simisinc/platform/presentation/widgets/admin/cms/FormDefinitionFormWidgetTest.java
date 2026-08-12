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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.lang.reflect.InvocationTargetException;
import java.net.ConnectException;

import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.ImageHtmlEmail;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.email.EmailCommand;
import com.simisinc.platform.domain.model.cms.FormDefinition;
import com.simisinc.platform.infrastructure.persistence.cms.FormDefinitionRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * @author SimIS Inc.
 */
class FormDefinitionFormWidgetTest extends WidgetBase {

  @Test
  void executeLoadsAnExistingFormById() {
    FormDefinition contactUs = new FormDefinition();
    contactUs.setId(5L);
    contactUs.setName("Contact Us");

    addQueryParameter(widgetContext, "formDefinitionId", "5");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(5L)).thenReturn(contactUs);

      WidgetContext result = new FormDefinitionFormWidget().execute(widgetContext);

      Assertions.assertEquals(FormDefinitionFormWidget.JSP, result.getJsp());
      FormDefinition requestBean = (FormDefinition) request.getAttribute("formDefinition");
      Assertions.assertEquals(5L, requestBean.getId());
      Assertions.assertEquals("Contact Us", requestBean.getName());
    }
  }

  @Test
  void executeWithNoIdFallsBackToABlankBean() {
    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(-1L)).thenReturn(null);

      WidgetContext result = new FormDefinitionFormWidget().execute(widgetContext);

      FormDefinition requestBean = (FormDefinition) request.getAttribute("formDefinition");
      Assertions.assertNotNull(requestBean);
      Assertions.assertEquals(-1L, requestBean.getId());
    }
  }

  @Test
  void postSavesANewFormAndRedirectsToItsEditor() throws InvocationTargetException, IllegalAccessException {
    addQueryParameter(widgetContext, "id", "-1");
    addQueryParameter(widgetContext, "name", "Contact Us");
    addQueryParameter(widgetContext, "title", "Get in touch");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findByUniqueId(any())).thenReturn(null);
      formDefinitionRepository.when(() -> FormDefinitionRepository.save(any())).thenAnswer(invocation -> {
        FormDefinition savedRecord = invocation.getArgument(0);
        savedRecord.setId(9L);
        return savedRecord;
      });

      WidgetContext result = new FormDefinitionFormWidget().post(widgetContext);

      Assertions.assertEquals("Form was saved", result.getSuccessMessage());
      Assertions.assertEquals("/admin/forms-editor?formDefinitionId=9", result.getRedirect());
    }
  }

  @Test
  void postWithABlankNameFailsValidationAndDoesNotSave() throws InvocationTargetException, IllegalAccessException {
    addQueryParameter(widgetContext, "id", "-1");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class)) {
      WidgetContext result = new FormDefinitionFormWidget().post(widgetContext);

      Assertions.assertNotNull(result.getErrorMessage());
      Assertions.assertNull(result.getRedirect());
      formDefinitionRepository.verify(() -> FormDefinitionRepository.save(any()), never());
    }
  }

  /**
   * Guards against reintroducing the bug where unchecking "Enabled?" or "Check for spam?" had no
   * effect: both fields default to true on a fresh FormDefinition bean, and BeanUtils.populate()
   * never overwrites a property whose checkbox parameter is simply absent from the request (as an
   * unchecked checkbox always is), so a form could never actually be disabled through this widget.
   */
  @Test
  void postWithBothCheckboxesUncheckedSavesThemAsFalse() throws InvocationTargetException, IllegalAccessException {
    FormDefinition existing = new FormDefinition();
    existing.setId(5L);
    existing.setUniqueId("contact-us");
    existing.setName("Contact Us");
    existing.setEnabled(true);
    existing.setCheckForSpam(true);

    // Neither "enabled" nor "checkForSpam" is present -- an unchecked HTML checkbox sends no
    // parameter at all, this is not a value of "false" being submitted
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "name", "Contact Us");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(5L)).thenReturn(existing);
      formDefinitionRepository.when(() -> FormDefinitionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      new FormDefinitionFormWidget().post(widgetContext);

      Assertions.assertFalse(existing.getEnabled());
      Assertions.assertFalse(existing.getCheckForSpam());
    }
  }

  @Test
  void postWithBothCheckboxesCheckedSavesThemAsTrue() throws InvocationTargetException, IllegalAccessException {
    FormDefinition existing = new FormDefinition();
    existing.setId(5L);
    existing.setUniqueId("contact-us");
    existing.setName("Contact Us");
    existing.setEnabled(false);
    existing.setCheckForSpam(false);

    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "name", "Contact Us");
    addQueryParameter(widgetContext, "enabled", "true");
    addQueryParameter(widgetContext, "checkForSpam", "true");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(5L)).thenReturn(existing);
      formDefinitionRepository.when(() -> FormDefinitionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      new FormDefinitionFormWidget().post(widgetContext);

      Assertions.assertTrue(existing.getEnabled());
      Assertions.assertTrue(existing.getCheckForSpam());
    }
  }

  @Test
  void postWithShowPrivacyNoticeCheckedSavesItAsTrue() throws InvocationTargetException, IllegalAccessException {
    // issue #1155 -- defaults to false like useCaptcha, so (unlike enabled/checkForSpam) BeanUtils.populate()
    // alone is sufficient: checked sends the parameter and sets true, unchecked sends nothing and stays false
    FormDefinition existing = new FormDefinition();
    existing.setId(5L);
    existing.setUniqueId("contact-us");
    existing.setName("Contact Us");
    existing.setShowPrivacyNotice(false);

    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "name", "Contact Us");
    addQueryParameter(widgetContext, "showPrivacyNotice", "true");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(5L)).thenReturn(existing);
      formDefinitionRepository.when(() -> FormDefinitionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      new FormDefinitionFormWidget().post(widgetContext);

      Assertions.assertTrue(existing.getShowPrivacyNotice());
    }
  }

  /**
   * Guards against reintroducing the createdBy-on-edit bug this codebase has already hit once in a
   * sibling command (mailing lists) -- editing an existing form must not overwrite createdBy with
   * whoever happens to be saving right now.
   */
  @Test
  void postOnAnExistingFormPreservesTheOriginalCreatedBy() throws InvocationTargetException, IllegalAccessException {
    FormDefinition existing = new FormDefinition();
    existing.setId(5L);
    existing.setUniqueId("contact-us");
    existing.setName("Contact Us");
    existing.setCreatedBy(42L);

    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "name", "Contact Us");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class)) {
      formDefinitionRepository.when(() -> FormDefinitionRepository.findById(5L)).thenReturn(existing);
      formDefinitionRepository.when(() -> FormDefinitionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      // The logged-in test user (see WidgetBase#login) is id 1, which must not clobber createdBy=42
      new FormDefinitionFormWidget().post(widgetContext);

      Assertions.assertEquals(42L, existing.getCreatedBy());
      Assertions.assertEquals(1L, existing.getModifiedBy());
    }
  }

  /**
   * A real ImageHtmlEmail (not a Mockito mock) so the production addTo/setSubject/setMsg calls run
   * their normal validation; only send() is overridden to succeed or throw on command. Mirrors the
   * stub in SendMailWidgetTest.
   */
  private static class StubEmail extends ImageHtmlEmail {
    private final EmailException toThrow;

    StubEmail(EmailException toThrow) {
      this.toThrow = toThrow;
    }

    @Override
    public String send() throws EmailException {
      if (toThrow != null) {
        throw toThrow;
      }
      return "stub-message-id";
    }
  }

  @Test
  void postSendTestEmailSendsToTheTypedAddressWithoutSaving() throws InvocationTargetException, IllegalAccessException {
    addQueryParameter(widgetContext, "action", "sendTestEmail");
    addQueryParameter(widgetContext, "id", "-1");
    addQueryParameter(widgetContext, "name", "Contact Us");
    addQueryParameter(widgetContext, "emailTo", "sales@simis.com, technical@simis.com");

    try (MockedStatic<FormDefinitionRepository> formDefinitionRepository = mockStatic(FormDefinitionRepository.class);
        MockedStatic<EmailCommand> emailCommand = mockStatic(EmailCommand.class, CALLS_REAL_METHODS)) {
      emailCommand.when(EmailCommand::prepareNewEmail).thenReturn(new StubEmail(null));

      WidgetContext result = new FormDefinitionFormWidget().post(widgetContext);

      Assertions.assertEquals("A test email was sent to sales@simis.com, technical@simis.com", result.getSuccessMessage());
      Assertions.assertNull(result.getErrorMessage());
      formDefinitionRepository.verify(() -> FormDefinitionRepository.save(any()), never());
    }
  }

  @Test
  void postSendTestEmailWithABlankAddressDoesNotAttemptToSend() throws InvocationTargetException, IllegalAccessException {
    addQueryParameter(widgetContext, "action", "sendTestEmail");
    addQueryParameter(widgetContext, "id", "-1");
    addQueryParameter(widgetContext, "name", "Contact Us");

    try (MockedStatic<EmailCommand> emailCommand = mockStatic(EmailCommand.class, CALLS_REAL_METHODS)) {
      WidgetContext result = new FormDefinitionFormWidget().post(widgetContext);

      Assertions.assertNotNull(result.getErrorMessage());
      Assertions.assertNull(result.getSuccessMessage());
      emailCommand.verify(EmailCommand::prepareNewEmail, never());
    }
  }

  @Test
  void postSendTestEmailWithAnInvalidAddressDoesNotAttemptToSend() throws InvocationTargetException, IllegalAccessException {
    addQueryParameter(widgetContext, "action", "sendTestEmail");
    addQueryParameter(widgetContext, "id", "-1");
    addQueryParameter(widgetContext, "name", "Contact Us");
    addQueryParameter(widgetContext, "emailTo", "not-an-email");

    try (MockedStatic<EmailCommand> emailCommand = mockStatic(EmailCommand.class, CALLS_REAL_METHODS)) {
      WidgetContext result = new FormDefinitionFormWidget().post(widgetContext);

      Assertions.assertNotNull(result.getErrorMessage());
      Assertions.assertTrue(result.getErrorMessage().contains("not-an-email"));
      emailCommand.verify(EmailCommand::prepareNewEmail, never());
    }
  }

  @Test
  void postSendTestEmailCategorizesAFailureWithoutLeakingTheRawMessage() throws InvocationTargetException, IllegalAccessException {
    addQueryParameter(widgetContext, "action", "sendTestEmail");
    addQueryParameter(widgetContext, "id", "-1");
    addQueryParameter(widgetContext, "name", "Contact Us");
    addQueryParameter(widgetContext, "emailTo", "sales@simis.com");

    EmailException toThrow = new EmailException(new ConnectException("Connection refused to internal-relay.simis.local:25"));

    try (MockedStatic<EmailCommand> emailCommand = mockStatic(EmailCommand.class, CALLS_REAL_METHODS)) {
      emailCommand.when(EmailCommand::prepareNewEmail).thenReturn(new StubEmail(toThrow));

      WidgetContext result = new FormDefinitionFormWidget().post(widgetContext);

      String errorMessage = result.getErrorMessage();
      Assertions.assertNotNull(errorMessage);
      Assertions.assertTrue(errorMessage.contains("connect"));
      Assertions.assertFalse(errorMessage.contains("internal-relay.simis.local"));
    }
  }

  @Test
  void postSendTestEmailOnAnExistingFormDoesNotSaveEvenWithoutARepositoryStub() throws InvocationTargetException, IllegalAccessException {
    // No FormDefinitionRepository stub is set up at all -- if the action dispatch ever fell through
    // to the save path, FormDefinitionRepository.findById() would return null against the real
    // (unmocked) class and this would blow up instead of silently passing, so the absence of a stub
    // here is itself part of the guard against that regression.
    addQueryParameter(widgetContext, "action", "sendTestEmail");
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "name", "Contact Us");
    addQueryParameter(widgetContext, "emailTo", "sales@simis.com");

    try (MockedStatic<EmailCommand> emailCommand = mockStatic(EmailCommand.class, CALLS_REAL_METHODS)) {
      emailCommand.when(EmailCommand::prepareNewEmail).thenReturn(new StubEmail(null));

      WidgetContext result = new FormDefinitionFormWidget().post(widgetContext);

      Assertions.assertEquals("/admin/forms-editor?formDefinitionId=5", result.getRedirect());
      Assertions.assertNotNull(result.getRequestObject());
    }
  }
}
