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

import com.simisinc.platform.application.cms.FormDataJSONCommand;
import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.infrastructure.database.*;
import com.simisinc.platform.presentation.controller.DataConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists and retrieves form data objects
 *
 * @author matt rajkowski
 * @created 6/1/18 2:42 PM
 */
public class FormDataRepository {

  private static Log LOG = LogFactory.getLog(FormDataRepository.class);

  private static String TABLE_NAME = "form_data";
  private static String[] PRIMARY_KEY = new String[]{"form_data_id"};

  public static long countAwaitingReview() {
    SqlUtils where = new SqlUtils()
        .add("processed IS NULL")
        .add("dismissed IS NULL");
    return DB.selectCountFrom(TABLE_NAME, where);
  }

  private static DataResult query(FormDataSpecification specification, DataConstraints constraints) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("form_data_id = ?", specification.getId(), -1)
          .addIfExists("form_unique_id = ?", specification.getFormUniqueId())
          .addIfExists("session_id = ?", specification.getSessionId())
          .addIfExists("claimed_by = ?", specification.getClaimedBy(), -1L);
      if (specification.getFlaggedAsSpam() != DataConstants.UNDEFINED) {
        if (specification.getFlaggedAsSpam() == DataConstants.TRUE) {
          where.add("flagged_as_spam = true");
        } else {
          where.add("flagged_as_spam = false");
        }
      }
      if (specification.getClaimed() != DataConstants.UNDEFINED) {
        if (specification.getClaimed() == DataConstants.TRUE) {
          where.add("claimed IS NOT NULL");
        } else {
          where.add("claimed IS NULL");
        }
      }
      if (specification.getDismissed() != DataConstants.UNDEFINED) {
        if (specification.getDismissed() == DataConstants.TRUE) {
          where.add("dismissed IS NOT NULL");
        } else {
          where.add("dismissed IS NULL");
        }
      }
      if (specification.getProcessed() != DataConstants.UNDEFINED) {
        if (specification.getProcessed() == DataConstants.TRUE) {
          where.add("processed IS NOT NULL");
        } else {
          where.add("processed IS NULL");
        }
      }
      where.addIfExists("created >= ?", specification.getOccurredAfter());
      where.addIfExists("created < ?", specification.getOccurredBefore());
    }
    return DB.selectAllFrom(TABLE_NAME, where, constraints, FormDataRepository::buildRecord);
  }

  public static FormData findById(long formDataId) {
    if (formDataId == -1) {
      return null;
    }
    return (FormData) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("form_data_id = ?", formDataId),
        FormDataRepository::buildRecord);
  }

  public static List<FormData> findAll(FormDataSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("form_data_id desc");
    DataResult result = query(specification, constraints);
    return (List<FormData>) result.getRecords();
  }

  /** Total submissions ever saved (issue #563) -- form_data only ever contains successful saves. */
  public static long countTotalSubmissions() {
    return DB.selectCountFrom(TABLE_NAME);
  }

  public static long countSpamFlagged(Timestamp startDate, Timestamp endDate) {
    SqlUtils where = new SqlUtils()
        .add("created >= ?", startDate)
        .add("created < ?", endDate)
        .add("flagged_as_spam = true");
    return DB.selectCountFrom(TABLE_NAME, where);
  }

  /** Submissions for a single form in a date range (issue #563 -- conversion-rate tracking). */
  public static long countSubmissions(String formUniqueId, Timestamp startDate, Timestamp endDate) {
    SqlUtils where = new SqlUtils()
        .add("form_unique_id = ?", formUniqueId)
        .add("created >= ?", startDate)
        .add("created < ?", endDate);
    return DB.selectCountFrom(TABLE_NAME, where);
  }

  /** Day-bucketed submission counts, zero-filled, mirroring UserRepository.findDailyUserRegistrations. */
  public static List<StatisticsData> findDailySubmissions(int daysToLimit) {
    String SQL_QUERY =
        "SELECT DATE_TRUNC('day', day)::VARCHAR(10) AS date_column, COUNT(form_data_id) AS daily_count " +
            "FROM (SELECT generate_series(NOW() - INTERVAL '" + daysToLimit + " days', NOW(), INTERVAL '1 day')::date) d(day) " +
            "LEFT JOIN " + TABLE_NAME + " ON DATE_TRUNC('day', created) = DATE_TRUNC('day', d.day) " +
            "GROUP BY d.day " +
            "ORDER BY d.day";
    return queryDateBucketedCounts(SQL_QUERY);
  }

  /** Month-bucketed submission counts, zero-filled, mirroring UserRepository.findMonthlyUserRegistrations. */
  public static List<StatisticsData> findMonthlySubmissions(int monthsLimit) {
    String SQL_QUERY =
        "SELECT DATE_TRUNC('month', month)::VARCHAR(10) AS date_column, COUNT(form_data_id) AS monthly_count " +
            "FROM (SELECT generate_series(NOW() - INTERVAL '" + monthsLimit + " months', NOW(), INTERVAL '1 month')::date) d(month) " +
            "LEFT JOIN " + TABLE_NAME + " ON DATE_TRUNC('month', created) = DATE_TRUNC('month', month) " +
            "GROUP BY d.month " +
            "ORDER BY d.month";
    return queryDateBucketedCounts(SQL_QUERY);
  }

  private static List<StatisticsData> queryDateBucketedCounts(String sqlQuery) {
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(sqlQuery);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("date_column"));
        data.setValue(String.valueOf(rs.getLong(2)));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  /**
   * Top submitted forms by form_unique_id (issue #563) -- there is no form-type taxonomy in this table
   * (see the issue discussion: newsletter/career submissions never reach form_data at all, they go
   * through separate widgets), so this is the finest real breakdown available.
   */
  public static List<StatisticsData> findSubmissionCountsByForm(int daysToLimit, int recordLimit) {
    String SQL_QUERY =
        "SELECT form_unique_id, COUNT(form_unique_id) AS form_count " +
            "FROM " + TABLE_NAME + " " +
            "WHERE created > NOW() - INTERVAL '" + daysToLimit + " days' " +
            "AND form_unique_id IS NOT NULL " +
            "GROUP BY form_unique_id " +
            "ORDER BY form_count DESC " +
            "LIMIT " + recordLimit;
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("form_unique_id"));
        data.setValue(String.valueOf(rs.getLong("form_count")));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  public static FormData save(FormData record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static FormData add(FormData record) {
    SqlUtils insertValues = new SqlUtils()
        .add("form_unique_id", StringUtils.trimToNull(record.getFormUniqueId()))
        .add("ip_address", record.getIpAddress())
        .add("session_id", record.getSessionId())
        .add("url", record.getUrl())
        .add("flagged_as_spam", record.getFlaggedAsSpam())
        .add("created_by", record.getCreatedBy(), -1)
        .add("modified_by", record.getModifiedBy(), -1);
    if (StringUtils.isNotBlank(record.getQueryParameters())) {
      // Convert from URL encoded to plain text
      String queryString = record.getQueryParameters();
      queryString = StringUtils.replace(queryString, "%20", " ");
      insertValues.add("query_params", queryString);
      record.setQueryParameters(queryString);
    }
    insertValues.add(new SqlValue("field_values", SqlValue.JSONB_TYPE, FormDataJSONCommand.createJSONString(record)));
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static FormData update(FormData record) {
    SqlUtils updateValues = new SqlUtils()
        .add("modified_by", record.getModifiedBy())
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("form_data_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean markAsArchived(FormData record, long userId) {
    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    SqlUtils updateValues = new SqlUtils()
        .add("dismissed", timestamp)
        .add("dismissed_by", userId);
    SqlUtils where = new SqlUtils()
        .add("form_data_id = ?", record.getId());
    boolean updated = DB.update(TABLE_NAME, updateValues, where);
    if (updated) {
      // Update the record for additional workflows
      record.setDismissed(timestamp);
      record.setDismissedBy(userId);
    }
    return updated;
  }

  public static boolean tryToMarkAsClaimed(FormData record, long userId) {
    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    SqlUtils updateValues = new SqlUtils()
        .add("claimed", timestamp)
        .add("claimed_by", userId);
    SqlUtils where = new SqlUtils()
        .add("form_data_id = ?", record.getId())
        .add("claimed IS NULL");
    boolean updated = DB.update(TABLE_NAME, updateValues, where);
    if (updated) {
      // Update the record for additional workflows
      record.setClaimed(timestamp);
      record.setClaimedBy(userId);
    }
    return updated;
  }

  public static boolean markAsProcessed(FormData record, long userId) {
    return markAsProcessed(record, userId, null);
  }

  public static boolean markAsProcessed(FormData record, long userId, String system) {
    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    SqlUtils updateValues = new SqlUtils()
        .add("processed", timestamp)
        .add("processed_by", userId)
        .addIfExists("processed_system", system);
    SqlUtils where = new SqlUtils()
        .add("form_data_id = ?", record.getId())
        .add("processed IS NULL");
    boolean updated = DB.update(TABLE_NAME, updateValues, where);
    if (updated) {
      // Update the record for additional workflows
      record.setProcessed(timestamp);
      record.setProcessedBy(userId);
    }
    return updated;
  }

  private static FormData buildRecord(ResultSet rs) {
    try {
      FormData record = new FormData();
      record.setId(rs.getLong("form_data_id"));
      record.setFormUniqueId(rs.getString("form_unique_id"));
      FormDataJSONCommand.populateFromJSONString(record, rs.getString("field_values"));
      record.setIpAddress(rs.getString("ip_address"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModified(rs.getTimestamp("modified"));
      record.setClaimed(rs.getTimestamp("claimed"));
      record.setClaimedBy(rs.getLong("claimed_by"));
      record.setDismissed(rs.getTimestamp("dismissed"));
      record.setUrl(rs.getString("url"));
      record.setQueryParameters(rs.getString("query_params"));
      record.setFlaggedAsSpam(rs.getBoolean("flagged_as_spam"));
      record.setSessionId(rs.getString("session_id"));
      record.setDismissedBy(rs.getLong("dismissed_by"));
      record.setProcessed(rs.getTimestamp("processed"));
      record.setProcessedBy(rs.getLong("processed_by"));
      record.setProcessedSystem(rs.getString("processed_system"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }

  /**
   * Exports form submissions to a CSV file (issue #483) so an admin can pull the raw IP addresses
   * offline, e.g. to cross-reference a spam source before adding it to the IP block list.
   */
  public static void export(DataConstraints constraints, File file) {
    SqlUtils selectFields = new SqlUtils()
        .addNames(
            "form_unique_id AS \"Form\"",
            "ip_address AS \"IP Address\"",
            "created AS \"Submitted\"",
            "url AS \"URL\"",
            "flagged_as_spam AS \"Spam Flagged\""
        );
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("form_data_id desc");
    DB.exportToCsvAllFrom(TABLE_NAME, selectFields, null, null, null, constraints, file);
  }
}
