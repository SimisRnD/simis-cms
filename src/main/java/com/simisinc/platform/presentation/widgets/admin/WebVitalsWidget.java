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

import com.simisinc.platform.domain.model.cms.WebVitalsAggregate;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.persistence.cms.WebVitalsAggregateRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Displays Core Web Vitals performance metrics aggregated from real user sessions.
 * Shows p75 percentiles by URL and metric (LCP, CLS, INP, FCP, TTFB), plus a selectable
 * URL/metric/date-range trend line of the same daily aggregates (issue #762).
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

  // Trend chart support (issue #762)
  static final List<String> METRIC_TYPES = List.of("LCP", "CLS", "INP", "FCP", "TTFB");
  static final String DEFAULT_TREND_METRIC = "LCP";
  static final int DEFAULT_TREND_DAYS = 30;
  static final Set<Integer> VALID_TREND_DAYS = Set.of(7, 30, 90);

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

      // Trend chart: URL/metric/range picker plus the initial series to paint on first load
      List<String> trendUrls = WebVitalsAggregateRepository.findDistinctUrls(WebVitalsAggregateRepository.MAX_TREND_DAYS);
      String trendUrl = resolveTrendUrl(context.getParameter("trendUrl"), trendUrls, sortedUrls);
      String trendMetric = resolveTrendMetric(context.getParameter("trendMetric"));
      int trendDays = resolveTrendDays(context.getParameter("trendDays"));
      // Always a real ArrayList (never List.of()/Collections.emptyList()) -- the JSP's
      // <jsp:useBean class="java.util.ArrayList"> casts this attribute directly, and an
      // immutable-list implementation would throw ClassCastException on that cast (see
      // WebPageHitRepository/WebVitalsAggregateRepository, which return "new ArrayList<>()" for
      // the same reason).
      List<WebVitalsAggregate> trendDataList = StringUtils.isNotBlank(trendUrl)
          ? WebVitalsAggregateRepository.findAggregates(trendUrl, trendMetric, trendDays)
          : new ArrayList<>();

      context.getRequest().setAttribute("trendUrls", trendUrls);
      context.getRequest().setAttribute("trendUrl", trendUrl);
      context.getRequest().setAttribute("trendMetric", trendMetric);
      context.getRequest().setAttribute("trendDays", trendDays);
      context.getRequest().setAttribute("trendDataList", trendDataList);

      context.setJsp(JSP);
      return context;
    } catch (Exception e) {
      LOG.error("Error loading web vitals widget", e);
      context.getRequest().setAttribute("errorMessage", "Error loading performance data");
      context.setJsp(JSP);
      return context;
    }
  }

  /**
   * AJAX endpoint (GET ?widget=...&action=get&trendUrl=...&trendMetric=...&trendDays=...) behind
   * the trend chart's URL/metric/range picker -- same GET+action=/token= dispatch shape as
   * SiteStatsWidget.action(), which WebContainerCommand routes to this method (not post()) because
   * the request is a GET carrying an "action" parameter, not a POST.
   */
  public WidgetContext action(WidgetContext context) {
    String trendUrl = context.getParameter("trendUrl");
    String trendMetric = resolveTrendMetric(context.getParameter("trendMetric"));
    int trendDays = resolveTrendDays(context.getParameter("trendDays"));

    String json = "[]";
    if (StringUtils.isNotBlank(trendUrl)) {
      List<WebVitalsAggregate> trendDataList = WebVitalsAggregateRepository.findAggregates(trendUrl, trendMetric, trendDays);
      try (Jsonb jsonb = JsonbBuilder.create()) {
        json = jsonb.toJson(toChartPoints(trendDataList));
      } catch (Exception e) {
        LOG.error("Error serializing web vitals trend data", e);
      }
    }
    context.setJson(json);
    return context;
  }

  private static List<TrendPoint> toChartPoints(List<WebVitalsAggregate> trendDataList) {
    List<TrendPoint> points = new ArrayList<>();
    for (WebVitalsAggregate aggregate : trendDataList) {
      points.add(new TrendPoint(aggregate.getDateLabel(), aggregate.getP50Value(), aggregate.getP75Value(), aggregate.getP95Value()));
    }
    return points;
  }

  /** Only an already-known URL is honored, so a bogus/stale value falls back cleanly. */
  static String resolveTrendUrl(String requestedUrl, List<String> trendUrls, List<String> sortedUrls) {
    if (StringUtils.isNotBlank(requestedUrl) && trendUrls.contains(requestedUrl)) {
      return requestedUrl;
    }
    // Default to the same URL the summary table leads with (slowest by LCP p75), if it also has
    // aggregate history; otherwise the first URL with any trend data at all.
    if (!sortedUrls.isEmpty() && trendUrls.contains(sortedUrls.get(0))) {
      return sortedUrls.get(0);
    }
    return trendUrls.isEmpty() ? null : trendUrls.get(0);
  }

  /** Whitelist, not a free-text passthrough -- the value is interpolated into a JS array below. */
  static String resolveTrendMetric(String requestedMetric) {
    if (requestedMetric != null) {
      String upper = requestedMetric.trim().toUpperCase();
      if (METRIC_TYPES.contains(upper)) {
        return upper;
      }
    }
    return DEFAULT_TREND_METRIC;
  }

  /** Only 7/30/90 are offered by the UI; anything else falls back to the 30-day default. */
  static int resolveTrendDays(String requestedDays) {
    if (StringUtils.isNotBlank(requestedDays)) {
      try {
        int days = Integer.parseInt(requestedDays.trim());
        if (VALID_TREND_DAYS.contains(days)) {
          return days;
        }
      } catch (NumberFormatException e) {
        // fall through to the default
      }
    }
    return DEFAULT_TREND_DAYS;
  }

  /** A single day's chart point, serialized to JSON for the trend chart's AJAX refresh. */
  public static class TrendPoint {
    private String date;
    private double p50;
    private double p75;
    private double p95;

    public TrendPoint() {
    }

    public TrendPoint(String date, double p50, double p75, double p95) {
      this.date = date;
      this.p50 = p50;
      this.p75 = p75;
      this.p95 = p95;
    }

    public String getDate() {
      return date;
    }

    public void setDate(String date) {
      this.date = date;
    }

    public double getP50() {
      return p50;
    }

    public void setP50(double p50) {
      this.p50 = p50;
    }

    public double getP75() {
      return p75;
    }

    public void setP75(double p75) {
      this.p75 = p75;
    }

    public double getP95() {
      return p95;
    }

    public void setP95(double p95) {
      this.p95 = p95;
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

    // JSP EL's BeanELResolver only reaches these through JavaBean getters, not the public
    // fields directly -- without them, every ${summary.xxx} expression in web-vitals.jsp throws
    // jakarta.el.PropertyNotFoundException the moment the page renders (only caught by loading
    // the actual admin page in a browser; ant/ci-test never render a JSP).
    public String getUrl() {
      return url;
    }

    public int getLcpP75() {
      return lcpP75;
    }

    public double getClsP75() {
      return clsP75;
    }

    public int getInpP75() {
      return inpP75;
    }

    public int getFcpP75() {
      return fcpP75;
    }

    public int getTtfbP75() {
      return ttfbP75;
    }

    public String getLcpStatus() {
      return lcpStatus;
    }

    public String getClsStatus() {
      return clsStatus;
    }

    public String getInpStatus() {
      return inpStatus;
    }

    public String getFcpStatus() {
      return fcpStatus;
    }

    public String getTtfbStatus() {
      return ttfbStatus;
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
