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

package com.simisinc.platform.infrastructure.scheduler.cms;

import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;

/**
 * Computes p50/p75/p95 percentiles for Core Web Vitals from raw metrics.
 * Runs daily to aggregate yesterday's metrics for dashboard display.
 *
 * @author claude
 * @created 8/27/26
 */
public class WebVitalsAggregationJob {

  private static Log LOG = LogFactory.getLog(WebVitalsAggregationJob.class);

  @Job(name = "Compute Web Vitals aggregates (p50/p75/p95)")
  public static void execute() {
    // Distributed lock: prevent multiple instances from running
    String lock = LockManager.lock(SchedulerManager.WEB_VITALS_AGGREGATION_JOB, Duration.ofHours(2));
    if (lock == null) {
      return;
    }

    try {
      computeAggregates();
    } catch (Exception e) {
      LOG.error("Error computing web vitals aggregates", e);
    }
  }

  private static void computeAggregates() {
    LOG.info("Starting web vitals aggregation...");

    try (java.sql.Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(
             "SELECT DISTINCT url, metric_name FROM web_vitals WHERE recorded_at > NOW() - INTERVAL '24 hours'");
         ResultSet rs = pst.executeQuery()) {

      while (rs.next()) {
        String url = rs.getString("url");
        String metricName = rs.getString("metric_name");
        computePercentiles(connection, url, metricName);
      }
    } catch (SQLException e) {
      LOG.error("Error loading vitals for aggregation", e);
    }

    LOG.info("Completed web vitals aggregation");
  }

  private static void computePercentiles(java.sql.Connection connection, String url, String metricName) {
    try {
      String percentileQuery =
          "SELECT " +
          "  PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY metric_value) as p50, " +
          "  PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY metric_value) as p75, " +
          "  PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY metric_value) as p95, " +
          "  COUNT(*) as sample_count " +
          "FROM web_vitals " +
          "WHERE url = ? AND metric_name = ? AND recorded_at > NOW() - INTERVAL '24 hours'";

      try (PreparedStatement pst = connection.prepareStatement(percentileQuery)) {
        pst.setString(1, url);
        pst.setString(2, metricName);

        try (ResultSet rs = pst.executeQuery()) {
          if (rs.next()) {
            long p50 = rs.getLong("p50");
            long p75 = rs.getLong("p75");
            long p95 = rs.getLong("p95");
            long sampleCount = rs.getLong("sample_count");

            if (sampleCount > 0) {
              insertAggregate(connection, url, metricName, p50, p75, p95, sampleCount);
              LOG.debug("Aggregated: " + url + " / " + metricName + " (samples: " + sampleCount + ")");
            }
          }
        }
      }
    } catch (SQLException e) {
      LOG.error("Error computing percentiles for " + url + " / " + metricName, e);
    }
  }

  private static void insertAggregate(java.sql.Connection connection, String url, String metricName,
                                      long p50, long p75, long p95, long sampleCount) throws SQLException {
    String sql = "INSERT INTO web_vitals_aggregates (url, metric_name, p50_value, p75_value, p95_value, sample_count, aggregated_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, NOW()) " +
        "ON CONFLICT (url, metric_name, aggregated_at) DO UPDATE SET " +
        "  p50_value = EXCLUDED.p50_value, " +
        "  p75_value = EXCLUDED.p75_value, " +
        "  p95_value = EXCLUDED.p95_value, " +
        "  sample_count = EXCLUDED.sample_count";

    try (PreparedStatement pst = connection.prepareStatement(sql)) {
      pst.setString(1, url);
      pst.setString(2, metricName);
      pst.setLong(3, p50);
      pst.setLong(4, p75);
      pst.setLong(5, p95);
      pst.setLong(6, sampleCount);
      pst.executeUpdate();
    }
  }
}
