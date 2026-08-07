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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.BlockedIP;
import com.simisinc.platform.infrastructure.persistence.BlockedIPRepository;
import com.simisinc.platform.application.cms.SaveBlockedIPCommand;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Guards a real gap found while building #644 (audit history links): adding a blocked IP through this
 * form never recorded an audit event - only delete/import/export did - so "who blocked this IP and when"
 * was unanswerable for the single most common action on this page.
 *
 * @author elizabeth houser
 */
class BlockedIPFormWidgetTest extends WidgetBase {

  @Test
  void addingABlockedIpRecordsAnAuditEvent() throws Exception {
    addQueryParameter(widgetContext, "ipAddress", "203.0.113.9");
    addQueryParameter(widgetContext, "reason", "Test entry");

    BlockedIP saved = new BlockedIP();
    saved.setId(7L);
    saved.setIpAddress("203.0.113.9");
    saved.setReason("Test entry");

    try (MockedStatic<BlockedIPRepository> repository = mockStatic(BlockedIPRepository.class);
        MockedStatic<SaveBlockedIPCommand> saveCommand = mockStatic(SaveBlockedIPCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> BlockedIPRepository.findByIpAddress("203.0.113.9")).thenReturn(null);
      saveCommand.when(() -> SaveBlockedIPCommand.save(any(BlockedIP.class))).thenReturn(saved);

      new BlockedIPFormWidget().post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONFIGURATION), eq("blocked_ip.add"),
          eq(AuditEventCommand.SUCCESS), eq("blocked_ip"), eq("7"), eq("203.0.113.9"), any()));
    }
  }

  @Test
  void editingABlockedIpWithoutChangingItsAddressIsNotRejectedAsADuplicate() throws Exception {
    // Editing record id 5, leaving its address unchanged and only updating the Reason -- the
    // duplicate lookup will find record 5 itself, which must not be treated as a collision
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "ipAddress", "203.0.113.11");
    addQueryParameter(widgetContext, "reason", "Updated reason");

    BlockedIP existingRecord = new BlockedIP();
    existingRecord.setId(5L);
    existingRecord.setIpAddress("203.0.113.11");
    existingRecord.setReason("Old reason");

    BlockedIP saved = new BlockedIP();
    saved.setId(5L);
    saved.setIpAddress("203.0.113.11");
    saved.setReason("Updated reason");

    try (MockedStatic<BlockedIPRepository> repository = mockStatic(BlockedIPRepository.class);
        MockedStatic<SaveBlockedIPCommand> saveCommand = mockStatic(SaveBlockedIPCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> BlockedIPRepository.findByIpAddress("203.0.113.11")).thenReturn(existingRecord);
      saveCommand.when(() -> SaveBlockedIPCommand.save(any(BlockedIP.class))).thenReturn(saved);

      WidgetContext result = new BlockedIPFormWidget().post(widgetContext);

      assertNull(result.getWarningMessage());
      assertEquals("Record was saved", result.getSuccessMessage());
      saveCommand.verify(() -> SaveBlockedIPCommand.save(any(BlockedIP.class)));
    }
  }

  @Test
  void addingANewBlockedIpThatCollidesWithAnotherRecordIsRejectedAsADuplicate() throws Exception {
    // No "id" parameter -- this is a new entry, and its address collides with a different, existing record
    addQueryParameter(widgetContext, "ipAddress", "203.0.113.21");
    addQueryParameter(widgetContext, "reason", "New entry");

    BlockedIP existingRecord = new BlockedIP();
    existingRecord.setId(9L);
    existingRecord.setIpAddress("203.0.113.21");
    existingRecord.setReason("Existing entry");

    try (MockedStatic<BlockedIPRepository> repository = mockStatic(BlockedIPRepository.class);
        MockedStatic<SaveBlockedIPCommand> saveCommand = mockStatic(SaveBlockedIPCommand.class)) {
      repository.when(() -> BlockedIPRepository.findByIpAddress("203.0.113.21")).thenReturn(existingRecord);

      WidgetContext result = new BlockedIPFormWidget().post(widgetContext);

      assertEquals("IP already exists", result.getWarningMessage());
      saveCommand.verifyNoInteractions();
    }
  }
}
