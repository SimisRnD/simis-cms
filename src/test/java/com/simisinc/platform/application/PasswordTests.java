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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests password functions
 *
 * @author matt rajkowski
 * @created 1/11/21 2:33 PM
 */
public class PasswordTests {

  @Test
  void passwordHash() {
    String password = "test";
    String hash1 = UserPasswordCommand.hash(password);
    String hash2 = UserPasswordCommand.hash(password);
    assertNotEquals(hash1, hash2);
    assertTrue(UserPasswordCommand.verify(password, hash1));
    assertTrue(UserPasswordCommand.verify(password, hash2));
    assertFalse(UserPasswordCommand.verify("test1", hash1));
    assertFalse(UserPasswordCommand.verify("test1", hash2));
  }

  @Test
  void passwordHashByArchitecture() {
    // Test jna/argon compatibility with this hash created on macOS Universal/ARM
    assertTrue(UserPasswordCommand.verify("test", "$argon2i$v=19$m=65536,t=2,p=1$ZLNPUFu3s0tD5oeYEKcchw$Hfsl0Vfpr1Ic5iDl0YKLpVYaIPUWn+q/wsL2KXeMPr0"));
    // Test jna/argon compatibility with this hash created on macOS x64
    assertTrue(UserPasswordCommand.verify("test", "$argon2i$v=19$m=65536,t=2,p=1$H+cLG+jJY+i6RkwuQs+iFQ$sf0ta9OjUKY570uYbBaIWfhK32dBS6YHJDPZvV3aM6U"));
  }
}
