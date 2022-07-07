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

import com.github.benmanes.caffeine.cache.Cache;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.UserPasswordCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.UserToken;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.persistence.login.UserTokenRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.security.auth.login.LoginException;
import java.time.Duration;
import java.util.Date;

/**
 * Commands for working with user authentication
 *
 * @author matt rajkowski
 * @created 4/8/18 9:36 PM
 */
public class AuthenticateLoginCommand {

  public static final String INVALID_CREDENTIALS = "The account information provided did not match our records. Please try again.";
  public static final String INVALID_ATTEMPTS = "Too many login attempts. Please try again later.";

  private static Log LOG = LogFactory.getLog(AuthenticateLoginCommand.class);

  /**
   * Rate limiting on a login page can be applied according to the user's username.
   *
   * @param username
   * @param startWatching
   * @return
   */
  public static boolean isUsernameAllowedRightNow(String username, boolean startWatching) {
    Cache cache = CacheManager.getCache(CacheManager.LOGIN_ATTEMPT_BY_USERNAME_CACHE);
    Bucket bucket = (Bucket) cache.getIfPresent(username);
    if (bucket == null) {
      if (startWatching) {
        Bandwidth limit = Bandwidth.simple(5, Duration.ofMinutes(30));
        bucket = Bucket.builder().addLimit(limit).build();
        cache.put(username, bucket);
      }
      return true;
    }
    return bucket.tryConsume(1);
  }

  /**
   * Rate limiting on a login page can be applied according to the IP address trying to log in
   *
   * @param ipAddress
   * @param startWatching
   * @return
   */
  public static boolean isIpAllowedRightNow(String ipAddress, boolean startWatching) {
    Cache cache = CacheManager.getCache(CacheManager.LOGIN_ATTEMPT_BY_IP_CACHE);
    Bucket bucket = (Bucket) cache.getIfPresent(ipAddress);
    if (bucket == null) {
      if (startWatching) {
        Bandwidth limit = Bandwidth.simple(10, Duration.ofMinutes(30));
        bucket = Bucket.builder().addLimit(limit).build();
        cache.put(ipAddress, bucket);
      }
      return true;
    }
    return bucket.tryConsume(1);
  }

  public static User getAuthenticatedUser(String username, String password, String ipAddress) throws DataException, LoginException {

    // Validate the inputs
    if (StringUtils.isBlank(username) || StringUtils.isBlank(password) || StringUtils.isBlank(ipAddress)) {
      throw new DataException("Please check the fields and try again");
    }

    // Check and enforce rate limiting
    // - One username trying to be logged into by one IP address (enforce limit)
    // - One username trying to be logged into by multiple IP addresses (enforce limit)
    // - Many usernames (valid or not) trying to be logged into by one IP address
    // - Many usernames (valid or not) trying to be logged into by multiple IP addresses
    if (!isUsernameAllowedRightNow(username, false)) {
      throw new LoginException(INVALID_ATTEMPTS);
    }
    if (!isIpAllowedRightNow(ipAddress, false)) {
      throw new LoginException(INVALID_ATTEMPTS);
    }

    // See if a user exists
    User user = LoadUserCommand.loadUser(username);
    if (user == null) {
      LOG.debug("Account not found");
      // Check and enforce rate limiting
      // Limit the number of attempts per ip accessing different usernames
      if (!isIpAllowedRightNow(ipAddress, true)) {
        throw new LoginException(INVALID_ATTEMPTS);
      }
      throw new LoginException(INVALID_CREDENTIALS);
    }
    if (user.isNotValidated()) {
      LOG.debug("Account not validated");
      throw new LoginException("This account needs to be validated by email. Please check your email for instructions.");
    }
    if (!user.isEnabled()) {
      throw new LoginException("The account has been suspended. Please contact an administrator.");
    }

    // Check the credentials cache
    Cache cache = CacheManager.getCache(CacheManager.USER_CREDENTIALS_CACHE);
    String comparison = (String) cache.getIfPresent(user.getId());
    if (comparison != null && comparison.equals(username + ":" + password)) {
      return user;
    }

    // Verify the password
    boolean verified = UserPasswordCommand.verify(password, user.getPassword());
    if (verified) {
      // Hash matches password
      LOG.debug("User validated");
      cache.put(user.getId(), username + ":" + password);
      return user;
    }

    // Record rate limiting
    // Limit the number of attempts per username (system(s) attempting the same username)
    // Limit the number of attempts per ip (a system attempting multiple users)
    isUsernameAllowedRightNow(username, true);
    isIpAllowedRightNow(ipAddress, true);
    LOG.debug("Password incorrect");
    throw new LoginException(INVALID_CREDENTIALS);
  }

  public static UserToken getValidToken(String token) {
    UserToken userToken = UserTokenRepository.findByToken(token);
    if (userToken == null) {
      return null;
    }
    // Check if token is expired
    if (userToken.getExpires().before(new Date())) {
      LOG.debug("Token is expired, request a new one");
      return null;
    }
    return userToken;
  }

  public static User getAuthenticatedUser(UserToken userToken) {
    // Check the user account
    User user = LoadUserCommand.loadUser(userToken.getUserId());
    if (user == null) {
      LOG.debug("No user for token");
      return null;
    }
    if (user.isNotValidated()) {
      LOG.warn("Account not validated");
      return null;
    }
    if (!user.isEnabled()) {
      LOG.warn("Account not enabled");
      return null;
    }
    return user;
  }

  public static User getAuthenticatedUser(String token) {
    UserToken userToken = getValidToken(token);
    if (userToken == null) {
      return null;
    }
    return getAuthenticatedUser(userToken);
  }

  public static void extendTokenExpiration(String token, int seconds) {
    if (token == null) {
      return;
    }
    UserTokenRepository.extendTokenExpiration(token, seconds);
  }
}
