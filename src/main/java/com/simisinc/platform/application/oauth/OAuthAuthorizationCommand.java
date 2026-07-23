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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.presentation.controller.SessionConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.text.RandomStringGenerator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.concurrent.TimeUnit;

/**
 * Configures and verifies OpenAuth2 and OIDC
 *
 * @author matt rajkowski
 * @created 4/20/22 6:19 PM
 */
public class OAuthAuthorizationCommand {

  private static Log LOG = LogFactory.getLog(OAuthAuthorizationCommand.class);

  private static Cache<String, String> stateCache = Caffeine.newBuilder()
      .maximumSize(1_000_000)
      .expireAfterAccess(5, TimeUnit.MINUTES)
      .build();

  private static RandomStringGenerator generator = new RandomStringGenerator.Builder()
      .selectFrom("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz0123456789".toCharArray()).build();

  public static String getAuthorizationUrl(HttpServletRequest request, String resource) {
    String serviceUrl = LoadSitePropertyCommand.loadByName("oauth.serviceUrl");
    String clientId = LoadSitePropertyCommand.loadByName("oauth.clientId");
    if (StringUtils.isAnyBlank(serviceUrl, clientId)) {
      return null;
    }

    String siteUrl = LoadSitePropertyCommand.loadByName("site.url");
    String callback = siteUrl + (siteUrl.endsWith("/") ? "" : "/") + "oauth/callback";
    String state = generator.generate(14);
    // Determine resource to redirect back to after credentials are verified
    if (StringUtils.isBlank(resource) || "/logout".equals(resource) || "/login".equals(resource)) {
      resource ="/";
    }
    stateCache.put(state, resource);
    // [CSRF] Bind the state to THIS user's session. The callback is only accepted if it returns
    // the state this session generated -- otherwise an attacker who obtains a valid state+code for
    // their own account could trick a victim into the callback and log the victim into the
    // attacker's account (login CSRF / session fixation).
    request.getSession(true).setAttribute(SessionConstants.OAUTH_STATE, state);

    String authorizationUrl =
        serviceUrl + (serviceUrl.endsWith("/") ? "" : "/") + "protocol/openid-connect/auth" +
            "?client_id=" + UrlCommand.encodeUri(clientId) +
            "&response_type=code" +
            "&scope=openid%20profile%20email" +
            "&redirect_uri=" + UrlCommand.encodeUri(callback) +
            "&state=" + UrlCommand.encodeUri(state);

    LOG.debug("Using authorizationUrl: " + authorizationUrl);
    return authorizationUrl;
  }

  public static String resourceIfStateIsValid(String state) {
    return (stateCache.getIfPresent(state));
  }

  /**
   * Confirms that an OAuth callback's {@code state} is the one this session generated, and consumes
   * it so it cannot be replayed. This is the CSRF protection for the login flow: the state stored in
   * {@code getAuthorizationUrl} is bound to the session that started the flow, so a callback carrying
   * a state that some other session obtained is rejected. Returns false for a null session, a blank
   * state, or a mismatch; the stored state is always cleared (single use).
   *
   * @param session the current session ({@code request.getSession(false)}), may be null
   * @param state   the state parameter returned on the callback
   * @return true only if the state matches the one bound to this session
   */
  public static boolean consumeValidState(HttpSession session, String state) {
    if (session == null || StringUtils.isBlank(state)) {
      return false;
    }
    Object expected = session.getAttribute(SessionConstants.OAUTH_STATE);
    // Single use: clear it whether or not it matches, so a state cannot be replayed
    session.removeAttribute(SessionConstants.OAUTH_STATE);
    return state.equals(expected);
  }
}
