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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;

/**
 * @author SimIS Inc.
 * @created 2026-07-17
 */
class UserMfaCommandTest {

  private static User userWithSecret(String secret) {
    User user = new User();
    user.setId(7L);
    user.setEmail("user@example.com");
    user.setMfaSecret(secret);
    return user;
  }

  @Test
  void startEnrollmentStoresSecretAndReturnsUri() {
    User user = new User();
    user.setId(7L);
    user.setEmail("user@example.com");
    try (MockedStatic<UserRepository> repo = mockStatic(UserRepository.class);
        MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class)) {
      props.when(() -> LoadSitePropertyCommand.loadByName("site.name")).thenReturn("Test Site");

      String uri = UserMfaCommand.startEnrollment(user);

      assertTrue(uri.startsWith("otpauth://totp/Test%20Site:user%40example.com?"), uri);
      assertTrue(uri.contains("secret="));
      repo.verify(() -> UserRepository.saveMfaSecret(eq(user), anyString()));
    }
  }

  @Test
  void confirmEnrollmentAcceptsAValidCodeAndEnables() {
    String secret = TotpCommand.generateSecret();
    User user = userWithSecret(secret);
    String valid = TotpCommand.generateCode(secret, Instant.now().getEpochSecond() / 30L);
    try (MockedStatic<UserRepository> repo = mockStatic(UserRepository.class)) {
      repo.when(() -> UserRepository.enableMfa(user)).thenReturn(user);

      assertTrue(UserMfaCommand.confirmEnrollment(user, valid));
      repo.verify(() -> UserRepository.enableMfa(user));
    }
  }

  @Test
  void confirmEnrollmentRejectsAWrongCode() {
    String secret = TotpCommand.generateSecret();
    User user = userWithSecret(secret);
    String valid = TotpCommand.generateCode(secret, Instant.now().getEpochSecond() / 30L);
    String wrong = valid.equals("000000") ? "111111" : "000000";
    try (MockedStatic<UserRepository> repo = mockStatic(UserRepository.class)) {
      assertFalse(UserMfaCommand.confirmEnrollment(user, wrong));
      repo.verifyNoInteractions();
    }
  }

  @Test
  void confirmEnrollmentRejectsWhenNoSecretIsPending() {
    assertFalse(UserMfaCommand.confirmEnrollment(new User(), "123456"));
  }
}
