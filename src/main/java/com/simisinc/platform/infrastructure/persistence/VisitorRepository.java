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

import com.simisinc.platform.domain.model.Visitor;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves visitor objects
 *
 * @author matt rajkowski
 * @created 4/7/19 11:43 AM
 */
public class VisitorRepository {

  private static Log LOG = LogFactory.getLog(VisitorRepository.class);

  private static String TABLE_NAME = "visitors";
  private static String[] PRIMARY_KEY = new String[]{"visitor_id"};

  public static List<Visitor> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("visitor_id"),
        VisitorRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<Visitor>) result.getRecords();
    }
    return null;
  }

  public static Visitor findByToken(String visitorUniqueId) {
    if (StringUtils.isBlank(visitorUniqueId)) {
      return null;
    }
    return (Visitor) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("token = ?", visitorUniqueId),
        VisitorRepository::buildRecord);
  }

  public static Visitor add(Visitor record) {
    SqlUtils insertValues = new SqlUtils()
        .add("token", record.getToken())
        .add("session_id", record.getSessionId());
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
        // Manage the related session
        SessionRepository.updateVisitorId(connection, record);
        // Finish the transaction
        transaction.commit();
        return record;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("An id was not set!");
    return null;
  }

  /**
   * Percent of the visitors active (had a session) in the last daysToLimit who have MORE than one
   * session in the {@code sessions} table across all of history, for the "return-visitor-rate"
   * engagement report (issue #568).
   *
   * <p>
   * This deliberately queries {@code sessions.visitor_id} rather than counting rows in this table
   * ({@code visitors}). {@link com.simisinc.platform.presentation.controller.WebRequestFilter} only
   * inserts a new {@code visitors} row when a request's token is NOT already recognized
   * ({@code LoadVisitorCommand.loadVisitorByToken} returns null); a returning visitor is recognized
   * by that same lookup and reuses their existing {@code visitors} row -- a new {@code sessions} row
   * is created instead, carrying forward the same {@code visitor_id}. So a token's row count in
   * {@code visitors} is 1 for the visitor's entire lifetime, and a query keyed on that count would
   * always see ~0% "returning". {@code sessions}, by contrast, gets a new row per browser session
   * while linking back to the same {@code visitor_id}, so it is the table that actually holds the
   * "returned before" signal.
   * </p>
   *
   * <p>
   * A visitor's very first-ever session doesn't make them "returning", but any later one does --
   * regardless of when that later session happened -- so the total-session count per visitor is
   * all-time, not restricted to the window; only which visitors count toward the denominator is
   * windowed. Bot sessions are excluded (is_bot lives directly on sessions here, unlike
   * web_page_hits' NOT EXISTS join). Returns 0.0 when nothing is active in the window, to avoid a
   * division by zero.
   * </p>
   */
  public static double findReturnVisitorRatePercent(int daysToLimit) {
    String SQL_QUERY =
        "WITH active_visitors AS (" +
            "SELECT DISTINCT visitor_id FROM sessions " +
            "WHERE created > NOW() - INTERVAL '" + daysToLimit + " days' " +
            "AND visitor_id IS NOT NULL AND is_bot = false" +
            "), visitor_totals AS (" +
            "SELECT s.visitor_id, COUNT(*) AS total_sessions " +
            "FROM sessions s " +
            "JOIN active_visitors a ON s.visitor_id = a.visitor_id " +
            "WHERE s.is_bot = false " +
            "GROUP BY s.visitor_id" +
            ") " +
            "SELECT COUNT(*) FILTER (WHERE total_sessions >= 2) AS returning_count, COUNT(*) AS active_count " +
            "FROM visitor_totals";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        long activeCount = rs.getLong("active_count");
        if (activeCount == 0) {
          return 0.0;
        }
        long returningCount = rs.getLong("returning_count");
        return 100.0 * returningCount / activeCount;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return 0.0;
  }

  private static Visitor buildRecord(ResultSet rs) {
    try {
      Visitor record = new Visitor();
      record.setId(rs.getLong("visitor_id"));
      record.setToken(rs.getString("token"));
      record.setSessionId(rs.getString("session_id"));
      record.setCreated(rs.getTimestamp("created"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
