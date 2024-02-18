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

package com.simisinc.platform.application.login;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.simisinc.platform.application.oauth.OAuthConfigurationCommand;
import com.simisinc.platform.application.oauth.OAuthLogoutCommand;
import com.simisinc.platform.infrastructure.persistence.login.UserTokenRepository;
import com.simisinc.platform.presentation.controller.CookieConstants;
import com.simisinc.platform.presentation.controller.SessionConstants;
import com.simisinc.platform.presentation.controller.UserSession;

/**
 * Commands for logging the user out
 *
 * @author matt rajkowski
 * @created 2/4//21 12:33 PM
 */
public class LogoutCommand {

  public static void logout(HttpServletRequest request, HttpServletResponse response) {
    // Remove the cookie
    Cookie cookie = new Cookie(CookieConstants.USER_TOKEN, "");
    if (request.isSecure()) {
      cookie.setSecure(true);
    }
    cookie.setHttpOnly(true);
    cookie.setPath("/");
    cookie.setMaxAge(0);
    response.addCookie(cookie);

    // Update the user's session
    long userId = -1;
    UserSession userSession = (UserSession) request.getSession().getAttribute(SessionConstants.USER);
    if (userSession != null) {
      if (userSession.isLoggedIn()) {
        userId = userSession.getUserId();
      }
      request.getSession().removeAttribute(SessionConstants.USER);
      // Prevent other sessions
      UserTokenRepository.removeAll(userId);
    }

    // Log out of OAuth
    if (OAuthConfigurationCommand.isEnabled()) {
      OAuthLogoutCommand.logout(userId, request);
    }
  }
}
