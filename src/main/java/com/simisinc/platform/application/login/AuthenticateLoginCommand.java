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
import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.application.UserPasswordCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.UserToken;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserTokenRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.security.auth.login.LoginException;
import java.sql.Timestamp;
import java.util.Date;

/**
 * Commands for working with user authentication
 *
 * @author matt rajkowski
 * @created 4/8/18 9:36 PM
 */
public class AuthenticateLoginCommand {

  public static final String INVALID_CREDENTIALS = "The account information provided did not match our records. Please try again.";

  private static Log LOG = LogFactory.getLog(AuthenticateLoginCommand.class);

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
    if (!RateLimitCommand.isUsernameAllowedRightNow(username, false)) {
      throw new LoginException(RateLimitCommand.INVALID_ATTEMPTS);
    }
    if (!RateLimitCommand.isIpAllowedRightNow(ipAddress, false)) {
      throw new LoginException(RateLimitCommand.INVALID_ATTEMPTS);
    }

    // See if a user exists
    User user = LoadUserCommand.loadUser(username);
    if (user == null) {
      LOG.debug("Account not found");
      // Check and enforce rate limiting
      // Limit the number of attempts per ip accessing different usernames
      if (!RateLimitCommand.isIpAllowedRightNow(ipAddress, true)) {
        throw new LoginException(RateLimitCommand.INVALID_ATTEMPTS);
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

    // Account lockout (#295): a locked account cannot log in even with the correct password, until the
    // lock expires or an administrator clears it. Checked before the credentials cache so a lock always wins.
    if (user.isLocked()) {
      LOG.debug("Account locked until " + user.getLockedUntil());
      throw new LoginException("This account is temporarily locked due to failed login attempts. "
          + "Please try again later or contact an administrator.");
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
      // Clear any prior failed-attempt / lockout state on a successful login (#295)
      if (user.getFailedAttemptCount() > 0 || user.getLockedUntil() != null) {
        UserRepository.resetLockout(user.getId());
      }
      // Upgrade-on-login: now that the plaintext is confirmed, migrate an older hash to argon2id
      if (!user.getPassword().startsWith("$argon2id$")) {
        upgradeLegacyPasswordHash(user, password);
      }
      cache.put(user.getId(), username + ":" + password);
      return user;
    }

    // Record the failed attempt and lock the account once the threshold is crossed (#295, AC-7)
    int newCount = user.getFailedAttemptCount() + 1;
    Timestamp lockedUntil = null;
    if (newCount >= lockoutThreshold()) {
      lockedUntil = new Timestamp(System.currentTimeMillis() + lockoutDurationMinutes() * 60_000L);
      SaveAuditEventCommand.recordAuthentication("account.lockout", "failure", user.getId(), username,
          ipAddress, null, "Account locked after " + newCount + " consecutive failed attempts until " + lockedUntil);
      LOG.warn("Account locked (user id " + user.getId() + ") after " + newCount + " failed login attempts");
    }
    UserRepository.updateLockoutState(user.getId(), newCount, lockedUntil);

    // Record rate limiting
    // Limit the number of attempts per username (system(s) attempting the same username)
    // Limit the number of attempts per ip (a system attempting multiple users)
    RateLimitCommand.isUsernameAllowedRightNow(username, true);
    RateLimitCommand.isIpAllowedRightNow(ipAddress, true);
    LOG.debug("Password incorrect");
    throw new LoginException(INVALID_CREDENTIALS);
  }

  /**
   * Transparently migrates a just-verified legacy password hash to argon2id.
   *
   * <p>PR #117 switched new hashes to argon2id while keeping verification of older $argon2i$ hashes, so existing
   * users would otherwise keep their weaker hash until they happened to change their password. Once the supplied
   * plaintext has been confirmed against the stored hash, it is re-hashed with the current algorithm and persisted
   * through the same path the password-reset flow uses, so the whole store migrates during ordinary logins.
   *
   * <p>A persistence failure must never turn a valid login into a failed one, so any error is logged and swallowed;
   * the user is still authenticated and the upgrade is simply retried on a later login.
   */
  private static void upgradeLegacyPasswordHash(User user, String password) {
    try {
      user.setPassword(UserPasswordCommand.hash(password));
      UserRepository.updatePassword(user);
      LOG.info("Upgraded password hash to argon2id for user id: " + user.getId());
    } catch (Exception e) {
      LOG.error("Unable to upgrade password hash to argon2id for user id: " + user.getId(), e);
    }
  }

  /** @return the consecutive-failed-attempt threshold before lockout (site property, default 5). */
  private static int lockoutThreshold() {
    return parsePositiveInt(LoadSitePropertyCommand.loadByName("account.lockout.threshold"), 5);
  }

  /** @return how long a locked account stays locked, in minutes (site property, default 15). */
  private static int lockoutDurationMinutes() {
    return parsePositiveInt(LoadSitePropertyCommand.loadByName("account.lockout.durationMinutes"), 15);
  }

  private static int parsePositiveInt(String value, int defaultValue) {
    if (StringUtils.isBlank(value)) {
      return defaultValue;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      return parsed > 0 ? parsed : defaultValue;
    } catch (NumberFormatException e) {
      return defaultValue;
    }
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
