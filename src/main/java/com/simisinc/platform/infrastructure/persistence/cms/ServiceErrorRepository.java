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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.ServiceError;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and retrieves recent service errors (issue #556).
 *
 * @author SimIS
 * @created 8/10/2026
 */
public class ServiceErrorRepository {

  private static Log LOG = LogFactory.getLog(ServiceErrorRepository.class);

  private static String TABLE_NAME = "service_errors";
  private static String[] PRIMARY_KEY = new String[]{"service_error_id"};

  public static ServiceError save(ServiceError record) {
    SqlUtils insertValues = new SqlUtils()
        .add("request_uri", record.getRequestUri(), 500)
        .add("exception_class", record.getExceptionClass(), 255)
        .add("message", record.getMessage(), 1000)
        .add("stack_trace", record.getStackTrace());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  /** The most recent {@code limit} errors, newest first. */
  public static List<ServiceError> findRecent(int limit) {
    String SQL_QUERY =
        "SELECT * FROM " + TABLE_NAME + " " +
            "ORDER BY occurred_at DESC, service_error_id DESC " +
            "LIMIT ?";
    List<ServiceError> records = new ArrayList<>();
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY)) {
      pst.setInt(1, limit);
      try (ResultSet rs = pst.executeQuery()) {
        while (rs.next()) {
          records.add(buildRecord(rs));
        }
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  /** Prunes error history older than 30 days, mirroring system_health_checks' retention window. */
  public static void deleteOld() {
    DB.deleteFrom(TABLE_NAME, new SqlUtils().add("occurred_at < NOW() - INTERVAL '30 days'"));
  }

  private static ServiceError buildRecord(ResultSet rs) throws SQLException {
    ServiceError record = new ServiceError();
    record.setId(rs.getLong("service_error_id"));
    record.setRequestUri(rs.getString("request_uri"));
    record.setExceptionClass(rs.getString("exception_class"));
    record.setMessage(rs.getString("message"));
    record.setStackTrace(rs.getString("stack_trace"));
    Timestamp occurredAt = rs.getTimestamp("occurred_at");
    record.setOccurredAt(occurredAt);
    return record;
  }
}
