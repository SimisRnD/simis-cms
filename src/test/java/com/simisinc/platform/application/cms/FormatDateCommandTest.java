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

  // formatMonthDayYear()/formatTime() now route through getSiteZoneId() (previously they used
  // SimpleDateFormat with no zone at all, i.e. whatever zone the JVM happened to default to) --
  // reaches LoadSitePropertyCommand's Caffeine-backed cache, which needs a real DB connection on a
  // genuine cache miss, so every call in this file must mock LoadSitePropertyCommand explicitly.
  @Test
  void formatMonthDayYear() {
    long time = 1651362006994L;
    Timestamp timestamp = new Timestamp(time);
    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      String formattedMonthDayYear = FormatDateCommand.formatMonthDayYear(timestamp);
      Assertions.assertEquals("April 30th, 2022", formattedMonthDayYear);
    }
  }

  @Test
  void formatMonthDayYearUsesTheGivenZoneNotJvmDefault() {
    // 1651362006994L is 2022-04-30T22:00:06Z -- still April 30th in New York (UTC-4 in April) but
    // already May 1st in a zone far enough ahead of UTC, so this only passes if the configured
    // site.timezone is actually honored.
    Timestamp timestamp = new Timestamp(1651362006994L);
    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("Pacific/Auckland");
      Assertions.assertEquals("May 1st, 2022", FormatDateCommand.formatMonthDayYear(timestamp));
    }
  }

  @Test
  void formatTime() {
    long time = 1651362006994L;
    Timestamp timestamp = new Timestamp(time);
    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      String formattedTime = FormatDateCommand.formatTime(timestamp);
      Assertions.assertTrue(formattedTime.contains(":"));
    }
  }

  @Test
  void formatTimeUsesTheGivenZoneNotJvmDefault() {
    // 1651362006994L is 23:40:06 UTC -- 7:40 PM in New York (UTC-4 in April). The AM/PM marker's
    // case is locale-dependent, so this compares case-insensitively rather than hardcoding "PM"/"pm".
    Timestamp timestamp = new Timestamp(1651362006994L);
    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      String formatted = FormatDateCommand.formatTime(timestamp);
      Assertions.assertTrue(formatted.equalsIgnoreCase("7:40 pm"), formatted);
    }
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

  // --- formatDateTimeInput(): the value that goes back into a date/time form input -------------
  // PageServlet registers a SqlTimestampConverter with pattern "MM-dd-yyyy HH:mm" and the site
  // timezone. A form pre-filled with the raw Timestamp ("2026-10-15 13:00:00.0") does not match
  // that pattern, so BeanUtils converts it to null and the save fails -- these pin the format.

  @Test
  void formatDateTimeInputUsesTheConverterPatternInTheSiteZone() {
    // 2026-10-15T13:00:00Z is 09:00 in New York (UTC-4 in October)
    Timestamp timestamp = Timestamp.from(Instant.parse("2026-10-15T13:00:00Z"));
    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      Assertions.assertEquals("10-15-2026 09:00", FormatDateCommand.formatDateTimeInput(timestamp));
    }
  }

  @Test
  void formatDateTimeInputReturnsEmptyForNullSoTheInputRendersBlank() {
    Assertions.assertEquals("", FormatDateCommand.formatDateTimeInput(null));
  }

  @Test
  void formatDateTimeInputRoundTripsThroughTheConvertersPattern() throws Exception {
    // The regression guard: whatever this emits must parse back, in the same zone, to the same
    // instant (to the minute -- the pattern carries no seconds).
    Timestamp timestamp = Timestamp.from(Instant.parse("2026-10-15T13:00:00Z"));
    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      String rendered = FormatDateCommand.formatDateTimeInput(timestamp);

      java.text.SimpleDateFormat parser = new java.text.SimpleDateFormat("MM-dd-yyyy HH:mm");
      parser.setTimeZone(java.util.TimeZone.getTimeZone(ZoneId.of("America/New_York")));
      Date parsed = parser.parse(rendered);

      Assertions.assertEquals(timestamp.toInstant().getEpochSecond() / 60, parsed.toInstant().getEpochSecond() / 60,
          "the pre-filled form value must convert back to the instant it came from");
    }
  }

  @Test
  void formatDateTimeInputHonorsDaylightSavingBoundaries() {
    // Late November is EST (UTC-5); the same wall-clock hour maps to a different UTC instant than
    // it would in October, so a hard-coded offset would fail this.
    Timestamp timestamp = Timestamp.from(Instant.parse("2026-11-30T14:00:00Z"));
    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      Assertions.assertEquals("11-30-2026 09:00", FormatDateCommand.formatDateTimeInput(timestamp));
    }
  }

  // --- format(Timestamp, pattern): the EL stand-in for <fmt:formatDate> -----------------------
  // calendar-event-details.jsp rendered every date through <fmt:formatDate>, which emitted the
  // raw Timestamp on the deployed build ("2026-11-30 05:00:00.0") and skipped the site timezone
  // with it. These pin the shapes that JSP actually asks for.

  @Test
  void formatAppliesTheSiteTimezoneNotUtc() {
    // 2026-11-30T05:00:00Z is midnight in New York (UTC-5 in November, after DST ends)
    Timestamp timestamp = Timestamp.from(Instant.parse("2026-11-30T05:00:00Z"));
    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      Assertions.assertEquals("November 30, 2026", FormatDateCommand.format(timestamp, "MMMM d, yyyy"));
    }
  }

  @Test
  void formatSupportsEveryPatternTheEventDetailsPageUses() {
    Timestamp timestamp = Timestamp.from(Instant.parse("2026-11-30T05:00:00Z"));
    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      Assertions.assertEquals("November 30", FormatDateCommand.format(timestamp, "MMMM d"));
      Assertions.assertEquals("2026", FormatDateCommand.format(timestamp, "yyyy"));
      Assertions.assertEquals("November 2026", FormatDateCommand.format(timestamp, "MMMM yyyy"));
      Assertions.assertEquals("12:00 AM", FormatDateCommand.format(timestamp, "h:mm a"));
      Assertions.assertEquals("11/30/2026", FormatDateCommand.format(timestamp, "MM/dd/yyyy"));
      Assertions.assertEquals("2026-11-30", FormatDateCommand.format(timestamp, "yyyy-MM-dd"));
    }
  }

  @Test
  void formatEmitsAnIso8601OffsetForTheAddToCalendarLinks() {
    // The add-to-calendar spans feed .ics/Outlook links; a raw Timestamp there produces a
    // malformed entry, so the offset has to be the site's, not the container's.
    Timestamp timestamp = Timestamp.from(Instant.parse("2026-11-30T05:00:00Z"));
    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      Assertions.assertEquals("2026-11-30T00:00:00-05:00",
          FormatDateCommand.format(timestamp, "yyyy-MM-dd'T'HH:mm:00XXX"));
    }
  }

  @Test
  void formatReturnsEmptyRatherThanTheLiteralNullWhenUnset() {
    Assertions.assertEquals("", FormatDateCommand.format(null, "MMMM d, yyyy"));
    Assertions.assertEquals("", FormatDateCommand.format(Timestamp.from(Instant.parse("2026-11-30T05:00:00Z")), null));
    Assertions.assertEquals("", FormatDateCommand.format(Timestamp.from(Instant.parse("2026-11-30T05:00:00Z")), ""));
  }

  @Test
  void formatSwallowsAnInvalidPatternInsteadOfBreakingThePage() {
    Timestamp timestamp = Timestamp.from(Instant.parse("2026-11-30T05:00:00Z"));
    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      // an unterminated quote is the classic SimpleDateFormat parse failure
      Assertions.assertEquals("", FormatDateCommand.format(timestamp, "yyyy 'unterminated"));
    }
  }
}