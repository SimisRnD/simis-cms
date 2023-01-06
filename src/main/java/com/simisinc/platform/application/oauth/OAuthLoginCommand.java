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

package com.simisinc.platform.application.oauth;

import com.simisinc.platform.application.CreateSessionCommand;
import com.simisinc.platform.application.SaveSessionCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.OAuthToken;
import com.simisinc.platform.domain.model.login.UserLogin;
import com.simisinc.platform.domain.model.login.UserToken;
import com.simisinc.platform.infrastructure.persistence.oauth.OAuthTokenRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserLoginRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserTokenRepository;
import com.simisinc.platform.presentation.controller.CookieConstants;
import com.simisinc.platform.presentation.controller.SessionConstants;
import com.simisinc.platform.presentation.controller.UserSession;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.Timestamp;
import java.util.UUID;

import static com.simisinc.platform.presentation.controller.UserSession.OAUTH_SOURCE;

/**
 * Retrieves user information and uses it to login the user
 *
 * @author matt rajkowski
 * @created 4/20/22 6:19 PM
 */
public class OAuthLoginCommand {

  private static Log LOG = LogFactory.getLog(OAuthLoginCommand.class);

  public static void loginTheUser(HttpServletRequest request, HttpServletResponse response, OAuthToken oAuthToken) {
    LOG.debug("Logging the user in using OAUTH request...");
    if (oAuthToken == null || StringUtils.isBlank(oAuthToken.getAccessToken())) {
      LOG.warn("An accessToken is required");
      return;
    }
    // Create or update the user record
    User user = OAuthUserInfoCommand.createUser(oAuthToken);
    if (user == null) {
      LOG.warn("The user was not found");
      return;
    }

    // Track the login
    LOG.debug("Logging the user in...");
    UserLogin userLogin = new UserLogin();
    userLogin.setSource(OAUTH_SOURCE);
    userLogin.setUserId(user.getId());
    userLogin.setIpAddress(request.getRemoteAddr());
    userLogin.setSessionId(request.getSession().getId());
    userLogin.setUserAgent(request.getHeader("USER-AGENT"));
    UserLoginRepository.save(userLogin);

    // Store a token to log the user in
    LOG.debug("Setting up a user token");
    int oAuthExpirationSeconds = 14 * 24 * 60 * 60;
    if (oAuthToken.getRefreshExpiresIn() > 0) {
      oAuthExpirationSeconds = oAuthToken.getRefreshExpiresIn();
    } else if (oAuthToken.getExpiresIn() > 0) {
      oAuthExpirationSeconds = oAuthToken.getExpiresIn();
    }
    String loginToken = UUID.randomUUID().toString() + user.getId();
    UserToken userToken = new UserToken();
    userToken.setUserId(user.getId());
    userToken.setLoginId(userLogin.getId());
    userToken.setToken(loginToken);
    userToken.setExpires(new Timestamp(System.currentTimeMillis() + (oAuthExpirationSeconds * 1000)));
    UserTokenRepository.add(userToken);

    // Save the oauth access token, (and refresh token) so future calls can be made
    oAuthToken.setUserId(user.getId());
    oAuthToken.setUserTokenId(userToken.getId());
    OAuthTokenRepository.save(oAuthToken);

    // Set the browser cookie
    LOG.debug("Adding a user cookie");
    Cookie cookie = new Cookie(CookieConstants.USER_TOKEN, userToken.getToken());
    if (request.isSecure()) {
      cookie.setSecure(true);
    }
    cookie.setHttpOnly(true);
    cookie.setPath("/");
    cookie.setMaxAge(oAuthExpirationSeconds);
    response.addCookie(cookie);

    // Update the OAuth session for next time
    request.getSession().setAttribute(SessionConstants.OAUTH_USER_TOKEN, userToken.getToken());
    if (oAuthToken.getExpires() != null) {
      request.getSession().setAttribute(SessionConstants.OAUTH_USER_EXPIRATION_TIME, oAuthToken.getExpires().getTime());
    } else {
      request.getSession().removeAttribute(SessionConstants.OAUTH_USER_EXPIRATION_TIME);
    }

    // Give the user a userSession
    String ipAddress = request.getRemoteAddr();
    String referer = request.getHeader("Referer");
    String userAgent = request.getHeader("USER-AGENT");
    UserSession userSession = CreateSessionCommand.createSession(OAUTH_SOURCE, request.getSession().getId(), ipAddress, referer, userAgent);
    userSession.login(user);
    SaveSessionCommand.saveSession(userSession);
    request.getSession().setAttribute(SessionConstants.USER, userSession);
    LOG.debug("User has been signed in.");
  }
}
