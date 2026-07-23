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

package com.simisinc.platform.application.oauth;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;

/**
 * Verifies how an OAuth identity is matched to an existing local account. The email is trusted for
 * matching only when the identity provider asserts it is verified; otherwise an OAuth identity that
 * merely claims someone else's email must not be able to log in as that person (account takeover).
 *
 * @author Liz Houser
 * @created 7/23/2026
 */
class OAuthUserInfoCommandTest {

  private static User userWithId(long id) {
    User user = new User();
    user.setId(id);
    return user;
  }

  @Test
  void aVerifiedEmailMatchesAnExistingAccountByEmail() {
    User existing = userWithId(10L);
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      userRepo.when(() -> UserRepository.findByEmailAddress("known@example.com")).thenReturn(existing);

      User result = OAuthUserInfoCommand.resolveExistingUser("someusername", "known@example.com", true);

      assertSame(existing, result);
      // A verified email match short-circuits; the username lookup is not needed
      userRepo.verify(() -> UserRepository.findByUsername(anyString()), never());
    }
  }

  @Test
  void anUnverifiedEmailIsNeverMatchedByEmail() {
    // The takeover guard: an OAuth identity claiming an unverified victim email must NOT match the
    // victim's account by email; only the IdP's preferred_username is consulted.
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      userRepo.when(() -> UserRepository.findByUsername("attacker")).thenReturn(null);

      User result = OAuthUserInfoCommand.resolveExistingUser("attacker", "victim@example.com", false);

      assertNull(result, "an unverified email must not match an existing account");
      userRepo.verify(() -> UserRepository.findByEmailAddress(anyString()), never());
      userRepo.verify(() -> UserRepository.findByUsername("attacker"));
    }
  }

  @Test
  void anUnverifiedEmailFallsBackToTheUsername() {
    User existing = userWithId(11L);
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      userRepo.when(() -> UserRepository.findByUsername("realuser")).thenReturn(existing);

      User result = OAuthUserInfoCommand.resolveExistingUser("realuser", "unverified@example.com", false);

      assertSame(existing, result);
      userRepo.verify(() -> UserRepository.findByEmailAddress(anyString()), never());
    }
  }

  @Test
  void aVerifiedEmailWithNoEmailMatchFallsBackToTheUsername() {
    User existing = userWithId(12L);
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      userRepo.when(() -> UserRepository.findByEmailAddress("new@example.com")).thenReturn(null);
      userRepo.when(() -> UserRepository.findByUsername("existinguser")).thenReturn(existing);

      User result = OAuthUserInfoCommand.resolveExistingUser("existinguser", "new@example.com", true);

      assertSame(existing, result);
    }
  }

  @Test
  void noExistingAccountReturnsNullSoTheCallerCreatesOne() {
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      userRepo.when(() -> UserRepository.findByEmailAddress(anyString())).thenReturn(null);
      userRepo.when(() -> UserRepository.findByUsername(anyString())).thenReturn(null);

      assertNull(OAuthUserInfoCommand.resolveExistingUser("nobody", "nobody@example.com", true));
    }
  }
}
