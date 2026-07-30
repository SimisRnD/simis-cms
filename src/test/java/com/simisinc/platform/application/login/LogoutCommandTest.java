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

package com.simisinc.platform.application.login;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.oauth.OAuthRequestCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.login.UserTokenRepository;
import com.simisinc.platform.presentation.controller.SessionConstants;
import com.simisinc.platform.presentation.controller.UserSession;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Verifies logout() clears every per-user session flag it owns, not just the UserSession itself --
 * a flag left behind (e.g. the visual editor's edit mode) must not survive to whichever identity,
 * if any, authenticates next on this same HttpSession.
 *
 * @author elizabeth houser
 */
class LogoutCommandTest {

  @Test
  void logoutClearsThePageEditModeFlagAlongsideTheUserSession() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    when(request.getSession()).thenReturn(session);

    UserSession userSession = new UserSession();
    userSession.login(new User());
    when(session.getAttribute(SessionConstants.USER)).thenReturn(userSession);

    try (MockedStatic<OAuthRequestCommand> oauth = mockStatic(OAuthRequestCommand.class);
        MockedStatic<UserTokenRepository> userTokenRepo = mockStatic(UserTokenRepository.class)) {
      oauth.when(OAuthRequestCommand::isEnabled).thenReturn(false);
      userTokenRepo.when(() -> UserTokenRepository.removeAll(anyLong())).thenAnswer(invocation -> null);

      LogoutCommand.logout(request, response);
    }

    verify(session).removeAttribute(SessionConstants.USER);
    verify(session).removeAttribute(SessionConstants.PAGE_EDIT_MODE);
  }
}
