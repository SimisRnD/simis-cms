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

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.login.OAuthToken;
import com.simisinc.platform.domain.model.login.UserToken;
import com.simisinc.platform.infrastructure.persistence.login.UserTokenRepository;
import com.simisinc.platform.infrastructure.persistence.oauth.OAuthTokenRepository;
import com.simisinc.platform.presentation.controller.SessionConstants;

/**
 * Logs out and removes tokens
 *
 * @author matt rajkowski
 * @created 4/20/22 6:19 PM
 */
public class OAuthLogoutCommand {

  private static Log LOG = LogFactory.getLog(OAuthLogoutCommand.class);

  public static void logout(long userId, HttpServletRequest request) {
    // Remove the user token
    String userTokenValue = (String) request.getSession().getAttribute(SessionConstants.OAUTH_USER_TOKEN);
    request.getSession().removeAttribute(SessionConstants.OAUTH_USER_TOKEN);
    request.getSession().removeAttribute(SessionConstants.OAUTH_USER_EXPIRATION_TIME);

    // Cleanup the database
    UserToken userToken = UserTokenRepository.findByToken(userTokenValue);
    if (userToken != null) {
      OAuthToken oAuthToken = OAuthTokenRepository.findByUserTokenId(userId, userToken.getId());
      UserTokenRepository.remove(userToken);
      if (oAuthToken != null) {
        // http://localhost:8100/realms/name/protocol/openid-connect/logout
        Map<String, String> parameters = new HashMap<>();
        parameters.put("refresh_token", oAuthToken.getRefreshToken());
        OAuthHttpCommand.sendHttpPost("protocol/openid-connect/logout", parameters);
        LOG.debug("Logout complete.");
      }
    }
  }

  public static String getLogoutRedirect() {
    // http://localhost:8100/realms/name/protocol/openid-connect/logout
    // id_token_hint
    // end_session_endpoint
    return "/login";
  }

  public static String revoke(String accessToken) {
    // http://localhost:8100/realms/name/protocol/openid-connect/revoke
    return null;
  }
}
