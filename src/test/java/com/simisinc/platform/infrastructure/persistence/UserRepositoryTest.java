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

package com.simisinc.platform.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import com.simisinc.platform.infrastructure.persistence.login.UserTokenRepository;

/**
 * Covers the security-relevant cleanup that runs when a user's password changes: alongside
 * revoking user tokens, the cached plaintext credentials must be invalidated, or the old
 * password keeps authenticating via AuthenticateLoginCommand's credentials cache.
 *
 * @author Liz Houser
 * @created 7/23/2026
 */
class UserRepositoryTest {

  @Test
  void updatePasswordInvalidatesTheCachedCredentials() {
    User user = new User();
    user.setId(42L);
    user.setPassword("$argon2id$v=19$m=65536,t=3,p=1$newhash");

    try (MockedStatic<DB> db = mockStatic(DB.class);
        MockedStatic<UserTokenRepository> tokens = mockStatic(UserTokenRepository.class);
        MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class)) {
      db.when(() -> DB.update(anyString(), any(SqlUtils.class), any(SqlUtils.class))).thenReturn(true);

      UserRepository.updatePassword(user);

      // The old cached "username:password" for this user must be dropped so it cannot authenticate
      cacheManager.verify(() -> CacheManager.invalidateKey(CacheManager.USER_CREDENTIALS_CACHE, 42L));
      // ... and the existing token revocation must still happen
      tokens.verify(() -> UserTokenRepository.removeAll(42L));
    }
  }

  @Test
  void updatePasswordDoesNotTouchTheCacheWhenTheWriteFails() {
    User user = new User();
    user.setId(7L);
    user.setPassword("$argon2id$hash");

    try (MockedStatic<DB> db = mockStatic(DB.class);
        MockedStatic<UserTokenRepository> tokens = mockStatic(UserTokenRepository.class);
        MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class)) {
      db.when(() -> DB.update(anyString(), any(SqlUtils.class), any(SqlUtils.class))).thenReturn(false);

      UserRepository.updatePassword(user);

      // No successful write -> no revocation and no cache eviction
      cacheManager.verify(() -> CacheManager.invalidateKey(anyString(), any()), never());
      tokens.verify(() -> UserTokenRepository.removeAll(anyLong()), never());
    }
  }

  @Test
  void suspendAccountPersistsTheReason() {
    User user = new User();
    user.setId(9L);

    ArgumentCaptor<SqlUtils> valuesCaptor = ArgumentCaptor.forClass(SqlUtils.class);
    try (MockedStatic<DB> db = mockStatic(DB.class)) {
      db.when(() -> DB.update(anyString(), valuesCaptor.capture(), any(SqlUtils.class))).thenReturn(true);

      UserRepository.suspendAccount(user, "Reported phishing attempt from this account");

      assertEquals("Reported phishing attempt from this account", fieldValue(valuesCaptor.getValue(), "suspension_reason"));
    }
  }

  @Test
  void restoreAccountClearsTheReason() {
    User user = new User();
    user.setId(9L);

    ArgumentCaptor<SqlUtils> valuesCaptor = ArgumentCaptor.forClass(SqlUtils.class);
    try (MockedStatic<DB> db = mockStatic(DB.class)) {
      db.when(() -> DB.update(anyString(), valuesCaptor.capture(), any(SqlUtils.class))).thenReturn(true);

      UserRepository.restoreAccount(user);

      assertEquals(null, fieldValue(valuesCaptor.getValue(), "suspension_reason"));
    }
  }

  @Test
  void resolvePasswordMaxAgeDaysFallsBackOnBlankOrUnparseableOrNonPositive() {
    assertEquals(90, UserRepository.resolvePasswordMaxAgeDays(null), "default on null");
    assertEquals(90, UserRepository.resolvePasswordMaxAgeDays(""), "default on blank");
    assertEquals(90, UserRepository.resolvePasswordMaxAgeDays("not-a-number"), "default on unparseable");
    assertEquals(90, UserRepository.resolvePasswordMaxAgeDays("0"), "default on non-positive");
    assertEquals(90, UserRepository.resolvePasswordMaxAgeDays("-5"), "default on negative");
    assertEquals(180, UserRepository.resolvePasswordMaxAgeDays("180"), "valid value passes through");
  }

  private static String fieldValue(SqlUtils sqlUtils, String fieldName) {
    for (com.simisinc.platform.infrastructure.database.SqlValue value : sqlUtils.getValues()) {
      if (fieldName.equals(value.getFieldOrClause())) {
        return value.getStringValue();
      }
    }
    throw new AssertionError("No field named " + fieldName + " was set");
  }
}
