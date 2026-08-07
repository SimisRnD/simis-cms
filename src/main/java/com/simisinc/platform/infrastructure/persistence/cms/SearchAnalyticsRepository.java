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

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.cms.SearchAnalytics;
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists and retrieves search analytics events (issue #424). See SearchAnalytics for why this is
 * a separate table from web_searches rather than a replacement for it.
 *
 * @author SimIS
 * @created 7/29/2026
 */
public class SearchAnalyticsRepository {

  private static Log LOG = LogFactory.getLog(SearchAnalyticsRepository.class);

  private static String TABLE_NAME = "search_analytics";
  private static String[] PRIMARY_KEY = new String[]{"search_analytics_id"};

  private static final int DEFAULT_ZERO_RESULT_ALERT_THRESHOLD = 20;

  public static SearchAnalytics save(SearchAnalytics record) {
    return add(record);
  }

  private static SearchAnalytics add(SearchAnalytics record) {
    SqlUtils insertValues = new SqlUtils()
        .add("query", record.getQuery(), 255)
        .add("search_type", record.getSearchType(), 50)
        .add("result_count", record.getResultCount())
        .add("page_path", record.getPagePath(), 255)
        .add("facet_key", record.getFacetKey(), 100);
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  /** Terms searched over the last {@code daysToLimit} days which never returned a result, most-searched
   * first. daysToLimit and recordLimit are ints, so placing them in the interval/limit cannot inject SQL.
   * Uses DB.selectGroupedFrom (issue #637) -- the first migration of a hand-written GROUP BY report to
   * the generic helper; see the class javadoc there for why the remaining reports in this file and in
   * WebPageHitRepository are left as follow-up. */
  public static List<StatisticsData> findZeroResultTerms(int daysToLimit, int recordLimit) {
    SqlUtils where = new SqlUtils()
        .add("created > NOW() - INTERVAL '" + daysToLimit + " days'")
        .add("result_count = 0")
        .add("query IS NOT NULL AND query <> ''");
    SqlUtils orderBy = new SqlUtils().add("query_count DESC");
    return DB.selectGroupedFrom(TABLE_NAME, "query", "query_count", where, orderBy, recordLimit);
  }

  /** Terms whose search volume grew the most from the prior 7 days to the last 7 days, biggest jump
   * first. A term searched for the first time this week (no prior-week baseline) counts as trending
   * from a baseline of zero. recordLimit is an int, so placing it in the LIMIT cannot inject SQL. */
  public static List<StatisticsData> findTrendingTerms(int recordLimit) {
    String SQL_QUERY =
        "SELECT this_week.query AS query, this_week.query_count AS query_count " +
            "FROM (" +
            "  SELECT query, count(query) AS query_count " +
            "  FROM " + TABLE_NAME + " " +
            "  WHERE created > NOW() - INTERVAL '7 days' AND query IS NOT NULL AND query <> '' " +
            "  GROUP BY query" +
            ") this_week " +
            "LEFT JOIN (" +
            "  SELECT query, count(query) AS query_count " +
            "  FROM " + TABLE_NAME + " " +
            "  WHERE created > NOW() - INTERVAL '14 days' AND created <= NOW() - INTERVAL '7 days' " +
            "  AND query IS NOT NULL AND query <> '' " +
            "  GROUP BY query" +
            ") last_week ON this_week.query = last_week.query " +
            "ORDER BY (this_week.query_count - COALESCE(last_week.query_count, 0)) DESC, this_week.query_count DESC " +
            "LIMIT " + recordLimit;
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("query"));
        data.setValue(String.valueOf(rs.getLong("query_count")));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  /** Count of zero-result searches over the last {@code daysToLimit} days, for the zero-result-search-
   * spike alert tile (issue #566). daysToLimit is an int, so placing it in the interval cannot inject SQL. */
  public static long countZeroResultSearches(int daysToLimit) {
    String SQL_QUERY =
        "SELECT count(*) AS record_count " +
            "FROM " + TABLE_NAME + " " +
            "WHERE created > NOW() - INTERVAL '" + daysToLimit + " days' " +
            "AND result_count = 0";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        return rs.getLong("record_count");
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return 0;
  }

  /** Count of all searches over the last {@code daysToLimit} days, the denominator for the facet-
   * adoption-rate tile (issue #638). daysToLimit is an int, so placing it in the interval cannot
   * inject SQL. */
  public static long countSearches(int daysToLimit) {
    String SQL_QUERY =
        "SELECT count(*) AS record_count " +
            "FROM " + TABLE_NAME + " " +
            "WHERE created > NOW() - INTERVAL '" + daysToLimit + " days'";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        return rs.getLong("record_count");
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return 0;
  }

  /** Count of searches over the last {@code daysToLimit} days that had at least one facet/filter
   * applied (facet_key is set), the numerator for the facet-adoption-rate tile (issue #638). */
  public static long countSearchesWithFacetApplied(int daysToLimit) {
    String SQL_QUERY =
        "SELECT count(*) AS record_count " +
            "FROM " + TABLE_NAME + " " +
            "WHERE created > NOW() - INTERVAL '" + daysToLimit + " days' " +
            "AND facet_key IS NOT NULL";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        return rs.getLong("record_count");
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return 0;
  }

  /** Breakdown of which facet dimension(s) were applied over the last {@code daysToLimit} days,
   * most-used first (issue #638). Searches with no facet applied (facet_key IS NULL) are excluded --
   * this is a breakdown of facet usage, not of all searches. */
  public static List<StatisticsData> findFacetUsageBreakdown(int daysToLimit, int recordLimit) {
    SqlUtils where = new SqlUtils()
        .add("created > NOW() - INTERVAL '" + daysToLimit + " days'")
        .add("facet_key IS NOT NULL");
    SqlUtils orderBy = new SqlUtils().add("facet_key_count DESC");
    return DB.selectGroupedFrom(TABLE_NAME, "facet_key", "facet_key_count", where, orderBy, recordLimit);
  }

  /** Search volume over the last {@code daysToLimit} days, grouped by search_type (pages/content/blog/
   * wiki/items/calendar), busiest surface first (issue #1014). Every search event carries a
   * search_type, so unlike findTopSearchPaths there's no null/blank filter needed here. daysToLimit
   * and recordLimit are ints, so placing them in the interval/limit cannot inject SQL. */
  public static List<StatisticsData> findSearchVolumeByType(int daysToLimit, int recordLimit) {
    SqlUtils where = new SqlUtils()
        .add("created > NOW() - INTERVAL '" + daysToLimit + " days'");
    SqlUtils orderBy = new SqlUtils().add("search_type_count DESC");
    return DB.selectGroupedFrom(TABLE_NAME, "search_type", "search_type_count", where, orderBy, recordLimit);
  }

  /** Zero-result rate over the last {@code daysToLimit} days, per search_type -- which of the six
   * search surfaces (pages/content/blog/wiki/items/calendar) fails visitors most often, not just
   * which terms fail (issue #1014). A per-type rate needs both a numerator and a denominator in the
   * same row, so unlike findSearchVolumeByType this doesn't fit DB.selectGroupedFrom's single
   * COUNT(*) shape -- hand-rolled SQL, mirroring findEngagementBySolutionType's per-group-computed-
   * metric style in WebPageHitRepository. daysToLimit and recordLimit are ints, so placing them in
   * the interval/limit cannot inject SQL. */
  public static List<StatisticsData> findZeroResultRateByType(int daysToLimit, int recordLimit) {
    String SQL_QUERY =
        "SELECT search_type, " +
            "ROUND(100.0 * SUM(CASE WHEN result_count = 0 THEN 1 ELSE 0 END) / COUNT(*), 1) AS zero_result_rate " +
            "FROM " + TABLE_NAME + " " +
            "WHERE created > NOW() - INTERVAL '" + daysToLimit + " days' " +
            "GROUP BY search_type " +
            "ORDER BY zero_result_rate DESC " +
            "LIMIT " + recordLimit;
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("search_type"));
        // Locale.US pins the decimal separator to '.' regardless of JVM default locale, matching
        // findAvgTimeOnPageByPath's precedent for the same reason.
        data.setValue(String.format(java.util.Locale.US, "%.1f", rs.getDouble("zero_result_rate")));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  /** Page paths that generated the most searches (of any outcome) over the last {@code daysToLimit}
   * days (issue #1014) -- which pages visitors reach for and then search from, a candidate list for
   * on-page navigation/content review. page_path is nullable (not every search-results widget sets
   * it), so blank/null paths are excluded the same way findZeroResultTerms excludes blank queries.
   * daysToLimit and recordLimit are ints, so placing them in the interval/limit cannot inject SQL. */
  public static List<StatisticsData> findTopSearchPaths(int daysToLimit, int recordLimit) {
    SqlUtils where = new SqlUtils()
        .add("created > NOW() - INTERVAL '" + daysToLimit + " days'")
        .add("page_path IS NOT NULL AND page_path <> ''");
    SqlUtils orderBy = new SqlUtils().add("page_path_count DESC");
    return DB.selectGroupedFrom(TABLE_NAME, "page_path", "page_path_count", where, orderBy, recordLimit);
  }

  /** Page paths that generated the most zero-result searches specifically over the last
   * {@code daysToLimit} days (issue #1014) -- narrower than findTopSearchPaths: which pages are
   * sending visitors into a search that comes up empty, the sharpest candidate list for a content
   * gap or on-page navigation fix. daysToLimit and recordLimit are ints, so placing them in the
   * interval/limit cannot inject SQL. */
  public static List<StatisticsData> findTopZeroResultPaths(int daysToLimit, int recordLimit) {
    SqlUtils where = new SqlUtils()
        .add("created > NOW() - INTERVAL '" + daysToLimit + " days'")
        .add("result_count = 0")
        .add("page_path IS NOT NULL AND page_path <> ''");
    SqlUtils orderBy = new SqlUtils().add("page_path_count DESC");
    return DB.selectGroupedFrom(TABLE_NAME, "page_path", "page_path_count", where, orderBy, recordLimit);
  }

  // "Near-miss" means a search that technically succeeded (result_count > 0) but with so few results
  // that recall is suspect and the term is worth a look -- 1 to 3 results, a fixed threshold rather
  // than a configurable site property (issue #1014 called this out as a good candidate to stay
  // hardcoded rather than become another admin-facing setting; revisit only if an operator asks for
  // a different cutoff).
  private static final String NEAR_MISS_RESULT_COUNT_RANGE = "result_count BETWEEN 1 AND 3";

  /** Terms searched over the last {@code daysToLimit} days that returned a low-but-nonzero result
   * count (see NEAR_MISS_RESULT_COUNT_RANGE), most-searched first -- these found something, but few
   * enough results that recall may be poor, unlike findZeroResultTerms' hard failures (issue #1014).
   * daysToLimit and recordLimit are ints, so placing them in the interval/limit cannot inject SQL. */
  public static List<StatisticsData> findNearMissTerms(int daysToLimit, int recordLimit) {
    SqlUtils where = new SqlUtils()
        .add("created > NOW() - INTERVAL '" + daysToLimit + " days'")
        .add(NEAR_MISS_RESULT_COUNT_RANGE)
        .add("query IS NOT NULL AND query <> ''");
    SqlUtils orderBy = new SqlUtils().add("query_count DESC");
    return DB.selectGroupedFrom(TABLE_NAME, "query", "query_count", where, orderBy, recordLimit);
  }

  /** Resolves the configurable zero-result-search alert threshold (search.zeroResultAlertThreshold),
   * falling back to the default when unset or unparseable, matching
   * MailingListMemberRepository.resolveQuarantineAlertThresholdPercent's precedent. */
  public static int resolveZeroResultAlertThreshold(String value) {
    if (StringUtils.isBlank(value)) {
      return DEFAULT_ZERO_RESULT_ALERT_THRESHOLD;
    }
    int threshold;
    try {
      threshold = Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return DEFAULT_ZERO_RESULT_ALERT_THRESHOLD;
    }
    return Math.max(threshold, 0);
  }

  /** Prunes events older than the configured retention window (search.retentionDays, shared with
   * web_searches -- decoupled from analytics.retentionDays since the two search-logging tables back
   * the Search Analytics page and can be pruned on their own, shorter schedule). */
  public static void deleteOld() {
    int days = SessionRepository.resolveRetentionDays(LoadSitePropertyCommand.loadByName("search.retentionDays"));
    DB.deleteFrom(TABLE_NAME, new SqlUtils().add("created < NOW() - INTERVAL '" + days + " days'"));
  }
}
