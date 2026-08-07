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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.ProcessBotListCSVFileCommand;
import com.simisinc.platform.application.cms.DeleteBotUserAgentListCommand;
import com.simisinc.platform.domain.model.BotUserAgent;
import com.simisinc.platform.infrastructure.persistence.BotUserAgentRepository;

/**
 * Verifies {@link BotUserAgentListWidget}'s delete-of-already-removed-record guard and its
 * CSV-import summary message, including that added/removed counts are both stated explicitly
 * rather than a fully-successful bulk-removal (0 added, N removed) reading as "0 records added".
 *
 * @author SimIS Inc.
 */
class BotUserAgentListWidgetTest extends WidgetBase {

  @Test
  void deleteWarnsInsteadOfNpeWhenTheRecordIsAlreadyGone() {
    addQueryParameter(widgetContext, "botListId", "5");

    try (MockedStatic<BotUserAgentRepository> repository = mockStatic(BotUserAgentRepository.class)) {
      repository.when(() -> BotUserAgentRepository.findById(5L)).thenReturn(null);

      new BotUserAgentListWidget().delete(widgetContext);

      assertEquals("This entry was already removed.", widgetContext.getWarningMessage());
      assertNull(widgetContext.getErrorMessage());
    }
  }

  @Test
  void deleteRemovesAnExistingRecord() {
    addQueryParameter(widgetContext, "botListId", "5");
    BotUserAgent existing = new BotUserAgent();
    existing.setId(5L);
    existing.setUserAgent("ExampleBot");

    try (MockedStatic<BotUserAgentRepository> repository = mockStatic(BotUserAgentRepository.class);
        MockedStatic<DeleteBotUserAgentListCommand> deleteCommand = mockStatic(DeleteBotUserAgentListCommand.class)) {
      repository.when(() -> BotUserAgentRepository.findById(5L)).thenReturn(existing);
      deleteCommand.when(() -> DeleteBotUserAgentListCommand.delete(existing)).thenReturn(true);

      new BotUserAgentListWidget().delete(widgetContext);

      assertEquals("Record deleted", widgetContext.getSuccessMessage());
    }
  }

  @Test
  void uploadCsvSummaryStatesAddedAndRemovedCountsExplicitly() throws Exception {
    // The bug this test guards against: a CSV of only successful Remove=true rows (0 added, N
    // removed, 0 skipped) previously fell into the "0 records added" branch with zero mention of
    // the removals, making a fully successful bulk-removal look like it did nothing.
    addQueryParameter(widgetContext, "command", "uploadCSVFile");
    setRoles(widgetContext, ADMIN);
    ProcessBotListCSVFileCommand.ImportResult result =
        new ProcessBotListCSVFileCommand.ImportResult(0, 0, 4, 4);

    try (MockedStatic<ProcessBotListCSVFileCommand> processCommand = mockStatic(ProcessBotListCSVFileCommand.class)) {
      processCommand.when(() -> ProcessBotListCSVFileCommand.processCSV(any())).thenReturn(result);

      new BotUserAgentListWidget().post(widgetContext);

      String message = widgetContext.getSuccessMessage();
      assertNotNull(message);
      assertTrue(message.contains("4 removed"), "removals must be stated explicitly: " + message);
      assertTrue(message.contains("0 records added"), "added count must still be stated: " + message);
    }
  }

  @Test
  void uploadCsvSummaryStatesSkippedCountAlongsideAddedAndRemoved() throws Exception {
    addQueryParameter(widgetContext, "command", "uploadCSVFile");
    setRoles(widgetContext, ADMIN);
    ProcessBotListCSVFileCommand.ImportResult result =
        new ProcessBotListCSVFileCommand.ImportResult(1, 1, 1, 3);

    try (MockedStatic<ProcessBotListCSVFileCommand> processCommand = mockStatic(ProcessBotListCSVFileCommand.class)) {
      processCommand.when(() -> ProcessBotListCSVFileCommand.processCSV(any())).thenReturn(result);

      new BotUserAgentListWidget().post(widgetContext);

      String message = widgetContext.getSuccessMessage();
      assertNotNull(message);
      assertTrue(message.contains("1 record added"), message);
      assertTrue(message.contains("1 removed"), message);
      assertTrue(message.contains("1 skipped"), message);
    }
  }

  @Test
  void postIsRefusedWithoutAdminRole() throws Exception {
    addQueryParameter(widgetContext, "command", "uploadCSVFile");

    try (MockedStatic<ProcessBotListCSVFileCommand> processCommand = mockStatic(ProcessBotListCSVFileCommand.class)) {
      new BotUserAgentListWidget().post(widgetContext);

      processCommand.verify(() -> ProcessBotListCSVFileCommand.processCSV(any()), never());
    }
  }
}
