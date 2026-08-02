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

package com.simisinc.platform.application.webhooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GenerateWebhookSecretCommandTest {

  @Test
  void generatesASixtyFourCharacterLowercaseHexString() {
    String secret = GenerateWebhookSecretCommand.generate();
    assertEquals(64, secret.length());
    assertTrue(secret.matches("[0-9a-f]{64}"), "expected lowercase hex, got: " + secret);
  }

  @Test
  void generatesADifferentValueEveryCall() {
    String a = GenerateWebhookSecretCommand.generate();
    String b = GenerateWebhookSecretCommand.generate();
    assertNotEquals(a, b);
  }
}
