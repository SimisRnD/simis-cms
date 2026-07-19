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

import javax.security.auth.login.LoginException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.login.AuthenticateLoginCommand;
import com.simisinc.platform.application.login.TotpCommand;
import com.simisinc.platform.application.login.UserMfaRecoveryCodeCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserLoginRepository;
import com.simisinc.platform.presentation.controller.SessionConstants;
import com.simisinc.platform.presentation.controller.UserSession;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies the two-step login gate: a correct password alone must not establish a session when MFA is enabled, an
 * invalid code is rejected, a valid code completes the login, and accounts without MFA sign in as before.
 *
 * @author SimIS Inc.
 * @created 2026-07-17
 */
class LoginWidgetTest extends WidgetBase {

  // RFC 6238 reference secret; the value is irrelevant here because code verification is stubbed.
  private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

  private static User mfaUser(long id) {
    User user = new User();
    user.setId(id);
    user.setMfaEnabled(true);
    user.setMfaSecret(SECRET);
    return user;
  }

  @Test
  void passwordAloneDoesNotAuthenticateWhenMfaIsEnabled() {
    addQueryParameter(widgetContext, "email", "admin@example.com");
    addQueryParameter(widgetContext, "password", "correct-horse-battery");
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");

    LoginWidget widget = new LoginWidget();
    try (MockedStatic<AuthenticateLoginCommand> auth = mockStatic(AuthenticateLoginCommand.class)) {
      auth.when(() -> AuthenticateLoginCommand.getAuthenticatedUser(anyString(), anyString(), anyString()))
          .thenReturn(mfaUser(42L));
      widget.post(widgetContext);
    }

    // Held for MFA: the pending marker points at this user and the form is told to ask for a code.
    Assertions.assertEquals(42L, session.getAttribute(SessionConstants.MFA_PENDING_USER_ID));
    Assertions.assertEquals("true", request.getAttribute("mfaRequired"));
    // Critically, no session was established -- no landing-page redirect and no auth cookie.
    Assertions.assertNull(widgetContext.getRedirect());
    Assertions.assertNull(widgetContext.getErrorMessage());
    verify(response, never()).addCookie(any());
  }

  @Test
  void invalidMfaCodeIsRejectedAndDoesNotAuthenticate() {
    session.setAttribute(SessionConstants.MFA_PENDING_USER_ID, 42L);
    addQueryParameter(widgetContext, "code", "000000");

    LoginWidget widget = new LoginWidget();
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<TotpCommand> totp = mockStatic(TotpCommand.class);
        MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<UserMfaRecoveryCodeCommand> recovery = mockStatic(UserMfaRecoveryCodeCommand.class)) {
      userRepo.when(() -> UserRepository.findByUserId(anyLong())).thenReturn(mfaUser(42L));
      totp.when(() -> TotpCommand.verifyCode(anyString(), anyString())).thenReturn(false);
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      recovery.when(() -> UserMfaRecoveryCodeCommand.consume(any(), anyString())).thenReturn(false);
      widget.post(widgetContext);
    }

