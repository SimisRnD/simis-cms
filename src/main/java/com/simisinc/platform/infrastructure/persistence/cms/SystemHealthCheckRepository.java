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

import com.simisinc.platform.domain.model.cms.SystemHealthCheck;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists and retrieves system health check history (issue #466).
 *
 * @author SimIS
 * @created 7/30/2026
 */
public class SystemHealthCheckRepository {

  private static Log LOG = LogFactory.getLog(SystemHealthCheckRepository.class);

  private static String TABLE_NAME = "system_health_checks";
  private static String[] PRIMARY_KEY = new String[]{"system_health_check_id"};

  public static SystemHealthCheck save(SystemHealthCheck record) {
    SqlUtils insertValues = new SqlUtils()
        .add("service_name", record.getServiceName(), 50)
        .add("status", record.getStatus(), 10)
        .add("response_time_ms", record.getResponseTimeMs())
        .add("error_message", record.getErrorMessage(), 500);
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  /** The single most recent check for each service that has ever been checked, most recently
   * checked first. Uses Postgres' DISTINCT ON rather than a self-join or per-service query. */
  public static List<SystemHealthCheck> findLatestPerService() {
    String SQL_QUERY =
        "SELECT DISTINCT ON (service_name) * " +
            "FROM " + TABLE_NAME + " " +
            "ORDER BY service_name, checked_at DESC";
    List<SystemHealthCheck> records = new ArrayList<>();
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      while (rs.next()) {
        records.add(buildRecord(rs));
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  /** Percent of checks for the given service that passed over the last {@code hours} hours, or null
   * if the service has no checks in that window. hours is an int, so placing it in the interval
   * cannot inject SQL. */
  public static Double findUptimePercent(String serviceName, int hours) {
    String SQL_QUERY =
        "SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE status = 'UP') AS up_count " +
            "FROM " + TABLE_NAME + " " +
            "WHERE service_name = ? AND checked_at > NOW() - INTERVAL '" + hours + " hours'";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY)) {
      pst.setString(1, serviceName);
      try (ResultSet rs = pst.executeQuery()) {
        if (rs.next()) {
          long total = rs.getLong("total");
          if (total == 0) {
            return null;
          }
          long up = rs.getLong("up_count");
          return (up * 100.0) / total;
        }
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return null;
  }

  /** Prunes check history older than 30 days, mirroring the raw web_vitals retention window. */
  public static void deleteOld() {
    DB.deleteFrom(TABLE_NAME, new SqlUtils().add("checked_at < NOW() - INTERVAL '30 days'"));
  }

  private static SystemHealthCheck buildRecord(ResultSet rs) throws SQLException {
    SystemHealthCheck record = new SystemHealthCheck();
    record.setId(rs.getLong("system_health_check_id"));
    record.setServiceName(rs.getString("service_name"));
    record.setStatus(rs.getString("status"));
    record.setResponseTimeMs(rs.getInt("response_time_ms"));
    record.setErrorMessage(rs.getString("error_message"));
    Timestamp checkedAt = rs.getTimestamp("checked_at");
    record.setCheckedAt(checkedAt);
    return record;
  }
}
