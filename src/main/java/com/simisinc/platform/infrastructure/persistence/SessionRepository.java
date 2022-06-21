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
import com.simisinc.platform.domain.model.Session;
import com.simisinc.platform.domain.model.Visitor;
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
import java.util.List;

/**
 * Persists and retrieves session objects
 *
 * @author matt rajkowski
 * @created 7/2/18 11:21 AM
 */
public class SessionRepository {

  private static Log LOG = LogFactory.getLog(SessionRepository.class);

  private static String TABLE_NAME = "sessions";
  private static String PRIMARY_KEY[] = new String[]{"id"};

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
            "AND latitude IS NOT NULL " +
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

  public static long countDistinctSessions(Timestamp startDate, Timestamp endDate) {
    // Query the data, skip some things
    SqlUtils where = new SqlUtils()
        .add("created >= ?", startDate)
        .add("created < ?", endDate)
        .add("is_bot = ?", false);
    return DB.selectCountFrom(TABLE_NAME, where);
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
