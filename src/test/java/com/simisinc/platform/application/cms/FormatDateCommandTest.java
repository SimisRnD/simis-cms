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

package com.simisinc.platform.application.cms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

/**
 * @author matt rajkowski
 * @created 5/3/2022 7:00 PM
 */
class FormatDateCommandTest {

  @Test
  void formatMonthDayYear() {
    long time = 1651362006994L;
    Timestamp timestamp = new Timestamp(time);
    String formattedMonthDayYear = FormatDateCommand.formatMonthDayYear(timestamp);
    Assertions.assertEquals("April 30th, 2022", formattedMonthDayYear);
  }

  @Test
  void formatTime() {
    long time = 1651362006994L;
    Timestamp timestamp = new Timestamp(time);
    String formattedTime = FormatDateCommand.formatTime(timestamp);
    Assertions.assertTrue(formattedTime.contains(":"));
  }

  // 2026-01-15T02:30:00Z is 2026-01-14 21:30 in America/New_York (EST, UTC-5, no DST in
  // January) -- a date near local midnight that lands on the previous UTC day, used to verify
  // formatting follows the given zone rather than the instant's UTC calendar day.
  private static final Date NEAR_LOCAL_MIDNIGHT = Date.from(Instant.parse("2026-01-15T02:30:00Z"));
  private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

  @Test
  void formatIsoDateUsesTheGivenZoneNotUtc() {
    Assertions.assertEquals("2026-01-14", FormatDateCommand.formatIsoDate(NEAR_LOCAL_MIDNIGHT, NEW_YORK));
    Assertions.assertEquals("2026-01-15", FormatDateCommand.formatIsoDate(NEAR_LOCAL_MIDNIGHT, ZoneId.of("UTC")));
  }

  @Test
  void formatIsoTimeUsesTheGivenZone() {
    Assertions.assertEquals("21:30", FormatDateCommand.formatIsoTime(NEAR_LOCAL_MIDNIGHT, NEW_YORK));
    Assertions.assertEquals("02:30", FormatDateCommand.formatIsoTime(NEAR_LOCAL_MIDNIGHT, ZoneId.of("UTC")));
  }

  @Test
  void formatIsoOffsetReflectsTheGivenZoneAtThatInstant() {
    Assertions.assertEquals("-05:00", FormatDateCommand.formatIsoOffset(NEAR_LOCAL_MIDNIGHT, NEW_YORK));
    Assertions.assertEquals("+00:00", FormatDateCommand.formatIsoOffset(NEAR_LOCAL_MIDNIGHT, ZoneId.of("UTC")));
  }

  @Test
  void getSiteZoneIdReadsTheConfiguredSiteTimezoneProperty() {
    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      Assertions.assertEquals(NEW_YORK, FormatDateCommand.getSiteZoneId());
    }
  }

  @Test
  void getSiteZoneIdFallsBackToJvmDefaultWhenPropertyIsUnset() {
    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      // loadByName(name, defaultValue) falls back to the passed-through default when unset
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any()))
          .thenAnswer(invocation -> invocation.getArgument(1));
      Assertions.assertEquals(ZoneId.systemDefault(), FormatDateCommand.getSiteZoneId());
    }
  }
}