    Assertions.assertNotNull(widgetContext.getErrorMessage());
    Assertions.assertEquals("true", request.getAttribute("mfaRequired"));
    // Still pending: the marker is not cleared, and nothing was established.
    Assertions.assertEquals(42L, session.getAttribute(SessionConstants.MFA_PENDING_USER_ID));
    Assertions.assertNull(widgetContext.getRedirect());
    verify(response, never()).addCookie(any());
  }

  @Test
  void validMfaCodeCompletesTheLogin() {
    session.setAttribute(SessionConstants.MFA_PENDING_USER_ID, 42L);
    session.setAttribute(SessionConstants.USER, new UserSession()); // finalizeLogin logs into this
    addQueryParameter(widgetContext, "code", "123456");

    LoginWidget widget = new LoginWidget();
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<TotpCommand> totp = mockStatic(TotpCommand.class);
        MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<UserLoginRepository> userLoginRepo = mockStatic(UserLoginRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      userRepo.when(() -> UserRepository.findByUserId(anyLong())).thenReturn(mfaUser(42L));
      totp.when(() -> TotpCommand.verifyCode(anyString(), anyString())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.online")).thenReturn(true);
      widget.post(widgetContext);
    }

    // Logged in: pending marker cleared (removeAttribute is called), redirected, auth cookie set, no error.
    verify(session).removeAttribute(SessionConstants.MFA_PENDING_USER_ID);
    Assertions.assertEquals("/my-page", widgetContext.getRedirect());
    Assertions.assertNull(widgetContext.getErrorMessage());
    verify(response, times(1)).addCookie(any());
  }

  @Test
  void validRecoveryCodeCompletesTheLogin() {
    session.setAttribute(SessionConstants.MFA_PENDING_USER_ID, 42L);
    session.setAttribute(SessionConstants.USER, new UserSession());
    addQueryParameter(widgetContext, "code", "abcde-fghij");

    LoginWidget widget = new LoginWidget();
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<TotpCommand> totp = mockStatic(TotpCommand.class);
        MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<UserMfaRecoveryCodeCommand> recovery = mockStatic(UserMfaRecoveryCodeCommand.class);
        MockedStatic<UserLoginRepository> userLoginRepo = mockStatic(UserLoginRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      userRepo.when(() -> UserRepository.findByUserId(anyLong())).thenReturn(mfaUser(42L));
      // TOTP fails, but a recovery code is accepted
      totp.when(() -> TotpCommand.verifyCode(anyString(), anyString())).thenReturn(false);
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      recovery.when(() -> UserMfaRecoveryCodeCommand.consume(any(), anyString())).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.online")).thenReturn(true);
      widget.post(widgetContext);
    }

    // A valid recovery code completes the login just like a TOTP code
    verify(session).removeAttribute(SessionConstants.MFA_PENDING_USER_ID);
    Assertions.assertEquals("/my-page", widgetContext.getRedirect());
    Assertions.assertNull(widgetContext.getErrorMessage());
    verify(response, times(1)).addCookie(any());
  }

  @Test
  void userWithoutMfaLogsInDirectly() {
    addQueryParameter(widgetContext, "email", "user@example.com");
    addQueryParameter(widgetContext, "password", "hunter2hunter2");
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    session.setAttribute(SessionConstants.USER, new UserSession());

    User user = new User();
    user.setId(7L);
    user.setMfaEnabled(false);

    LoginWidget widget = new LoginWidget();
    try (MockedStatic<AuthenticateLoginCommand> auth = mockStatic(AuthenticateLoginCommand.class);
        MockedStatic<UserLoginRepository> userLoginRepo = mockStatic(UserLoginRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      auth.when(() -> AuthenticateLoginCommand.getAuthenticatedUser(anyString(), anyString(), anyString()))
          .thenReturn(user);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("site.online")).thenReturn(true);
      widget.post(widgetContext);
    }

    // No MFA -> straight to a logged-in session, never held for a code.
    Assertions.assertNull(session.getAttribute(SessionConstants.MFA_PENDING_USER_ID));
    Assertions.assertEquals("/my-page", widgetContext.getRedirect());
    Assertions.assertNull(widgetContext.getErrorMessage());
    verify(response, times(1)).addCookie(any());
  }

  @Test
  void badPasswordShowsAnErrorAndDoesNotAuthenticate() {
    addQueryParameter(widgetContext, "email", "user@example.com");
    addQueryParameter(widgetContext, "password", "wrong");
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");

    LoginWidget widget = new LoginWidget();
    try (MockedStatic<AuthenticateLoginCommand> auth = mockStatic(AuthenticateLoginCommand.class)) {
      auth.when(() -> AuthenticateLoginCommand.getAuthenticatedUser(anyString(), anyString(), anyString()))
          .thenThrow(new LoginException("Your sign in was incorrect"));
      widget.post(widgetContext);
    }

    Assertions.assertNotNull(widgetContext.getErrorMessage());
    Assertions.assertNull(widgetContext.getRedirect());
    Assertions.assertNull(session.getAttribute(SessionConstants.MFA_PENDING_USER_ID));
    verify(response, never()).addCookie(any());
  }

  @Test
  void mfaCodeStepIsRateLimitedAfterTooManyAttempts() {
    session.setAttribute(SessionConstants.MFA_PENDING_USER_ID, 42L);
    addQueryParameter(widgetContext, "code", "000000");
    when(request.getRemoteAddr()).thenReturn("203.0.113.7");

    LoginWidget widget = new LoginWidget();
    try (MockedStatic<TotpCommand> totp = mockStatic(TotpCommand.class);
        MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class)) {
      // The account has used up its allowance -- the limiter says "no" before any code is checked
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(eq("mfa:42"), eq(false))).thenReturn(false);
      widget.post(widgetContext);

      // A brute-forcer never even gets to a code comparison while locked out
      totp.verify(() -> TotpCommand.verifyCode(anyString(), anyString()), never());
    }

    // Rejected with the rate-limit message; still pending; nothing established
    Assertions.assertEquals(RateLimitCommand.INVALID_ATTEMPTS, widgetContext.getErrorMessage());
    Assertions.assertEquals("true", request.getAttribute("mfaRequired"));
    Assertions.assertEquals(42L, session.getAttribute(SessionConstants.MFA_PENDING_USER_ID));
    Assertions.assertNull(widgetContext.getRedirect());
    // No session artifacts at all: the response (cookies, headers) was never touched
    verifyNoInteractions(response);
  }

  @Test
  void invalidMfaCodeRecordsTheFailedAttempt() {
    session.setAttribute(SessionConstants.MFA_PENDING_USER_ID, 42L);
    addQueryParameter(widgetContext, "code", "000000");
    when(request.getRemoteAddr()).thenReturn("203.0.113.7");

    LoginWidget widget = new LoginWidget();
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<TotpCommand> totp = mockStatic(TotpCommand.class);
        MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<UserMfaRecoveryCodeCommand> recovery = mockStatic(UserMfaRecoveryCodeCommand.class)) {
      userRepo.when(() -> UserRepository.findByUserId(anyLong())).thenReturn(mfaUser(42L));
      totp.when(() -> TotpCommand.verifyCode(anyString(), anyString())).thenReturn(false);
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      recovery.when(() -> UserMfaRecoveryCodeCommand.consume(any(), anyString())).thenReturn(false);
      widget.post(widgetContext);

      // A wrong code is recorded against both the account and the IP so repeated guesses lock out
      rateLimit.verify(() -> RateLimitCommand.isUsernameAllowedRightNow("mfa:42", true));
      rateLimit.verify(() -> RateLimitCommand.isIpAllowedRightNow("203.0.113.7", true));
    }

    Assertions.assertNotNull(widgetContext.getErrorMessage());
    // Still pending: a bad code does not clear the gate or establish a session
    Assertions.assertEquals(42L, session.getAttribute(SessionConstants.MFA_PENDING_USER_ID));
    // No session artifacts at all: the response (cookies, headers) was never touched
    verifyNoInteractions(response);
  }
}
