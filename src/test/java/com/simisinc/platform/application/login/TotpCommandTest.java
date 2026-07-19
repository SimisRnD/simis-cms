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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.Test;

/**
 * @author SimIS Inc.
 * @created 2026-07-17
 */
class TotpCommandTest {

  // The RFC 6238 reference secret is the ASCII "12345678901234567890" (20 bytes).
  private static final String RFC_SECRET =
      new Base32().encodeToString("12345678901234567890".getBytes(StandardCharsets.US_ASCII)).replace("=", "");

  @Test
  void generatesTheRfc6238ReferenceCodes() {
    // From RFC 6238 Appendix B (SHA1), truncated to the low 6 digits of the published 8-digit values.
    assertEquals("287082", TotpCommand.generateCode(RFC_SECRET, 59L / 30L));
    assertEquals("081804", TotpCommand.generateCode(RFC_SECRET, 1111111109L / 30L));
    assertEquals("050471", TotpCommand.generateCode(RFC_SECRET, 1111111111L / 30L));
    assertEquals("005924", TotpCommand.generateCode(RFC_SECRET, 1234567890L / 30L));
    assertEquals("279037", TotpCommand.generateCode(RFC_SECRET, 2000000000L / 30L));
    assertEquals("353130", TotpCommand.generateCode(RFC_SECRET, 20000000000L / 30L));
  }

  @Test
  void verifiesTheCurrentCode() {
    long now = 1600000000L;
    String code = TotpCommand.generateCode(RFC_SECRET, now / 30L);
    assertTrue(TotpCommand.verifyCode(RFC_SECRET, code, now));
  }

  @Test
  void rejectsAWrongCode() {
    long now = 1600000000L;
    String correct = TotpCommand.generateCode(RFC_SECRET, now / 30L);
    String wrong = correct.equals("000000") ? "111111" : "000000";
    assertFalse(TotpCommand.verifyCode(RFC_SECRET, wrong, now));
  }

  @Test
  void toleratesOneStepOfDriftButNotTwo() {
    long now = 1600000000L;
    String code = TotpCommand.generateCode(RFC_SECRET, now / 30L);
    assertTrue(TotpCommand.verifyCode(RFC_SECRET, code, now - 30), "previous step should pass");
    assertTrue(TotpCommand.verifyCode(RFC_SECRET, code, now + 30), "next step should pass");
    assertFalse(TotpCommand.verifyCode(RFC_SECRET, code, now - 60), "two steps back should fail");
    assertFalse(TotpCommand.verifyCode(RFC_SECRET, code, now + 60), "two steps forward should fail");
  }

  @Test
  void rejectsBlankInput() {
    assertFalse(TotpCommand.verifyCode(RFC_SECRET, null, 1600000000L));
    assertFalse(TotpCommand.verifyCode(RFC_SECRET, "  ", 1600000000L));
    assertFalse(TotpCommand.verifyCode(null, "287082", 1600000000L));
  }

  @Test
  void generatesABase32SecretThatRoundTrips() {
    String secret = TotpCommand.generateSecret();
    assertEquals(32, secret.length());
    assertTrue(secret.matches("[A-Z2-7]+"), "secret should be unpadded Base32");
    long now = 1600000000L;
    assertTrue(TotpCommand.verifyCode(secret, TotpCommand.generateCode(secret, now / 30L), now));
  }

  @Test
  void buildsAnEnrollmentUri() {
    String uri = TotpCommand.generateUri("SimIS CMS", "user@example.com", RFC_SECRET);
    assertTrue(uri.startsWith("otpauth://totp/SimIS%20CMS:user%40example.com?"), uri);
    assertTrue(uri.contains("secret=" + RFC_SECRET));
    assertTrue(uri.contains("issuer=SimIS%20CMS"));
    assertTrue(uri.contains("digits=6"));
    assertTrue(uri.contains("period=30"));
  }
}
