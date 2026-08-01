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

package com.simisinc.platform.presentation.widgets.mailinglists;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.mailinglists.SaveMailingListCommand;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * #753: creating, editing, or deleting a mailing list was completely unaudited. These tests cover
 * create and update -- {@link MailingListsWidgetTest} covers delete.
 *
 * @author elizabeth houser
 */
class MailingListFormWidgetTest extends WidgetBase {

  @Test
  void postRecordsMailingListCreateOnSuccess() throws Exception {
    addQueryParameter(widgetContext, "name", "Newsletter");
    // No "id" parameter -- a new record

    MailingList saved = new MailingList();
    saved.setId(5L);
    saved.setName("Newsletter");

    try (MockedStatic<SaveMailingListCommand> saveCommand = mockStatic(SaveMailingListCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveCommand.when(() -> SaveMailingListCommand.saveMailingList(any())).thenReturn(saved);

      WidgetContext result = new MailingListFormWidget().post(widgetContext);

      assertEquals("Mailing list was saved", result.getSuccessMessage());
      audit.verify(() -> AuditEventCommand.record(any(), any(), eq("mailing_list.create"), eq(AuditEventCommand.SUCCESS),
          eq("mailing_list"), eq("5"), eq("Newsletter"), any()));
    }
  }

  @Test
  void postRecordsMailingListUpdateOnSuccess() throws Exception {
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "name", "Newsletter Renamed");

    MailingList saved = new MailingList();
    saved.setId(5L);
    saved.setName("Newsletter Renamed");

    try (MockedStatic<SaveMailingListCommand> saveCommand = mockStatic(SaveMailingListCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveCommand.when(() -> SaveMailingListCommand.saveMailingList(any())).thenReturn(saved);

      WidgetContext result = new MailingListFormWidget().post(widgetContext);

      assertEquals("Mailing list was saved", result.getSuccessMessage());
      // An existing id means this is an edit, not a create -- must be audited as such
      audit.verify(() -> AuditEventCommand.record(any(), any(), eq("mailing_list.update"), eq(AuditEventCommand.SUCCESS),
          eq("mailing_list"), eq("5"), eq("Newsletter Renamed"), any()));
    }
  }

  @Test
  void postRecordsMailingListCreateFailureWhenValidationFails() throws Exception {
    // No "name" -- SaveMailingListCommand's own validation rejects this before touching the repository
    try (MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      WidgetContext result = new MailingListFormWidget().post(widgetContext);

      assertEquals("Please check the form and try again:\nA name is required", result.getErrorMessage());
      audit.verify(() -> AuditEventCommand.record(any(), any(), eq("mailing_list.create"), eq(AuditEventCommand.FAILURE),
          eq("mailing_list"), any(), any(), eq("Please check the form and try again:\nA name is required")));
    }
  }

  @Test
  void postRecordsMailingListUpdateFailureWhenTheSaveThrows() throws Exception {
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "name", "Newsletter");

    try (MockedStatic<SaveMailingListCommand> saveCommand = mockStatic(SaveMailingListCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveCommand.when(() -> SaveMailingListCommand.saveMailingList(any()))
          .thenThrow(new DataException("The existing record could not be found"));

      WidgetContext result = new MailingListFormWidget().post(widgetContext);

      assertEquals("The existing record could not be found", result.getErrorMessage());
      audit.verify(() -> AuditEventCommand.record(any(), any(), eq("mailing_list.update"), eq(AuditEventCommand.FAILURE),
          eq("mailing_list"), eq("5"), eq("Newsletter"), eq("The existing record could not be found")));
    }
  }
}
