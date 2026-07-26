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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.github.benmanes.caffeine.cache.Cache;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.application.UserPasswordCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.persistence.UserRepository;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

import javax.security.auth.login.LoginException;

/**
 * Verifies upgrade-on-login: a user whose password predates the argon2id switch (PR #117) has their stored hash
 * transparently migrated to argon2id the next time they sign in, while a user already on argon2id is left untouched.
 *
 * @author elizabeth houser
 * @created 2026-07-19
 */
class AuthenticateLoginCommandTest {

  private static final String IP_ADDRESS = "127.0.0.1";

  private static User enabledUser(long id, String storedHash) {
    User user = new User();
    user.setId(id);
    user.setEnabled(true);
    user.setValidated(new Timestamp(System.currentTimeMillis()));
    user.setPassword(storedHash);
    return user;
  }

  @Test
  @SuppressWarnings("unchecked")
  void legacyArgon2iHashIsUpgradedToArgon2idOnSuccessfulLogin() throws Exception {
    String username = "legacy@example.com";
    String password = "an existing user's password";

    // Simulate a password stored before the argon2id upgrade
    Argon2 legacy = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2i);
    String legacyHash = legacy.hash(2, 65536, 1, password.toCharArray());
    assertTrue(legacyHash.startsWith("$argon2i$"), "Sanity: the stored hash should start as argon2i: " + legacyHash);
    assertFalse(legacyHash.startsWith("$argon2id$"), "Sanity: a legacy hash is not yet argon2id");

    User user = enabledUser(7L, legacyHash);

    // A cache miss forces the real verify() path to run
    Cache credentialsCache = mock(Cache.class);
    when(credentialsCache.getIfPresent(any())).thenReturn(null);

    ArgumentCaptor<User> persisted = ArgumentCaptor.forClass(User.class);

    User result;
    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
        MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      loadUser.when(() -> LoadUserCommand.loadUser(username)).thenReturn(user);
      cacheManager.when(() -> CacheManager.getCache(CacheManager.USER_CREDENTIALS_CACHE)).thenReturn(credentialsCache);
      userRepo.when(() -> UserRepository.updatePassword(any(User.class))).thenReturn(user);

      result = AuthenticateLoginCommand.getAuthenticatedUser(username, password, IP_ADDRESS);

