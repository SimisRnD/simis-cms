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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.DeleteAllowedIPListCommand;
import com.simisinc.platform.domain.model.AllowedIP;
import com.simisinc.platform.infrastructure.persistence.AllowedIPRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Same dispatch gap as BlockedIPListWidgetTest, on the allow-list side - this widget's delete link
 * copied the same confirmPostAction()-submits-a-real-POST pattern when it was written, and post() had
 * the same missing "delete" branch. These tests call post(), not delete() directly, for the same reason.
 *
 * @author elizabeth houser
 */
class AllowedIPListWidgetTest extends WidgetBase {

  private static AllowedIP allowedIp() {
    AllowedIP record = new AllowedIP();
    record.setId(9L);
    record.setIpAddress("203.0.113.9");
    return record;
  }

  @Test
  void deleteCommandViaPostActuallyDeletesTheRecord() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "delete");
    addQueryParameter(widgetContext, "allowedIPListId", "9");

    AllowedIP target = allowedIp();

    try (MockedStatic<AllowedIPRepository> repository = mockStatic(AllowedIPRepository.class);
        MockedStatic<DeleteAllowedIPListCommand> deleteCommand = mockStatic(DeleteAllowedIPListCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> AllowedIPRepository.findById(9L)).thenReturn(target);
      deleteCommand.when(() -> DeleteAllowedIPListCommand.delete(target)).thenReturn(true);

      WidgetContext result = new AllowedIPListWidget().post(widgetContext);

      deleteCommand.verify(() -> DeleteAllowedIPListCommand.delete(target));
      audit.verify(() -> AuditEventCommand.record(any(), any(), org.mockito.ArgumentMatchers.eq("allowed_ip.remove"),
          any(), any(), any(), any(), any()));
      assertEquals("Record deleted", result.getSuccessMessage());
    }
  }

  @Test
  void deleteCommandIsRefusedWithoutAdminRole() throws Exception {
    setRoles(widgetContext); // logged in, but no admin role
    addQueryParameter(widgetContext, "command", "delete");
    addQueryParameter(widgetContext, "allowedIPListId", "9");

    try (MockedStatic<DeleteAllowedIPListCommand> deleteCommand = mockStatic(DeleteAllowedIPListCommand.class)) {
      WidgetContext result = new AllowedIPListWidget().post(widgetContext);

      deleteCommand.verify(() -> DeleteAllowedIPListCommand.delete(any()), never());
      assertNull(result.getSuccessMessage());
    }
  }
}
