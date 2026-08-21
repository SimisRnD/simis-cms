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

package com.simisinc.platform.application.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.items.ItemDateFacetCommand.DateFacetBucket;

/**
 * @author SimIS Inc.
 */
class ItemDateFacetCommandTest {

  /**
   * ItemDateFacetCommand.buckets() reads "site.timezone" via FormatDateCommand.getSiteZoneId()
   * (the same shared helper CalendarSearchResultsWidget/UpcomingCalendarEventsWidget now use too),
   * which otherwise requires a real DB connection through LoadSitePropertyCommand's Caffeine-backed
   * cache -- mock it to a fixed zone instead.
   */
  private static MockedStatic<LoadSitePropertyCommand> mockSiteTimezone() {
    return mockSiteTimezone("America/New_York");
  }

  private static MockedStatic<LoadSitePropertyCommand> mockSiteTimezone(String zoneId) {
    MockedStatic<LoadSitePropertyCommand> mock = mockStatic(LoadSitePropertyCommand.class);
    mock.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn(zoneId);
    return mock;
  }

  @Test
  void bucketsReturnsExactlyFourBuckets() {
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockSiteTimezone()) {
      List<DateFacetBucket> buckets = ItemDateFacetCommand.buckets();
      assertEquals(4, buckets.size());
    }
  }

  @Test
  void bucketsAreMutuallyExclusiveAndAscendingByRecency() {
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockSiteTimezone()) {
      List<DateFacetBucket> buckets = ItemDateFacetCommand.buckets();

      DateFacetBucket last7 = buckets.get(0);
      DateFacetBucket last30 = buckets.get(1);
      DateFacetBucket lastYear = buckets.get(2);
      DateFacetBucket older = buckets.get(3);

      // last7 is open-ended on the recent side (no upper bound needed -- nothing is created in the future)
      assertNull(last7.getEnd());

      // Each bucket's start equals the next-older bucket's end, so together they partition the
      // timeline with no gaps and no overlaps
      assertEquals(last7.getStart(), last30.getEnd());
      assertEquals(last30.getStart(), lastYear.getEnd());
      assertEquals(lastYear.getStart(), older.getEnd());

      // older is open-ended on the old side
      assertNull(older.getStart());

      assertTrue(last30.getStart().before(last7.getStart()));
      assertTrue(lastYear.getStart().before(last30.getStart()));
    }
  }

  @Test
  void bucketsHaveStableUniqueKeys() {
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockSiteTimezone()) {
      List<DateFacetBucket> buckets = ItemDateFacetCommand.buckets();
      assertEquals("last7", buckets.get(0).getKey());
      assertEquals("last30", buckets.get(1).getKey());
      assertEquals("lastYear", buckets.get(2).getKey());
      assertEquals("older", buckets.get(3).getKey());
    }
  }

  @Test
  void findByKeyReturnsTheMatchingBucket() {
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockSiteTimezone()) {
      DateFacetBucket bucket = ItemDateFacetCommand.findByKey("last30");
      assertEquals("last30", bucket.getKey());
      assertEquals("8-30 days ago", bucket.getLabel());
    }
  }

  @Test
  void findByKeyReturnsNullForAnUnrecognizedKey() {
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockSiteTimezone()) {
      assertNull(ItemDateFacetCommand.findByKey("not-a-real-bucket"));
    }
  }

  @Test
  void findByKeyReturnsNullForANullKeyWithoutTouchingSiteProperties() {
    // No mock needed -- a null key must short-circuit before buckets() (and therefore
    // LoadSitePropertyCommand) is ever called
    assertNull(ItemDateFacetCommand.findByKey(null));
  }

  @Test
  void bucketExposesItsConstructorArguments() {
    Timestamp start = Timestamp.valueOf("2026-01-01 00:00:00");
    Timestamp end = Timestamp.valueOf("2026-02-01 00:00:00");
    DateFacetBucket bucket = new DateFacetBucket("key", "Label", start, end);

    assertEquals("key", bucket.getKey());
    assertEquals("Label", bucket.getLabel());
    assertEquals(start, bucket.getStart());
    assertEquals(end, bucket.getEnd());
  }

  @Test
  void bucketEdgesLandOnTheSameInstantWhateverTheSiteTimezoneIs() {
    // "Seven days ago" is a single instant. Which timezone the site happens to display it in cannot
    // move it, so asking for the buckets under two very different zones must produce the same edges.
    //
    // This is the regression guard for issue #1386. The previous
    // Timestamp.valueOf(zoned.toLocalDateTime()) dropped the offset and let Timestamp.valueOf
    // re-read the result in the JVM's own zone, which moved every edge by the difference between
    // the two -- four hours on a UTC container with an America/New_York site. Comparing two site
    // zones against each other, rather than against a literal, keeps this deterministic no matter
    // what timezone the machine running the tests is set to.
    Timestamp newYorkEdge;
    Timestamp tokyoEdge;
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockSiteTimezone("America/New_York")) {
      newYorkEdge = ItemDateFacetCommand.buckets().get(0).getStart();
    }
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockSiteTimezone("Asia/Tokyo")) {
      tokyoEdge = ItemDateFacetCommand.buckets().get(0).getStart();
    }
    // Both are computed from Instant.now(), so allow for the milliseconds between the two calls --
    // the bug this guards against was thirteen hours, not milliseconds.
    long driftMillis = Math.abs(newYorkEdge.getTime() - tokyoEdge.getTime());
    assertTrue(driftMillis < 5_000L,
        "bucket edges differed by " + driftMillis + "ms across site timezones; they must be the same instant");
  }

  @Test
  void bucketEdgesAreMeasuredBackFromNowRatherThanFromAWallClockReading() {
    // The complement of the test above: the edge must actually sit seven days before now, not
    // seven days before some zone-shifted reading of now.
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockSiteTimezone()) {
      long expected = System.currentTimeMillis() - java.time.Duration.ofDays(7).toMillis();
      long actual = ItemDateFacetCommand.buckets().get(0).getStart().getTime();
      long driftMillis = Math.abs(expected - actual);
      assertTrue(driftMillis < 5_000L,
          "the last-7-days edge was " + driftMillis + "ms away from seven days before now");
    }
  }
}
