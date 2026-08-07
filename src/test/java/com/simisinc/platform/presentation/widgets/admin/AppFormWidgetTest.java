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

package com.simisinc.platform.presentation.widgets.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.SaveAppCommand;
import com.simisinc.platform.domain.model.App;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Covers the App admin form's create/update audit trail (previously nonexistent for anything
 * App-related), the Enabled checkbox wiring, the non-blocking duplicate-name warning, and the
 * explicit-parameter-read fix (5c/mass-assignment): {@link AppFormWidget#post} used to build the
 * save bean via {@code BeanUtils.populate()} against the *entire* raw parameter map, so a crafted
 * POST could set fields like publicKey/privateKey directly on the bean -- matches the fix already
 * applied to WebPageFormWidget's pageXml mass-assignment gap.
 *
 * @author elizabeth houser
 */
class AppFormWidgetTest extends WidgetBase {

  @Test
  void postRecordsAppCreateOnSuccess() throws Exception {
    addQueryParameter(widgetContext, "name", "Mobile App");
    // No "id" parameter -- a new record

    App saved = new App();
    saved.setId(5L);
    saved.setName("Mobile App");

    try (MockedStatic<SaveAppCommand> saveCommand = mockStatic(SaveAppCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveCommand.when(() -> SaveAppCommand.saveApp(any(), any())).thenReturn(saved);
      saveCommand.when(() -> SaveAppCommand.checkForDuplicateName(any())).thenReturn(null);

      WidgetContext result = new AppFormWidget().post(widgetContext);

      assertEquals("App was saved", result.getSuccessMessage());
      assertNull(result.getWarningMessage());
      audit.verify(() -> AuditEventCommand.record(any(), any(), eq("app.create"), eq(AuditEventCommand.SUCCESS),
          eq("app"), eq("5"), eq("Mobile App"), any()));
    }
  }

  @Test
  void postRecordsAppUpdateOnSuccess() throws Exception {
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "name", "Mobile App Renamed");

    App saved = new App();
    saved.setId(5L);
    saved.setName("Mobile App Renamed");

    try (MockedStatic<SaveAppCommand> saveCommand = mockStatic(SaveAppCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveCommand.when(() -> SaveAppCommand.saveApp(any(), any())).thenReturn(saved);
      saveCommand.when(() -> SaveAppCommand.checkForDuplicateName(any())).thenReturn(null);

      WidgetContext result = new AppFormWidget().post(widgetContext);

      assertEquals("App was saved", result.getSuccessMessage());
      // An existing id means this is an edit, not a create -- must be audited as such
      audit.verify(() -> AuditEventCommand.record(any(), any(), eq("app.update"), eq(AuditEventCommand.SUCCESS),
          eq("app"), eq("5"), eq("Mobile App Renamed"), any()));
    }
  }

  @Test
  void postRecordsAppCreateFailureWhenValidationFails() throws Exception {
    // No "name" -- SaveAppCommand's own validation rejects this before touching the repository
    try (MockedStatic<SaveAppCommand> saveCommand = mockStatic(SaveAppCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveCommand.when(() -> SaveAppCommand.checkForDuplicateName(any())).thenReturn(null);
      saveCommand.when(() -> SaveAppCommand.saveApp(any(), any()))
          .thenThrow(new DataException("Please check the form and try again:\nA name is required"));

      WidgetContext result = new AppFormWidget().post(widgetContext);

      assertEquals("Please check the form and try again:\nA name is required", result.getErrorMessage());
      audit.verify(() -> AuditEventCommand.record(any(), any(), eq("app.create"), eq(AuditEventCommand.FAILURE),
          eq("app"), any(), any(), eq("Please check the form and try again:\nA name is required")));
    }
  }

  @Test
  void postSurfacesTheDuplicateNameWarningAlongsideTheSuccessMessage() throws Exception {
    addQueryParameter(widgetContext, "name", "Mobile App");

    App saved = new App();
    saved.setId(5L);
    saved.setName("Mobile App");

    try (MockedStatic<SaveAppCommand> saveCommand = mockStatic(SaveAppCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveCommand.when(() -> SaveAppCommand.saveApp(any(), any())).thenReturn(saved);
      saveCommand.when(() -> SaveAppCommand.checkForDuplicateName(any()))
          .thenReturn("Note: another App is already named 'Mobile App' -- consider a more specific name to avoid confusion");

      WidgetContext result = new AppFormWidget().post(widgetContext);

      // Non-blocking: the save still succeeds, the warning is additional context, not an error
      assertEquals("App was saved", result.getSuccessMessage());
      assertTrue(result.getWarningMessage() != null && result.getWarningMessage().contains("Mobile App"));
    }
  }

  @Test
  void postReadsTheEnabledCheckboxWhenPresent() throws Exception {
    addQueryParameter(widgetContext, "name", "Mobile App");
    addQueryParameter(widgetContext, "enabled", "true");

    App saved = new App();
    saved.setId(5L);
    saved.setName("Mobile App");

    try (MockedStatic<SaveAppCommand> saveCommand = mockStatic(SaveAppCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveCommand.when(() -> SaveAppCommand.saveApp(any(), any())).thenReturn(saved);
      saveCommand.when(() -> SaveAppCommand.checkForDuplicateName(any())).thenReturn(null);

      new AppFormWidget().post(widgetContext);

      saveCommand.verify(() -> SaveAppCommand.saveApp(any(), argThat(App::isEnabled)));
    }
  }

  @Test
  void postTreatsAMissingEnabledParameterAsUnchecked() throws Exception {
    // HTML checkboxes send nothing at all when unchecked -- this is the standard "absent means false"
    // handling (matches WebPageFormWidget's searchable/showInSitemap fields).
    addQueryParameter(widgetContext, "name", "Mobile App");

    App saved = new App();
    saved.setId(5L);
    saved.setName("Mobile App");

    try (MockedStatic<SaveAppCommand> saveCommand = mockStatic(SaveAppCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveCommand.when(() -> SaveAppCommand.saveApp(any(), any())).thenReturn(saved);
      saveCommand.when(() -> SaveAppCommand.checkForDuplicateName(any())).thenReturn(null);

      new AppFormWidget().post(widgetContext);

      saveCommand.verify(() -> SaveAppCommand.saveApp(any(), argThat(bean -> !bean.isEnabled())));
    }
  }

  @Test
  void postIgnoresRawPostParametersOutsideTheFormsExplicitFieldSet() throws Exception {
    // A crafted POST including publicKey/privateKey must not reach the save bean -- previously
    // BeanUtils.populate() walked the *entire* raw parameter map, so these would have landed
    // directly on the App bean passed to SaveAppCommand.
    addQueryParameter(widgetContext, "name", "Mobile App");
    addQueryParameter(widgetContext, "publicKey", "attacker-supplied-client-id");
    addQueryParameter(widgetContext, "privateKey", "attacker-supplied-secret");

    App saved = new App();
    saved.setId(5L);
    saved.setName("Mobile App");

    try (MockedStatic<SaveAppCommand> saveCommand = mockStatic(SaveAppCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveCommand.when(() -> SaveAppCommand.saveApp(any(), any())).thenReturn(saved);
      saveCommand.when(() -> SaveAppCommand.checkForDuplicateName(any())).thenReturn(null);

      new AppFormWidget().post(widgetContext);

      saveCommand.verify(() -> SaveAppCommand.saveApp(any(),
          argThat(bean -> bean.getPublicKey() == null && bean.getPrivateKey() == null)));
    }
  }

  @Test
  void postReadsOnlyTheSubmittedNameAndSummaryFields() throws Exception {
    addQueryParameter(widgetContext, "name", "Mobile App");
    addQueryParameter(widgetContext, "summary", "Used by the mobile client");

    App saved = new App();
    saved.setId(5L);
    saved.setName("Mobile App");

    try (MockedStatic<SaveAppCommand> saveCommand = mockStatic(SaveAppCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveCommand.when(() -> SaveAppCommand.saveApp(any(), any())).thenReturn(saved);
      saveCommand.when(() -> SaveAppCommand.checkForDuplicateName(any())).thenReturn(null);

      new AppFormWidget().post(widgetContext);

      saveCommand.verify(() -> SaveAppCommand.saveApp(any(),
          argThat(bean -> "Mobile App".equals(bean.getName()) && "Used by the mobile client".equals(bean.getSummary()))));
    }
  }
}
