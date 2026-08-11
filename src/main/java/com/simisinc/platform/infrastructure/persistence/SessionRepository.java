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

package com.simisinc.platform.infrastructure.persistence;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.BotUserAgent;
import com.simisinc.platform.domain.model.Session;
import com.simisinc.platform.domain.model.Visitor;
import com.simisinc.platform.domain.model.dashboard.BotIdentityStats;
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.infrastructure.database.*;
import com.simisinc.platform.presentation.controller.UserSession;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists and retrieves session objects
 *
 * @author matt rajkowski
 * @created 7/2/18 11:21 AM
 */
public class SessionRepository {

  private static Log LOG = LogFactory.getLog(SessionRepository.class);

  private static String TABLE_NAME = "sessions";
  private static String[] PRIMARY_KEY = new String[]{"id"};

  public static Session findBySessionId(long sessionId) {
    if (sessionId == -1) {
      return null;
    }
    return (Session) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("session_id = ?", sessionId),
        SessionRepository::buildRecord);
  }

  public static List<Session> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("id"),
        SessionRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<Session>) result.getRecords();
    }
    return null;
  }

  public static List<Session> findDailyUniqueLocations(int daysToLimit) {
    String SQL_QUERY =
        "SELECT DISTINCT continent, country, state, city, latitude, longitude " +
            "FROM sessions " +
            "WHERE country IS NOT NULL " +
            "AND created > NOW() - INTERVAL '" + daysToLimit + " days' " +
            "AND is_anonymous = false " +
            "AND is_bot = false " +
            "ORDER BY continent, country, state, city, latitude, longitude";
    List<Session> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        Session data = new Session();
        data.setContinent(rs.getString("continent"));
        data.setCountry(rs.getString("country"));
        data.setState(rs.getString("state"));
        data.setCity(rs.getString("city"));
        data.setLatitude(rs.getDouble("latitude"));
        data.setLongitude(rs.getDouble("longitude"));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  public static long countSessionsWithPii() {
    return DB.selectCountFrom(TABLE_NAME, new SqlUtils().add("ip_address IS NOT NULL"));
  }

  /**
   * Total session count attributed to the given App's Client ID (the sessions.app_id column, set
   * for sessions established via an API key -- see RestRequestFilter/SaveSessionCommand). Backs the
   * admin Apps list's "Devices" column, which previously rendered a hardcoded 0 for every row.
   */
  public static long countByAppId(long appId) {
    if (appId < 1) {
      return 0;
    }
    return DB.selectCountFrom(TABLE_NAME, new SqlUtils().add("app_id = ?", appId));
  }

  public static long countDistinctSessions(Timestamp startDate, Timestamp endDate) {
    // Query the data, skip some things
    SqlUtils where = new SqlUtils()
        .add("created >= ?", startDate)
        .add("created < ?", endDate)
        .add("is_bot = ?", false);
    return DB.selectCountFrom(TABLE_NAME, where);
  }

  public static long countDistinctBotSessions(Timestamp startDate, Timestamp endDate) {
    SqlUtils where = new SqlUtils()
        .add("created >= ?", startDate)
        .add("created < ?", endDate)
        .add("is_bot = ?", true);
    return DB.selectCountFrom(TABLE_NAME, where);
  }

  /**
   * Counts bot sessions created within the given range. Same computation as
   * {@link #countDistinctBotSessions(Timestamp, Timestamp)} -- kept as its own entry point so the
   * bot-vs-real-traffic dashboard (issue #561) has a name that mirrors {@link #countDistinctSessions}
   * ("real" sessions) without renaming the existing, separately-tested alert-card call site.
   */
  public static long countBotSessions(Timestamp startDate, Timestamp endDate) {
    return countDistinctBotSessions(startDate, endDate);
  }

  /**
   * Builds a day-bucketed, zero-filled series of session counts for the given bot status, spanning
   * {@code daysToLimit} days ago through today (inclusive). Mirrors the generate_series + LEFT JOIN
   * pattern used by {@link UserRepository#findDailyUserRegistrations(int)}. The is_bot filter lives in
   * the JOIN condition rather than a WHERE clause so that days with no matching sessions still
   * zero-fill instead of being dropped by the LEFT JOIN.
   */
  public static List<StatisticsData> findDailySessionsByBotStatus(int daysToLimit, boolean isBot) {
    String SQL_QUERY =
        "SELECT DATE_TRUNC('day', day)::VARCHAR(10) AS date_column, COUNT(id) AS daily_count " +
            "FROM (SELECT generate_series(NOW() - INTERVAL '" + daysToLimit + " days', NOW(), INTERVAL '1 day')::date) d(day) " +
            "LEFT JOIN sessions ON DATE_TRUNC('day', created) = DATE_TRUNC('day', day) AND is_bot = " + isBot + " " +
            "GROUP BY d.day " +
            "ORDER BY d.day";
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("date_column"));
        data.setValue(String.valueOf(rs.getLong("daily_count")));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  /**
   * Breaks down bot sessions created in the last {@code daysToLimit} days by identity, classifying
   * each session's raw user_agent against the known {@link BotUserAgent} signatures (first substring
   * match wins, same semantics as {@link com.simisinc.platform.application.SessionCommand#checkForBot}).
   * A blank user_agent or one that matches no known signature falls into an "Unclassified" bucket.
   */
  public static List<StatisticsData> findBotSessionsByIdentity(int daysToLimit) {
    List<BotUserAgent> botUserAgentList = BotUserAgentRepository.findAll();
    String SQL_QUERY =
        "SELECT user_agent " +
            "FROM sessions " +
            "WHERE is_bot = true " +
            "AND created > NOW() - INTERVAL '" + daysToLimit + " days'";
    Map<String, Long> countByIdentity = new LinkedHashMap<>();
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      while (rs.next()) {
        String userAgent = rs.getString("user_agent");
        String identity = classifyBotUserAgent(userAgent, botUserAgentList);
        countByIdentity.merge(identity, 1L, Long::sum);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
      return null;
    }
    List<StatisticsData> records = new ArrayList<>();
    countByIdentity.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .forEach(entry -> {
          StatisticsData data = new StatisticsData();
          data.setLabel(entry.getKey());
          data.setValue(String.valueOf(entry.getValue()));
          records.add(data);
        });
    return records;
  }

  private static String classifyBotUserAgent(String userAgent, List<BotUserAgent> botUserAgentList) {
    if (StringUtils.isBlank(userAgent)) {
      return "Unclassified (blank User-Agent)";
    }
    if (botUserAgentList != null) {
      for (BotUserAgent botUserAgent : botUserAgentList) {
        if (StringUtils.isNotBlank(botUserAgent.getUserAgent()) && StringUtils.containsIgnoreCase(userAgent, botUserAgent.getUserAgent())) {
          return StringUtils.isNotBlank(botUserAgent.getLabel()) ? botUserAgent.getLabel() : botUserAgent.getUserAgent();
        }
      }
    }
    return "Unclassified";
  }

  private static final String BOT_IDENTITY_DATE_FORMAT = "MMM d, yyyy h:mm a";

  /**
   * Per-identity summary for the Bot Traffic by Identity report: session count (see
   * {@link #findBotSessionsByIdentity}), first/last seen within the window, and the most-crawled
   * page path (from a separate join against {@code web_page_hits} -- a bot session with zero
   * recorded hits still counts toward sessionCount/firstSeen/lastSeen but leaves topPage null).
   */
  public static List<BotIdentityStats> findBotSessionStatsByIdentity(int daysToLimit) {
    List<BotUserAgent> botUserAgentList = BotUserAgentRepository.findAll();

    Map<String, Long> countByIdentity = new LinkedHashMap<>();
    Map<String, Timestamp> firstSeenByIdentity = new HashMap<>();
    Map<String, Timestamp> lastSeenByIdentity = new HashMap<>();
    String sessionsSql =
        "SELECT user_agent, created " +
            "FROM sessions " +
            "WHERE is_bot = true " +
            "AND created > NOW() - INTERVAL '" + daysToLimit + " days'";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(sessionsSql);
         ResultSet rs = pst.executeQuery()) {
      while (rs.next()) {
        String userAgent = rs.getString("user_agent");
        Timestamp created = rs.getTimestamp("created");
        String identity = classifyBotUserAgent(userAgent, botUserAgentList);
        countByIdentity.merge(identity, 1L, Long::sum);
        firstSeenByIdentity.merge(identity, created, (a, b) -> a.before(b) ? a : b);
        lastSeenByIdentity.merge(identity, created, (a, b) -> a.after(b) ? a : b);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
      return null;
    }

    Map<String, Map<String, Long>> pageHitsByIdentity = new HashMap<>();
    String pagesSql =
        "SELECT s.user_agent, h.page_path " +
            "FROM sessions s " +
            "JOIN web_page_hits h ON h.session_id = s.session_id " +
            "WHERE s.is_bot = true " +
            "AND s.created > NOW() - INTERVAL '" + daysToLimit + " days'";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(pagesSql);
         ResultSet rs = pst.executeQuery()) {
      while (rs.next()) {
        String pagePath = rs.getString("page_path");
        if (StringUtils.isBlank(pagePath)) {
          continue;
        }
        String identity = classifyBotUserAgent(rs.getString("user_agent"), botUserAgentList);
        pageHitsByIdentity.computeIfAbsent(identity, k -> new HashMap<>()).merge(pagePath, 1L, Long::sum);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
      return null;
    }

    SimpleDateFormat dateFormat = new SimpleDateFormat(BOT_IDENTITY_DATE_FORMAT);
    List<BotIdentityStats> records = new ArrayList<>();
    countByIdentity.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .forEach(entry -> {
          String identity = entry.getKey();
          BotIdentityStats stats = new BotIdentityStats();
          stats.setIdentity(identity);
          stats.setSessionCount(entry.getValue());
          stats.setFirstSeen(dateFormat.format(firstSeenByIdentity.get(identity)));
          stats.setLastSeen(dateFormat.format(lastSeenByIdentity.get(identity)));
          Map<String, Long> pageHits = pageHitsByIdentity.get(identity);
          if (pageHits != null && !pageHits.isEmpty()) {
            pageHits.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(topPageEntry -> {
                  stats.setTopPage(topPageEntry.getKey());
                  stats.setTopPageHits(topPageEntry.getValue());
                });
          }
          records.add(stats);
        });
    return records;
  }

  public static long countSessionsToday() {
    LocalDate now = LocalDate.now();
    Timestamp timestamp = Timestamp.valueOf(now.atStartOfDay());
    String today = new SimpleDateFormat("yyyy-MM-dd").format(timestamp);

    long count = -1;
    String SQL_QUERY =
        "SELECT unique_sessions AS session_count " +
            "FROM web_page_hit_snapshots " +
            "WHERE date_value = '" + today + "'";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        count = rs.getLong("session_count");
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return count;
  }

  public static long countOnlineNow() {
    long count = -1;
    String SQL_QUERY =
        "SELECT COUNT(DISTINCT(session_id)) AS session_count " +
            "FROM web_page_hits " +
            "WHERE hit_date > NOW() - INTERVAL '20 minutes' " +
            "AND NOT EXISTS (SELECT 1 FROM sessions WHERE session_id = web_page_hits.session_id AND is_bot = TRUE)";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        count = rs.getLong("session_count");
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return count;
  }

  private static PreparedStatement createPreparedStatementTopReferrals(Connection connection, int value, char intervalType, int recordLimit) throws SQLException {

    // Filter out the site to remove self-referrals
    // Handles: http://[www.], https://[www.], [www.]
    String siteUrl = LoadSitePropertyCommand.loadByName("site.url");
    String siteUrl2 = null;
    String siteUrl3 = null;
    String siteUrl4 = null;
    String siteUrl5 = null;
    String siteUrl6 = null;
    if (StringUtils.isNotBlank(siteUrl)) {
      // Strip trailing /
      if (siteUrl.endsWith("/")) {
        siteUrl = siteUrl.substring(0, siteUrl.length() - 1);
      }
      // Handle www
      if (siteUrl.contains("://www.")) {
        siteUrl2 = siteUrl.replace("://www.", "://");
      } else {
        siteUrl2 = siteUrl.replace("://", "://www.");
      }
      // Handle http
      if (siteUrl.startsWith("http://")) {
        siteUrl3 = siteUrl.replace("http://", "https://");
        siteUrl4 = siteUrl2.replace("http://", "https://");
      } else {
        siteUrl3 = siteUrl.replace("https://", "http://");
        siteUrl4 = siteUrl2.replace("https://", "http://");
      }
      // Remove *://
      siteUrl5 = siteUrl3.substring(siteUrl3.indexOf("://") + 3);
      siteUrl6 = siteUrl4.substring(siteUrl4.indexOf("://") + 3);
    }

    String SQL_QUERY =
        "SELECT referer, count(referer) AS referer_count " +
            "FROM sessions " +
            "WHERE created > NOW() - INTERVAL '" + value + " " +
            (intervalType == 'y' ? "years" :
                (intervalType == 'm' ? "months" :
                    (intervalType == 'w' ? "weeks" :
                        (intervalType == 'h' ? "hours" :
                            "days")))) +
            "' " +
            "AND LOWER(referer) NOT LIKE 'http://localhost%' " +
            "AND LOWER(referer) NOT LIKE LOWER(?) " +
            "AND LOWER(referer) NOT LIKE LOWER(?) " +
            "AND LOWER(referer) NOT LIKE LOWER(?) " +
            "AND LOWER(referer) NOT LIKE LOWER(?) " +
            "AND LOWER(referer) NOT LIKE LOWER(?) " +
            "AND LOWER(referer) NOT LIKE LOWER(?) " +
            "AND is_bot = false " +
            "GROUP BY referer " +
            "ORDER BY referer_count desc " +
            "LIMIT " + recordLimit;
    PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
    pst.setString(1, siteUrl + "%");
    pst.setString(2, siteUrl2 + "%");
    pst.setString(3, siteUrl3 + "%");
    pst.setString(4, siteUrl4 + "%");
    pst.setString(5, siteUrl5 + "%");
    pst.setString(6, siteUrl6 + "%");
    return pst;
  }

  public static List<StatisticsData> findTopReferrals(int value, char intervalType, int recordLimit) {
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = createPreparedStatementTopReferrals(connection, value, intervalType, recordLimit);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("referer"));
        data.setValue(String.valueOf(rs.getLong("referer_count")));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  public static Session add(Session record) {
    // remove tailing slash on referer
    String referer = record.getReferer();
    if (referer != null && referer.length() > 1 && referer.endsWith("/")) {
      referer = referer.substring(0, referer.length() - 1);
    }
    // Insert the record
    SqlUtils insertValues = new SqlUtils()
        .add("session_id", record.getSessionId())
        .add("source", record.getSource())
        .add("ip_address", record.getIpAddress())
        .add("user_agent", StringUtils.abbreviate(record.getUserAgent(), 255))
        .add("referer", StringUtils.abbreviate(referer, 255))
        .add("continent", record.getContinent())
        .add("country_iso", record.getCountryIso())
        .add("country", record.getCountry())
        .add("city", record.getCity())
        .add("state_iso", record.getStateIso())
        .add("state", record.getState())
        .add("postal_code", record.getPostalCode())
        .add("timezone", record.getTimezone())
        .add("is_bot", record.getIsBot())
        .addIfExists("latitude", record.getLatitude(), 0)
        .addIfExists("longitude", record.getLongitude(), 0)
        .addIfExists("metro_code", record.getMetroCode(), -1)
        .addIfExists("app_id", record.getAppId(), -1L);
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static void updateVisitorId(Connection connection, Visitor record) throws SQLException {
    if (record.getId() == -1 || record.getSessionId() == null) {
      return;
    }
    SqlUtils set = new SqlUtils().add("visitor_id", record.getId());
    SqlUtils where = new SqlUtils().add("session_id = ?", record.getSessionId());
    DB.update(connection, TABLE_NAME, set, where);
  }

  public static void updateVisitorId(UserSession userSession, Visitor visitor) {
    if (userSession == null || userSession.getSessionId() == null || visitor == null || visitor.getId() == -1) {
      return;
    }
    SqlUtils set = new SqlUtils().add("visitor_id", visitor.getId());
    SqlUtils where = new SqlUtils().add("session_id = ?", userSession.getSessionId());
    DB.update(TABLE_NAME, set, where);
  }

  /**
   * Nullifies PII columns (ip_address, city, postal_code, latitude, longitude) for session rows
   * older than {@code days} days. Rows already scrubbed (ip_address IS NULL) are skipped so
   * repeated runs are idempotent. Returns the number of rows updated.
   */
  public static int scrubOldPii(int days) {
    if (days < 1) {
      return 0;
    }
    // days is an int parsed and bounded before interpolation — no SQL injection risk.
    String sql = "UPDATE sessions " +
        "SET ip_address = NULL, city = NULL, postal_code = NULL, latitude = NULL, longitude = NULL " +
        "WHERE created < NOW() - INTERVAL '" + days + " days' AND ip_address IS NOT NULL";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(sql)) {
      return pst.executeUpdate();
    } catch (SQLException se) {
      LOG.error("scrubOldPii SQLException: " + se.getMessage());
    }
    return 0;
  }

  private static final int DEFAULT_RETENTION_DAYS = 365;

  /** Parses the configured analytics retention window to a bounded positive integer, defaulting to 365 days. */
  public static int resolveRetentionDays(String value) {
    if (StringUtils.isBlank(value)) {
      return DEFAULT_RETENTION_DAYS;
    }
    int days;
    try {
      days = Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return DEFAULT_RETENTION_DAYS;
    }
    if (days < 1) {
      return 1;
    }
    if (days > 3650) {
      return 3650;
    }
    return days;
  }

  /**
   * Top {@code recordLimit} countries by non-bot session count within the given window -- backs the
   * geographic-anomaly alert (issue #569 slice 2): a country appearing in a short recent window's
   * top list that was not in a longer baseline window's top list. Sessions with no resolved country
   * (country IS NULL) are excluded, since they can't be attributed to any country's count.
   *
   * <p>Deliberately does NOT filter on is_anonymous, unlike {@link #findDailyUniqueLocations(int)}:
   * SaveSessionCommand populates country/continent for every session, anonymous or not -- only
   * city/postal/lat/long are anonymous-restricted. Filtering on is_anonymous here would silently
   * undercount and could hide a real anomaly made up mostly of anonymous traffic.
   */
  public static List<StatisticsData> findTopCountriesByCount(Timestamp startDate, Timestamp endDate, int recordLimit) {
    SqlUtils where = new SqlUtils()
        .add("created >= ?", startDate)
        .add("created < ?", endDate)
        .add("is_bot = ?", false)
        .add("country IS NOT NULL");
    SqlUtils orderBy = new SqlUtils().add("country_count DESC");
    return DB.selectGroupedFrom(TABLE_NAME, "country", "country_count", where, orderBy, recordLimit);
  }

  private static final int DEFAULT_GEO_ANOMALY_BASELINE_DAYS = 30;
  private static final int DEFAULT_GEO_ANOMALY_RECENT_HOURS = 24;

  /** Parses the configured geo-anomaly baseline window (days), defaulting to 30, matching resolveRetentionDays's precedent. */
  public static int resolveGeoAnomalyBaselineDays(String value) {
    if (StringUtils.isBlank(value)) {
      return DEFAULT_GEO_ANOMALY_BASELINE_DAYS;
    }
    int days;
    try {
      days = Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return DEFAULT_GEO_ANOMALY_BASELINE_DAYS;
    }
    if (days < 1) {
      return 1;
    }
    if (days > 365) {
      return 365;
    }
    return days;
  }

  /** Parses the configured geo-anomaly recent window (hours), defaulting to 24, capped at one week. */
  public static int resolveGeoAnomalyRecentHours(String value) {
    if (StringUtils.isBlank(value)) {
      return DEFAULT_GEO_ANOMALY_RECENT_HOURS;
    }
    int hours;
    try {
      hours = Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return DEFAULT_GEO_ANOMALY_RECENT_HOURS;
    }
    if (hours < 1) {
      return 1;
    }
    if (hours > 168) {
      return 168;
    }
    return hours;
  }

  private static Session buildRecord(ResultSet rs) {
    try {
      Session record = new Session();
      record.setId(rs.getLong("id"));
      record.setSessionId(rs.getString("session_id"));
      record.setSource(rs.getString("source"));
      record.setIpAddress(rs.getString("ip_address"));
      record.setUserAgent(rs.getString("user_agent"));
      record.setReferer(rs.getString("referer"));
      record.setContinent(rs.getString("continent"));
      record.setCountryIso(rs.getString("country_iso"));
      record.setCountry(rs.getString("country"));
      record.setCity(rs.getString("city"));
      record.setStateIso(rs.getString("state_iso"));
      record.setState(rs.getString("state"));
      record.setPostalCode(rs.getString("postal_code"));
      record.setTimezone(rs.getString("timezone"));
      record.setLatitude(rs.getDouble("latitude"));
      record.setLongitude(rs.getDouble("longitude"));
      record.setMetroCode(rs.getInt("metro_code"));
      record.setCreated(rs.getTimestamp("created"));
      record.setAppId(rs.getLong("app_id"));
      record.setIsBot(rs.getBoolean("is_bot"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
