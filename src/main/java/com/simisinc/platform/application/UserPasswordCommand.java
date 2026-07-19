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

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Methods to hash and verify a password
 *
 * @author matt rajkowski
 * @created 6/25/18 11:55 AM
 */
public class UserPasswordCommand {

  private static Log LOG = LogFactory.getLog(UserPasswordCommand.class);

  public static String hash(String password) {
    // Argon2id (RFC 9106 / OWASP default) resists both GPU and side-channel attacks; Argon2i alone is weaker
    // against GPU cracking. Cost params: 2 iterations, 65536 KiB (64 MiB) memory, 1 lane -- above OWASP minimums.
    Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
    char[] pw = password.toCharArray();
    try {
      return argon2.hash(2, 65536, 1, pw);
    } finally {
      // Wipe confidential data
      argon2.wipeArray(pw);
    }
  }

  public static boolean verify(String password, String hash) {
    // Select the verifier matching the stored hash's variant. New hashes are $argon2id$, but hashes created
    // before the argon2id upgrade are $argon2i$ -- the native verify requires the instance variant to match the
    // encoded prefix, so legacy passwords would fail to verify unless we pick the right one here.
    Argon2 argon2 = Argon2Factory.create(variantOf(hash));
    char[] pw = password.toCharArray();
    boolean verified = false;
    try {
      // Check hash
      verified = argon2.verify(hash, pw);
    } finally {
      // Wipe confidential data
      argon2.wipeArray(pw);
    }
    return verified;
  }

  private static Argon2Factory.Argon2Types variantOf(String hash) {
    if (hash != null && hash.startsWith("$argon2id$")) {
      return Argon2Factory.Argon2Types.ARGON2id;
    }
    if (hash != null && hash.startsWith("$argon2d$")) {
      return Argon2Factory.Argon2Types.ARGON2d;
    }
    // Legacy default: hashes produced before the upgrade are $argon2i$
    return Argon2Factory.Argon2Types.ARGON2i;
  }

}
