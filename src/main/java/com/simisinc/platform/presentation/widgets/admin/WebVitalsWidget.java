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

package com.simisinc.platform.presentation.widgets.admin;

import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Displays Core Web Vitals performance metrics aggregated from real user sessions.
 * Shows p75 percentiles by URL and metric (LCP, CLS, INP, FCP, TTFB).
 * Color-coded: green (good) / yellow (needs work) / red (poor).
 *
 * @author claude
 * @created 8/27/26
 */
public class WebVitalsWidget extends GenericWidget {

  private static Log LOG = LogFactory.getLog(WebVitalsWidget.class);
  static final long serialVersionUID = -8484048371911908893L;
  static String JSP = "/admin/web-vitals.jsp";

  // Google Core Web Vitals thresholds (p75 targets)
  static final int LCP_GOOD = 2500;      // 2.5s
  static final int LCP_NEEDS_WORK = 4000; // 4.0s
  static final double CLS_GOOD = 0.1;     // unitless layout-shift score
  static final double CLS_NEEDS_WORK = 0.25;
  static final int INP_GOOD = 200;        // 200ms
  static final int INP_NEEDS_WORK = 500;  // 500ms
  static final int FCP_GOOD = 1800;       // 1.8s
  static final int FCP_NEEDS_WORK = 3000; // 3.0s
  static final int TTFB_GOOD = 600;       // 600ms
  static final int TTFB_NEEDS_WORK = 1800; // 1800ms

  public WidgetContext execute(WidgetContext context) {
    try {
      // Load aggregates for the last 7 days
      List<Map<String, Object>> vitalsData = loadWebVitalsAggregates();

      // Group by URL and compute summary stats
      Map<String, VitalsSummary> summaryByUrl = summarizeByUrl(vitalsData);

      // Sort by slowest URLs (by LCP p75)
      List<String> sortedUrls = summaryByUrl.keySet().stream()
          .sorted((a, b) -> {
            int lcpA = summaryByUrl.get(a).lcpP75;
            int lcpB = summaryByUrl.get(b).lcpP75;
            return Integer.compare(lcpB, lcpA); // Descending: slowest first
          })
          .toList();

      context.getRequest().setAttribute("vitalsData", vitalsData);
      context.getRequest().setAttribute("summaryByUrl", summaryByUrl);
      context.getRequest().setAttribute("sortedUrls", sortedUrls);

      // Widget title/icon
      context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
      context.getRequest().setAttribute("title", context.getPreferences().get("title"));

      context.setJsp(JSP);
      return context;
    } catch (Exception e) {
      LOG.error("Error loading web vitals widget", e);
      context.getRequest().setAttribute("errorMessage", "Error loading performance data");
      context.setJsp(JSP);
      return context;
    }
  }

  private List<Map<String, Object>> loadWebVitalsAggregates() {
    List<Map<String, Object>> results = new ArrayList<>();

    String query = "SELECT url, metric_type, p75_value, sample_count, aggregated_at " +
        "FROM web_vitals_aggregates " +
        "WHERE aggregated_at > NOW() - INTERVAL '7 days' " +
        "ORDER BY url, metric_type, aggregated_at DESC";

    try (java.sql.Connection connection = DB.getConnection();
         java.sql.Statement stmt = connection.createStatement();
         ResultSet resultSet = stmt.executeQuery(query)) {

      while (resultSet.next()) {
        Map<String, Object> row = new HashMap<>();
        row.put("url", resultSet.getString("url"));
        row.put("metricName", resultSet.getString("metric_type"));
        java.math.BigDecimal p75Value = resultSet.getBigDecimal("p75_value");
        row.put("p75Value", p75Value != null ? p75Value.doubleValue() : 0.0);
        row.put("sampleCount", resultSet.getLong("sample_count"));
        row.put("aggregatedAt", resultSet.getTimestamp("aggregated_at"));
        results.add(row);
      }
    } catch (SQLException e) {
      LOG.error("Error loading vitals aggregates", e);
    }

    return results;
  }

  private Map<String, VitalsSummary> summarizeByUrl(List<Map<String, Object>> vitalsData) {
    Map<String, VitalsSummary> summary = new LinkedHashMap<>();

    for (Map<String, Object> row : vitalsData) {
      String url = (String) row.get("url");
      String metricType = (String) row.get("metricName");
      double p75Value = (Double) row.get("p75Value");

      VitalsSummary s = summary.computeIfAbsent(url, k -> new VitalsSummary(url));

      if ("LCP".equals(metricType)) {
        s.lcpP75 = (int) Math.round(p75Value);
        s.lcpStatus = getStatus(p75Value, LCP_GOOD, LCP_NEEDS_WORK);
      } else if ("CLS".equals(metricType)) {
        s.clsP75 = p75Value;
        s.clsStatus = getStatus(p75Value, CLS_GOOD, CLS_NEEDS_WORK);
      } else if ("INP".equals(metricType)) {
        s.inpP75 = (int) Math.round(p75Value);
        s.inpStatus = getStatus(p75Value, INP_GOOD, INP_NEEDS_WORK);
      } else if ("FCP".equals(metricType)) {
        s.fcpP75 = (int) Math.round(p75Value);
        s.fcpStatus = getStatus(p75Value, FCP_GOOD, FCP_NEEDS_WORK);
      } else if ("TTFB".equals(metricType)) {
        s.ttfbP75 = (int) Math.round(p75Value);
        s.ttfbStatus = getStatus(p75Value, TTFB_GOOD, TTFB_NEEDS_WORK);
      }
    }

    return summary;
  }

  private String getStatus(double value, double goodThreshold, double needsWorkThreshold) {
    if (value <= goodThreshold) {
      return "good";
    } else if (value <= needsWorkThreshold) {
      return "needsWork";
    } else {
      return "poor";
    }
  }

  public static class VitalsSummary {
    public String url;
    public int lcpP75 = 0;
    public double clsP75 = 0;
    public int inpP75 = 0;
    public int fcpP75 = 0;
    public int ttfbP75 = 0;
    public String lcpStatus = "unknown";
    public String clsStatus = "unknown";
    public String inpStatus = "unknown";
    public String fcpStatus = "unknown";
    public String ttfbStatus = "unknown";

    public VitalsSummary(String url) {
      this.url = url;
    }

    public int getOverallScore() {
      int scoreCount = 0;
      int totalScore = 0;
      if (lcpStatus.equals("good")) totalScore += 100;
      else if (lcpStatus.equals("needsWork")) totalScore += 50;
      else scoreCount--;
      scoreCount++;

      if (clsStatus.equals("good")) totalScore += 100;
      else if (clsStatus.equals("needsWork")) totalScore += 50;
      else scoreCount--;
      scoreCount++;

      if (inpStatus.equals("good")) totalScore += 100;
      else if (inpStatus.equals("needsWork")) totalScore += 50;
      else scoreCount--;
      scoreCount++;

      if (fcpStatus.equals("good")) totalScore += 100;
      else if (fcpStatus.equals("needsWork")) totalScore += 50;
      else scoreCount--;
      scoreCount++;

      if (ttfbStatus.equals("good")) totalScore += 100;
      else if (ttfbStatus.equals("needsWork")) totalScore += 50;
      else scoreCount--;
      scoreCount++;

      return scoreCount > 0 ? (int) (totalScore / scoreCount) : 0;
    }
  }
}
