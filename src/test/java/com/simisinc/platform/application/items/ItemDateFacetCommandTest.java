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
   * ItemDateFacetCommand.buckets() reads "site.timezone" via LoadSitePropertyCommand (the same
   * convention CalendarSearchResultsWidget already uses), which otherwise requires a real DB
   * connection through its Caffeine-backed cache -- mock it to a fixed zone instead.
   */
  private static MockedStatic<LoadSitePropertyCommand> mockSiteTimezone() {
    MockedStatic<LoadSitePropertyCommand> mock = mockStatic(LoadSitePropertyCommand.class);
    mock.when(() -> LoadSitePropertyCommand.loadByName("site.timezone")).thenReturn("America/New_York");
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
}
