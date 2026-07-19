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

package com.simisinc.platform.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies at-rest secret encryption: round-trip under a key, unique ciphertext per call, legacy-plaintext and blank
 * pass-through, and fail-safe behavior when no key or the wrong key is configured. The key is supplied via the
 * {@code cms.secret.key} system property so no environment variable is needed under test.
 *
 * @author SimIS Inc.
 * @created 2026-07-19
 */
class SecretCryptoCommandTest {

  private static final String PROP = "cms.secret.key";

  @AfterEach
  void clearKey() {
    System.clearProperty(PROP);
  }

  private void setKey(byte b0) {
    byte[] key = new byte[32]; // AES-256
    key[0] = b0;
    System.setProperty(PROP, Base64.getEncoder().encodeToString(key));
  }

  @Test
  void roundTripsAValueWhenAKeyIsConfigured() {
    setKey((byte) 7);
    String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"; // an RFC 6238 style seed
    String encrypted = SecretCryptoCommand.encrypt(secret);

    assertTrue(encrypted.startsWith("enc:"), "should be marked as encrypted");
    assertNotEquals(secret, encrypted, "ciphertext must differ from plaintext");
    assertEquals(secret, SecretCryptoCommand.decrypt(encrypted), "must decrypt back to the original");
  }

  @Test
  void producesDifferentCiphertextEachTime() {
    setKey((byte) 7);
    // A random IV per encryption means the same secret encrypts to different bytes, but both decrypt cleanly.
    String a = SecretCryptoCommand.encrypt("same-secret");
    String b = SecretCryptoCommand.encrypt("same-secret");
    assertNotEquals(a, b);
    assertEquals("same-secret", SecretCryptoCommand.decrypt(a));
    assertEquals("same-secret", SecretCryptoCommand.decrypt(b));
  }

  @Test
  void passesThroughLegacyPlaintextAndBlanks() {
    setKey((byte) 7);
    assertEquals("legacy-plaintext", SecretCryptoCommand.decrypt("legacy-plaintext"), "no enc: prefix -> unchanged");
    assertNull(SecretCryptoCommand.encrypt(null));
    assertEquals("", SecretCryptoCommand.encrypt(""));
    assertNull(SecretCryptoCommand.decrypt(null));
  }

  @Test
  void withoutAKeyEncryptionIsANoOpAndDecryptFailsSafe() {
    System.clearProperty(PROP);
    assertFalse(SecretCryptoCommand.isEnabled());
    assertEquals("secret", SecretCryptoCommand.encrypt("secret"), "no key -> stored as-is (backward compatible)");
    // An encrypted value cannot be read without the key: fail safe to null, never expose a broken value.
    assertNull(SecretCryptoCommand.decrypt("enc:AAAAAAAAAAAAAAAA"));
  }

  @Test
  void theWrongKeyFailsSafeToNull() {
    setKey((byte) 7);
    String encrypted = SecretCryptoCommand.encrypt("secret");
    setKey((byte) 9); // rotate to a different key
    assertNull(SecretCryptoCommand.decrypt(encrypted), "wrong key -> null (GCM auth fails), not garbage");
  }
}
