/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import com.simisinc.platform.domain.model.cms.WebPageHit;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists and retrieves web page hit objects
 *
 * @author matt rajkowski
 * @created 5/21/18 1:54 PM
 */
public class WebPageHitRepository {

  private static Log LOG = LogFactory.getLog(WebPageHitRepository.class);

  private static String TABLE_NAME = "web_page_hits";
  private static String[] PRIMARY_KEY = new String[]{"hit_id"};


  public static WebPageHit save(WebPageHit record) {
    return add(record);
  }

  private static WebPageHit add(WebPageHit record) {
    SqlUtils insertValues = new SqlUtils()
        .add("method", record.getMethod(), 6)
        .add("page_path", record.getPagePath(), 255)
        .add("web_page_id", record.getWebPageId(), -1)
        .add("ip_address", record.getIpAddress())
        .add("session_id", record.getSessionId())
        .add("is_logged_in", record.isLoggedIn());
    if (record.getHitDate() != null) {
      insertValues.add("hit_date", record.getHitDate());
    }
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static boolean remove(WebPageHit record) {
    try {
      try (Connection connection = DB.getConnection();
           PreparedStatement pst = createPreparedStatementForDelete(connection, record)) {
        pst.execute();
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("The delete failed!");
    return false;
  }

  private static PreparedStatement createPreparedStatementForDelete(Connection connection, WebPageHit record) throws SQLException {
    String SQL_QUERY =
        "DELETE FROM web_page_hits " +
            "WHERE hit_id = ?";
    int i = 0;
    PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
    pst.setLong(++i, record.getId());
    return pst;
  }

  public static void createSnapshot(Timestamp startDate, Timestamp endDate) {

    String startDateValue = new SimpleDateFormat("yyyy-MM-dd").format(startDate);

    // Query the data, skip some things
    SqlUtils where = new SqlUtils()
        .add("hit_date >= ?", startDate)
        .add("hit_date < ?", endDate)
        .add("page_path NOT LIKE ?", "/admin%")
        .add("page_path NOT LIKE ?", "/assets%")
        .add("page_path NOT LIKE ?", "/json%")
        .add("page_path NOT LIKE ?", "%/*")
        .add("page_path <> ?", "/login")
        .add("NOT EXISTS (SELECT 1 FROM sessions WHERE session_id = web_page_hits.session_id AND is_bot = TRUE)");
    long webPageHitCount = DB.selectCountFrom(TABLE_NAME, where);

    long uniqueSessionCount = SessionRepository.countDistinctSessions(startDate, endDate);

    // INSERT or UPDATE
    SqlUtils insertValues = new SqlUtils()
        .add("snapshot_date", startDate)
        .add("date_value", startDateValue)
        .add("web_page_hits", webPageHitCount)
        .add("unique_sessions", uniqueSessionCount);

    String onConflict = "ON CONFLICT (date_value) " +
        "DO UPDATE SET " +
        "web_page_hits = EXCLUDED.web_page_hits, " +
        "unique_sessions = EXCLUDED.unique_sessions";

    DB.insertIntoWithConflict("web_page_hit_snapshots", insertValues, onConflict);
  }

  public static void deleteOldWebHits() {
    // The retention window is configurable via the analytics.retentionDays site property. The value is
    // parsed to an int (and bounded) before it is placed in the interval, so it cannot inject SQL.
    int days = resolveRetentionDays(LoadSitePropertyCommand.loadByName("analytics.retentionDays"));
    DB.deleteFrom(TABLE_NAME, new SqlUtils().add("hit_date < NOW() - INTERVAL '" + days + " days'"));
  }

  private static final int DEFAULT_RETENTION_DAYS = 365;

  private static final int DEFAULT_IP_REQUEST_RATE_ALERT_THRESHOLD = 300;

  /** Parses the configured retention window to a bounded positive integer, defaulting to 365 days. */
  static int resolveRetentionDays(String value) {
    if (StringUtils.isBlank(value)) {
      return DEFAULT_RETENTION_DAYS;
    }
    int days;
    try {
      days = Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return DEFAULT_RETENTION_DAYS;
    }
    // Keep at least a day of data, and cap at ten years to avoid an unbounded interval
    if (days < 1) {
      return 1;
    }
    if (days > 3650) {
      return 3650;
    }
    return days;
  }

  public static List<StatisticsData> findDailyWebHits(int daysToLimit) {
    String SQL_QUERY =
        "SELECT date_value, web_page_hits " +
            "FROM web_page_hit_snapshots " +
            "WHERE snapshot_date > NOW() - INTERVAL '" + daysToLimit + " days' " +
            "ORDER BY snapshot_date";
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("date_value"));
        data.setValue(String.valueOf(rs.getLong("web_page_hits")));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  public static List<StatisticsData> findDailySessions(int daysToLimit) {
    String SQL_QUERY =
        "SELECT date_value, unique_sessions " +
            "FROM web_page_hit_snapshots " +
            "WHERE snapshot_date > NOW() - INTERVAL '" + daysToLimit + " days' " +
            "ORDER BY snapshot_date";
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("date_value"));
        data.setValue(String.valueOf(rs.getLong("unique_sessions")));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  public static List<StatisticsData> findMonthlySessions(int monthsLimit) {
    String SQL_QUERY =
        "SELECT DATE_TRUNC('month', month)::VARCHAR(10) AS date_column, SUM(unique_sessions) AS monthly_count " +
            "FROM (SELECT generate_series(NOW() - INTERVAL '" + monthsLimit + " months', NOW(), INTERVAL '1 month')::date) d(month) " +
            "LEFT JOIN web_page_hit_snapshots ON DATE_TRUNC('month', snapshot_date) = DATE_TRUNC('month', month) " +
            "GROUP BY d.month " +
            "ORDER BY d.month";
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("date_column"));
        data.setValue(String.valueOf(rs.getLong("monthly_count")));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  public static List<StatisticsData> findTopWebPages(int daysToLimit, int recordLimit) {
    String SQL_QUERY =
        "SELECT link, count(link) AS link_count " +
            "FROM web_pages " +
            "LEFT JOIN web_page_hits wph ON (wph.web_page_id = web_pages.web_page_id) " +
            "WHERE hit_date > NOW() - INTERVAL '" + daysToLimit + " days' " +
            "AND NOT EXISTS (SELECT 1 FROM sessions WHERE session_id = wph.session_id AND is_bot = TRUE) " +
            "GROUP BY link " +
            "ORDER BY link_count desc " +
            "LIMIT " + recordLimit;
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("link"));
        data.setValue(String.valueOf(rs.getLong("link_count")));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  /**
   * View counts for a specific set of pages over a trailing window, excluding bot sessions (issue
   * #497 -- the /admin/web-pages traffic column). findTopWebPages is the closest existing bulk
   * query but is a ranked, LIMIT-capped "top N" report; this returns every requested page's count
   * (including 0 for pages with no hits in range, by simply being absent from the map -- callers
   * must treat a missing key as zero) in one query, keyed by web_page_id to avoid an N+1 query per
   * table row. Returns an empty map for a null/empty input rather than querying with an empty
   * IN () clause (invalid SQL).
   */
  public static Map<Long, Long> countViewsByWebPageId(List<Long> webPageIds, int daysToLimit) {
    Map<Long, Long> countsByWebPageId = new HashMap<>();
    if (webPageIds == null || webPageIds.isEmpty()) {
      return countsByWebPageId;
    }
    StringBuilder placeholders = new StringBuilder();
    for (int i = 0; i < webPageIds.size(); i++) {
      if (i > 0) {
        placeholders.append(",");
      }
      placeholders.append("?");
    }
    String SQL_QUERY =
        "SELECT web_page_id, COUNT(*) AS hit_count " +
            "FROM web_page_hits " +
            "WHERE web_page_id IN (" + placeholders + ") " +
            "AND hit_date > NOW() - INTERVAL '" + daysToLimit + " days' " +
            "AND NOT EXISTS (SELECT 1 FROM sessions WHERE session_id = web_page_hits.session_id AND is_bot = TRUE) " +
            "GROUP BY web_page_id";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY)) {
      int parameterIndex = 1;
      for (Long webPageId : webPageIds) {
        pst.setLong(parameterIndex++, webPageId);
      }
      try (ResultSet rs = pst.executeQuery()) {
        while (rs.next()) {
          countsByWebPageId.put(rs.getLong("web_page_id"), rs.getLong("hit_count"));
        }
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return countsByWebPageId;
  }

  /**
   * Views of a single page path in a date range, excluding bot sessions (issue #563 -- conversion-rate
   * tracking). There is no existing per-page daily/range count; findTopWebPages/findTopPaths only rank
   * across all pages, and the web_page_hit_snapshots table used by findDailyWebHits has no page-level
   * column, so this queries the raw web_page_hits table directly, mirroring findTopPaths' bot-exclusion.
   */
  public static long countPageViews(String pagePath, Timestamp startDate, Timestamp endDate) {
    SqlUtils where = new SqlUtils()
        .add("page_path = ?", pagePath)
        .add("hit_date >= ?", startDate)
        .add("hit_date < ?", endDate)
        .add("NOT EXISTS (SELECT 1 FROM sessions WHERE session_id = web_page_hits.session_id AND is_bot = TRUE)");
    return DB.selectCountFrom(TABLE_NAME, where);
  }

  /**
   * Average number of page hits per session over the trailing window, for the "pages-per-session"
   * engagement report (issue #568). Mirrors findTopWebPages'/findTopPaths' bot-session exclusion so
   * a crawler binge doesn't skew the reported average. Uses a CASE guard (rather than dividing and
   * catching the error) because Postgres raises "division by zero" on float division same as integer
   * division -- it does not return NaN/Infinity. Returns 0.0 when there are no real sessions in
   * range.
   */
  public static double findAvgPagesPerSession(int daysToLimit) {
    String SQL_QUERY =
        "SELECT CASE WHEN COUNT(DISTINCT session_id) = 0 THEN 0.0 " +
            "ELSE COUNT(*)::float8 / COUNT(DISTINCT session_id) END AS avg_pages_per_session " +
            "FROM web_page_hits " +
            "WHERE hit_date > NOW() - INTERVAL '" + daysToLimit + " days' " +
            "AND NOT EXISTS (SELECT 1 FROM sessions WHERE session_id = web_page_hits.session_id AND is_bot = TRUE)";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        return rs.getDouble("avg_pages_per_session");
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return 0.0;
  }

  /**
   * Average dwell time on each page, derived from the gap between consecutive hits in the same
   * session (issue #568's "avg-time-on-page" engagement report). This doesn't fit DB.java's simple
   * where-clause helpers -- it needs the LEAD() window function -- so it uses a raw
   * PreparedStatement, mirroring createSnapshot's style for the same reason. Within each session,
   * hits are ordered by hit_date and each hit is diffed against the next hit in that same session;
   * a session's last hit has no "next" hit to diff against and is excluded (its delta is NULL) the
   * same way findAvgPagesPerSession excludes bot sessions -- both keep a single artifact of the data
   * from skewing the metric. Bot sessions are excluded per findTopWebPages'/findTopPaths' convention.
   */
  public static List<StatisticsData> findAvgTimeOnPageByPath(int daysToLimit, int recordLimit) {
    String SQL_QUERY =
        "SELECT page_path, AVG(seconds_to_next) AS avg_seconds " +
            "FROM (" +
            "SELECT page_path, " +
            "EXTRACT(EPOCH FROM (LEAD(hit_date) OVER (PARTITION BY session_id ORDER BY hit_date) - hit_date)) AS seconds_to_next " +
            "FROM web_page_hits " +
            "WHERE hit_date > NOW() - INTERVAL '" + daysToLimit + " days' " +
            "AND NOT EXISTS (SELECT 1 FROM sessions WHERE session_id = web_page_hits.session_id AND is_bot = TRUE)" +
            ") hit_deltas " +
            "WHERE seconds_to_next IS NOT NULL " +
            "GROUP BY page_path " +
            "ORDER BY avg_seconds DESC " +
            "LIMIT " + recordLimit;
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("page_path"));
        // Locale.US pins the decimal separator to '.' regardless of JVM default locale -- not
        // present in the original pre-merge version, added back here since a comma-decimal
        // locale would otherwise corrupt this into an unparseable value downstream.
        data.setValue(String.format(java.util.Locale.US, "%.1fs", rs.getDouble("avg_seconds")));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  // A floor so a page with only 1-4 hits (whose "average" dwell time is based on almost no
  // samples) cannot dominate either ranking below as noise -- see findTrafficEngagementRanking.
  private static final int MIN_HITS_FOR_ENGAGEMENT_RANKING = 5;

  /**
   * Pages with disproportionately high traffic but low engagement (issue #568) -- candidates for a
   * content/UX review, since visitors are arriving but not staying. Ranked by hit count descending,
   * then average dwell time ascending, using the exact same LEAD()-window dwell-time derivation as
   * {@link #findAvgTimeOnPageByPath}, including its same trade-off: a session's last hit on a page
   * has no "next" hit to diff against, so it counts toward neither this method's hit_count nor its
   * average -- both are counts of hits that had a following page view in the same session, not raw
   * total traffic.
   */
  public static List<StatisticsData> findHighTrafficLowEngagementPages(int daysToLimit, int recordLimit) {
    return findTrafficEngagementRanking(daysToLimit, recordLimit, "hit_count DESC, avg_seconds ASC");
  }

  /**
   * The inverse of {@link #findHighTrafficLowEngagementPages}: pages with low traffic but high
   * engagement per visit -- worth promoting, since the visitors who do find them stay a long time.
   */
  public static List<StatisticsData> findLowTrafficHighEngagementPages(int daysToLimit, int recordLimit) {
    return findTrafficEngagementRanking(daysToLimit, recordLimit, "hit_count ASC, avg_seconds DESC");
  }

  private static List<StatisticsData> findTrafficEngagementRanking(int daysToLimit, int recordLimit, String orderBy) {
    String SQL_QUERY =
        "SELECT page_path, COUNT(*) AS hit_count, AVG(seconds_to_next) AS avg_seconds " +
            "FROM (" +
            "SELECT page_path, " +
            "EXTRACT(EPOCH FROM (LEAD(hit_date) OVER (PARTITION BY session_id ORDER BY hit_date) - hit_date)) AS seconds_to_next " +
            "FROM web_page_hits " +
            "WHERE hit_date > NOW() - INTERVAL '" + daysToLimit + " days' " +
            "AND NOT EXISTS (SELECT 1 FROM sessions WHERE session_id = web_page_hits.session_id AND is_bot = TRUE)" +
            ") hit_deltas " +
            "WHERE seconds_to_next IS NOT NULL " +
            "GROUP BY page_path " +
            "HAVING COUNT(*) >= " + MIN_HITS_FOR_ENGAGEMENT_RANKING + " " +
            "ORDER BY " + orderBy + " " +
            "LIMIT " + recordLimit;
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("page_path"));
        data.setValue(rs.getLong("hit_count") + " hits, "
            + String.format(java.util.Locale.US, "%.1fs", rs.getDouble("avg_seconds")) + " avg");
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  /**
   * Total page views (real, non-bot) in the last {@code daysToLimit} days, grouped by the
   * web_pages.solution_type tag (issue #570). Pages with no tag set are excluded rather than
   * grouped under a catch-all label -- mirrors findTopWebPages' join/bot-exclusion shape, grouping
   * by solution_type instead of link.
   */
  public static List<StatisticsData> findTrafficBySolutionType(int daysToLimit) {
    String SQL_QUERY =
        "SELECT solution_type, count(wph.hit_id) AS hit_count " +
            "FROM web_pages " +
            "LEFT JOIN web_page_hits wph ON (wph.web_page_id = web_pages.web_page_id) " +
            "WHERE solution_type IS NOT NULL " +
            "AND hit_date > NOW() - INTERVAL '" + daysToLimit + " days' " +
            "AND NOT EXISTS (SELECT 1 FROM sessions WHERE session_id = wph.session_id AND is_bot = TRUE) " +
            "GROUP BY solution_type " +
            "ORDER BY hit_count DESC";
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("solution_type"));
        data.setValue(String.valueOf(rs.getLong("hit_count")));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  /**
   * Engagement depth (real, non-bot) in the last {@code daysToLimit} days, grouped by
   * web_pages.solution_type: average page views per session among sessions that viewed at least
   * one page of that solution type. Reuses the page-view/session data web_page_hits already
   * collects rather than adding new tracking -- no session-duration or bounce column exists in
   * this schema to reuse instead (see issue #570's scoping notes).
   */
  public static List<StatisticsData> findEngagementBySolutionType(int daysToLimit) {
    String SQL_QUERY =
        "SELECT solution_type, count(wph.hit_id) AS hit_count, count(DISTINCT wph.session_id) AS session_count " +
            "FROM web_pages " +
            "LEFT JOIN web_page_hits wph ON (wph.web_page_id = web_pages.web_page_id) " +
            "WHERE solution_type IS NOT NULL " +
            "AND hit_date > NOW() - INTERVAL '" + daysToLimit + " days' " +
            "AND NOT EXISTS (SELECT 1 FROM sessions WHERE session_id = wph.session_id AND is_bot = TRUE) " +
            "GROUP BY solution_type " +
            "ORDER BY solution_type";
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        long hitCount = rs.getLong("hit_count");
        long sessionCount = rs.getLong("session_count");
        double averageViewsPerSession = sessionCount == 0 ? 0 : (double) hitCount / sessionCount;
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("solution_type"));
        data.setValue(String.format("%.2f", averageViewsPerSession));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  /**
   * Highest number of hits recorded from a single non-bot IP address in the last
   * {@code hoursToLimit} hours -- the request-rate-per-IP spike alert tile (issue #569 slice 1:
   * the admin alert-delivery mechanism, demonstrated with one concrete traffic-quality signal
   * rather than speculative infrastructure with nothing real to alert on yet). Mirrors
   * findTopWebPages'/findAvgPagesPerSession's bot-session exclusion so a known crawler's burst
   * doesn't trip the alert. Rows with no ip_address are excluded since they can't be attributed to
   * a single source. hoursToLimit is an int, so placing it in the interval cannot inject SQL.
   * Returns 0 when there is no attributable hit data in the window.
   */
  public static long findMaxHitsFromSingleIp(int hoursToLimit) {
    String SQL_QUERY =
        "SELECT count(*) AS hit_count " +
            "FROM " + TABLE_NAME + " " +
            "WHERE hit_date > NOW() - INTERVAL '" + hoursToLimit + " hours' " +
            "AND ip_address IS NOT NULL " +
            "AND NOT EXISTS (SELECT 1 FROM sessions WHERE session_id = web_page_hits.session_id AND is_bot = TRUE) " +
            "GROUP BY ip_address " +
            "ORDER BY hit_count DESC " +
            "LIMIT 1";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        return rs.getLong("hit_count");
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return 0;
  }

  /** Resolves the configurable IP request-rate alert threshold (security.ipRequestRateAlertThreshold),
   * falling back to the default when unset or unparseable, matching
   * SearchAnalyticsRepository.resolveZeroResultAlertThreshold's precedent. */
  public static int resolveIpRequestRateAlertThreshold(String value) {
    if (StringUtils.isBlank(value)) {
      return DEFAULT_IP_REQUEST_RATE_ALERT_THRESHOLD;
    }
    int threshold;
    try {
      threshold = Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return DEFAULT_IP_REQUEST_RATE_ALERT_THRESHOLD;
    }
    return Math.max(threshold, 0);
  }

  /**
   * Backs the admin "Top Pages" report, which is documented to exclude admin/login/asset paths.
   * /web-content/ is a distinct static-asset path (favicons, logos) from /assets/ -- without its
   * own exclusion, a hit like /web-content/images/favicon.png (fired on every page load) shows up
   * in the traffic ranking as if it were a real page view.
   */
  public static List<StatisticsData> findTopPaths(int value, char intervalType, int recordLimit) {
    String SQL_QUERY =
        "SELECT page_path, count(page_path) AS path_count " +
            "FROM web_page_hits " +
            "WHERE hit_date > NOW() - INTERVAL '" + value + " " +
            (intervalType == 'y' ? "years" :
                (intervalType == 'm' ? "months" :
                    (intervalType == 'w' ? "weeks" :
                        (intervalType == 'h' ? "hours" :
                            "days")))) +
            "' " +
            "AND page_path NOT LIKE '/admin%' " +
            "AND page_path NOT LIKE '/assets/%' " +
            "AND page_path NOT LIKE '/web-content/%' " +
            "AND page_path NOT LIKE '/json/%' " +
            "AND page_path NOT LIKE '%/*' " +
            "AND page_path <> '/content-editor' " +
            "AND page_path <> '/login' " +
            "AND NOT EXISTS (SELECT 1 FROM sessions WHERE session_id = web_page_hits.session_id AND is_bot = TRUE) " +
            "GROUP BY page_path " +
            "ORDER BY path_count desc " +
            "LIMIT " + recordLimit;
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("page_path"));
        data.setValue(String.valueOf(rs.getLong("path_count")));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }
}
