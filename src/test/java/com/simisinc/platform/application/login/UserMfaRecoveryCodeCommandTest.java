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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.UserMfaRecoveryCode;
import com.simisinc.platform.infrastructure.persistence.login.UserMfaRecoveryCodeRepository;

/**
 * Tests recovery-code generation, verification, and lifecycle. The repository (database) layer is mocked; the command's
 * hashing and normalization are exercised for real.
 *
 * @author SimIS Inc.
 * @created 2026-07-17
 */
class UserMfaRecoveryCodeCommandTest {

  private static User user(long id) {
    User user = new User();
    user.setId(id);
    return user;
  }

  @Test
  void generateReturnsTenUniqueFormattedCodesAndStoresThem() {
    try (MockedStatic<UserMfaRecoveryCodeRepository> repo = mockStatic(UserMfaRecoveryCodeRepository.class)) {
      List<String> codes = UserMfaRecoveryCodeCommand.generate(user(1L));

      assertEquals(10, codes.size());
      for (String code : codes) {
        assertEquals(11, code.length(), code); // five chars, a dash, five chars
        assertEquals('-', code.charAt(5), code);
      }
      assertEquals(10, codes.stream().distinct().count(), "codes should be unique");
      // A fresh set replaces any prior codes, and each code is stored
      repo.verify(() -> UserMfaRecoveryCodeRepository.removeAll(1L));
      repo.verify(() -> UserMfaRecoveryCodeRepository.add(any()), times(10));
    }
  }

  @Test
  void consumeAcceptsAMatchingCodeIgnoringCaseAndDashes() {
    UserMfaRecoveryCode stored = new UserMfaRecoveryCode();
    stored.setId(5L);
    String expectedHash = DigestUtils.sha256Hex("abcdefghij"); // the normalized form of the code below
    try (MockedStatic<UserMfaRecoveryCodeRepository> repo = mockStatic(UserMfaRecoveryCodeRepository.class)) {
      repo.when(() -> UserMfaRecoveryCodeRepository.findUnusedByUserIdAndHash(1L, expectedHash)).thenReturn(stored);
      repo.when(() -> UserMfaRecoveryCodeRepository.markUsed(stored)).thenReturn(true);

      // The same code, typed with a dash and in upper case, both normalize to the stored hash
      assertTrue(UserMfaRecoveryCodeCommand.consume(user(1L), "abcde-fghij"));
      assertTrue(UserMfaRecoveryCodeCommand.consume(user(1L), "ABCDEFGHIJ"));
      repo.verify(() -> UserMfaRecoveryCodeRepository.markUsed(stored), times(2));
    }
  }

  @Test
  void consumeReturnsFalseWhenTheCodeWasAlreadyConsumedConcurrently() {
    UserMfaRecoveryCode stored = new UserMfaRecoveryCode();
    stored.setId(5L);
    String expectedHash = DigestUtils.sha256Hex("abcdefghij");
    try (MockedStatic<UserMfaRecoveryCodeRepository> repo = mockStatic(UserMfaRecoveryCodeRepository.class)) {
      repo.when(() -> UserMfaRecoveryCodeRepository.findUnusedByUserIdAndHash(1L, expectedHash)).thenReturn(stored);
      // The lookup found the code unused, but a concurrent request flipped it first:
      // the atomic update affects no row, so this login must NOT succeed.
      repo.when(() -> UserMfaRecoveryCodeRepository.markUsed(stored)).thenReturn(false);

      assertFalse(UserMfaRecoveryCodeCommand.consume(user(1L), "abcde-fghij"));
    }
  }

  @Test
  void consumeRejectsAnUnknownCode() {
    try (MockedStatic<UserMfaRecoveryCodeRepository> repo = mockStatic(UserMfaRecoveryCodeRepository.class)) {
      repo.when(() -> UserMfaRecoveryCodeRepository.findUnusedByUserIdAndHash(anyLong(), anyString())).thenReturn(null);
      assertFalse(UserMfaRecoveryCodeCommand.consume(user(1L), "zzzzz-zzzzz"));
    }
  }

  @Test
  void consumeRejectsBlankOrWrongLengthWithoutQueryingTheDatabase() {
    try (MockedStatic<UserMfaRecoveryCodeRepository> repo = mockStatic(UserMfaRecoveryCodeRepository.class)) {
      assertFalse(UserMfaRecoveryCodeCommand.consume(user(1L), ""));
      assertFalse(UserMfaRecoveryCodeCommand.consume(user(1L), null));
      assertFalse(UserMfaRecoveryCodeCommand.consume(user(1L), "123456")); // a TOTP code, not a recovery code
      repo.verifyNoInteractions();
    }
  }

  @Test
  void clearRemovesAllCodes() {
    try (MockedStatic<UserMfaRecoveryCodeRepository> repo = mockStatic(UserMfaRecoveryCodeRepository.class)) {
      UserMfaRecoveryCodeCommand.clear(user(1L));
      repo.verify(() -> UserMfaRecoveryCodeRepository.removeAll(1L));
    }
  }

  @Test
  void countRemainingDelegatesToRepository() {
    try (MockedStatic<UserMfaRecoveryCodeRepository> repo = mockStatic(UserMfaRecoveryCodeRepository.class)) {
      repo.when(() -> UserMfaRecoveryCodeRepository.countUnusedByUserId(1L)).thenReturn(7L);
      assertEquals(7L, UserMfaRecoveryCodeCommand.countRemaining(user(1L)));
    }
  }
}
