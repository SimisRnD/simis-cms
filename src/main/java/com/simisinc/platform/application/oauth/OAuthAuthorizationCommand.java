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

import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.text.RandomStringGenerator;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.simisinc.platform.application.cms.UrlCommand;

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

  public static String getAuthorizationUrl(String resource) {
    String clientId = OAuthConfigurationCommand.getClientId();
    if (StringUtils.isAnyBlank(clientId)) {
      return null;
    }

    String callback = OAuthConfigurationCommand.getRedirectUrl();

    String state = generator.generate(14);
    // Determine resource to redirect back to after credentials are verified
    if (StringUtils.isBlank(resource) || "/logout".equals(resource) || "/login".equals(resource)) {
      resource = "/";
    }
    stateCache.put(state, resource);

    String authEndpoint = OAuthConfigurationCommand.retrieveAuthEndpoint();
    if (StringUtils.isBlank(authEndpoint)) {
      return null;
    }

    String authorizationUrl = authEndpoint +
        "?client_id=" + UrlCommand.encodeUri(clientId) +
        "&response_type=code" +
        "&scope=openid%20profile%20email" +
        "&redirect_uri=" + UrlCommand.encodeUri(callback) +
        "&nonce=" + UrlCommand.encodeUri(state) +
        "&state=" + UrlCommand.encodeUri(state);

    LOG.debug("Using authorizationUrl: " + authorizationUrl);
    return authorizationUrl;
  }

  public static String resourceIfStateIsValid(String state) {
    return (stateCache.getIfPresent(state));
  }
}
