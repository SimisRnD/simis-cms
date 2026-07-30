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

package com.simisinc.platform.presentation.widgets.login;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.AuditEventCommand;

/**
 * Verifies the self-service forgot-password request is recorded in the audit log (issue #492) --
 * before this, only an admin-initiated reset (UserDetailsWidget) was audited; a visitor requesting
 * their own reset left no trace at all.
 *
 * @author SimIS Inc.
 */
class ForgotPasswordWidgetTest extends WidgetBase {

  private static User targetUser() {
    User user = new User();
    user.setId(11L);
    user.setUsername("target@example.com");
    user.setEmail("target@example.com");
    return user;
  }

  @Test
  void postAuditsTheRequestWhenTheUserExists() {
    logout(widgetContext);
    addQueryParameter(widgetContext, "username", "target@example.com");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
          .thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), org.mockito.ArgumentMatchers.anyBoolean()))
          .thenReturn(true);
      User target = targetUser();
      loadUser.when(() -> LoadUserCommand.loadUser("target@example.com")).thenReturn(target);
      userRepo.when(() -> UserRepository.createAccountToken(target)).thenReturn(target);

      new ForgotPasswordWidget().post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT),
          eq("user.password.reset.requested"), eq(AuditEventCommand.SUCCESS), eq("user"), eq("11"),
          eq("target@example.com"), any()), times(1));
    }
  }

  @Test
  void postDoesNotAuditWhenNoMatchingUserExists() {
    // Enumeration prevention: the response is identical whether the user exists or not, and no
    // token/email/audit path runs at all for a nonexistent username.
    logout(widgetContext);
    addQueryParameter(widgetContext, "username", "nobody@example.com");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
          .thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), org.mockito.ArgumentMatchers.anyBoolean()))
          .thenReturn(true);
      loadUser.when(() -> LoadUserCommand.loadUser("nobody@example.com")).thenReturn(null);

      new ForgotPasswordWidget().post(widgetContext);

      audit.verifyNoInteractions();
    }
  }
}
