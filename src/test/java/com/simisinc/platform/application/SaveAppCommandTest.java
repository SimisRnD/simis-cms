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

package com.simisinc.platform.application;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.App;
import com.simisinc.platform.infrastructure.persistence.AppRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;

/**
 * An App record is an API-client credential with no in-app remediation path before this change --
 * these tests cover the enabled-flag wiring, the createdBy-preservation fix (precedent: the
 * createdBy-overwrite family of fixes around issue #989, mirrored here the same way
 * SaveMailingListCommandTest covers it for MailingList), and the non-blocking duplicate-name check.
 *
 * @author elizabeth houser
 */
class SaveAppCommandTest {

  @Test
  void aBlankNameIsRejectedBeforeTouchingTheRepository() {
    App bean = new App();
    bean.setName("   ");

    try (MockedStatic<AppRepository> repository = mockStatic(AppRepository.class)) {
      DataException e = assertThrows(DataException.class, () -> SaveAppCommand.saveApp(null, bean));
      assertTrue(e.getMessage().contains("A name is required"));
      repository.verify(() -> AppRepository.save(any()), never());
    }
  }

  @Test
  void newRecordGetsCreatedByFromTheSubmitterAndIsAudited() throws DataException {
    App bean = new App();
    bean.setName("Mobile App");
    bean.setCreatedBy(42L);
    bean.setEnabled(true);

    try (MockedStatic<AppRepository> repository = mockStatic(AppRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> AppRepository.save(any())).thenAnswer(i -> {
        App saved = i.getArgument(0);
        saved.setId(5L);
        return saved;
      });

      SaveAppCommand.saveApp(null, bean);

      repository.verify(() -> AppRepository.save(argThat(saved -> saved.getCreatedBy() == 42L)));
      // A brand-new app's initial enabled state is covered by the widget's generic app.create
      // event, not a separate enable/disable transition -- there was no "previous" state to flip from.
      audit.verifyNoInteractions();
    }
  }

