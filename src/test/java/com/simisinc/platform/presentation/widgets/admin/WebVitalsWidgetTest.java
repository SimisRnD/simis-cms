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

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.cms.WebVitalsAggregate;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.persistence.cms.WebVitalsAggregateRepository;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers the trend chart wiring WebVitalsWidget feeds to web-vitals.jsp (issue #762): the
 * URL/metric/date-range picker's defaulting and whitelist-validation logic in execute(), and the
 * AJAX JSON endpoint in action() that the picker calls on every change.
 *
 * @author claude
 * @created 7/31/26
 */
class WebVitalsWidgetTest extends WidgetBase {

  private static WebVitalsAggregate aggregate(String url, String metricType, String date, double p50, double p75, double p95) {
    WebVitalsAggregate aggregate = new WebVitalsAggregate();
    aggregate.setUrl(url);
    aggregate.setMetricType(metricType);
    aggregate.setP50Value(p50);
    aggregate.setP75Value(p75);
    aggregate.setP95Value(p95);
    aggregate.setSampleCount(42);
    aggregate.setAggregatedAt(Timestamp.valueOf(date + " 00:00:00"));
    return aggregate;
  }

  /**
   * loadWebVitalsAggregates() (the pre-existing 7-day summary query) hits DB.getConnection()
   * directly rather than through a mockable repository; stubbing it to throw SQLException exercises
   * that method's own catch block (an empty summary) without needing a real database, so these tests
   * can focus on the new trend attributes.
   */
  private static MockedStatic<DB> noDatabase() throws SQLException {
    MockedStatic<DB> db = mockStatic(DB.class);
    db.when(DB::getConnection).thenThrow(new SQLException("no database in this unit test"));
    return db;
  }

  /**
   * Builds a row exactly as loadWebVitalsAggregates() shapes it (same keys/types: url,
   * metricName, p75Value as Double, sampleCount as Long, aggregatedAt as Timestamp) so
   * summarizeByUrl() can be exercised directly without mocking raw JDBC.
   */
  private static Map<String, Object> row(String url, String metricName, double p75Value, long sampleCount, String date) {
    Map<String, Object> row = new HashMap<>();
    row.put("url", url);
    row.put("metricName", metricName);
    row.put("p75Value", p75Value);
    row.put("sampleCount", sampleCount);
    row.put("aggregatedAt", Timestamp.valueOf(date + " 00:00:00"));
    return row;
  }

  /**
   * Regression test for the stale-summary bug: loadWebVitalsAggregates() orders
   * "ORDER BY url, metric_type, aggregated_at DESC" -- newest day first within each url/metric
   * group, spanning the last 7 days. summarizeByUrl() used to unconditionally overwrite each
   * metric's fields on every row it processed, so the LAST row in each group (the OLDEST day)
   * silently won instead of the newest. Feeding it rows in that same newest-first query order
   * confirms the fix: the newest day's value/status/sample-count win, not the oldest.
   */
  @Test
  void summarizeByUrlUsesTheNewestDayNotTheOldestWhenMultipleDaysExistPerMetric() {
    List<Map<String, Object>> rows = List.of(
        // CLS sorts before LCP alphabetically, so the real query places CLS's rows first for this
        // url -- newest CLS day first, then progressively older CLS days.
        row("/checkout", "CLS", 0.05, 800, "2026-08-05"), // newest CLS day -- must win
        row("/checkout", "CLS", 0.50, 10, "2026-08-04"), // older -- must be ignored
        // Then LCP's rows, again newest first.
        row("/checkout", "LCP", 2000, 500, "2026-08-05"), // newest LCP day -- must win
        row("/checkout", "LCP", 5000, 10, "2026-08-04"), // older -- must be ignored
        row("/checkout", "LCP", 5000, 10, "2026-08-03")); // oldest -- must be ignored

    Map<String, WebVitalsWidget.VitalsSummary> summary = new WebVitalsWidget().summarizeByUrl(rows);

    WebVitalsWidget.VitalsSummary s = summary.get("/checkout");
    Assertions.assertNotNull(s);

    // LCP: newest day is 2000ms (good); the stale-summary bug would instead surface 5000ms (poor),
    // the last/oldest row processed in the group.
    Assertions.assertEquals(2000, s.getLcpP75(), "newest day's LCP value must win, not the oldest");
    Assertions.assertEquals("good", s.getLcpStatus(), "newest day's status must win, not the oldest (poor)");

    // CLS: newest day is 0.05 (good); the bug would instead surface 0.50 (poor).
    Assertions.assertEquals(0.05, s.getClsP75(), 0.0001, "newest day's CLS value must win, not the oldest");
    Assertions.assertEquals("good", s.getClsStatus(), "newest day's status must win, not the oldest (poor)");

    // Sample count must be surfaced from the newest row actually processed for this url (CLS's
    // newest day, since it sorts first per the real query order), not left at zero and not taken
    // from a later/older row.
    Assertions.assertEquals(800, s.getSampleCount(), "sample count must come from the newest row, not the oldest");
  }

  @Test
  void summarizeByUrlLeavesFieldsAtDefaultsWhenNoRowsExistForThatMetric() {
    List<Map<String, Object>> rows = List.of(row("/about", "LCP", 1000, 50, "2026-08-05"));

    Map<String, WebVitalsWidget.VitalsSummary> summary = new WebVitalsWidget().summarizeByUrl(rows);

    WebVitalsWidget.VitalsSummary s = summary.get("/about");
    Assertions.assertEquals(1000, s.getLcpP75());
    Assertions.assertEquals("good", s.getLcpStatus());
    Assertions.assertEquals(50, s.getSampleCount());
    // CLS/INP/FCP/TTFB never appeared in the rows for this url, so they stay at their defaults.
    Assertions.assertEquals(0, s.getClsP75());
    Assertions.assertEquals("unknown", s.getClsStatus());
    Assertions.assertEquals("unknown", s.getInpStatus());
    Assertions.assertEquals("unknown", s.getFcpStatus());
    Assertions.assertEquals("unknown", s.getTtfbStatus());
  }

  @Test
  void executeDefaultsTrendToTheSlowestSummaryUrlLcpAnd30Days() throws SQLException {
    List<String> trendUrls = List.of("/checkout", "/pricing");
    List<WebVitalsAggregate> trendRows = List.of(aggregate("/checkout", "LCP", "2026-07-01", 1200, 2400, 3600));

    try (MockedStatic<DB> db = noDatabase();
        MockedStatic<WebVitalsAggregateRepository> repo = mockStatic(WebVitalsAggregateRepository.class)) {
      repo.when(() -> WebVitalsAggregateRepository.findDistinctUrls(anyInt())).thenReturn(trendUrls);
      repo.when(() -> WebVitalsAggregateRepository.findAggregates(eq("/checkout"), eq("LCP"), eq(30))).thenReturn(trendRows);

      setRoles(widgetContext, ADMIN);
      new WebVitalsWidget().execute(widgetContext);

      Assertions.assertEquals(WebVitalsWidget.JSP, widgetContext.getJsp());
      Assertions.assertEquals(trendUrls, request.getAttribute("trendUrls"));
      // No summary data (loadWebVitalsAggregates failed), so sortedUrls is empty and the trend
      // picker falls back to the first URL that actually has trend history.
      Assertions.assertEquals("/checkout", request.getAttribute("trendUrl"));
      Assertions.assertEquals("LCP", request.getAttribute("trendMetric"));
      Assertions.assertEquals(30, request.getAttribute("trendDays"));
      Assertions.assertEquals(trendRows, request.getAttribute("trendDataList"));
    }
  }

  @Test
  void executeHonorsExplicitTrendUrlMetricAndDaysParameters() throws SQLException {
    List<String> trendUrls = List.of("/checkout", "/pricing");
    List<WebVitalsAggregate> trendRows = List.of(aggregate("/pricing", "CLS", "2026-06-01", 0.05, 0.08, 0.12));

    try (MockedStatic<DB> db = noDatabase();
        MockedStatic<WebVitalsAggregateRepository> repo = mockStatic(WebVitalsAggregateRepository.class)) {
      repo.when(() -> WebVitalsAggregateRepository.findDistinctUrls(anyInt())).thenReturn(trendUrls);
      repo.when(() -> WebVitalsAggregateRepository.findAggregates(eq("/pricing"), eq("CLS"), eq(90))).thenReturn(trendRows);

      addQueryParameter(widgetContext, "trendUrl", "/pricing");
      addQueryParameter(widgetContext, "trendMetric", "cls");
      addQueryParameter(widgetContext, "trendDays", "90");
      setRoles(widgetContext, ADMIN);
      new WebVitalsWidget().execute(widgetContext);

      Assertions.assertEquals("/pricing", request.getAttribute("trendUrl"));
      Assertions.assertEquals("CLS", request.getAttribute("trendMetric"));
      Assertions.assertEquals(90, request.getAttribute("trendDays"));
      Assertions.assertEquals(trendRows, request.getAttribute("trendDataList"));
    }
  }

  @Test
  void executeFallsBackCleanlyWhenThereIsNoTrendDataAtAll() throws SQLException {
    try (MockedStatic<DB> db = noDatabase();
        MockedStatic<WebVitalsAggregateRepository> repo = mockStatic(WebVitalsAggregateRepository.class)) {
      repo.when(() -> WebVitalsAggregateRepository.findDistinctUrls(anyInt())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      new WebVitalsWidget().execute(widgetContext);

      Assertions.assertEquals(WebVitalsWidget.JSP, widgetContext.getJsp());
      Assertions.assertNull(request.getAttribute("trendUrl"));
      Assertions.assertEquals(List.of(), request.getAttribute("trendUrls"));
      // Must be an empty list the JSP's <jsp:useBean class="java.util.ArrayList"> can still cast --
      // not null, and not an immutable List.of() (see the comment in WebVitalsWidget.execute()).
      Object trendDataList = request.getAttribute("trendDataList");
      Assertions.assertNotNull(trendDataList);
      Assertions.assertTrue(trendDataList instanceof ArrayList, "trendDataList must be a real ArrayList");
      Assertions.assertTrue(((ArrayList<?>) trendDataList).isEmpty());
      // findAggregates must never be called with a null/blank URL
      repo.verify(() -> WebVitalsAggregateRepository.findAggregates(anyString(), anyString(), anyInt()), org.mockito.Mockito.never());
    }
  }

  @Test
  void resolveTrendUrlPrefersAnExplicitlyRequestedKnownUrl() {
    List<String> trendUrls = List.of("/a", "/b");
    Assertions.assertEquals("/b", WebVitalsWidget.resolveTrendUrl("/b", trendUrls, List.of("/a")));
  }

  @Test
  void resolveTrendUrlIgnoresAnUnknownRequestedUrl() {
    List<String> trendUrls = List.of("/a", "/b");
    // "/z" has no trend history, so it falls back rather than querying an empty series
    Assertions.assertEquals("/a", WebVitalsWidget.resolveTrendUrl("/z", trendUrls, List.of()));
  }

  @Test
  void resolveTrendUrlPrefersTheSummarysSlowestUrlWhenNoneWasRequested() {
    List<String> trendUrls = List.of("/a", "/b", "/c");
    Assertions.assertEquals("/b", WebVitalsWidget.resolveTrendUrl(null, trendUrls, List.of("/b", "/a")));
  }

  @Test
  void resolveTrendUrlReturnsNullWhenThereIsNoTrendDataAtAll() {
    Assertions.assertNull(WebVitalsWidget.resolveTrendUrl(null, List.of(), List.of("/a")));
  }

  @Test
  void resolveTrendMetricUppercasesAndWhitelists() {
    Assertions.assertEquals("CLS", WebVitalsWidget.resolveTrendMetric("cls"));
    Assertions.assertEquals("LCP", WebVitalsWidget.resolveTrendMetric(null));
    Assertions.assertEquals("LCP", WebVitalsWidget.resolveTrendMetric("not-a-real-metric"));
    Assertions.assertEquals("LCP", WebVitalsWidget.resolveTrendMetric("<script>"));
  }

  @Test
  void resolveTrendDaysOnlyAllowsSevenThirtyOrNinety() {
    Assertions.assertEquals(7, WebVitalsWidget.resolveTrendDays("7"));
    Assertions.assertEquals(30, WebVitalsWidget.resolveTrendDays("30"));
    Assertions.assertEquals(90, WebVitalsWidget.resolveTrendDays("90"));
    Assertions.assertEquals(30, WebVitalsWidget.resolveTrendDays(null));
    Assertions.assertEquals(30, WebVitalsWidget.resolveTrendDays(""));
    Assertions.assertEquals(30, WebVitalsWidget.resolveTrendDays("15"));
    Assertions.assertEquals(30, WebVitalsWidget.resolveTrendDays("not-a-number"));
    Assertions.assertEquals(30, WebVitalsWidget.resolveTrendDays("-7"));
  }

  @Test
  void actionReturnsTheSeriesAsJsonForTheRequestedUrlMetricAndDays() {
    List<WebVitalsAggregate> trendRows = List.of(
        aggregate("/pricing", "LCP", "2026-07-01", 1200, 2400, 3600),
        aggregate("/pricing", "LCP", "2026-07-02", 1300, 2500, 3700));

    try (MockedStatic<WebVitalsAggregateRepository> repo = mockStatic(WebVitalsAggregateRepository.class)) {
      repo.when(() -> WebVitalsAggregateRepository.findAggregates(eq("/pricing"), eq("LCP"), eq(7))).thenReturn(trendRows);

      addQueryParameter(widgetContext, "trendUrl", "/pricing");
      addQueryParameter(widgetContext, "trendMetric", "LCP");
      addQueryParameter(widgetContext, "trendDays", "7");
      new WebVitalsWidget().action(widgetContext);

      String json = widgetContext.getJson();
      Assertions.assertNotNull(json);
      Assertions.assertTrue(json.contains("\"2026-07-01\""), json);
      Assertions.assertTrue(json.contains("2400"), json);
      Assertions.assertTrue(json.contains("2500"), json);
    }
  }

  @Test
  void actionReturnsAnEmptyArrayWhenNoUrlWasRequested() {
    new WebVitalsWidget().action(widgetContext);
    Assertions.assertEquals("[]", widgetContext.getJson());
  }
}
