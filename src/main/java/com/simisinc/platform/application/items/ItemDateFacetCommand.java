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

import com.simisinc.platform.application.cms.FormatDateCommand;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixed, mutually-exclusive date-range buckets for the Items search date facet (issue #421).
 * Kept to a small bounded set rather than a free-text date-range picker, since each bucket's
 * facet count is computed via its own DB round trip (see ItemRepository.countByDateRange) and a
 * calendar-widget UI is out of scope for this slice.
 *
 * @author SimIS Inc.
 */
public class ItemDateFacetCommand {

  /** One bucket's key, display label, and [start, end) bounds -- either bound may be null (open-ended). */
  public static class DateFacetBucket {
    private final String key;
    private final String label;
    private final Timestamp start;
    private final Timestamp end;

    public DateFacetBucket(String key, String label, Timestamp start, Timestamp end) {
      this.key = key;
      this.label = label;
      this.start = start;
      this.end = end;
    }

    public String getKey() {
      return key;
    }

    public String getLabel() {
      return label;
    }

    public Timestamp getStart() {
      return start;
    }

    public Timestamp getEnd() {
      return end;
    }
  }

  private ItemDateFacetCommand() {
  }

  /**
   * The 4 fixed buckets, computed relative to "now" in the site's configured timezone -- the
   * same convention CalendarSearchResultsWidget already uses for its own date-range query.
   * Bounds are mutually exclusive so bucket counts partition the full result set.
   */
  public static List<DateFacetBucket> buckets() {
    ZoneId siteZoneId = FormatDateCommand.getSiteZoneId();
    ZonedDateTime now = ZonedDateTime.ofInstant(Instant.now(), siteZoneId);

    Timestamp sevenDaysAgo = Timestamp.valueOf(now.minusDays(7).toLocalDateTime());
    Timestamp thirtyDaysAgo = Timestamp.valueOf(now.minusDays(30).toLocalDateTime());
    Timestamp oneYearAgo = Timestamp.valueOf(now.minusYears(1).toLocalDateTime());

    List<DateFacetBucket> buckets = new ArrayList<>();
    buckets.add(new DateFacetBucket("last7", "Last 7 days", sevenDaysAgo, null));
    buckets.add(new DateFacetBucket("last30", "8-30 days ago", thirtyDaysAgo, sevenDaysAgo));
    buckets.add(new DateFacetBucket("lastYear", "31 days - 1 year ago", oneYearAgo, thirtyDaysAgo));
    buckets.add(new DateFacetBucket("older", "Older than a year", null, oneYearAgo));
    return buckets;
  }

  /** Looks up a bucket by its query-string key, or null if key is null/unrecognized. */
  public static DateFacetBucket findByKey(String key) {
    if (key == null) {
      return null;
    }
    for (DateFacetBucket bucket : buckets()) {
      if (bucket.getKey().equals(key)) {
        return bucket;
      }
    }
    return null;
  }
}
