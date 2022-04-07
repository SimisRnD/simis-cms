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
 * Description
 *
 * @author matt rajkowski
 * @created 6/25/18 11:55 AM
 */
public class UserPasswordCommand {

  private static Log LOG = LogFactory.getLog(UserPasswordCommand.class);

  public static String hash(String password) {
    Argon2 argon2 = Argon2Factory.create();
    char[] pw = password.toCharArray();
    return argon2.hash(2, 65536, 1, pw);
  }

  public static boolean verify(String password, String hash) {
    Argon2 argon2 = Argon2Factory.create();
    // 1000 = The hash call must take at most 1000 ms
    // 65536 = Memory cost
    // 1 = parallelism
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

}
