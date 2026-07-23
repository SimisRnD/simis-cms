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

package com.simisinc.platform.application.datasets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.sql.Timestamp;
import java.util.Calendar;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;

/**
 * Verifies that dataset-import field mappings for the date fields and assignedTo are
 * actually applied -- previously these were empty branches that silently dropped the
 * value. The guarantees under test: recognizable dates parse, an unrecognizable or
 * invalid date is skipped (never stored wrong), and assignedTo resolves a username to a
 * user id or is skipped rather than assigned to a bogus/wrong user.
 *
 * @author Liz Houser
 * @created 7/23/2026
 */
class SaveDatasetRowCommandTest {

  private static void assertYmd(Timestamp ts, int year, int month0Based, int day) {
    Assertions.assertNotNull(ts, "expected a parsed timestamp, not null");
    Calendar c = Calendar.getInstance();
    c.setTime(ts);
    Assertions.assertEquals(year, c.get(Calendar.YEAR));
    Assertions.assertEquals(month0Based, c.get(Calendar.MONTH));
    Assertions.assertEquals(day, c.get(Calendar.DAY_OF_MONTH));
  }

  @Test
  void parsesIsoDate() {
    assertYmd(SaveDatasetRowCommand.parseTimestamp("2026-01-15"), 2026, Calendar.JANUARY, 15);
  }

  @Test
  void parsesSpaceSeparatedDateTime() {
    Timestamp ts = SaveDatasetRowCommand.parseTimestamp("2026-01-15 14:30:00");
    assertYmd(ts, 2026, Calendar.JANUARY, 15);
    Calendar c = Calendar.getInstance();
    c.setTime(ts);
    Assertions.assertEquals(14, c.get(Calendar.HOUR_OF_DAY));
    Assertions.assertEquals(30, c.get(Calendar.MINUTE));
  }

  @Test
  void parsesUsSlashDate() {
    assertYmd(SaveDatasetRowCommand.parseTimestamp("01/15/2026"), 2026, Calendar.JANUARY, 15);
  }

  @Test
  void parsesIsoUtcInstant() {
    assertYmd(SaveDatasetRowCommand.parseTimestamp("2026-01-15T14:30:00Z"), 2026, Calendar.JANUARY, 15);
  }

  @Test
  void unparseableDateReturnsNullRatherThanWrongData() {
    Assertions.assertNull(SaveDatasetRowCommand.parseTimestamp("not a date"));
  }

  @Test
  void invalidCalendarDateRejectedByStrictParsing() {
    // Lenient parsing would roll "month 13, day 45" over into a real (wrong) date;
    // strict parsing must reject it so no nonsense date is imported.
    Assertions.assertNull(SaveDatasetRowCommand.parseTimestamp("2026-13-45"));
  }

  @Test
  void assignedToResolvesUsernameToUserId() {
    User user = new User();
    user.setId(42L);
    try (MockedStatic<UserRepository> repo = mockStatic(UserRepository.class)) {
      repo.when(() -> UserRepository.findByUsername(eq("jsmith"))).thenReturn(user);

      Assertions.assertEquals(42L, SaveDatasetRowCommand.resolveAssignedToUserId("jsmith"));
    }
  }

  @Test
  void assignedToUnknownUserReturnsMinusOneRatherThanBogusId() {
    try (MockedStatic<UserRepository> repo = mockStatic(UserRepository.class)) {
      repo.when(() -> UserRepository.findByUsername(any())).thenReturn(null);

      Assertions.assertEquals(-1L, SaveDatasetRowCommand.resolveAssignedToUserId("ghost"));
    }
  }
}
