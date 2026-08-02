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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

/**
 * Verifies HMAC-SHA256 signing (issue #418) independently of {@link SignWebhookPayloadCommand}'s
 * own implementation: this test recomputes the expected signature itself with the raw JDK
 * {@code javax.crypto.Mac} API and compares byte-for-byte with
 * {@link MessageDigest#isEqual(byte[], byte[])} -- a constant-time compare, per this codebase's
 * rule that a timing-unsafe {@code String.equals}/{@code Arrays.equals} must never be introduced
 * anywhere a signature is compared, sender-side test included.
 */
class SignWebhookPayloadCommandTest {

  private static final String PAYLOAD = "{\"event\":\"web-page-published\",\"occurredOn\":123}";
  private static final String SECRET = "a-subscription-signing-secret";

  @Test
  void signMatchesAnIndependentlyComputedHmacSha256() throws Exception {
    String actualHex = SignWebhookPayloadCommand.sign(PAYLOAD, SECRET);

    byte[] expected = independentHmacSha256(PAYLOAD, SECRET);
    byte[] actual = Hex.decodeHex(actualHex);

    assertTrue(MessageDigest.isEqual(expected, actual),
        "SignWebhookPayloadCommand.sign must match an independently computed HMAC-SHA256");
  }

  @Test
  void signatureHeaderValueHasTheSha256PrefixAndACorrectSignature() throws Exception {
    String header = SignWebhookPayloadCommand.signatureHeaderValue(PAYLOAD, SECRET);

    assertTrue(header.startsWith("sha256="), "header value must be prefixed sha256=<hex> per issue #418");
    String hex = header.substring("sha256=".length());

    byte[] expected = independentHmacSha256(PAYLOAD, SECRET);
    assertTrue(MessageDigest.isEqual(expected, Hex.decodeHex(hex)));
  }

  @Test
  void differentPayloadsProduceDifferentSignatures() {
    String signatureA = SignWebhookPayloadCommand.sign(PAYLOAD, SECRET);
    String signatureB = SignWebhookPayloadCommand.sign(PAYLOAD + "x", SECRET);
    assertNotEquals(signatureA, signatureB);
  }

  @Test
  void differentSecretsProduceDifferentSignatures() {
    String signatureA = SignWebhookPayloadCommand.sign(PAYLOAD, SECRET);
    String signatureB = SignWebhookPayloadCommand.sign(PAYLOAD, SECRET + "x");
    assertNotEquals(signatureA, signatureB);
  }

  @Test
  void headerNameConstantMatchesIssue418sSpec() {
    assertEquals("X-Simis-Signature", SignWebhookPayloadCommand.HEADER_NAME);
  }

  @Test
  void signReturnsNullRatherThanThrowingWhenTheSecretIsBlank() {
    assertNull(SignWebhookPayloadCommand.sign(PAYLOAD, ""));
    assertNull(SignWebhookPayloadCommand.sign(PAYLOAD, null));
  }

  private static byte[] independentHmacSha256(String payload, String secret) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
  }
}
