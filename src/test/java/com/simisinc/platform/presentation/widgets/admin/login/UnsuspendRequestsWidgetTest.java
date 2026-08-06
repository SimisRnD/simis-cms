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

package com.simisinc.platform.presentation.widgets.admin.login;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.login.UnsuspendAccountCommand;
import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.domain.model.login.UnsuspendRequest;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Covers the users:manage permission gate added to {@link UnsuspendRequestsWidget#post} alongside
 * its existing hasRole("admin")/hasRole("community-manager") check (issue #733 follow-up) - a
 * user granted the capability directly, with no legacy role at all, must still be able to act on
 * a pending unsuspend request, and a user with neither must still be denied.
 *
 * @author SimIS Inc.
 */
class UnsuspendRequestsWidgetTest extends WidgetBase {

  private static UnsuspendRequest deniedRequest() {
    UnsuspendRequest request = new UnsuspendRequest();
    request.setId(9L);
    request.setTargetUserId(5L);
    request.setTargetEmail("target@example.com");
    request.setRequestedByEmail("requester@example.com");
    return request;
  }

  @Test
  void capabilityOnlyUserWithUsersManageCanDenyARequest() throws Exception {
    // No role set at all -- WidgetBase's default session already has an empty role list; the
    // only thing granting access here is the users:manage capability (issue #733 follow-up).
    Capability usersManage = new Capability();
    usersManage.setCode("users:manage");
    widgetContext.getUserSession().setCapabilityList(List.of(usersManage));
    addQueryParameter(widgetContext, "command", "deny");
    addQueryParameter(widgetContext, "requestId", "9");
    addQueryParameter(widgetContext, "denialReason", "capability-only access check");

    try (MockedStatic<UnsuspendAccountCommand> command = mockStatic(UnsuspendAccountCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      command.when(() -> UnsuspendAccountCommand.deny(9L, widgetContext.getUserId(), "capability-only access check"))
          .thenReturn(deniedRequest());

      WidgetContext result = new UnsuspendRequestsWidget().post(widgetContext);

      command.verify(() -> UnsuspendAccountCommand.deny(9L, widgetContext.getUserId(), "capability-only access check"),
          times(1));
      audit.verify(() -> AuditEventCommand.record(any(WidgetContext.class), anyString(), anyString(), anyString(),
          anyString(), anyString(), anyString(), anyString()), times(1));
      assertNull(result.getErrorMessage());
    }
  }

  @Test
  void userWithNeitherRoleNorUsersManageCapabilityCannotReachApprovalActionsAtAll() throws Exception {
    // WidgetBase's default session has neither a role nor a capability list populated
    addQueryParameter(widgetContext, "command", "deny");
    addQueryParameter(widgetContext, "requestId", "9");
    addQueryParameter(widgetContext, "denialReason", "n/a");

    try (MockedStatic<UnsuspendAccountCommand> command = mockStatic(UnsuspendAccountCommand.class)) {
      new UnsuspendRequestsWidget().post(widgetContext);

      command.verify(() -> UnsuspendAccountCommand.deny(anyLong(), anyLong(), any()), never());
    }
  }
}
