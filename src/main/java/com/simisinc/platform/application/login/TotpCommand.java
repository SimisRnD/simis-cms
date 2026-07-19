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

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Time-based one-time password support (TOTP, RFC 6238) for multi-factor authentication. Generates the shared secret
 * and the otpauth:// enrollment URI an authenticator app consumes, and verifies user-supplied codes. This is the
 * cryptographic primitive only; enrolling a user and prompting for a code at login are handled separately.
 *
 * @author SimIS Inc.
 * @created 2026-07-17
 */
public class TotpCommand {

  private static Log LOG = LogFactory.getLog(TotpCommand.class);

  private static final int SECRET_BYTES = 20;         // 160-bit shared secret, encodes to 32 Base32 chars (no padding)
  private static final int DIGITS = 6;
  private static final int PERIOD_SECONDS = 30;
  private static final int ALLOWED_DRIFT_STEPS = 1;   // accept the adjacent steps to tolerate clock skew
  private static final String HMAC_ALGORITHM = "HmacSHA1";
  private static final int[] DIGITS_DIVISOR = { 1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000 };

  private TotpCommand() {
    // Static utility, not instantiated
  }

  /**
   * Generates a new random Base32-encoded shared secret to store for a user.
   *
   * @return a 32-character Base32 secret
   */
  public static String generateSecret() {
    byte[] bytes = new byte[SECRET_BYTES];
    new SecureRandom().nextBytes(bytes);
    return new Base32().encodeToString(bytes).replace("=", "");
  }

  /**
   * Builds the otpauth:// URI an authenticator app consumes, typically rendered as a QR code during enrollment.
   *
   * @param issuer the site or organization name shown in the authenticator
   * @param account the account identifier shown in the authenticator (e.g. the email address)
   * @param base32Secret the user's Base32 secret from generateSecret
   * @return the otpauth:// URI
   */
  public static String generateUri(String issuer, String account, String base32Secret) {
    String label = urlEncode(issuer) + ":" + urlEncode(account);
    return "otpauth://totp/" + label
        + "?secret=" + base32Secret
        + "&issuer=" + urlEncode(issuer)
        + "&algorithm=SHA1"
        + "&digits=" + DIGITS
        + "&period=" + PERIOD_SECONDS;
  }

  /**
   * Verifies a user-supplied code against the secret for the current time, tolerating one step of clock drift.
   *
   * @param base32Secret the user's Base32 secret
   * @param code the code entered by the user
   * @return true when the code is valid for the current time window
   */
  public static boolean verifyCode(String base32Secret, String code) {
    return verifyCode(base32Secret, code, Instant.now().getEpochSecond());
  }

  /**
   * Verifies a code against the secret at a specific point in time. Package-private so tests can pin the clock.
   */
  static boolean verifyCode(String base32Secret, String code, long epochSeconds) {
    if (StringUtils.isBlank(base32Secret) || StringUtils.isBlank(code)) {
      return false;
    }
    String candidate = code.trim();
    long step = epochSeconds / PERIOD_SECONDS;
    for (long drift = -ALLOWED_DRIFT_STEPS; drift <= ALLOWED_DRIFT_STEPS; drift++) {
      if (constantTimeEquals(candidate, generateCode(base32Secret, step + drift))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Generates the code for a given time step using the RFC 4226 dynamic truncation. Package-private for testing
   * against the RFC 6238 reference vectors.
   */
  static String generateCode(String base32Secret, long step) {
    byte[] key = new Base32().decode(base32Secret);
    byte[] counter = ByteBuffer.allocate(Long.BYTES).putLong(step).array();
    byte[] hash;
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
      hash = mac.doFinal(counter);
    } catch (Exception e) {
      LOG.error("Could not compute TOTP code", e);
      return "";
    }
    int offset = hash[hash.length - 1] & 0x0f;
    int binary = ((hash[offset] & 0x7f) << 24)
        | ((hash[offset + 1] & 0xff) << 16)
        | ((hash[offset + 2] & 0xff) << 8)
        | (hash[offset + 3] & 0xff);
    int otp = binary % DIGITS_DIVISOR[DIGITS];
    return StringUtils.leftPad(String.valueOf(otp), DIGITS, '0');
  }

  private static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null || a.length() != b.length()) {
      return false;
    }
    int result = 0;
    for (int i = 0; i < a.length(); i++) {
      result |= a.charAt(i) ^ b.charAt(i);
    }
    return result == 0;
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
