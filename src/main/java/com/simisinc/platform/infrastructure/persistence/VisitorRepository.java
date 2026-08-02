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
   * Percent of the visitor tokens active (created) in the last daysToLimit that have MORE than one
   * row in this table across all of history, for the "return-visitor-rate" engagement report
   * (issue #568). A token's very first-ever session doesn't make it "returning", but any later one
   * does -- regardless of when that later session happened -- so the total-row count per token is
   * all-time, not restricted to the window; only which tokens count toward the denominator is
   * windowed. Returns 0.0 when nothing is active in the window, to avoid a division by zero.
   */
  public static double findReturnVisitorRatePercent(int daysToLimit) {
    String SQL_QUERY =
        "WITH active_tokens AS (" +
            "SELECT DISTINCT token FROM " + TABLE_NAME + " WHERE created > NOW() - INTERVAL '" + daysToLimit + " days'" +
            "), token_totals AS (" +
            "SELECT v.token, COUNT(*) AS total_rows " +
            "FROM " + TABLE_NAME + " v " +
            "JOIN active_tokens a ON v.token = a.token " +
            "GROUP BY v.token" +
            ") " +
            "SELECT COUNT(*) FILTER (WHERE total_rows >= 2) AS returning_count, COUNT(*) AS active_count " +
            "FROM token_totals";
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