      // The migrated hash was persisted through the same path the password-reset flow uses, exactly once
      userRepo.verify(() -> UserRepository.updatePassword(persisted.capture()), times(1));
    }

    // The correct legacy password still authenticates
    assertNotNull(result, "A correct legacy password must still authenticate");

    // The hash handed to the persistence layer is now argon2id
    String storedHash = persisted.getValue().getPassword();
    assertTrue(storedHash.startsWith("$argon2id$"),
        "After one successful login, the stored hash must be migrated to argon2id: " + storedHash);
    // The same in-memory user reflects the migrated hash
    assertTrue(result.getPassword().startsWith("$argon2id$"), "The authenticated user carries the upgraded hash");
    // The migrated hash still verifies the user's original password
    assertTrue(UserPasswordCommand.verify(password, storedHash),
        "The re-hashed password must still verify the original plaintext");
  }

  @Test
  @SuppressWarnings("unchecked")
  void currentArgon2idHashIsNotReUpgraded() throws Exception {
    String username = "current@example.com";
    String password = "an already migrated password";

    User user = enabledUser(9L, UserPasswordCommand.hash(password));
    assertTrue(user.getPassword().startsWith("$argon2id$"), "Sanity: this user is already on argon2id");

    Cache credentialsCache = mock(Cache.class);
    when(credentialsCache.getIfPresent(any())).thenReturn(null);

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
        MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      loadUser.when(() -> LoadUserCommand.loadUser(username)).thenReturn(user);
      cacheManager.when(() -> CacheManager.getCache(CacheManager.USER_CREDENTIALS_CACHE)).thenReturn(credentialsCache);

      User result = AuthenticateLoginCommand.getAuthenticatedUser(username, password, IP_ADDRESS);
      assertNotNull(result, "A correct password must authenticate");

      // No migration is needed, so the password-update path is never touched
      userRepo.verify(() -> UserRepository.updatePassword(any(User.class)), never());
    }
  }

  // --- Failure branches of the password login gate (previously untested) ---

  @Test
  void blankCredentialsAreRejected() {
    assertThrows(DataException.class,
        () -> AuthenticateLoginCommand.getAuthenticatedUser("", "pw", IP_ADDRESS));
    assertThrows(DataException.class,
        () -> AuthenticateLoginCommand.getAuthenticatedUser("user@example.com", "", IP_ADDRESS));
    assertThrows(DataException.class,
        () -> AuthenticateLoginCommand.getAuthenticatedUser("user@example.com", "pw", ""));
  }

  @Test
  void aRateLimitedUsernameIsRejected() {
    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class)) {
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow("user@example.com", false)).thenReturn(false);
      assertThrows(LoginException.class,
          () -> AuthenticateLoginCommand.getAuthenticatedUser("user@example.com", "pw", IP_ADDRESS));
    }
  }

  @Test
  void anUnknownUserIsRejectedWithTheGenericMessage() {
    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class)) {
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      loadUser.when(() -> LoadUserCommand.loadUser("ghost@example.com")).thenReturn(null);

      LoginException ex = assertThrows(LoginException.class,
          () -> AuthenticateLoginCommand.getAuthenticatedUser("ghost@example.com", "pw", IP_ADDRESS));
      assertTrue(ex.getMessage().contains("did not match"), ex.getMessage());
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void anUnvalidatedAccountIsRejectedAfterCredentialVerification() {
    // Status checks happen AFTER credential verification to prevent username enumeration.
    // A correct password on an unvalidated account should still show the validation error.
    String password = "correct";
    User user = new User();
    user.setId(3L);
    user.setEnabled(true);
    user.setPassword(UserPasswordCommand.hash(password)); // validated left null -> isNotValidated() is true
    Cache credentialsCache = mock(Cache.class);
    when(credentialsCache.getIfPresent(any())).thenReturn(null);
    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
        MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      loadUser.when(() -> LoadUserCommand.loadUser("unvalidated@example.com")).thenReturn(user);
      cacheManager.when(() -> CacheManager.getCache(CacheManager.USER_CREDENTIALS_CACHE)).thenReturn(credentialsCache);

      LoginException ex = assertThrows(LoginException.class,
          () -> AuthenticateLoginCommand.getAuthenticatedUser("unvalidated@example.com", password, IP_ADDRESS));
      assertTrue(ex.getMessage().toLowerCase().contains("validated"), ex.getMessage());
      // A correct password on an inactive account must NOT increment the failed-attempt counter
      userRepo.verify(() -> UserRepository.updateLockoutState(anyLong(), anyInt(), any()), never());
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void aSuspendedAccountIsRejectedAfterCredentialVerification() {
    String password = "correct";
    User user = new User();
    user.setId(4L);
    user.setValidated(new Timestamp(System.currentTimeMillis()));
    user.setEnabled(false); // suspended
    user.setPassword(UserPasswordCommand.hash(password));
    Cache credentialsCache = mock(Cache.class);
    when(credentialsCache.getIfPresent(any())).thenReturn(null);
    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
        MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      loadUser.when(() -> LoadUserCommand.loadUser("suspended@example.com")).thenReturn(user);
      cacheManager.when(() -> CacheManager.getCache(CacheManager.USER_CREDENTIALS_CACHE)).thenReturn(credentialsCache);

      LoginException ex = assertThrows(LoginException.class,
          () -> AuthenticateLoginCommand.getAuthenticatedUser("suspended@example.com", password, IP_ADDRESS));
      assertTrue(ex.getMessage().toLowerCase().contains("suspended"), ex.getMessage());
      userRepo.verify(() -> UserRepository.updateLockoutState(anyLong(), anyInt(), any()), never());
    }
  }

  @Test
  void aWrongPasswordIsRejected() {
    User user = enabledUser(5L, UserPasswordCommand.hash("the real password"));
    Cache credentialsCache = mock(Cache.class);
    when(credentialsCache.getIfPresent(any())).thenReturn(null); // cache miss -> real verify runs
    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
        MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      loadUser.when(() -> LoadUserCommand.loadUser("user@example.com")).thenReturn(user);
      cacheManager.when(() -> CacheManager.getCache(CacheManager.USER_CREDENTIALS_CACHE)).thenReturn(credentialsCache);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn("");

      LoginException ex = assertThrows(LoginException.class,
          () -> AuthenticateLoginCommand.getAuthenticatedUser("user@example.com", "the WRONG password", IP_ADDRESS));
      assertTrue(ex.getMessage().contains("did not match"), ex.getMessage());
    }
  }

  @Test
  void aCachedCredentialAuthenticatesWithoutReverifyingTheStoredHash() throws Exception {
    // The stored hash is for a DIFFERENT (new) password, so a real hash verify would FAIL. But
    // the credentials cache holds the submitted username:password, so the login short-circuits
    // and returns the user. This is the mechanism that makes evicting the cache on a password
    // change essential (see UserRepository.updatePassword) -- otherwise the old password would
    // keep authenticating after a change.
    String username = "cached@example.com";
    String oldPassword = "the old password";
    User user = enabledUser(6L, UserPasswordCommand.hash("a different, newer password"));
    assertFalse(UserPasswordCommand.verify(oldPassword, user.getPassword()),
        "Sanity: the stored hash must NOT verify the old password");

    Cache credentialsCache = mock(Cache.class);
    when(credentialsCache.getIfPresent(6L)).thenReturn(AuthenticateLoginCommand.cacheToken(username, oldPassword));
    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
        MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class)) {
      rateLimit.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
      loadUser.when(() -> LoadUserCommand.loadUser(username)).thenReturn(user);
      cacheManager.when(() -> CacheManager.getCache(CacheManager.USER_CREDENTIALS_CACHE)).thenReturn(credentialsCache);

      User result = AuthenticateLoginCommand.getAuthenticatedUser(username, oldPassword, IP_ADDRESS);
      assertNotNull(result, "a cache hit authenticates without re-verifying the stored hash");
    }
  }

  // --- Durable account lockout (#295, AC-7) ---

  private static User lockoutUser(int failedAttemptCount, Timestamp lockedUntil) {
    User user = enabledUser(42L, UserPasswordCommand.hash("correct"));
    user.setFailedAttemptCount(failedAttemptCount);
    user.setLockedUntil(lockedUntil);
    return user;
  }

  private static void stubForLogin(MockedStatic<RateLimitCommand> rl, MockedStatic<CacheManager> cm,
      MockedStatic<LoadSitePropertyCommand> sp) {
    rl.when(() -> RateLimitCommand.isUsernameAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
    rl.when(() -> RateLimitCommand.isIpAllowedRightNow(anyString(), anyBoolean())).thenReturn(true);
    sp.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(""); // -> default threshold 5 / 15m
    Cache cache = mock(Cache.class);
    when(cache.getIfPresent(any())).thenReturn(null); // cache miss -> real verify path
    cm.when(() -> CacheManager.getCache(anyString())).thenReturn(cache);
  }

  @Test
  void locksTheAccountOnceTheThresholdIsCrossed() {
    try (MockedStatic<RateLimitCommand> rl = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> lu = mockStatic(LoadUserCommand.class);
        MockedStatic<CacheManager> cm = mockStatic(CacheManager.class);
        MockedStatic<UserRepository> ur = mockStatic(UserRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class);
        MockedStatic<LoadSitePropertyCommand> sp = mockStatic(LoadSitePropertyCommand.class)) {
      stubForLogin(rl, cm, sp);
      lu.when(() -> LoadUserCommand.loadUser("alice")).thenReturn(lockoutUser(4, null)); // one below default 5
      assertThrows(LoginException.class,
          () -> AuthenticateLoginCommand.getAuthenticatedUser("alice", "wrong", IP_ADDRESS));
      // Count advances to 5 and a lock (non-null expiry) is set; the lockout is audited.
      ur.verify(() -> UserRepository.updateLockoutState(eq(42L), eq(5), argThat(t -> t != null)));
      audit.verify(() -> SaveAuditEventCommand.recordAuthentication(eq("account.lockout"), any(), eq(42L),
          any(), any(), any(), any()));
    }
  }

  @Test
  void incrementsButDoesNotLockBelowTheThreshold() {
    try (MockedStatic<RateLimitCommand> rl = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> lu = mockStatic(LoadUserCommand.class);
        MockedStatic<CacheManager> cm = mockStatic(CacheManager.class);
        MockedStatic<UserRepository> ur = mockStatic(UserRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class);
        MockedStatic<LoadSitePropertyCommand> sp = mockStatic(LoadSitePropertyCommand.class)) {
      stubForLogin(rl, cm, sp);
      lu.when(() -> LoadUserCommand.loadUser("alice")).thenReturn(lockoutUser(1, null));
      assertThrows(LoginException.class,
          () -> AuthenticateLoginCommand.getAuthenticatedUser("alice", "wrong", IP_ADDRESS));
      ur.verify(() -> UserRepository.updateLockoutState(eq(42L), eq(2), isNull()));
      audit.verify(() -> SaveAuditEventCommand.recordAuthentication(eq("account.lockout"), any(), anyLong(),
          any(), any(), any(), any()), never());
    }
  }

  @Test
  void aLockedAccountIsRejectedBeforeThePasswordIsChecked() {
    try (MockedStatic<RateLimitCommand> rl = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> lu = mockStatic(LoadUserCommand.class);
        MockedStatic<CacheManager> cm = mockStatic(CacheManager.class);
        MockedStatic<UserRepository> ur = mockStatic(UserRepository.class);
        MockedStatic<LoadSitePropertyCommand> sp = mockStatic(LoadSitePropertyCommand.class)) {
      stubForLogin(rl, cm, sp);
      Timestamp locked = new Timestamp(System.currentTimeMillis() + 600_000L); // locked 10 minutes out
      lu.when(() -> LoadUserCommand.loadUser("alice")).thenReturn(lockoutUser(5, locked));
      // Even the correct password is refused while the account is locked.
      assertThrows(LoginException.class,
          () -> AuthenticateLoginCommand.getAuthenticatedUser("alice", "correct", IP_ADDRESS));
      ur.verify(() -> UserRepository.updateLockoutState(anyLong(), anyInt(), any()), never());
      ur.verify(() -> UserRepository.resetLockout(anyLong()), never());
    }
  }

  @Test
  void aSuccessfulLoginClearsThePriorFailedAttempts() throws Exception {
    try (MockedStatic<RateLimitCommand> rl = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> lu = mockStatic(LoadUserCommand.class);
        MockedStatic<CacheManager> cm = mockStatic(CacheManager.class);
        MockedStatic<UserRepository> ur = mockStatic(UserRepository.class);
        MockedStatic<LoadSitePropertyCommand> sp = mockStatic(LoadSitePropertyCommand.class)) {
      stubForLogin(rl, cm, sp);
      User u = lockoutUser(3, null); // 3 prior failures, not locked
      lu.when(() -> LoadUserCommand.loadUser("alice")).thenReturn(u);
      User result = AuthenticateLoginCommand.getAuthenticatedUser("alice", "correct", IP_ADDRESS);
      assertEquals(u, result);
      ur.verify(() -> UserRepository.resetLockout(42L));
    }
  }

  @Test
  void anExpiredLockNoLongerBlocksLogin() throws Exception {
    try (MockedStatic<RateLimitCommand> rl = mockStatic(RateLimitCommand.class);
        MockedStatic<LoadUserCommand> lu = mockStatic(LoadUserCommand.class);
        MockedStatic<CacheManager> cm = mockStatic(CacheManager.class);
        MockedStatic<UserRepository> ur = mockStatic(UserRepository.class);
        MockedStatic<LoadSitePropertyCommand> sp = mockStatic(LoadSitePropertyCommand.class)) {
      stubForLogin(rl, cm, sp);
      Timestamp expired = new Timestamp(System.currentTimeMillis() - 1000L); // lock already expired
      User u = lockoutUser(5, expired);
      lu.when(() -> LoadUserCommand.loadUser("alice")).thenReturn(u);
      User result = AuthenticateLoginCommand.getAuthenticatedUser("alice", "correct", IP_ADDRESS);
      assertEquals(u, result);
      ur.verify(() -> UserRepository.resetLockout(42L));
    }
  }
}
