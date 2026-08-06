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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.admin.SecretSitePropertiesCommand;
import com.simisinc.platform.application.login.StepUpAuthCommand;
import com.simisinc.platform.application.mailinglists.MailChimpCommand;
import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.SitePropertyRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;

/**
 * Tests the site properties editor, including that masked secret fields do not wipe stored values
 *
 * @author elizabeth houser
 */
class SitePropertiesEditorWidgetTest extends WidgetBase {

  private SiteProperty property(String name, String value, String type) {
    SiteProperty siteProperty = new SiteProperty();
    siteProperty.setName(name);
    siteProperty.setValue(value);
    siteProperty.setType(type);
    return siteProperty;
  }

  @Test
  void secretListMatchesKnownProperties() {
    assertTrue(SecretSitePropertiesCommand.isSecret("mail.password"));
    assertTrue(SecretSitePropertiesCommand.isSecret("ecommerce.stripe.production.secret"));
    assertTrue(SecretSitePropertiesCommand.isSecret("captcha.google.secretkey"));
    // Issue #519: Cloudflare Turnstile's secret key gets the same treatment as Google's above
    assertTrue(SecretSitePropertiesCommand.isSecret("captcha.turnstile.secretkey"));
    // Browser-bound publishable values must never be masked
    assertFalse(SecretSitePropertiesCommand.isSecret("ecommerce.stripe.production.key"));
    assertFalse(SecretSitePropertiesCommand.isSecret("captcha.google.sitekey"));
    assertFalse(SecretSitePropertiesCommand.isSecret("captcha.turnstile.sitekey"));
    assertFalse(SecretSitePropertiesCommand.isSecret(null));
  }

