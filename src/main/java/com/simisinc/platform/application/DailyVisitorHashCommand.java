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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;

import org.apache.commons.lang3.StringUtils;

/**
 * Produces a cookieless, privacy-preserving visitor identifier for analytics -- the Plausible/Fathom model. The id is
 * a SHA-256 hash of a per-day random salt combined with the request fingerprint (IP, user agent, host). Because the
 * salt is regenerated at midnight and never persisted, the identifier is stable within a day (so daily unique visitors
 * can be counted) but cannot be correlated across days and cannot be reversed to an IP address. No cookie and no
 * persistent identifier are stored -- supporting a data-minimized, consent-banner-free analytics posture.
 *
 * @author SimIS Inc.
 * @created 2026-07-19
 */
public class DailyVisitorHashCommand {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int SALT_BYTES = 32;
  private static final String PREFIX = "d:"; // marks a daily-hash visitor token (vs. a persistent UUID token)

  private static final Object LOCK = new Object();
  private static byte[] salt;
  private static LocalDate saltDate;

  private DailyVisitorHashCommand() {
    // Static utility, not instantiated
  }

  /**
   * @return the salt for the current day, regenerated (and the previous day's silently discarded) at midnight
   */
  private static byte[] currentSalt() {
    LocalDate today = LocalDate.now();
    synchronized (LOCK) {
      if (salt == null || !today.equals(saltDate)) {
        salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        saltDate = today;
      }
      return salt;
    }
  }

  /**
   * Computes today's pseudonymous visitor id from the request fingerprint.
   *
   * @param ipAddress the client IP (used only as hash input; never stored via this id)
   * @param userAgent the request user agent
   * @param host      the requested host
   * @return a {@code d:}-prefixed opaque identifier, or null when there is nothing to fingerprint
   */
  public static String dailyHash(String ipAddress, String userAgent, String host) {
    if (StringUtils.isBlank(ipAddress) && StringUtils.isBlank(userAgent)) {
      return null;
    }
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(currentSalt());
      md.update(StringUtils.trimToEmpty(ipAddress).getBytes(StandardCharsets.UTF_8));
      md.update((byte) '|');
      md.update(StringUtils.trimToEmpty(userAgent).getBytes(StandardCharsets.UTF_8));
      md.update((byte) '|');
      md.update(StringUtils.trimToEmpty(host).getBytes(StandardCharsets.UTF_8));
      return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required for the visitor hash", e);
    }
  }

  /**
   * Test hook: discards the cached salt so the next call rotates it (simulating a day boundary).
   */
  static void resetSaltForTest() {
    synchronized (LOCK) {
      salt = null;
      saltDate = null;
    }
  }
}
