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
}
