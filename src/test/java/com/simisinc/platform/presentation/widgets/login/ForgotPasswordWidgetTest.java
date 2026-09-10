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
 * <p>The three postWhenTheTokenWrite* tests pin the null return of
 * {@code UserRepository#createAccountToken}. The widget assigned that return back over its own
 * {@code user} reference and then read {@code user.getId()} out of it as an argument to the audit
 * call, so a failed write threw a NullPointerException before any record was written -- an
 * unauthenticated visitor got a 500 and the attempt left no trace at all. Because that 500 could
 * only ever happen for an account that exists, it also answered the one question this page is
 * built to refuse; postAnswersAFailedTokenWriteExactlyLikeANonexistentUsername is the regression
 * test for that property.
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

  /** An account already holding a link that still resolves, as if one was emailed minutes ago. */
  private static User userWithOutstandingLink() {
    User user = targetUser();
    user.setAccountToken("still-valid-token");
    user.setAccountTokenExpires(new java.sql.Timestamp(System.currentTimeMillis() + 3_600_000L));
    return user;
  }

  @Test
  void postReusesAnOutstandingLinkRatherThanReplacingIt() {
    // An account holds exactly one token, so minting here would stop the link already in that
    // person's inbox from resolving (#1836). This page needs no authentication, so unconditional
    // minting let anyone who knew a username break an in-progress recovery at will.
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
      User target = userWithOutstandingLink();
      loadUser.when(() -> LoadUserCommand.loadUser("target@example.com")).thenReturn(target);

      new ForgotPasswordWidget().post(widgetContext);

      // The stored token is left alone...
      userRepo.verify(() -> UserRepository.createAccountToken(any()), never());
      Assertions.assertEquals("still-valid-token", target.getAccountToken(),
          "the outstanding link must survive the request that would have replaced it");
      // ...but the person still gets their link re-sent, and the request is still recorded.
      workflow.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT),
          eq("user.password.reset.requested"), eq(AuditEventCommand.SUCCESS), eq("user"), eq("11"),
          eq("target@example.com"), any()), times(1));
    }
  }

  @Test
  void postMintsANewLinkWhenTheOutstandingOneHasExpired() {
    // Preserving a link that no longer resolves would leave the account unable to recover at all,
    // so an expired token is replaced exactly as before.
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
      target.setAccountToken("long-since-expired");
      target.setAccountTokenExpires(new java.sql.Timestamp(System.currentTimeMillis() - 1_000L));
      loadUser.when(() -> LoadUserCommand.loadUser("target@example.com")).thenReturn(target);
      userRepo.when(() -> UserRepository.createAccountToken(target)).thenReturn(target);

      new ForgotPasswordWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.createAccountToken(target), times(1));
      workflow.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), times(1));
    }
  }

  @Test
  void postAnswersIdenticallyWhetherOrNotTheLinkWasReused() {
    // Enumeration safety: reusing must not become an oracle. The message and JSP have to match the
    // mint path exactly, which is also what a nonexistent username already returns.
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
      User reused = userWithOutstandingLink();
      loadUser.when(() -> LoadUserCommand.loadUser("target@example.com")).thenReturn(reused);

      WidgetContext result = new ForgotPasswordWidget().post(widgetContext);

      Assertions.assertEquals(ForgotPasswordWidget.SUCCESS_JSP, result.getJsp());
      Assertions.assertEquals(
          "If the email you specified exists in our system, we've sent a password reset link to it.",
          result.getSuccessMessage());
    }
  }

  @Test
  void anAccountWithAnUnusableEmailAnswersLikeEveryOtherOutcome() {
    // This branch is only reachable when the username DOES resolve to an account, so the previous
    // "Check the username and try again" warning told an unauthenticated caller that the account
    // exists -- the one hole in this page's otherwise careful enumeration defence. It now returns
    // the same message and the same JSP as the not-found and the mailed paths.
    //
    // All three paths reference GENERIC_RESPONSE_MESSAGE, so asserting against that constant is
    // what makes them indistinguishable; three separate literals could have drifted apart.
    logout(widgetContext);
    addQueryParameter(widgetContext, "username", "target@example.com");

    User unusable = targetUser();
    unusable.setEmail("not-an-email");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), anyBoolean())).thenReturn(true);
      loadUser.when(() -> LoadUserCommand.loadUser("target@example.com")).thenReturn(unusable);

      WidgetContext result = new ForgotPasswordWidget().post(widgetContext);

      Assertions.assertEquals(ForgotPasswordWidget.GENERIC_RESPONSE_MESSAGE, result.getSuccessMessage());
      Assertions.assertEquals(ForgotPasswordWidget.SUCCESS_JSP, result.getJsp(),
          "the JSP must match too -- landing on the form is as good an oracle as a different message");
      Assertions.assertNull(result.getWarningMessage(),
          "the warning that distinguished an existing account must be gone");
      // Nothing was mailed, because nothing could be: the address is unusable either way.
      userRepo.verify(() -> UserRepository.createAccountToken(any()), never());
      workflow.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), never());
    }
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

  /** The response every outcome of this page must share, whatever actually happened server-side. */
  private static final String GENERIC_MESSAGE =
      "If the email you specified exists in our system, we've sent a password reset link to it.";

  @Test
  void postWhenTheTokenWriteFailsRecordsFailureAndSendsNoLink() {
    // UserRepository.createAccountToken() returns null when its DB update does not take (it logs
    // "createAccountToken failed!"). The widget assigned that null back over `user`, so
    // String.valueOf(user.getId()) -- an argument to the audit call, evaluated before it -- threw.
    // The record was never written: a failed reset left no evidence it had been attempted. No token
    // was written either, so no reset email may be triggered.
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

      new ForgotPasswordWidget().post(widgetContext);

      // The id and email come from the reference captured before the call, not from the null return
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT),
          eq("user.password.reset.requested"), eq(AuditEventCommand.FAILURE), eq("user"), eq("11"),
          eq("target@example.com"), any()), times(1));
      workflow.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), never());
    }
  }

  @Test
  void postAnswersAFailedTokenWriteExactlyLikeANonexistentUsername() {
    // Enumeration safety is the constraint the admin-side sibling (#1837) did not have: there, the
    // failure is reported to a signed-in admin by name. Here the caller is anonymous and may not be
    // the account holder, so a failed write must be indistinguishable from "no such account" -- a
    // failed write can only happen for an account that exists, so any distinct response is an
    // oracle. Runs both paths against the same context and compares what the visitor actually sees.
    logout(widgetContext);

    String[] nonexistent;
    String[] writeFailed;

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), anyBoolean())).thenReturn(true);
      User target = targetUser();
      loadUser.when(() -> LoadUserCommand.loadUser("nobody@example.com")).thenReturn(null);
      loadUser.when(() -> LoadUserCommand.loadUser("target@example.com")).thenReturn(target);
      userRepo.when(() -> UserRepository.createAccountToken(target)).thenReturn(null);

      addQueryParameter(widgetContext, "username", "nobody@example.com");
      new ForgotPasswordWidget().post(widgetContext);
      nonexistent = visitorResponse(widgetContext);

      resetVisitorResponse(widgetContext);

      addQueryParameter(widgetContext, "username", "target@example.com");
      new ForgotPasswordWidget().post(widgetContext);
      writeFailed = visitorResponse(widgetContext);
    }

    Assertions.assertArrayEquals(nonexistent, writeFailed,
        "a failed token write must be indistinguishable from a username that does not exist");
    // Pin the shared response too, so making both paths identically wrong would not pass
    Assertions.assertArrayEquals(new String[] { GENERIC_MESSAGE, ForgotPasswordWidget.SUCCESS_JSP, null, null }, writeFailed);
  }

  @Test
  void postWhenTheTokenWriteSucceedsStillTriggersTheResetEvent() {
    // The guard must not cost the working case its email: a token that writes still audits SUCCESS
    // and dispatches the reset event.
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
      userRepo.when(() -> UserRepository.createAccountToken(target)).thenReturn(target);

      new ForgotPasswordWidget().post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT),
          eq("user.password.reset.requested"), eq(AuditEventCommand.SUCCESS), eq("user"), eq("11"),
          eq("target@example.com"), any()), times(1));
      workflow.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), times(1));
      Assertions.assertArrayEquals(new String[] { GENERIC_MESSAGE, ForgotPasswordWidget.SUCCESS_JSP, null, null },
          visitorResponse(widgetContext));
    }
  }

  /** Everything this page hands back to an unauthenticated visitor. */
  private static String[] visitorResponse(WidgetContext context) {
    return new String[] { context.getSuccessMessage(), context.getJsp(), context.getWarningMessage(),
        context.getErrorMessage() };
  }

  private static void resetVisitorResponse(WidgetContext context) {
    context.setSuccessMessage(null);
    context.setJsp(null);
    context.setWarningMessage(null);
    context.setErrorMessage(null);
  }
}
