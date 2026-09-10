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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.login.LogoutCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.login.UnsuspendRequestRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.AuditEventCommand;

/**
 * Verifies AccountValidationWidget.post() (issue #492) records a distinct audit event depending
 * on which real-world action just happened -- a first-time account activation vs. a returning
 * user's password reset -- since before this fix neither completion was audited at all (only the
 * admin's *request* to reset a password was, in UserDetailsWidget).
 *
 * @author SimIS Inc.
 */
class AccountValidationWidgetTest extends WidgetBase {

  private static User userWithToken(Timestamp validated) {
    User user = new User();
    user.setId(21L);
    user.setEmail("target@example.com");
    user.setPassword("new");
    user.setAccountToken("a-real-token");
    user.setValidated(validated);
    return user;
  }

  @Test
  void postAuditsRegistrationWhenTheUserWasNeverValidated() {
    logout(widgetContext);
    addQueryParameter(widgetContext, "confirmation", "a-real-token");
    addQueryParameter(widgetContext, "password", "Correct-Horse-B4ttery!");
    addQueryParameter(widgetContext, "password2", "Correct-Horse-B4ttery!");

    User target = userWithToken(null);

    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<LogoutCommand> logoutCommand = mockStatic(LogoutCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      userRepo.when(() -> UserRepository.findByAccountToken("a-real-token")).thenReturn(target);

      new AccountValidationWidget().post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT),
          eq("user.registered"), eq(AuditEventCommand.SUCCESS), eq("user"), eq("21"),
          eq("target@example.com"), any()), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), any(), eq("user.password.reset.completed"),
          any(), any(), any(), any(), any()), never());
      userRepo.verify(() -> UserRepository.updateValidated(target), times(1));
    }
  }

  @Test
  void postAuditsPasswordResetCompletionWhenTheUserWasAlreadyValidated() {
    logout(widgetContext);
    addQueryParameter(widgetContext, "confirmation", "a-real-token");
    addQueryParameter(widgetContext, "password", "Correct-Horse-B4ttery!");
    addQueryParameter(widgetContext, "password2", "Correct-Horse-B4ttery!");

    User target = userWithToken(new Timestamp(System.currentTimeMillis() - 86_400_000L));

    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<LogoutCommand> logoutCommand = mockStatic(LogoutCommand.class);
        MockedStatic<UnsuspendRequestRepository> requestRepo = mockStatic(UnsuspendRequestRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      userRepo.when(() -> UserRepository.findByAccountToken("a-real-token")).thenReturn(target);
      // #492 Phase 3: this completion is also checked against a pending maker-checker
      // reverification -- none exists for this plain self-service reset.
      requestRepo.when(() -> UnsuspendRequestRepository.findApprovedByTargetUserId(21L)).thenReturn(null);

      new AccountValidationWidget().post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT),
          eq("user.password.reset.completed"), eq(AuditEventCommand.SUCCESS), eq("user"), eq("21"),
          eq("target@example.com"), any()), times(1));
      requestRepo.verify(() -> UnsuspendRequestRepository.markReverified(anyLong()), never());
      audit.verify(() -> AuditEventCommand.record(any(), any(), eq("user.registered"),
          any(), any(), any(), any(), any()), never());
      userRepo.verify(() -> UserRepository.updateValidated(any()), never());
    }
  }

  @Test
  void postDoesNotAuditWhenThePasswordsDoNotMatch() {
    logout(widgetContext);
    addQueryParameter(widgetContext, "confirmation", "a-real-token");
    addQueryParameter(widgetContext, "password", "correcthorsebattery");
    addQueryParameter(widgetContext, "password2", "somethingElse");

    User target = userWithToken(null);

    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      userRepo.when(() -> UserRepository.findByAccountToken("a-real-token")).thenReturn(target);

      new AccountValidationWidget().post(widgetContext);

      audit.verifyNoInteractions();
      userRepo.verify(() -> UserRepository.updatePassword(any()), never());
    }
  }

  @Test
  void resolveConfirmationPrefersPathSegment() {
    // The email link now carries the token as a path segment; that is what defeats a path-caching proxy.
    assertEquals("c48f6666-531b-4302-8ef1-a911016aea9b",
        AccountValidationWidget.resolveConfirmation("/validate-account/c48f6666-531b-4302-8ef1-a911016aea9b", null));
  }

  @Test
  void resolveConfirmationPathBeatsQuery() {
    // If both are present the path wins; they should be the same token in practice.
    assertEquals("path-token",
        AccountValidationWidget.resolveConfirmation("/validate-account/path-token", "query-token"));
  }

  @Test
  void resolveConfirmationFallsBackToQueryForLegacyLinks() {
    // Links delivered before this change (and the hidden field the password form posts back) still work.
    assertEquals("legacy-token",
        AccountValidationWidget.resolveConfirmation("/validate-account", "legacy-token"));
  }

  @Test
  void resolveConfirmationHandlesContextPathAndTrailingSegments() {
    assertEquals("tok", AccountValidationWidget.resolveConfirmation("/app/validate-account/tok", null));
    assertEquals("tok", AccountValidationWidget.resolveConfirmation("/validate-account/tok/extra", null));
  }

  @Test
  void resolveConfirmationBlankPathSegmentFallsBackAndNullWhenAbsent() {
    // A trailing slash with no token, and the ?status=complete case, must not be read as a token.
    assertEquals("q", AccountValidationWidget.resolveConfirmation("/validate-account/", "q"));
    assertNull(AccountValidationWidget.resolveConfirmation("/validate-account", null));
    assertEquals("q", AccountValidationWidget.resolveConfirmation(null, "q"));
  }
}