  @Test
  void editingAnExistingRecordDoesNotChangeItsOriginalCreatedBy() throws DataException {
    App existing = new App();
    existing.setId(1L);
    existing.setCreatedBy(7L); // the original creator
    existing.setEnabled(true);

    App bean = new App();
    bean.setId(1L);
    bean.setName("Renamed App");
    bean.setCreatedBy(42L); // a different user editing it today
    bean.setEnabled(true);

    try (MockedStatic<AppRepository> repository = mockStatic(AppRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> AppRepository.findById(1L)).thenReturn(existing);
      repository.when(() -> AppRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      SaveAppCommand.saveApp(null, bean);

      repository.verify(() -> AppRepository.save(argThat(saved -> saved.getCreatedBy() == 7L)));
    }
  }

  @Test
  void editingAMissingRecordThrowsBeforeTouchingSave() {
    App bean = new App();
    bean.setId(99L);
    bean.setName("Ghost app");

    try (MockedStatic<AppRepository> repository = mockStatic(AppRepository.class)) {
      repository.when(() -> AppRepository.findById(99L)).thenReturn(null);

      assertThrows(DataException.class, () -> SaveAppCommand.saveApp(null, bean));

      repository.verify(() -> AppRepository.save(any()), never());
    }
  }

  @Test
  void theEnabledFlagIsPersistedOnCreate() throws DataException {
    App bean = new App();
    bean.setName("Disabled from the start");
    bean.setEnabled(false);

    try (MockedStatic<AppRepository> repository = mockStatic(AppRepository.class)) {
      repository.when(() -> AppRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      SaveAppCommand.saveApp(null, bean);

      repository.verify(() -> AppRepository.save(argThat(saved -> !saved.isEnabled())));
    }
  }

  @Test
  void theEnabledFlagIsPersistedOnUpdate() throws DataException {
    App existing = new App();
    existing.setId(1L);
    existing.setEnabled(true);

    App bean = new App();
    bean.setId(1L);
    bean.setName("Turning it off");
    bean.setEnabled(false);

    try (MockedStatic<AppRepository> repository = mockStatic(AppRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> AppRepository.findById(1L)).thenReturn(existing);
      repository.when(() -> AppRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      SaveAppCommand.saveApp(null, bean);

      repository.verify(() -> AppRepository.save(argThat(saved -> !saved.isEnabled())));
    }
  }

  @Test
  void disablingAPreviouslyEnabledAppRecordsAnAuditEventDistinctFromTheGenericUpdate() throws DataException {
    App existing = new App();
    existing.setId(1L);
    existing.setName("Leaked Client");
    existing.setEnabled(true);

    App bean = new App();
    bean.setId(1L);
    bean.setName("Leaked Client");
    bean.setEnabled(false);

    try (MockedStatic<AppRepository> repository = mockStatic(AppRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> AppRepository.findById(1L)).thenReturn(existing);
      repository.when(() -> AppRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      SaveAppCommand.saveApp(null, bean);

      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONFIGURATION), eq("app.disable"),
          eq(AuditEventCommand.SUCCESS), eq("app"), eq("1"), eq("Leaked Client"), any()));
    }
  }

  @Test
  void reEnablingADisabledAppRecordsAnEnableAuditEvent() throws DataException {
    App existing = new App();
    existing.setId(1L);
    existing.setName("Reinstated");
    existing.setEnabled(false);

    App bean = new App();
    bean.setId(1L);
    bean.setName("Reinstated");
    bean.setEnabled(true);

    try (MockedStatic<AppRepository> repository = mockStatic(AppRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> AppRepository.findById(1L)).thenReturn(existing);
      repository.when(() -> AppRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      SaveAppCommand.saveApp(null, bean);

      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONFIGURATION), eq("app.enable"),
          eq(AuditEventCommand.SUCCESS), eq("app"), eq("1"), eq("Reinstated"), any()));
    }
  }

  @Test
  void savingWithNoChangeToEnabledDoesNotRecordAnEnableDisableEvent() throws DataException {
    App existing = new App();
    existing.setId(1L);
    existing.setName("Unchanged");
    existing.setEnabled(true);

    App bean = new App();
    bean.setId(1L);
    bean.setName("Unchanged (renamed)");
    bean.setEnabled(true);

    try (MockedStatic<AppRepository> repository = mockStatic(AppRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> AppRepository.findById(1L)).thenReturn(existing);
      repository.when(() -> AppRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      SaveAppCommand.saveApp(null, bean);

      audit.verifyNoInteractions();
    }
  }

  @Test
  void checkForDuplicateNameReturnsAWarningWhenAnotherAppHasTheSameNameCaseInsensitively() {
    App other = new App();
    other.setId(2L);
    other.setName("Mobile App");

    App bean = new App();
    bean.setId(-1L);
    bean.setName("mobile app");

    try (MockedStatic<AppRepository> repository = mockStatic(AppRepository.class)) {
      repository.when(AppRepository::findAll).thenReturn(List.of(other));

      String warning = SaveAppCommand.checkForDuplicateName(bean);

      assertTrue(warning != null && warning.contains("mobile app"), "expected a duplicate-name warning: " + warning);
    }
  }

  @Test
  void checkForDuplicateNameIgnoresItsOwnRecordWhenEditing() {
    App self = new App();
    self.setId(1L);
    self.setName("My App");

    App bean = new App();
    bean.setId(1L);
    bean.setName("My App");

    try (MockedStatic<AppRepository> repository = mockStatic(AppRepository.class)) {
      repository.when(AppRepository::findAll).thenReturn(List.of(self));

      assertNull(SaveAppCommand.checkForDuplicateName(bean));
    }
  }

  @Test
  void checkForDuplicateNameReturnsNullWhenNameIsUnique() {
    App other = new App();
    other.setId(2L);
    other.setName("Some Other App");

    App bean = new App();
    bean.setId(-1L);
    bean.setName("Unique Name");

    try (MockedStatic<AppRepository> repository = mockStatic(AppRepository.class)) {
      repository.when(AppRepository::findAll).thenReturn(List.of(other));

      assertNull(SaveAppCommand.checkForDuplicateName(bean));
    }
  }
}
