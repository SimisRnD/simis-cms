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

import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.infrastructure.database.*;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserGroupRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserLoginRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserRoleRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserTokenRepository;
import com.simisinc.platform.presentation.controller.DataConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persists and retrieves user objects
 *
 * @author matt rajkowski
 * @created 4/8/18 4:33 PM
 */
public class UserRepository {

  private static Log LOG = LogFactory.getLog(UserRepository.class);

  private static String TABLE_NAME = "users";
  private static String PRIMARY_KEY[] = new String[]{"user_id"};

  private static DataResult query(UserSpecification specification, DataConstraints constraints) {
    SqlUtils select = new SqlUtils();
    SqlUtils where = new SqlUtils();
    SqlUtils orderBy = new SqlUtils();
    if (specification != null) {
      where.addIfExists("user_id = ?", specification.getId(), -1);
      if (specification.getRoleId() > -1) {
        where.add("EXISTS (SELECT 1 FROM user_roles WHERE user_id = users.user_id AND role_id = ?)", specification.getRoleId());
      }
      if (specification.getGroupId() > -1) {
        where.add("EXISTS (SELECT 1 FROM user_groups WHERE user_id = users.user_id AND group_id = ?)", specification.getGroupId());
      }
      if (specification.getIsEnabled() != DataConstants.UNDEFINED) {
        where.add("enabled = ?", specification.getIsEnabled() == DataConstants.TRUE);
      }
      if (specification.getIsVerified() != DataConstants.UNDEFINED) {
        if (specification.getIsVerified() == DataConstants.TRUE) {
          where.add("validated IS NOT NULL");
        } else {
          where.add("validated IS NULL");
        }
      }
      if (specification.getMatchesName() != null) {
        if (specification.getMatchesName().contains("@")) {
          // Exact match on an email
          where.add("LOWER(email) = LOWER(?)", specification.getMatchesName().trim());
        } else {
          // Like matching on a name
          String likeValue = specification.getMatchesName().trim()
              .replace("!", "!!")
              .replace("%", "!%")
              .replace("_", "!_")
              .replace("[", "![");
          where.add("LOWER(concat_ws(' ', first_name, last_name, nickname)) LIKE LOWER(?) ESCAPE '!'", "%" + likeValue + "%");
        }
      }
    }
    return DB.selectAllFrom(
        TABLE_NAME, select, where, orderBy, constraints, UserRepository::buildRecord);
  }

