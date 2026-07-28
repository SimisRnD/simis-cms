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
 * - WebVitalsAggregationJob rolls these up nightly into web_vitals_aggregates
 * - Retention: raw data 30 days, aggregates 1 year (see WebVitalsCleanupJob)
 *
 * @author claude
 * @created 7/26/26
 */
public class WebVitalsCollector {

  private static Log LOG = LogFactory.getLog(WebVitalsCollector.class);
  private static final String[] PRIMARY_KEY = new String[]{"id"};

  /**
   * Store a collection of metrics for a page load.
   *
   * @param url            The page URL (/news/article)
   * @param metricsNode    JSON object with metric_name -> {value, rating}
   * @param sessionId      Session ID for correlation (optional)
   * @param webPageId      The matching web_pages.web_page_id, if the URL resolved to a known page (optional)
   * @param userAgentHash  SHA-256 hash of the client's User-Agent header (optional)
   * @param viewportWidth  Client viewport width in pixels, if reported (optional)
   * @param connectionType Client connection type, if reported (optional)
   */
  public static void collectMetrics(String url, JsonNode metricsNode, String sessionId,
                                     Long webPageId, String userAgentHash, Integer viewportWidth,
                                     String connectionType) {
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

      storeMetric(url, metricType, value, rating, sessionId, webPageId, userAgentHash, viewportWidth, connectionType);
    }
  }

  /**
   * Store a single metric in the database.
   *
   * @param url            The page URL
   * @param metricType     LCP, CLS, INP, FCP, or TTFB
   * @param value          Numeric value (ms for timing, unitless for CLS)
   * @param rating         "good", "needs-improvement", or "poor"
   * @param sessionId      Session ID (optional)
   * @param webPageId      The matching web_pages.web_page_id (optional)
   * @param userAgentHash  SHA-256 hash of the client's User-Agent header (optional)
   * @param viewportWidth  Client viewport width in pixels (optional)
   * @param connectionType Client connection type (optional)
   */
  private static void storeMetric(String url, String metricType, Double value, String rating, String sessionId,
                                   Long webPageId, String userAgentHash, Integer viewportWidth, String connectionType) {
    try {
      SqlUtils insertValues = new SqlUtils()
          .add("url", url)
          .add("metric_type", metricType)
          .add("value", value)
          .add("rating", rating);

      if (sessionId != null && !sessionId.isEmpty()) {
        insertValues.add("session_id", sessionId);
      }
      if (webPageId != null) {
        insertValues.add("web_page_id", webPageId);
      }
      if (userAgentHash != null) {
        insertValues.add("user_agent_hash", userAgentHash);
      }
      if (viewportWidth != null) {
        insertValues.add("viewport_width", viewportWidth);
      }
      if (connectionType != null && !connectionType.isEmpty()) {
        insertValues.add("connection_type", connectionType);
      }

      long insertId = DB.insertInto("web_vitals", insertValues, PRIMARY_KEY);
      if (insertId > 0) {
        LOG.debug("Stored " + metricType + " metric: " + value + "ms for " + url);
      } else {
        LOG.error("Failed to store " + metricType + " metric for " + url);
      }
    } catch (Exception e) {
      LOG.error("Error storing metric " + metricType + " for " + url + ": " + e.getMessage(), e);
    }
  }
}
