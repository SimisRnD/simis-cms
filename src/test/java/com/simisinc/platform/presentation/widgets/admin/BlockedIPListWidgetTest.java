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
import com.simisinc.platform.application.cms.DeleteBlockedIPListCommand;
import com.simisinc.platform.domain.model.BlockedIP;
import com.simisinc.platform.infrastructure.persistence.BlockedIPRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * The row delete link on /admin/blocked-ip-list submits via confirmPostAction() - a real HTTP POST -
 * so WebContainerCommand's dispatch (isPost() checked before isDelete()) always routes it to post(),
 * never to delete() directly. post() previously had no "delete" command branch at all, so every click
 * silently no-opped: a 302 redirect back to the same page, no error, no repository call, the row still
 * there on reload (confirmed live via a docker rehearsal + direct curl repro before this fix). These
 * tests call post(), the method a real request actually reaches, so they fail if that gap reopens - a
 * test calling delete() directly would pass either way and prove nothing about the real path.
 *
 * @author elizabeth houser
 */
class BlockedIPListWidgetTest extends WidgetBase {

  private static BlockedIP blockedIp() {
    BlockedIP record = new BlockedIP();
    record.setId(5L);
    record.setIpAddress("203.0.113.5");
    return record;
  }

  @Test
  void deleteCommandViaPostActuallyDeletesTheRecord() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "delete");
    addQueryParameter(widgetContext, "blockedIPListId", "5");

    BlockedIP target = blockedIp();

    try (MockedStatic<BlockedIPRepository> repository = mockStatic(BlockedIPRepository.class);
        MockedStatic<DeleteBlockedIPListCommand> deleteCommand = mockStatic(DeleteBlockedIPListCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> BlockedIPRepository.findById(5L)).thenReturn(target);
      deleteCommand.when(() -> DeleteBlockedIPListCommand.delete(target)).thenReturn(true);

      WidgetContext result = new BlockedIPListWidget().post(widgetContext);

      deleteCommand.verify(() -> DeleteBlockedIPListCommand.delete(target));
      audit.verify(() -> AuditEventCommand.record(any(), any(), org.mockito.ArgumentMatchers.eq("blocked_ip.remove"),
          any(), any(), any(), any(), any()));
      assertEquals("Record deleted", result.getSuccessMessage());
    }
  }

  @Test
  void deleteCommandIsRefusedWithoutAdminRole() throws Exception {
    setRoles(widgetContext); // logged in, but no admin role
    addQueryParameter(widgetContext, "command", "delete");
    addQueryParameter(widgetContext, "blockedIPListId", "5");

    try (MockedStatic<DeleteBlockedIPListCommand> deleteCommand = mockStatic(DeleteBlockedIPListCommand.class)) {
      WidgetContext result = new BlockedIPListWidget().post(widgetContext);

      deleteCommand.verify(() -> DeleteBlockedIPListCommand.delete(any()), never());
      assertNull(result.getSuccessMessage());
    }
  }
}
