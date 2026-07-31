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

package com.simisinc.platform.infrastructure.persistence.cms;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.WebVitalsAggregate;
import com.simisinc.platform.infrastructure.database.DB;

/**
 * Reads the daily p50/p75/p95 rows that WebVitalsAggregationJob writes into
 * web_vitals_aggregates, for plotting a Core Web Vitals trend over a selectable date range
 * (issue #762). Purely a query layer -- no new collection or aggregation logic; the nightly job
 * (WebVitalsAggregationJob) already computes and stores one row per (url, metric_type, day).
 *
 * @author claude
 * @created 7/31/26
 */
public class WebVitalsAggregateRepository {

  private static Log LOG = LogFactory.getLog(WebVitalsAggregateRepository.class);

  // The widest range the admin dashboard's trend chart offers (7/30/90 days); also used to bound
  // findDistinctUrls() so the URL picker covers every range the chart can show.
  public static final int MAX_TREND_DAYS = 90;

  /**
   * Distinct URLs with at least one aggregate row in the last {@code daysToLimit} days, for
   * populating the trend chart's URL picker. Ordered alphabetically for a stable, scannable list.
   */
  public static List<String> findDistinctUrls(int daysToLimit) {
    int days = boundDays(daysToLimit);
    String SQL_QUERY = "SELECT DISTINCT url " +
        "FROM web_vitals_aggregates " +
        "WHERE aggregated_at > NOW() - INTERVAL '" + days + " days' " +
        "ORDER BY url";
    List<String> urls = new ArrayList<>();
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
        ResultSet rs = pst.executeQuery()) {
      while (rs.next()) {
        urls.add(rs.getString("url"));
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return urls;
  }

  /**
   * The daily p50/p75/p95 aggregate rows for one URL and metric over the last {@code daysToLimit}
   * days, oldest first -- the exact series a trend line chart plots. Returns an empty list (not
   * null) for a blank url/metricType so callers can render "no data" without a null check.
   */
  public static List<WebVitalsAggregate> findAggregates(String url, String metricType, int daysToLimit) {
    List<WebVitalsAggregate> records = new ArrayList<>();
    if (StringUtils.isBlank(url) || StringUtils.isBlank(metricType)) {
      return records;
    }
    int days = boundDays(daysToLimit);
    String SQL_QUERY = "SELECT url, metric_type, p50_value, p75_value, p95_value, sample_count, aggregated_at " +
        "FROM web_vitals_aggregates " +
        "WHERE url = ? AND metric_type = ? AND aggregated_at > NOW() - INTERVAL '" + days + " days' " +
        "ORDER BY aggregated_at";
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(SQL_QUERY)) {
      pst.setString(1, url);
      pst.setString(2, metricType);
      try (ResultSet rs = pst.executeQuery()) {
        while (rs.next()) {
          records.add(mapRow(rs));
        }
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  private static WebVitalsAggregate mapRow(ResultSet rs) throws SQLException {
    WebVitalsAggregate record = new WebVitalsAggregate();
    record.setUrl(rs.getString("url"));
    record.setMetricType(rs.getString("metric_type"));
    record.setP50Value(toDouble(rs.getBigDecimal("p50_value")));
    record.setP75Value(toDouble(rs.getBigDecimal("p75_value")));
    record.setP95Value(toDouble(rs.getBigDecimal("p95_value")));
    record.setSampleCount(rs.getLong("sample_count"));
    record.setAggregatedAt(rs.getTimestamp("aggregated_at"));
    return record;
  }

  private static double toDouble(BigDecimal value) {
    return value != null ? value.doubleValue() : 0.0;
  }

  /**
   * Bounds a requested day count to a sane, positive range so a bad/absent value can't build an
   * unbounded or negative SQL INTERVAL. Mirrors WebPageHitRepository.resolveRetentionDays' shape,
   * capped at MAX_TREND_DAYS (90) rather than 10 years since this only ever backs the 7/30/90-day
   * trend chart, not a retention policy.
   */
  static int boundDays(int days) {
    if (days < 1) {
      return 1;
    }
    if (days > MAX_TREND_DAYS) {
      return MAX_TREND_DAYS;
    }
    return days;
  }
}
