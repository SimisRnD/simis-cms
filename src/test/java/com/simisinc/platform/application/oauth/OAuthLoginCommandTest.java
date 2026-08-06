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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.SaveSessionCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.OAuthToken;
import com.simisinc.platform.infrastructure.persistence.login.UserLoginRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserTokenRepository;
import com.simisinc.platform.infrastructure.persistence.oauth.OAuthTokenRepository;
import com.simisinc.platform.presentation.controller.SessionConstants;
import com.simisinc.platform.presentation.controller.UserSession;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Verifies that a completed OAuth login stamps UserSession.lastLoginTrackedDate the same way the
 * interactive password login (LoginWidget.finalizeLogin) does. Both paths write a user_logins row
 * directly rather than through WebRequestFilter.trackDailyLogin, so both must also seed the tracked
 * date themselves -- otherwise the very next request through WebRequestFilter (e.g. the page load
 * that follows this login) would see the field still null and write a second row for today.
 *
 * @author SimIS Inc.
 * @created 2026-08-05
 */
class OAuthLoginCommandTest {

  private static final ZoneId TEST_ZONE = ZoneId.of("UTC");

  @Test
  void loginTheUserStampsLastLoginTrackedDateSoTheNextRequestDoesNotWriteADuplicateRow() {
    OAuthToken oAuthToken = new OAuthToken();
    oAuthToken.setAccessToken("test-access-token");

    User user = new User();
    user.setId(9L);
    user.setEmail("oauth-user@example.com");

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    when(request.getSession()).thenReturn(session);
    when(session.getId()).thenReturn("session-id");
    // Localhost short-circuits GeoIPCommand.getLocation(...) to null, so CreateSessionCommand
    // (deliberately left un-mocked here) can run for real without a geo-ip database configured
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");

    try (MockedStatic<OAuthUserInfoCommand> userInfo = mockStatic(OAuthUserInfoCommand.class);
        MockedStatic<UserLoginRepository> userLoginRepo = mockStatic(UserLoginRepository.class);
        MockedStatic<UserTokenRepository> userTokenRepo = mockStatic(UserTokenRepository.class);
        MockedStatic<OAuthTokenRepository> oAuthTokenRepo = mockStatic(OAuthTokenRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SaveSessionCommand> saveSession = mockStatic(SaveSessionCommand.class)) {
      userInfo.when(() -> OAuthUserInfoCommand.createUser(oAuthToken)).thenReturn(user);
      // getSiteZoneId() falls back to its passed-in default when unconfigured -- pin it to a known zone
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), anyString()))
          .thenReturn(TEST_ZONE.getId());

      OAuthLoginCommand.loginTheUser(request, response, oAuthToken);

      ArgumentCaptor<UserSession> captor = ArgumentCaptor.forClass(UserSession.class);
      verify(session).setAttribute(eq(SessionConstants.USER), captor.capture());
      UserSession userSession = captor.getValue();

      Assertions.assertEquals(LocalDate.now(TEST_ZONE), userSession.getLastLoginTrackedDate());
    }
  }
}