  public static User findByUniqueId(String uniqueId) {
    if (StringUtils.isBlank(uniqueId)) {
      return null;
    }
    return (User) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("unique_id = ?", uniqueId),
        UserRepository::buildRecord);
  }

  public static User findByUsername(String username) {
    if (StringUtils.isBlank(username)) {
      return null;
    }
    return (User) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("LOWER(username) = ?", username.toLowerCase()),
        UserRepository::buildRecord);
  }

  public static User findByUserId(long userId) {
    if (userId == -1) {
      return null;
    }
    return (User) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("user_id = ?", userId),
        UserRepository::buildRecord);
  }

  public static User findByAccountToken(String token) {
    if (StringUtils.isBlank(token)) {
      return null;
    }
    return (User) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("account_token = ?", token),
        UserRepository::buildRecord);
  }

  public static User findByEmailAddress(String email) {
    if (StringUtils.isBlank(email)) {
      return null;
    }
    return (User) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("LOWER(email) = ?", email.toLowerCase()),
        UserRepository::buildRecord);
  }

  public static List<User> findAllByRole(Role role) {
    UserSpecification specification = new UserSpecification();
    specification.setRoleId(role.getId());
    specification.setIsEnabled(true);
    return findAll(specification, null);
  }

  public static List<User> findAll(UserSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("user_id desc");
    DataResult result = query(specification, constraints);
    return (List<User>) result.getRecords();
  }

  public static List<StatisticsData> findMonthlyUserRegistrations(int monthsLimit) {
    String SQL_QUERY =
        "SELECT DATE_TRUNC('month', month)::VARCHAR(10) AS date_column, COUNT(user_id) AS monthly_count " +
            "FROM (SELECT generate_series(NOW() - INTERVAL '" + monthsLimit + " months', NOW(), INTERVAL '1 month')::date) d(month) " +
            "LEFT JOIN users ON DATE_TRUNC('month', created) = DATE_TRUNC('month', month) " +
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

  public static List<StatisticsData> findDailyUserRegistrations(int daysToLimit) {
    String SQL_QUERY =
        "SELECT DATE_TRUNC('day', day)::VARCHAR(10) AS date_column, COUNT(user_id) AS daily_count " +
            "FROM (SELECT generate_series(NOW() - INTERVAL '" + daysToLimit + " days', NOW(), INTERVAL '1 day')::date) d(day) " +
            "LEFT JOIN users ON DATE_TRUNC('day', created) = DATE_TRUNC('day', day) " +
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

  public static long countTotalUsers() {
    long count = -1;
    String SQL_QUERY =
        "SELECT COUNT(user_id) AS user_count " +
            "FROM users ";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        count = rs.getLong("user_count");
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return count;
  }

  public static User save(User record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static User add(User record) {
    record.setAccountToken(UUID.randomUUID().toString());
    if (record.getEmail() != null) {
      record.setEmail(record.getEmail().trim().toLowerCase());
    }
    if (record.getUsername() != null) {
      record.setUsername(record.getUsername().trim().toLowerCase());
    }
    SqlUtils insertValues = new SqlUtils()
        .add("unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("first_name", StringUtils.trimToNull(record.getFirstName()))
        .add("last_name", StringUtils.trimToNull(record.getLastName()))
        .add("organization", StringUtils.trimToNull(record.getOrganization()))
        .add("nickname", StringUtils.trimToNull(record.getNickname()))
        .add("email", StringUtils.trimToNull(record.getEmail()))
        .add("username", StringUtils.trimToNull(record.getUsername()))
        .add("title", StringUtils.trimToNull(record.getTitle()))
        .add("department", StringUtils.trimToNull(record.getDepartment()))
        .add("timezone", StringUtils.trimToNull(record.getTimeZone()))
        .add("city", StringUtils.trimToNull(record.getCity()))
        .add("state", StringUtils.trimToNull(record.getState()))
        .add("country", StringUtils.trimToNull(record.getCountry()))
        .add("postal_code", StringUtils.trimToNull(record.getPostalCode()))
        .add("password", record.getPassword())
        .add("enabled", true)
        .add("account_token", record.getAccountToken())
        .addIfExists("created", record.getCreated())
        .add("created_by", record.getCreatedBy(), -1);
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
        // Manage the access groups
        UserGroupRepository.insertUserGroupList(connection, record);
        // Manage the roles
        UserRoleRepository.insertUserRoleList(connection, record);
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

  private static User update(User record) {
    if (record.getEmail() != null) {
      record.setEmail(record.getEmail().trim().toLowerCase());
    }
    if (record.getUsername() != null) {
      record.setUsername(record.getUsername().trim().toLowerCase());
    }
    SqlUtils updateValues = new SqlUtils()
        .add("unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("first_name", StringUtils.trimToNull(record.getFirstName()))
        .add("last_name", StringUtils.trimToNull(record.getLastName()))
        .add("organization", StringUtils.trimToNull(record.getOrganization()))
        .add("nickname", StringUtils.trimToNull(record.getNickname()))
        .add("email", StringUtils.trimToNull(record.getEmail()))
        .add("username", StringUtils.trimToNull(record.getUsername()))
        .add("title", StringUtils.trimToNull(record.getTitle()))
        .add("department", StringUtils.trimToNull(record.getDepartment()))
        .add("timezone", StringUtils.trimToNull(record.getTimeZone()))
        .add("city", StringUtils.trimToNull(record.getCity()))
        .add("state", StringUtils.trimToNull(record.getState()))
        .add("country", StringUtils.trimToNull(record.getCountry()))
        .add("postal_code", StringUtils.trimToNull(record.getPostalCode()))
        .add("modified_by", record.getModifiedBy(), -1)
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("user_id = ?", record.getId());
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        DB.update(connection, TABLE_NAME, updateValues, where);
        // Manage the access groups
        UserGroupRepository.removeAll(connection, record);
        UserGroupRepository.insertUserGroupList(connection, record);
        // Manage the roles
        UserRoleRepository.removeAll(connection, record);
        UserRoleRepository.insertUserRoleList(connection, record);
        // Finish the transaction
        transaction.commit();
        return record;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage(), se);
    }
    return null;
  }

  public static User updateValidated(User record) {
    Timestamp occurred = new Timestamp(System.currentTimeMillis());
    SqlUtils updateValues = new SqlUtils()
        .add("validated", occurred)
        .add("account_token", (String) null)
        .add("modified", occurred);
    SqlUtils where = new SqlUtils()
        .add("user_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      // Updated related records
      OrderRepository.updateUserOrders(record);
      return record;
    }
    LOG.error("updateValidated failed!");
    return null;
  }

  public static User updatePassword(User record) {
    SqlUtils updateValues = new SqlUtils()
        .add("password", record.getPassword())
        .add("account_token", (String) null)
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("user_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("updatePassword failed!");
    return null;
  }

  public static User createAccountToken(User record) {
    String newToken = UUID.randomUUID().toString();
    SqlUtils updateValues = new SqlUtils()
        .add("account_token", newToken)
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("user_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      record.setAccountToken(newToken);
      return record;
    }
    LOG.error("createAccountToken failed!");
    return null;
  }

  public static User suspendAccount(User record) {
    SqlUtils updateValues = new SqlUtils()
        .add("enabled", false)
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("user_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("suspendAccount failed!");
    return null;
  }

  public static User restoreAccount(User record) {
    SqlUtils updateValues = new SqlUtils()
        .add("enabled", true)
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("user_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("restoreAccount failed!");
    return null;
  }

  // Remove
  public static boolean remove(User record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        // @note the User is currently not cleaned up from all tables
        // until a business decision is made
        UserGroupRepository.removeAll(connection, record);
        UserRoleRepository.removeAll(connection, record);
        UserTokenRepository.removeAll(connection, record);
        UserLoginRepository.removeAll(connection, record);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("user_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("The delete failed!");
    return false;
  }

  private static User buildRecord(ResultSet rs) {
    try {
      User record = new User();
      record.setId(rs.getLong("user_id"));
      record.setUniqueId(rs.getString("unique_id"));
      record.setFirstName(rs.getString("first_name"));
      record.setLastName(rs.getString("last_name"));
      record.setOrganization(rs.getString("organization"));
      record.setNickname(rs.getString("nickname"));
      record.setEmail(rs.getString("email"));
      record.setUsername(rs.getString("username"));
      record.setPassword(rs.getString("password"));
      record.setEnabled(rs.getBoolean("enabled"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModified(rs.getTimestamp("modified"));
      record.setAccountToken(rs.getString("account_token"));
      record.setValidated(rs.getTimestamp("validated"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setTitle(rs.getString("title"));
      record.setDepartment(rs.getString("department"));
      record.setTimeZone(rs.getString("timezone"));
      record.setCity(rs.getString("city"));
      record.setState(rs.getString("state"));
      record.setCountry(rs.getString("country"));
      record.setPostalCode(rs.getString("postal_code"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
