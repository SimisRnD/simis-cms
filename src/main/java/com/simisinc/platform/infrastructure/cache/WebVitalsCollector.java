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

package com.simisinc.platform.infrastructure.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import com.simisinc.platform.infrastructure.database.SqlValue;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Stores Core Web Vitals metrics in the database.
 *
 * Metrics collected:
 * - LCP (Largest Contentful Paint): time to render largest visual element (target: < 2.5s)
 * - CLS (Cumulative Layout Shift): layout instability score (target: < 0.1)
 * - INP (Interaction to Next Paint): input latency (target: < 200ms)
 * - FCP (First Contentful Paint): time to first visible content (target: < 1.8s)
 * - TTFB (Time to First Byte): backend responsiveness (target: < 600ms)
 *
 * Ratings (from web-vitals library):
 * - "good" — within target threshold
 * - "needs-improvement" — between good and poor
 * - "poor" — exceeds target
 *
 * Storage:
 * - Raw metrics stored in web_vitals table (one row per metric per page load)
 * - Admin dashboard queries for p50/p75/p95 aggregates over time windows
 * - Retention: raw data 30 days, aggregates 90 days (configurable)
 *
 * @author claude
 * @created 7/26/26
 */
public class WebVitalsCollector {

  private static Log LOG = LogFactory.getLog(WebVitalsCollector.class);

  /**
   * Store a collection of metrics for a page load.
   *
   * @param url       The page URL (/news/article)
   * @param metricsNode JSON object with metric_name -> {value, rating}
   * @param sessionId Session ID for correlation (optional)
   */
  public static void collectMetrics(String url, JsonNode metricsNode, String sessionId) {
    if (metricsNode == null || !metricsNode.isObject()) {
      LOG.warn("Invalid metrics node for url: " + url);
      return;
    }

    // Process each metric in the payload
    String[] metricTypes = {"LCP", "CLS", "INP", "FCP", "TTFB"};
    for (String metricType : metricTypes) {
      JsonNode metricNode = metricsNode.path(metricType);
      if (metricNode.isMissingNode()) {
        // Metric not included (e.g., page left before LCP finalized)
        continue;
      }

      Double value = metricNode.path("value").asDouble(-1);
      String rating = metricNode.path("rating").asText(null);

      if (value < 0) {
        LOG.debug("Skipping invalid value for " + metricType + ": " + value);
        continue;
      }

      storeMetric(url, metricType, value, rating, sessionId);
    }
  }

  /**
   * Store a single metric in the database.
   *
   * @param url        The page URL
   * @param metricType LCP, CLS, INP, FCP, or TTFB
   * @param value      Numeric value (ms for timing, unitless for CLS)
   * @param rating     "good", "needs-improvement", or "poor"
   * @param sessionId  Session ID (optional)
   */
  private static void storeMetric(String url, String metricType, Double value,
                                   String rating, String sessionId) {
    try {
      SqlUtils insertValues = new SqlUtils()
          .add("url", url)
          .add("metric_type", metricType)
          .add("value", value)
          .add("rating", rating);

      if (sessionId != null && !sessionId.isEmpty()) {
        insertValues.add("session_id", sessionId);
      }

      if (DB.insertInto("web_vitals", insertValues)) {
        LOG.debug("Stored " + metricType + " metric: " + value + "ms for " + url);
      } else {
        LOG.error("Failed to store " + metricType + " metric for " + url);
      }
    } catch (Exception e) {
      LOG.error("Error storing metric " + metricType + " for " + url + ": " + e.getMessage(), e);
    }
  }

  /**
   * Query p75 values for a given metric across all pages (for dashboard).
   *
   * @param metricType The metric to query (LCP, CLS, etc.)
   * @param hoursBack  How many hours back to query (e.g., 24 for last day)
   * @return Aggregated p75 values by URL
   */
  public static String queryP75ByUrl(String metricType, int hoursBack) {
    // TODO: Implement query using percentile_cont() or similar
    // SELECT url,
    //        PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY value) as p75,
    //        COUNT(*) as sample_count
    // FROM web_vitals
    // WHERE metric_type = metricType
    //   AND created_at > NOW() - INTERVAL 'hoursBack hours'
    // GROUP BY url
    // ORDER BY p75 DESC
    // LIMIT 100

    return null;  // TODO
  }

  /**
   * Aggregate raw metrics into hourly buckets for long-term storage.
   * Run periodically (e.g., daily) to summarize past 24 hours into smaller dataset.
   *
   * (Future: reduce storage footprint by keeping hourly aggregates instead of raw data)
   */
  public static void aggregateMetrics() {
    // TODO: Implement hourly aggregation
    // SELECT date_trunc('hour', created_at) as hour,
    //        url, metric_type,
    //        PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY value) as p50,
    //        PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY value) as p75,
    //        PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY value) as p95,
    //        COUNT(*) as sample_count
    // FROM web_vitals
    // WHERE created_at BETWEEN now() - INTERVAL '2 days' AND now() - INTERVAL '1 day'
    // GROUP BY hour, url, metric_type
    // INSERT INTO web_vitals_hourly (...)
  }
}
