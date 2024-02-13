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

import java.sql.Timestamp;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.text.RandomStringGenerator;

import com.simisinc.platform.application.login.AuthenticateLoginCommand;
import com.simisinc.platform.domain.model.login.OAuthToken;
import com.simisinc.platform.domain.model.login.UserToken;
import com.simisinc.platform.infrastructure.persistence.login.UserTokenRepository;
import com.simisinc.platform.infrastructure.persistence.oauth.OAuthTokenRepository;
import com.simisinc.platform.presentation.controller.CookieConstants;
import com.simisinc.platform.presentation.controller.SessionConstants;

/**
 * Configures and verifies OpenAuth2
 *
 * @author matt rajkowski
 * @created 4/20/22 6:19 PM
 */
public class OAuthRequestCommand {

  private static Log LOG = LogFactory.getLog(OAuthRequestCommand.class);

  private static RandomStringGenerator generator = new RandomStringGenerator.Builder()
      .selectFrom("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz0123456789".toCharArray()).build();

  public static String handleRequest(HttpServletRequest request, HttpServletResponse response, String resource) {
    if (!OAuthConfigurationCommand.isEnabled()) {
      // Skip if not turned on
      LOG.trace("OAuth is not enabled");
      return null;
    }

    // Check the URL for a "/oauth/callback"...
    if (OAuthConfigurationCommand.getRedirectUri().equals(resource)) {
      LOG.debug("Checking callback, retrieving access token...");
      String state = request.getParameter("state");
      String code = request.getParameter("code");
      OAuthToken oAuthToken = OAuthAccessTokenCommand.retrieveAccessToken(state, code);
      if (oAuthToken == null) {
        // Failed, return user back to login
        LOG.error("NO OAUTH TOKEN FOUND");
        return "/";
      }
      // Retrieve the user info and log them in
      OAuthLoginCommand.loginTheUser(request, response, oAuthToken);
      // Return the user to the site home page (or back to provider)
      if (StringUtils.isNotBlank(oAuthToken.getResource())) {
        return oAuthToken.getResource();
      }
      return "/";
    }

    // Every request, check if the user session is known, check the expiration
    HttpSession session = request.getSession(false);
    if (session != null) {
      String userTokenValue = (String) session.getAttribute(SessionConstants.OAUTH_USER_TOKEN);
      Object oAuthExpiresValue = session.getAttribute(SessionConstants.OAUTH_USER_EXPIRATION_TIME);
      boolean oAuthNotExpired = true;
      if (oAuthExpiresValue != null) {
        long expiredTime = (Long) oAuthExpiresValue;
        if (expiredTime < System.currentTimeMillis()) {
          oAuthNotExpired = false;
        }
      }
      // Check if it is valid
      if (StringUtils.isNotBlank(userTokenValue) && oAuthNotExpired) {
        LOG.debug("OAuth check valid");
        return null;
      }
    }

    // User session is not found, check the USER TOKEN cookie, then check OAUTH expiration, refresh if needed
    LOG.debug("Checking cookie value");
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (Cookie thisCookie : cookies) {
        if (!thisCookie.getName().equals(CookieConstants.USER_TOKEN)) {
          continue;
        }
        String userTokenValue = thisCookie.getValue();
        UserToken userToken = UserTokenRepository.findByToken(userTokenValue);
        if (userToken == null) {
          LOG.debug("Cookie userToken not found");
          break;
        }
        OAuthToken oAuthToken = OAuthTokenRepository.findByUserTokenId(userToken.getUserId(), userToken.getId());
        if (oAuthToken == null) {
          LOG.debug("Cookie oAuthToken not found");
          break;
        }
        // If the token is not expired, but the aToken is, then use the rToken to get a new aToken
        Timestamp now = new Timestamp(System.currentTimeMillis());
        if (userToken.getExpires().before(now)) {
          // Remove it and logout
          UserTokenRepository.remove(userToken);
          LOG.debug("Token is already expired");
          break;
        }
        LOG.debug("User token is not expired");

        // Check if the access token is expired
        if (oAuthToken.getExpires().after(now)) {
          LOG.debug("Access token is not expired");
          // Continue to use it
          request.getSession().setAttribute(SessionConstants.OAUTH_USER_TOKEN, userToken.getToken());
          if (oAuthToken.getExpires() != null) {
            request.getSession().setAttribute(SessionConstants.OAUTH_USER_EXPIRATION_TIME,
                oAuthToken.getExpires().getTime());
          }
          return null;
        }

        // Determine if the expiration can be extended
        if (oAuthToken.getRefreshExpires() != null && oAuthToken.getRefreshExpires().before(now)) {
          LOG.debug("Refresh token is expired, force login");
          break;
        }

        LOG.debug("Access token is expired, refresh token is not expired (or unknown)");
        oAuthToken = OAuthAccessTokenCommand.refreshAccessToken(oAuthToken);
        if (oAuthToken == null) {
          LOG.debug("Refreshed token not found");
          break;
        }
        // Extend the token expiration date
        int oAuthExpirationSeconds = 14 * 24 * 60 * 60;
        if (oAuthToken.getRefreshExpiresIn() > 0) {
          oAuthExpirationSeconds = oAuthToken.getRefreshExpiresIn();
        } else if (oAuthToken.getExpiresIn() > 0) {
          oAuthExpirationSeconds = oAuthToken.getExpiresIn();
        }
        LOG.debug("Extending the token expiration");
        AuthenticateLoginCommand.extendTokenExpiration(userTokenValue, oAuthExpirationSeconds);
        LOG.debug("Updating the OAuthToken record");
        oAuthToken = OAuthTokenRepository.save(oAuthToken);
        if (oAuthToken == null) {
          LOG.debug("Token not updated");
          break;
        }
        // Extend the cookie
        LOG.debug("Extending the cookie");
        Cookie cookie = new Cookie(CookieConstants.USER_TOKEN, userTokenValue);
        if (request.isSecure()) {
          cookie.setSecure(true);
        }
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(oAuthExpirationSeconds);
        response.addCookie(cookie);
        // Continue to use it
        request.getSession().setAttribute(SessionConstants.OAUTH_USER_TOKEN, userToken.getToken());
        if (oAuthToken.getExpires() != null) {
          request.getSession().setAttribute(SessionConstants.OAUTH_USER_EXPIRATION_TIME,
              oAuthToken.getExpires().getTime());
        }
        return null;
      }
    }

    // Otherwise, the user must log in
    return OAuthAuthorizationCommand.getAuthorizationUrl(resource);
  }
}
