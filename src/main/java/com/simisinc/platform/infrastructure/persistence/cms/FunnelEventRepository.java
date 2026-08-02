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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Records conversion funnel stage events (issue #565, phase 1) -- one row per stage event (e.g. a
 * contact-form page view, a successful submission, or an admin marking a submission processed).
 * {@code funnel_key} names the logical funnel ('contact-form' for this phase); later phases can reuse
 * this same table with a different funnel_key/stage set without a schema change.
 *
 * @author SimIS Inc.
 * @created 8/2/2026
 */
public class FunnelEventRepository {

  private static Log LOG = LogFactory.getLog(FunnelEventRepository.class);

  private static String TABLE_NAME = "funnel_events";
  private static String[] PRIMARY_KEY = new String[]{"funnel_event_id"};

  private static final int DEFAULT_RETENTION_DAYS = 90;
  private static final int MIN_RETENTION_DAYS = 7;
  private static final int MAX_RETENTION_DAYS = 3650;

  /** Never throws -- a failed recording must not become a second, unrelated failure for the caller
   *  (a form submission, a page render, or an admin marking a submission processed). */
  public static void record(String funnelKey, String stage, String sessionId) {
    try {
      SqlUtils insertValues = new SqlUtils()
          .add("funnel_key", funnelKey, 50)
          .add("stage", stage, 30)
          .add("session_id", sessionId);
      long id = DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY);
      if (id == -1) {
        LOG.error("A funnel event was not saved");
      }
    } catch (Exception e) {
      LOG.error("Could not record a funnel event", e);
    }
  }

  /**
   * Per-stage counts for one funnel within a date range, e.g. {@code {view=100, submitted=40,
   * processed=30}}, for the phase-1 "per-stage counts and drop-off" report. A stage with zero events
   * in range is simply absent from the map -- callers default missing stages to zero. Stage order
   * (view -> submitted -> processed) is a property of the funnel definition, not this table, so it is
   * not returned here.
   */
  public static Map<String, Long> countStagesInRange(String funnelKey, Timestamp startDate, Timestamp endDate) {
    String SQL_QUERY =
        "SELECT stage, COUNT(*) AS stage_count " +
            "FROM " + TABLE_NAME + " " +
            "WHERE funnel_key = ? AND occurred >= ? AND occurred < ? " +
            "GROUP BY stage";
    Map<String, Long> counts = new LinkedHashMap<>();
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY)) {
      int i = 0;
      pst.setString(++i, funnelKey);
      pst.setTimestamp(++i, startDate);
      pst.setTimestamp(++i, endDate);
      try (ResultSet rs = pst.executeQuery()) {
        while (rs.next()) {
          counts.put(rs.getString("stage"), rs.getLong("stage_count"));
        }
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return counts;
  }

  /**
   * Deletes funnel events past the configured retention window, mirroring
   * FormSubmissionFailureRepository.deleteOlderThan -- this is unbounded-growth, anonymous-traffic
   * telemetry (reachable by any visitor of the configured page/form, like web_page_hits), not a
   * compliance-grade evidentiary trail, so it gets the same routine cleanup treatment. Returns the
   * number of rows removed.
   */
  public static int deleteOlderThan(int days) {
    if (days < 1) {
      return 0;
    }
    return DB.deleteFrom(TABLE_NAME, new SqlUtils().add("occurred < NOW() - INTERVAL '" + days + " days'"));
  }

  /** Parses the configured retention window to a bounded positive integer, defaulting to 90 days. */
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
    if (days < MIN_RETENTION_DAYS) {
      return MIN_RETENTION_DAYS;
    }
    if (days > MAX_RETENTION_DAYS) {
      return MAX_RETENTION_DAYS;
    }
    return days;
  }
}
