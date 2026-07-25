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

import com.simisinc.platform.application.UserPasswordCommand;
import com.simisinc.platform.domain.model.User;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Tests for StepUpAuthCommand: password verification, TOTP verification, and null-safety.
 */
class StepUpAuthCommandTest {

  private static User userWithPassword(String plaintext) {
    User user = new User();
    user.setId(1L);
    user.setPassword(UserPasswordCommand.hash(plaintext));
    user.setMfaEnabled(false);
    return user;
  }

  private static User userWithPasswordAndMfa(String plaintext, String mfaSecret) {
    User user = userWithPassword(plaintext);
    user.setMfaEnabled(true);
    user.setMfaSecret(mfaSecret);
    return user;
  }

  private static String generateBase32Secret() {
    byte[] bytes = new byte[20];
    new SecureRandom().nextBytes(bytes);
    return new Base32().encodeAsString(bytes);
  }

  @Test
  void nullUserReturnsFalse() {
    assertFalse(StepUpAuthCommand.verify(null, "password", null));
  }

  @Test
  void correctPasswordSucceeds() {
    User user = userWithPassword("correct-password");
    assertTrue(StepUpAuthCommand.verify(user, "correct-password", null));
  }

  @Test
  void wrongPasswordFails() {
    User user = userWithPassword("correct-password");
    assertFalse(StepUpAuthCommand.verify(user, "wrong-password", null));
  }

  @Test
  void blankPasswordFails() {
    User user = userWithPassword("correct-password");
    assertFalse(StepUpAuthCommand.verify(user, "", null));
    assertFalse(StepUpAuthCommand.verify(user, null, null));
  }

  @Test
  void totpIgnoredWhenMfaNotEnabled() {
    // A user without MFA should not be able to step up via a TOTP code
    String secret = generateBase32Secret();
    User user = userWithPassword("correct-password");
    user.setMfaEnabled(false);
    user.setMfaSecret(secret);
    // Generate a valid TOTP code for this secret
    String code = currentTotpCode(secret);
    // TOTP alone should not succeed when MFA is not enabled
    assertFalse(StepUpAuthCommand.verify(user, "", code));
    assertFalse(StepUpAuthCommand.verify(user, null, code));
  }

  @Test
  void validTotpCodeSucceeds() {
    String secret = generateBase32Secret();
    User user = userWithPasswordAndMfa("some-password", secret);
    String code = currentTotpCode(secret);
    assertTrue(StepUpAuthCommand.verify(user, null, code));
    assertTrue(StepUpAuthCommand.verify(user, "", code));
  }

  @Test
  void invalidTotpCodeFails() {
    String secret = generateBase32Secret();
    User user = userWithPasswordAndMfa("some-password", secret);
    assertFalse(StepUpAuthCommand.verify(user, null, "000000"));
  }

  @Test
  void passwordSucceedsEvenWhenMfaEnabled() {
    String secret = generateBase32Secret();
    User user = userWithPasswordAndMfa("correct-password", secret);
    assertTrue(StepUpAuthCommand.verify(user, "correct-password", null));
  }

  /** Generates the current TOTP code for a given Base32 secret using TotpCommand's own clock. */
  private static String currentTotpCode(String secret) {
    long epoch = Instant.now().getEpochSecond();
    long step = epoch / 30;
    try {
      javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
      byte[] keyBytes = new Base32().decode(secret);
      mac.init(new javax.crypto.spec.SecretKeySpec(keyBytes, "HmacSHA1"));
      byte[] data = java.nio.ByteBuffer.allocate(8).putLong(step).array();
      byte[] hash = mac.doFinal(data);
      int offset = hash[hash.length - 1] & 0x0F;
      int code = ((hash[offset] & 0x7F) << 24)
          | ((hash[offset + 1] & 0xFF) << 16)
          | ((hash[offset + 2] & 0xFF) << 8)
          | (hash[offset + 3] & 0xFF);
      return String.format("%06d", code % 1_000_000);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
