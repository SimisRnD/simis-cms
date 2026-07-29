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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Records rejected form submissions (issue #563) -- deliberately lean, no field values, since most of
 * this volume is bot/spam noise (captcha failures, rate-limited requests) not worth persisting PII for.
 * A rejection here never has a corresponding {@code form_data} row -- that table only ever contains
 * successfully-saved submissions.
 *
 * @author SimIS Inc.
 * @created 7/28/2026
 */
public class FormSubmissionFailureRepository {

  private static Log LOG = LogFactory.getLog(FormSubmissionFailureRepository.class);

  private static String TABLE_NAME = "form_submission_failures";
  private static String[] PRIMARY_KEY = new String[]{"failure_id"};

  public static final String REASON_MISSING_FIELD = "missing_field";
  public static final String REASON_INVALID_EMAIL = "invalid_email";
  public static final String REASON_BLANK = "blank";
  public static final String REASON_CAPTCHA_FAILED = "captcha_failed";
  public static final String REASON_RATE_LIMITED = "rate_limited";

  private static final int DEFAULT_RETENTION_DAYS = 90;
  private static final int MIN_RETENTION_DAYS = 7;
  private static final int MAX_RETENTION_DAYS = 3650;

  /** Never throws -- a failed recording must not turn into a second, unrelated failure for the caller. */
  public static void record(String formUniqueId, String reason, String ipAddress, String url) {
    try {
      SqlUtils insertValues = new SqlUtils()
          .add("form_unique_id", StringUtils.trimToNull(formUniqueId))
          .add("reason", reason)
          .add("ip_address", ipAddress)
          .add("url", url);
      long id = DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY);
      if (id == -1) {
        LOG.error("A form submission failure record was not saved");
      }
    } catch (Exception e) {
      LOG.error("Could not record a form submission failure", e);
    }
  }

  public static long countTotalFailures(Timestamp startDate, Timestamp endDate) {
    SqlUtils where = new SqlUtils()
        .add("occurred >= ?", startDate)
        .add("occurred < ?", endDate);
    return DB.selectCountFrom(TABLE_NAME, where);
  }

  /** Breakdown by reason for the given range, e.g. for a "top error messages" table. */
  public static List<StatisticsData> findFailureCountsByReason(int daysToLimit, int recordLimit) {
    String SQL_QUERY =
        "SELECT reason, COUNT(reason) AS reason_count " +
            "FROM " + TABLE_NAME + " " +
            "WHERE occurred > NOW() - INTERVAL '" + daysToLimit + " days' " +
            "GROUP BY reason " +
            "ORDER BY reason_count DESC " +
            "LIMIT " + recordLimit;
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("reason"));
        data.setValue(String.valueOf(rs.getLong("reason_count")));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  /**
   * Deletes failure records past the configured retention window, mirroring
   * AuditLogRepository.deleteOlderThan. Returns the number of rows removed.
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
