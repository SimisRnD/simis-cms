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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Verifies the cookieless visitor hash: stable within a day for the same fingerprint, sensitive to each input,
 * opaque and prefixed, null when there is nothing to fingerprint, and rotated when the daily salt changes.
 *
 * @author SimIS Inc.
 * @created 2026-07-19
 */
class DailyVisitorHashCommandTest {

  @Test
  void sameFingerprintSameDayYieldsTheSameHash() {
    String a = DailyVisitorHashCommand.dailyHash("203.0.113.7", "Mozilla/5.0", "example.mil");
    String b = DailyVisitorHashCommand.dailyHash("203.0.113.7", "Mozilla/5.0", "example.mil");
    assertEquals(a, b, "same fingerprint within a day -> same id (so daily uniques can be counted)");
    assertTrue(a.startsWith("d:"), "should be a daily-hash token");
  }

  @Test
  void eachInputChangesTheHash() {
    String base = DailyVisitorHashCommand.dailyHash("203.0.113.7", "Mozilla/5.0", "example.mil");
    assertNotEquals(base, DailyVisitorHashCommand.dailyHash("203.0.113.8", "Mozilla/5.0", "example.mil"), "IP");
    assertNotEquals(base, DailyVisitorHashCommand.dailyHash("203.0.113.7", "curl/8.0", "example.mil"), "user agent");
    assertNotEquals(base, DailyVisitorHashCommand.dailyHash("203.0.113.7", "Mozilla/5.0", "other.mil"), "host");
  }

  @Test
  void nothingToFingerprintReturnsNull() {
    assertNull(DailyVisitorHashCommand.dailyHash(null, null, "example.mil"));
    assertNull(DailyVisitorHashCommand.dailyHash("", "  ", "example.mil"));
  }

  @Test
  void theHashRotatesWhenTheDailySaltRotates() {
    String today = DailyVisitorHashCommand.dailyHash("203.0.113.7", "Mozilla/5.0", "example.mil");
    // Simulate crossing midnight: a new salt means the same visitor no longer maps to the same id.
    DailyVisitorHashCommand.resetSaltForTest();
    String nextDay = DailyVisitorHashCommand.dailyHash("203.0.113.7", "Mozilla/5.0", "example.mil");
    assertNotEquals(today, nextDay, "a new day's salt breaks cross-day correlation");
  }
}