  @Test
  void blankSecretSubmissionKeepsTheStoredValue() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"sitePropertiesEditor\">\n" +
        "  <prefix>mail</prefix>\n" +
        "</widget>");

    List<SiteProperty> stored = new ArrayList<>();
    stored.add(property("mail.host.name", "smtp.example.com", null));
    stored.add(property("mail.password", "existing-smtp-password", null));

    // The admin edits the host but leaves the masked password field blank
    addQueryParameter(widgetContext, "mail.host.name", "smtp2.example.com");
    addQueryParameter(widgetContext, "mail.password", "");

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> SitePropertyRepository.findAllByPrefix(anyString())).thenReturn(stored);
      repository.when(() -> SitePropertyRepository.saveAll(eq("mail"), eq(stored), eq(1L), any())).thenReturn(true);

      SitePropertiesEditorWidget widget = new SitePropertiesEditorWidget();
      widget.post(widgetContext);

      assertEquals("smtp2.example.com", stored.get(0).getValue());
      // The blank masked field must not wipe the stored secret
      assertEquals("existing-smtp-password", stored.get(1).getValue());
    }
  }

  @Test
  void aNewSecretValueIsSaved() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"sitePropertiesEditor\">\n" +
        "  <prefix>mail</prefix>\n" +
        "</widget>");

    List<SiteProperty> stored = new ArrayList<>();
    stored.add(property("mail.password", "old-password", null));

    addQueryParameter(widgetContext, "mail.password", "new-password");

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> SitePropertyRepository.findAllByPrefix(anyString())).thenReturn(stored);
      repository.when(() -> SitePropertyRepository.saveAll(eq("mail"), eq(stored), eq(1L), any())).thenReturn(true);

      SitePropertiesEditorWidget widget = new SitePropertiesEditorWidget();
      widget.post(widgetContext);

      assertEquals("new-password", stored.get(0).getValue());
    }
  }

  @Test
  void blankNonSecretSubmissionStillClearsTheValue() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"sitePropertiesEditor\">\n" +
        "  <prefix>site</prefix>\n" +
        "</widget>");

    List<SiteProperty> stored = new ArrayList<>();
    stored.add(property("site.header.line1", "A header", null));

    addQueryParameter(widgetContext, "site.header.line1", "");

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> SitePropertyRepository.findAllByPrefix(anyString())).thenReturn(stored);
      repository.when(() -> SitePropertyRepository.saveAll(eq("site"), eq(stored), eq(1L), any())).thenReturn(true);

      SitePropertiesEditorWidget widget = new SitePropertiesEditorWidget();
      widget.post(widgetContext);

      // Pre-existing behavior for normal fields is unchanged
      assertEquals("", stored.get(0).getValue());
    }
  }

  @Test
  void testMailChimpConnectionActionDoesNotSaveAndShowsTheResult() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"sitePropertiesEditor\">\n" +
        "  <prefix>mailing-list</prefix>\n" +
        "</widget>");

    List<SiteProperty> stored = new ArrayList<>();
    stored.add(property("mailing-list.service", "mailchimp", null));

    addQueryParameter(widgetContext, "action", "testMailChimpConnection");
    // If the action branch fell through to the generic save logic, this bogus value would end up
    // stored -- asserting it doesn't is how this test proves the save path was skipped.
    addQueryParameter(widgetContext, "mailing-list.service", "should-not-be-saved");

    // A real (not mocked) ConnectionTestResult, obtained deterministically without any network
    // call -- blank credentials always short-circuit to a fixed failure result. Its constructor
    // is private, so this is the only way to get a real instance to stub with.
    MailChimpCommand.ConnectionTestResult result;
    try (MockedStatic<LoadSitePropertyCommand> blankSiteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      blankSiteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn("");
      result = MailChimpCommand.testConnection();
    }

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class);
        MockedStatic<MailChimpCommand> mailChimp = mockStatic(MailChimpCommand.class)) {
      repository.when(() -> SitePropertyRepository.findAllByPrefix(anyString())).thenReturn(stored);
      mailChimp.when(MailChimpCommand::testConnection).thenReturn(result);

      SitePropertiesEditorWidget widget = new SitePropertiesEditorWidget();
      widget.post(widgetContext);

      repository.verify(
          () -> SitePropertyRepository.saveAll(anyString(), org.mockito.ArgumentMatchers.anyList(), anyLong(), any()),
          never());
      assertEquals("mailchimp", stored.get(0).getValue(), "the action must not fall through to the save logic");
      assertEquals(SitePropertiesEditorWidget.JSP, widgetContext.getJsp());
    }
  }

  @Test
  void postToASecurityPrefixWithoutStepUpShowsReAuthPanelAndDoesNotSave() {
    // Previously this redirected to /step-up-auth, a page that was never actually merged into the
    // app -- the redirect resolved to a generic "not ready" placeholder and the save silently
    // never happened. This proves the fix: an inline re-auth prompt on the same page instead.
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"sitePropertiesEditor\">\n" +
        "  <prefix>mfa</prefix>\n" +
        "</widget>");

    List<SiteProperty> stored = new ArrayList<>();
    stored.add(property("mfa.required.roles", "admin", "text"));
    addQueryParameter(widgetContext, "mfa.required.roles", "admin,content-manager");

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class)) {
      repository.when(() -> SitePropertyRepository.findAllByPrefix(anyString())).thenReturn(stored);

      new SitePropertiesEditorWidget().post(widgetContext);

      repository.verify(
          () -> SitePropertyRepository.saveAll(anyString(), org.mockito.ArgumentMatchers.anyList(), anyLong(), any()),
          never());
      assertEquals("true", widgetContext.getSharedRequestValue("stepUpRequired"));
      assertNull(widgetContext.getRedirect(), "must not redirect to the dead /step-up-auth page");
      assertEquals(SitePropertiesEditorWidget.JSP, widgetContext.getJsp());
      assertEquals(stored, widgetContext.getRequest().getAttribute("sitePropertyList"),
          "the editor must still have something to render alongside the re-auth prompt");
    }
  }

  @Test
  void postToASecurityPrefixWithAWrongStepUpCredentialShowsAnErrorAndDoesNotSave() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"sitePropertiesEditor\">\n" +
        "  <prefix>mfa</prefix>\n" +
        "</widget>");

    List<SiteProperty> stored = new ArrayList<>();
    stored.add(property("mfa.required.roles", "admin", "text"));
    addQueryParameter(widgetContext, "mfa.required.roles", "admin,content-manager");
    addQueryParameter(widgetContext, "stepUpCredential", "wrong-password");

    User actingUser = new User();
    actingUser.setId(1L);

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
        MockedStatic<StepUpAuthCommand> stepUp = mockStatic(StepUpAuthCommand.class)) {
      repository.when(() -> SitePropertyRepository.findAllByPrefix(anyString())).thenReturn(stored);
      loadUser.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(actingUser);
      stepUp.when(() -> StepUpAuthCommand.verify(any(), eq(actingUser), eq("wrong-password"))).thenReturn(false);

      new SitePropertiesEditorWidget().post(widgetContext);

      repository.verify(
          () -> SitePropertyRepository.saveAll(anyString(), org.mockito.ArgumentMatchers.anyList(), anyLong(), any()),
          never());
      assertEquals("Re-authentication failed. Enter your password or authenticator code.", widgetContext.getErrorMessage());
      assertEquals("true", widgetContext.getSharedRequestValue("stepUpRequired"));
    }
  }

  @Test
  void postToASecurityPrefixWithAValidStepUpCredentialSaves() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"sitePropertiesEditor\">\n" +
        "  <prefix>mfa</prefix>\n" +
        "</widget>");

    List<SiteProperty> stored = new ArrayList<>();
    stored.add(property("mfa.required.roles", "admin", "text"));
    addQueryParameter(widgetContext, "mfa.required.roles", "admin,content-manager");
    addQueryParameter(widgetContext, "stepUpCredential", "correct-password");

    User actingUser = new User();
    actingUser.setId(1L);

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
        MockedStatic<StepUpAuthCommand> stepUp = mockStatic(StepUpAuthCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> SitePropertyRepository.findAllByPrefix(anyString())).thenReturn(stored);
      repository.when(() -> SitePropertyRepository.saveAll(anyString(), org.mockito.ArgumentMatchers.anyList(), anyLong(), any()))
          .thenReturn(true);
      loadUser.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(actingUser);
      stepUp.when(() -> StepUpAuthCommand.verify(any(), eq(actingUser), eq("correct-password"))).thenReturn(true);
      stepUp.when(() -> StepUpAuthCommand.isValid(any())).thenReturn(false);

      new SitePropertiesEditorWidget().post(widgetContext);

      repository.verify(
          () -> SitePropertyRepository.saveAll(anyString(), org.mockito.ArgumentMatchers.anyList(), anyLong(), any()));
      assertNull(widgetContext.getErrorMessage());
      assertEquals("Values were saved", widgetContext.getSuccessMessage());
    }
  }

  @Test
  void postToANonSecurityPrefixIsUnaffectedByTheStepUpGate() {
    // mail is not in SECURITY_PREFIXES -- confirms the gate is scoped to mfa/content.review/security only
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"sitePropertiesEditor\">\n" +
        "  <prefix>mail</prefix>\n" +
        "</widget>");

    List<SiteProperty> stored = new ArrayList<>();
    stored.add(property("mail.host.name", "smtp.example.com", "text"));
    addQueryParameter(widgetContext, "mail.host.name", "smtp2.example.com");

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> SitePropertyRepository.findAllByPrefix(anyString())).thenReturn(stored);
      repository.when(() -> SitePropertyRepository.saveAll(anyString(), org.mockito.ArgumentMatchers.anyList(), anyLong(), any()))
          .thenReturn(true);

      new SitePropertiesEditorWidget().post(widgetContext);

      assertNull(widgetContext.getSharedRequestValue("stepUpRequired"));
      repository.verify(
          () -> SitePropertyRepository.saveAll(anyString(), org.mockito.ArgumentMatchers.anyList(), anyLong(), any()));
    }
  }

  @Test
  void anExpiryDateIsParsedAndStoredForASecretProperty() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"sitePropertiesEditor\">\n" +
        "  <prefix>mail</prefix>\n" +
        "</widget>");

    List<SiteProperty> stored = new ArrayList<>();
    stored.add(property("mail.password", "existing-smtp-password", null));

    // A blank value submission (unchanged secret) with a newly-set expiry
    addQueryParameter(widgetContext, "mail.password", "");
    addQueryParameter(widgetContext, "mail.password__expiresAt", "2026-12-31");

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> SitePropertyRepository.findAllByPrefix(anyString())).thenReturn(stored);
      repository.when(() -> SitePropertyRepository.saveAll(eq("mail"), eq(stored), eq(1L), any())).thenReturn(true);

      new SitePropertiesEditorWidget().post(widgetContext);

      assertEquals(Timestamp.valueOf("2026-12-31 00:00:00"), stored.get(0).getExpiresAt());
      // The value itself is unchanged (blank submission), so this is not a rotation
      audit.verify(() -> AuditEventCommand.record(any(), any(), eq("secret.rotate"), any(), any(), any(), any(), any()),
          never());
    }
  }

  @Test
  void aBlankExpiryDateClearsAPreviouslySetExpiry() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"sitePropertiesEditor\">\n" +
        "  <prefix>mail</prefix>\n" +
        "</widget>");

    List<SiteProperty> stored = new ArrayList<>();
    SiteProperty mailPassword = property("mail.password", "existing-smtp-password", null);
    mailPassword.setExpiresAt(Timestamp.valueOf("2026-06-01 00:00:00"));
    stored.add(mailPassword);

    addQueryParameter(widgetContext, "mail.password", "");
    // No mail.password__expiresAt param at all -- the field was cleared in the form

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> SitePropertyRepository.findAllByPrefix(anyString())).thenReturn(stored);
      repository.when(() -> SitePropertyRepository.saveAll(eq("mail"), eq(stored), eq(1L), any())).thenReturn(true);

      new SitePropertiesEditorWidget().post(widgetContext);

      assertNull(stored.get(0).getExpiresAt());
    }
  }

  @Test
  void anInvalidExpiryDateProducesAnErrorMessageAndDoesNotSave() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"sitePropertiesEditor\">\n" +
        "  <prefix>mail</prefix>\n" +
        "</widget>");

    List<SiteProperty> stored = new ArrayList<>();
    SiteProperty mailPassword = property("mail.password", "existing-smtp-password", null);
    mailPassword.setLabel("SMTP Password");
    stored.add(mailPassword);

    addQueryParameter(widgetContext, "mail.password", "");
    addQueryParameter(widgetContext, "mail.password__expiresAt", "not-a-date");

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> SitePropertyRepository.findAllByPrefix(anyString())).thenReturn(stored);

      new SitePropertiesEditorWidget().post(widgetContext);

      assertEquals("SMTP Password has an invalid expiration date", widgetContext.getErrorMessage());
      repository.verify(() -> SitePropertyRepository.saveAll(anyString(), any(), anyLong(), any()), never());
    }
  }

  @Test
  void savesWithTheLoggedInUsersIdAsTheActor() {
    // issue #454 review: nothing previously proved the real userId flows through rather than some
    // other value -- every prior stub/verify used anyLong(). WidgetBase.login() deterministically
    // logs in as user id 1L.
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"sitePropertiesEditor\">\n" +
        "  <prefix>mail</prefix>\n" +
        "</widget>");

    List<SiteProperty> stored = new ArrayList<>();
    stored.add(property("mail.host.name", "smtp.example.com", null));
    addQueryParameter(widgetContext, "mail.host.name", "smtp2.example.com");

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> SitePropertyRepository.findAllByPrefix(anyString())).thenReturn(stored);
      repository.when(() -> SitePropertyRepository.saveAll(eq("mail"), eq(stored), eq(1L), any())).thenReturn(true);

      new SitePropertiesEditorWidget().post(widgetContext);

      repository.verify(() -> SitePropertyRepository.saveAll(eq("mail"), eq(stored), eq(1L), any()));
    }
  }

  @Test
  void onlyThePropertyWhoseValueActuallyChangedIsMarkedChangedForModifiedStamping() {
    // issue #454 review: modified/modified_by must only be stamped for a property whose value
    // actually changed -- SitePropertyRepository.saveAll skips stamping for every OTHER name not
    // in this set, so its exact contents matter, not just that the call happened
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"sitePropertiesEditor\">\n" +
        "  <prefix>mail</prefix>\n" +
        "</widget>");

    List<SiteProperty> stored = new ArrayList<>();
    stored.add(property("mail.host.name", "smtp.example.com", null));
    stored.add(property("mail.password", "existing-smtp-password", null));

    // Only the host name is actually changed; the masked password field is left blank (unchanged)
    addQueryParameter(widgetContext, "mail.host.name", "smtp2.example.com");
    addQueryParameter(widgetContext, "mail.password", "");

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> SitePropertyRepository.findAllByPrefix(anyString())).thenReturn(stored);
      repository.when(() -> SitePropertyRepository.saveAll(eq("mail"), eq(stored), eq(1L), any())).thenReturn(true);

      new SitePropertiesEditorWidget().post(widgetContext);

      org.mockito.ArgumentCaptor<java.util.Set<String>> captor = org.mockito.ArgumentCaptor.forClass(java.util.Set.class);
      repository.verify(() -> SitePropertyRepository.saveAll(eq("mail"), eq(stored), eq(1L), captor.capture()));
      assertEquals(java.util.Set.of("mail.host.name"), captor.getValue());
    }
  }

  @Test
  void rotatingASecretFiresADedicatedPerSecretAuditEvent() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"sitePropertiesEditor\">\n" +
        "  <prefix>mail</prefix>\n" +
        "</widget>");

    List<SiteProperty> stored = new ArrayList<>();
    stored.add(property("mail.host.name", "smtp.example.com", null));
    stored.add(property("mail.password", "old-password", null));

    addQueryParameter(widgetContext, "mail.host.name", "smtp.example.com");
    addQueryParameter(widgetContext, "mail.password", "new-password");

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> SitePropertyRepository.findAllByPrefix(anyString())).thenReturn(stored);
      repository.when(() -> SitePropertyRepository.saveAll(eq("mail"), eq(stored), eq(1L), any())).thenReturn(true);

      new SitePropertiesEditorWidget().post(widgetContext);

      // Fired once, by name only -- never the rotated value, never the non-rotated mail.host.name
      audit.verify(() -> AuditEventCommand.record(eq(widgetContext), eq(AuditEventCommand.CONFIGURATION),
          eq("secret.rotate"), eq(AuditEventCommand.SUCCESS), eq("site_property"), eq("mail.password"),
          eq("mail.password"), isNull()), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), any(), eq("secret.rotate"), any(), any(), any(), any(), any()),
          times(1));
    }
  }

  @Test
  void noSecretRotateEventFiresWhenNoSecretValueChanges() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"sitePropertiesEditor\">\n" +
        "  <prefix>mail</prefix>\n" +
        "</widget>");

    List<SiteProperty> stored = new ArrayList<>();
    stored.add(property("mail.host.name", "smtp.example.com", null));
    stored.add(property("mail.password", "existing-password", null));

    addQueryParameter(widgetContext, "mail.host.name", "smtp2.example.com");
    addQueryParameter(widgetContext, "mail.password", "");

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> SitePropertyRepository.findAllByPrefix(anyString())).thenReturn(stored);
      repository.when(() -> SitePropertyRepository.saveAll(eq("mail"), eq(stored), eq(1L), any())).thenReturn(true);

      new SitePropertiesEditorWidget().post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(), any(), eq("secret.rotate"), any(), any(), any(), any(), any()),
          never());
    }
  }
}
