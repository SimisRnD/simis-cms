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

package com.simisinc.platform.infrastructure.persistence.login;

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.StatisticsData;
import com.simisinc.platform.domain.model.login.UserLogin;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists and retrieves user login objects
 *
 * @author matt rajkowski
 * @created 4/10/18 5:23 PM
 */
public class UserLoginRepository {

  private static Log LOG = LogFactory.getLog(UserLoginRepository.class);

  private static String TABLE_NAME = "user_logins";
  private static String PRIMARY_KEY[] = new String[]{"login_id"};

  private static DataResult query(UserLoginSpecification specification, DataConstraints constraints) {
    SqlUtils select = new SqlUtils();
    SqlUtils where = new SqlUtils();
    SqlUtils orderBy = new SqlUtils();
    if (specification != null) {
      where.addIfExists("user_id = ?", specification.getUserId(), -1);
    }
    return DB.selectAllFrom(
        TABLE_NAME, select, where, orderBy, constraints, UserLoginRepository::buildRecord);
  }

  public static List<UserLogin> findAll(UserLoginSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("login_id desc");
    DataResult result = query(specification, constraints);
    return (List<UserLogin>) result.getRecords();
  }

  public static UserLogin queryLastLogin(long userId) {
    UserLoginSpecification specification = new UserLoginSpecification();
    specification.setUserId(userId);
    DataConstraints constraints = new DataConstraints();
    constraints.setDefaultColumnToSortBy("created desc");
    constraints.setPageSize(1);
    DataResult result = query(specification, constraints);
    if (result.hasRecords()) {
      return (UserLogin) result.getRecords().get(0);
    }
    return null;
  }

  public static long queryTodaysLoginCount(long userId) {
    SqlUtils where = new SqlUtils();
    where.add("user_id = ?", userId);
    where.add("created >= ?", Timestamp.valueOf(LocalDate.now().atStartOfDay()));
    return DB.selectCountFrom(TABLE_NAME, where);
  }

  public static List<StatisticsData> findUniqueDailyLogins(int daysToLimit) {
    String SQL_QUERY =
        "SELECT DATE_TRUNC('day', day)::VARCHAR(10) AS date_column, COUNT(DISTINCT(user_id)) AS daily_count " +
            "FROM (SELECT generate_series(NOW() - INTERVAL '" + daysToLimit + " days', NOW(), INTERVAL '1 day')::date) d(day) " +
            "LEFT JOIN user_logins ON DATE_TRUNC('day', created) = DATE_TRUNC('day', day) " +
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

  public static List<StatisticsData> findUniqueMonthlyLogins(int monthsLimit) {
    String SQL_QUERY =
        "SELECT DATE_TRUNC('month', month)::VARCHAR(10) AS date_column, COUNT(DISTINCT(user_id)) AS monthly_count " +
            "FROM (SELECT generate_series(NOW() - INTERVAL '" + monthsLimit + " months', NOW(), INTERVAL '1 month')::date) d(month) " +
            "LEFT JOIN user_logins ON DATE_TRUNC('month', created) = DATE_TRUNC('month', month) " +
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

  public static UserLogin save(UserLogin record) {
//    if (record.getId() > -1) {
//      return update(record);
//    }
    return add(record);
  }

  private static UserLogin add(UserLogin record) {
    SqlUtils insertValues = new SqlUtils()
        .add("user_id", record.getUserId())
        .add("source", record.getSource())
        .add("ip_address", record.getIpAddress())
        .add("session_id", record.getSessionId())
        .add("user_agent", StringUtils.abbreviate(record.getUserAgent(), 255));
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static int removeAll(Connection connection, User user) throws SQLException {
    return DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("user_id = ?", user.getId()));
  }

  private static UserLogin buildRecord(ResultSet rs) {
    try {
      UserLogin record = new UserLogin();
      record.setId(rs.getLong("login_id"));
      record.setSource(rs.getString("source"));
      record.setUserId(rs.getLong("user_id"));
      record.setIpAddress(rs.getString("ip_address"));
      record.setUserAgent(rs.getString("user_agent"));
      record.setCreated(rs.getTimestamp("created"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
