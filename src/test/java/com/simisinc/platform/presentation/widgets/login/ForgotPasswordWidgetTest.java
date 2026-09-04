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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Verifies the self-service forgot-password request is recorded in the audit log (issue #492) --
 * before this, only an admin-initiated reset (UserDetailsWidget) was audited; a visitor requesting
 * their own reset left no trace at all.
 *
 * <p>Also covers issue #1791: the per-ip rate limit has to be keyed to the address the reset is
 * being driven from, not the one the session was created at.
 *
 * <p>postRecordsFailureWhenTheTokenWriteFails and
 * postAnswersIdenticallyWhetherTheAccountIsMissingOrTheTokenWriteFailed pin the null return of
 * UserRepository#createAccountToken on this public page. The audit line recorded SUCCESS and the
 * statements after it dereferenced the same null reference, so a write that did not take produced a
 * NullPointerException for an unauthenticated visitor plus an audit trail claiming a reset that
 * never happened. The admin-initiated path answers this by telling the admin (UserDetailsWidget,
 * #1837); this one cannot, because the response here is the enumeration control -- so the second
 * test asserts the failure response is indistinguishable from the no-such-username response rather
 * than asserting any particular wording.
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

  /** The address the session was created at, hours ago. */
  private static final String SESSION_IP = "203.0.113.10";
  /** The address this reset is actually being submitted from. */
  private static final String REQUEST_IP = "198.51.100.7";

  @Test
  void postRateLimitsAgainstTheRequestAddressNotTheOneTheSessionBeganAt() {
    // Issue #1791. UserSession fixes its address in the constructor and web.xml's 60 minute timeout
    // is an idle one, so a session outlives the address it started at. Keying the per-ip bucket on
    // that address lets a reset driven from REQUEST_IP be charged to SESSION_IP instead -- which is
    // both an evasion (rotate the session, never fill a bucket) and a collateral block (everyone
    // whose session was created by the same upstream scanner or NAT shares one bucket).
    logout(widgetContext);
    widgetContext.getUserSession().setIpAddress(SESSION_IP);
    when(request.getRemoteAddr()).thenReturn(REQUEST_IP);
    addQueryParameter(widgetContext, "username", "target@example.com");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), anyBoolean())).thenReturn(true);
      User target = targetUser();
      loadUser.when(() -> LoadUserCommand.loadUser("target@example.com")).thenReturn(target);
      userRepo.when(() -> UserRepository.createAccountToken(target)).thenReturn(target);

      new ForgotPasswordWidget().post(widgetContext);

      // Both the check and the record -- the two the existing-user path reaches
      rateLimit.verify(() -> RateLimitCommand.isIpAllowedRightNow(eq(REQUEST_IP), anyBoolean()), times(2));
      rateLimit.verify(() -> RateLimitCommand.isIpAllowedRightNow(eq(SESSION_IP), anyBoolean()), never());
    }
  }

  @Test
  void postFallsBackToTheSessionAddressWhenTheRequestHasNone() {
    // The fallback is what keeps this at least as safe as before: no request address must never
    // become an unkeyed (null) bucket. Uses the nonexistent-username path, which is the third
    // rate-limit call site in the widget.
    logout(widgetContext);
    widgetContext.getUserSession().setIpAddress(SESSION_IP);
    when(request.getRemoteAddr()).thenReturn(null);
    addQueryParameter(widgetContext, "username", "nobody@example.com");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), anyBoolean())).thenReturn(true);
      loadUser.when(() -> LoadUserCommand.loadUser("nobody@example.com")).thenReturn(null);

      new ForgotPasswordWidget().post(widgetContext);

      rateLimit.verify(() -> RateLimitCommand.isIpAllowedRightNow(eq(SESSION_IP), anyBoolean()), times(2));
      rateLimit.verify(() -> RateLimitCommand.isIpAllowedRightNow(isNull(), anyBoolean()), never());
    }
  }

  @Test
  void postRecordsFailureWhenTheTokenWriteFails() {
    // UserRepository.createAccountToken() returns null when its DB update does not take (it logs
    // "createAccountToken failed!"). The audit line recorded SUCCESS unconditionally and then read
    // getId()/getEmail() off that same null reference, so the visitor got a NullPointerException and
    // the trail got a reset that never happened. No token was written, so no reset email or webhook
    // may be triggered either.
    logout(widgetContext);
    addQueryParameter(widgetContext, "username", "target@example.com");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), anyBoolean())).thenReturn(true);
      User target = targetUser();
      loadUser.when(() -> LoadUserCommand.loadUser("target@example.com")).thenReturn(target);
      userRepo.when(() -> UserRepository.createAccountToken(target)).thenReturn(null);

      WidgetContext result = new ForgotPasswordWidget().post(widgetContext);

      // The id and address come from the values captured before the call, not from the null return
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT),
          eq("user.password.reset.requested"), eq(AuditEventCommand.FAILURE), eq("user"), eq("11"),
          eq("target@example.com"), any()), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), any(), any(), eq(AuditEventCommand.SUCCESS),
          any(), any(), any(), any()), never());
      workflow.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), never());
      Assertions.assertNull(result.getErrorMessage());
      Assertions.assertNull(result.getWarningMessage());
    }
  }

  @Test
  void postAnswersIdenticallyWhetherTheAccountIsMissingOrTheTokenWriteFailed() {
    // The enumeration control on this page is that the response does not vary with account
    // existence, so the write-failure arm cannot report the failure the way the admin page does --
    // any failure response would be reachable only for a username that does exist, which is exactly
    // the distinction the shared message exists to hide. A generic failure message would be no
    // safer: what leaks is the difference, not the wording. This asserts the two responses match
    // rather than asserting a literal string, so it keeps holding if the copy is ever reworded.
    logout(widgetContext);

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), anyBoolean())).thenReturn(true);
      User target = targetUser();
      loadUser.when(() -> LoadUserCommand.loadUser("target@example.com")).thenReturn(target);
      loadUser.when(() -> LoadUserCommand.loadUser("nobody@example.com")).thenReturn(null);
      userRepo.when(() -> UserRepository.createAccountToken(target)).thenReturn(null);

      // An account that does not exist at all
      addQueryParameter(widgetContext, "username", "nobody@example.com");
      WidgetContext missing = new ForgotPasswordWidget().post(widgetContext);
      String missingMessage = missing.getSuccessMessage();
      String missingJsp = missing.getJsp();
      String missingError = missing.getErrorMessage();
      String missingWarning = missing.getWarningMessage();

      // An account that does exist, whose token write did not take
      addQueryParameter(widgetContext, "username", "target@example.com");
      WidgetContext failed = new ForgotPasswordWidget().post(widgetContext);

      Assertions.assertNotNull(missingMessage);
      Assertions.assertEquals(missingMessage, failed.getSuccessMessage());
      Assertions.assertEquals(missingJsp, failed.getJsp());
      Assertions.assertEquals(missingError, failed.getErrorMessage());
      Assertions.assertEquals(missingWarning, failed.getWarningMessage());
    }
  }
}
