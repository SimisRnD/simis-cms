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

package com.simisinc.platform.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

/**
 * Tests the password hashing and verification, including backward compatibility with legacy argon2i hashes.
 *
 * @author elizabeth houser
 */
class UserPasswordCommandTest {

  @Test
  void newHashesUseArgon2id() {
    String hash = UserPasswordCommand.hash("correct horse battery staple");
    assertTrue(hash.startsWith("$argon2id$"), "New hashes should use the argon2id variant: " + hash);
  }

  @Test
  void aFreshHashVerifies() {
    String password = "correct horse battery staple";
    String hash = UserPasswordCommand.hash(password);
    assertTrue(UserPasswordCommand.verify(password, hash));
    assertFalse(UserPasswordCommand.verify("wrong password", hash));
  }

  @Test
  void legacyArgon2iHashesStillVerify() {
    // Simulate a password stored before the argon2id upgrade
    String password = "an existing user's password";
    Argon2 legacy = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2i);
    String legacyHash = legacy.hash(2, 65536, 1, password.toCharArray());
    assertTrue(legacyHash.startsWith("$argon2i$"), "Sanity: the legacy hash should be argon2i: " + legacyHash);

    // The upgraded verify() must still accept it, so existing logins are not broken
    assertTrue(UserPasswordCommand.verify(password, legacyHash),
        "A legacy argon2i hash must continue to verify after the argon2id upgrade");
    assertFalse(UserPasswordCommand.verify("wrong password", legacyHash));
  }
}
