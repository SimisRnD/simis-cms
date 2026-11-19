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

package com.simisinc.platform.application.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.simisinc.platform.application.SaveSessionCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.application.login.AuthenticateLoginCommand;
import com.simisinc.platform.application.register.SaveUserCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.OAuthToken;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserLoginRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserTokenRepository;
import com.simisinc.platform.infrastructure.persistence.oauth.OAuthTokenRepository;
import com.simisinc.platform.presentation.controller.SessionConstants;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * A suspended account cannot sign in through SSO, and signing in is not a way to change it.
 *
 * <p>OAuthLoginCommand used to record a successful login, issue a "stay signed in" token and cookie, and
 * create a session for whatever account the identity provider named, suspended or not; only
 * WebRequestFilter's re-check on the next request signed a suspended account out. And
 * OAuthUserInfoCommand.createUser rewrote the account from the provider first -- profile, roles and
 * groups -- and marked it validated.
 *
 * @author SimIS Inc.
 */
class OAuthSuspendedAccountSignInTest {

  private static OAuthToken token() {
    OAuthToken token = new OAuthToken();
    token.setAccessToken("access-token");
    token.setRefreshToken("refresh-token");
    return token;
  }

  private static User account(long id, boolean enabled) {
    User user = new User();
    user.setId(id);
    user.setUsername("person");
    user.setEmail("person@example.com");
    user.setFirstName("Before");
    user.setEnabled(enabled);
    return user;
  }

  private static ObjectNode userInfo() {
    ObjectNode json = new ObjectMapper().createObjectNode();
    json.put("preferred_username", "person");
    json.put("email", "person@example.com");
    json.put("email_verified", true);
    json.put("given_name", "FromProvider");
    return json;
  }

  // --- OAuthUserInfoCommand.createUser -----------------------------------------------------

  @Test
  void aSuspendedAccountIsNotRewrittenFromTheProvider() {
    User suspended = account(10L, false);
    try (MockedStatic<OAuthHttpCommand> http = mockStatic(OAuthHttpCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class)) {
      http.when(() -> OAuthHttpCommand.sendHttpGet(anyString(), any())).thenReturn(userInfo());
      userRepo.when(() -> UserRepository.findByEmailAddress("person@example.com")).thenReturn(suspended);
      saveCmd.when(() -> SaveUserCommand.saveUser(suspended, true)).thenReturn(suspended);

      assertSame(suspended, OAuthUserInfoCommand.createUser(token()));

      assertEquals("Before", suspended.getFirstName());
      saveCmd.verify(() -> SaveUserCommand.saveUser(any(), eq(true)), never());
      userRepo.verify(() -> UserRepository.updateValidated(any()), never());
    }
  }

  @Test
  void anActiveAccountIsStillUpdatedFromTheProvider() {
    User active = account(11L, true);
    try (MockedStatic<OAuthHttpCommand> http = mockStatic(OAuthHttpCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class)) {
      http.when(() -> OAuthHttpCommand.sendHttpGet(anyString(), any())).thenReturn(userInfo());
      userRepo.when(() -> UserRepository.findByEmailAddress("person@example.com")).thenReturn(active);
      saveCmd.when(() -> SaveUserCommand.saveUser(active, true)).thenReturn(active);

      assertSame(active, OAuthUserInfoCommand.createUser(token()));

      assertEquals("FromProvider", active.getFirstName());
      saveCmd.verify(() -> SaveUserCommand.saveUser(active, true));
      userRepo.verify(() -> UserRepository.updateValidated(active));
    }
  }

  // --- OAuthLoginCommand.loginTheUser ------------------------------------------------------

  @Test
  void aSuspendedAccountGetsNoLoginTokenCookieOrSession() {
    OAuthToken oAuthToken = token();
    User suspended = account(10L, false);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    when(request.getSession()).thenReturn(session);
    when(session.getId()).thenReturn("session-id");
    // Localhost short-circuits the geo-ip lookup in CreateSessionCommand, which the previous code reached
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");

    try (MockedStatic<OAuthUserInfoCommand> userInfo = mockStatic(OAuthUserInfoCommand.class);
        MockedStatic<AuthenticateLoginCommand> auth = mockStatic(AuthenticateLoginCommand.class);
        MockedStatic<OAuthLogoutCommand> oAuthLogout = mockStatic(OAuthLogoutCommand.class);
        MockedStatic<UserLoginRepository> userLoginRepo = mockStatic(UserLoginRepository.class);
        MockedStatic<UserTokenRepository> userTokenRepo = mockStatic(UserTokenRepository.class);
        MockedStatic<OAuthTokenRepository> oAuthTokenRepo = mockStatic(OAuthTokenRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class);
        MockedStatic<SaveSessionCommand> saveSession = mockStatic(SaveSessionCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), anyString())).thenReturn("UTC");
      userInfo.when(() -> OAuthUserInfoCommand.createUser(oAuthToken)).thenReturn(suspended);
      // What WebRequestFilter's per-request check also answers for a suspended account
      auth.when(() -> AuthenticateLoginCommand.getAuthenticatedUser(10L)).thenReturn(null);

      OAuthLoginCommand.loginTheUser(request, response, oAuthToken);

      userLoginRepo.verify(() -> UserLoginRepository.save(any()), never());
      userTokenRepo.verify(() -> UserTokenRepository.add(any()), never());
      oAuthTokenRepo.verify(() -> OAuthTokenRepository.save(any()), never());
      verify(response, never()).addCookie(any(Cookie.class));
      verify(session, never()).setAttribute(eq(SessionConstants.USER), any());
      verify(session, never()).setAttribute(eq(SessionConstants.OAUTH_USER_TOKEN), any());
      audit.verify(() -> SaveAuditEventCommand.recordAuthentication(eq("authentication.login.failure"), eq("failure"),
          eq(10L), eq("person@example.com"), eq("127.0.0.1"), eq("session-id"), anyString()));
      audit.verify(() -> SaveAuditEventCommand.recordAuthentication(eq("authentication.login.success"), anyString(),
          any(Long.class), any(), any(), any(), any()), never());
      // So the provider asks them to sign in rather than sending them straight back to be refused again
      oAuthLogout.verify(() -> OAuthLogoutCommand.endProviderSession(oAuthToken));
    }
  }

  // --- OAuthLogoutCommand.endProviderSession -----------------------------------------------

  @Test
  void endingTheProviderSessionSendsTheRefreshToken() {
    try (MockedStatic<OAuthHttpCommand> http = mockStatic(OAuthHttpCommand.class)) {
      OAuthLogoutCommand.endProviderSession(token());
      http.verify(() -> OAuthHttpCommand.sendHttpPost("protocol/openid-connect/logout",
          Map.of("refresh_token", "refresh-token")));
    }
  }

  @Test
  void withNoRefreshTokenNothingIsSent() {
    OAuthToken noRefresh = token();
    noRefresh.setRefreshToken(null);
    try (MockedStatic<OAuthHttpCommand> http = mockStatic(OAuthHttpCommand.class)) {
      OAuthLogoutCommand.endProviderSession(noRefresh);
      OAuthLogoutCommand.endProviderSession(null);
      http.verify(() -> OAuthHttpCommand.sendHttpPost(anyString(), anyMap()), never());
    }
  }
}
