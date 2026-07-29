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
import com.simisinc.platform.domain.model.AllowedIP;
import com.simisinc.platform.infrastructure.persistence.AllowedIPRepository;
import com.simisinc.platform.application.cms.SaveAllowedIPCommand;
import com.simisinc.platform.presentation.controller.AuditEventCommand;

/**
 * Guards the same gap as BlockedIPFormWidgetTest, on the allow-list side: adding an allowed IP never
 * recorded an audit event before #644.
 *
 * @author elizabeth houser
 */
class AllowedIPFormWidgetTest extends WidgetBase {

  @Test
  void addingAnAllowedIpRecordsAnAuditEvent() throws Exception {
    addQueryParameter(widgetContext, "ipAddress", "203.0.113.10");
    addQueryParameter(widgetContext, "reason", "Test entry");

    AllowedIP saved = new AllowedIP();
    saved.setId(3L);
    saved.setIpAddress("203.0.113.10");
    saved.setReason("Test entry");

    try (MockedStatic<AllowedIPRepository> repository = mockStatic(AllowedIPRepository.class);
        MockedStatic<SaveAllowedIPCommand> saveCommand = mockStatic(SaveAllowedIPCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> AllowedIPRepository.findByIpAddress("203.0.113.10")).thenReturn(null);
      saveCommand.when(() -> SaveAllowedIPCommand.save(any(AllowedIP.class))).thenReturn(saved);

      new AllowedIPFormWidget().post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONFIGURATION), eq("allowed_ip.add"),
          eq(AuditEventCommand.SUCCESS), eq("allowed_ip"), eq("3"), eq("203.0.113.10"), any()));
    }
  }
}
