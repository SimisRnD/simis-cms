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

import com.simisinc.platform.application.SecretCryptoCommand;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.database.*;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderRepository;
import com.simisinc.platform.infrastructure.persistence.login.UnsuspendRequestRepository;
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
  private static String[] PRIMARY_KEY = new String[]{"user_id"};

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
      if (specification.getIsLocked() != DataConstants.UNDEFINED) {
        if (specification.getIsLocked() == DataConstants.TRUE) {
          where.add("locked_until IS NOT NULL AND locked_until > NOW()");
        } else {
          where.add("(locked_until IS NULL OR locked_until <= NOW())");
        }
      }
      if (specification.getIsMfaEnabled() != DataConstants.UNDEFINED) {
        where.add("mfa_enabled = ?", specification.getIsMfaEnabled() == DataConstants.TRUE);
      }
      if (specification.getPasswordOlderThanDays() > -1) {
        where.add("(last_password_changed_at IS NULL OR last_password_changed_at < NOW() - (? || ' days')::INTERVAL)",
            specification.getPasswordOlderThanDays());
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
    // Uses buildSummaryRecord rather than buildRecord: list-style queries (the /admin/users list,
    // the editorial-calendar author dropdown, the user lookup autocomplete) never read
    // User#getMfaSecret(), only the separate mfa_enabled boolean, so there is no reason to pay for
    // a SecretCryptoCommand.decrypt() call (and its ERROR log on a misconfigured CMS_SECRET_KEY)
    // on every MFA-enabled row of every list render. Single-record lookups (findByUserId,
    // findByUsername, etc., used by login/MFA-verification flows that do need the plaintext seed)
    // still go through buildRecord via DB.selectRecordFrom.
    return DB.selectAllFrom(
        TABLE_NAME, select, where, orderBy, constraints, UserRepository::buildSummaryRecord);
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
            .add("account_token = ?", token)
            .add("(account_token_expires IS NULL OR account_token_expires > NOW())"),
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

  public static long countLockedAccounts() {
    return DB.selectCountFrom(TABLE_NAME, new SqlUtils().add("locked_until > ?", new Timestamp(System.currentTimeMillis())));
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

  /** Uses the existing enabled-filter query rather than a bespoke SQL string (selectAllFrom always runs a real COUNT(*), independent of page size -- see DB.selectAllFrom). */
  public static long countEnabledAccounts() {
    UserSpecification specification = new UserSpecification();
    specification.setIsEnabled(true);
    DataConstraints constraints = new DataConstraints();
    constraints.setPageSize(1);
    findAll(specification, constraints);
    return constraints.getTotalRecordCount();
  }

  public static long countValidatedAccounts() {
    UserSpecification specification = new UserSpecification();
    specification.setIsVerified(true);
    DataConstraints constraints = new DataConstraints();
    constraints.setPageSize(1);
    findAll(specification, constraints);
    return constraints.getTotalRecordCount();
  }

  public static long countNewRegistrationsThisMonth() {
    long count = -1;
    String SQL_QUERY =
        "SELECT COUNT(user_id) AS user_count " +
            "FROM users " +
            "WHERE DATE_TRUNC('month', created) = DATE_TRUNC('month', NOW())";
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

  /**
   * Counts users holding at least one row in user_roles (admin/staff -- there is no "public" role
   * row to compare against; every privileged account has a role assignment, every public account
   * has none). DISTINCT so a user holding more than one role is not double-counted.
   */
  public static long countAccountsWithAnyRole() {
    long count = -1;
    String SQL_QUERY =
        "SELECT COUNT(DISTINCT user_id) AS user_count " +
            "FROM user_roles";
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

  /** Public accounts = everyone minus everyone with a role assignment (see countAccountsWithAnyRole). */
  public static long countPublicAccounts() {
    long total = countTotalUsers();
    long withRole = countAccountsWithAnyRole();
    if (total < 0 || withRole < 0) {
      return -1;
    }
    return total - withRole;
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
    if (record.hasGeoPoint()) {
      insertValues.add("latitude", record.getLatitude());
      insertValues.add("longitude", record.getLongitude());
      insertValues.addGeomPoint("geom", record.getLatitude(), record.getLongitude());
    }
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
    if (record.hasGeoPoint()) {
      updateValues.add("latitude", record.getLatitude());
      updateValues.add("longitude", record.getLongitude());
      updateValues.addGeomPoint("geom", record.getLatitude(), record.getLongitude());
    } else {
      updateValues.add("latitude", 0L, 0L);
      updateValues.add("longitude", 0L, 0L);
      updateValues.addGeomPoint("geom", 0, 0);
    }
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
        .add("last_password_changed_at", new Timestamp(System.currentTimeMillis()))
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("user_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      // Invalidate user_tokens since there is a new password
      UserTokenRepository.removeAll(record.getId());
      // Invalidate the cached plaintext credentials keyed by user id. AuthenticateLoginCommand
      // caches "username:password" on a successful login and returns on a cache hit WITHOUT
      // re-verifying the stored hash; without this eviction the OLD password keeps authenticating
      // after a change/reset (the cache is expireAfterAccess, so use keeps it alive) until the
      // user next logs in with the new one. Mirrors the token invalidation above. invalidateKey
      // is null-safe if the cache manager has not been started.
      CacheManager.invalidateKey(CacheManager.USER_CREDENTIALS_CACHE, record.getId());
      return record;
    }
    LOG.error("updatePassword failed!");
    return null;
  }

  /**
   * Persists the account-lockout state after a login attempt (#295): the consecutive failed-attempt
   * counter and the lockout expiry (null = not locked). The login flow decides the values.
   */
  public static void updateLockoutState(long userId, int failedAttemptCount, Timestamp lockedUntil) {
    SqlUtils updateValues = new SqlUtils()
        .add("failed_attempt_count", failedAttemptCount)
        .add("locked_until", lockedUntil);
    SqlUtils where = new SqlUtils()
        .add("user_id = ?", userId);
    if (!DB.update(TABLE_NAME, updateValues, where)) {
      LOG.error("updateLockoutState failed for user id: " + userId);
    }
  }

  /**
   * Clears the lockout -- resets the failed-attempt count to 0 and removes any lock. Used on a
   * successful login and when an administrator unlocks the account (#295).
   */
  public static void resetLockout(long userId) {
    updateLockoutState(userId, 0, null);
  }

  private static final int DEFAULT_PASSWORD_MAX_AGE_DAYS = 90;

  /**
   * Parses the configurable password.maxAgeDays site property, falling back to the default on a
   * blank or unparseable value -- mirrors AuditLogRepository.resolveRetentionDays's shape (#492).
   */
  public static int resolvePasswordMaxAgeDays(String value) {
    if (StringUtils.isBlank(value)) {
      return DEFAULT_PASSWORD_MAX_AGE_DAYS;
    }
    try {
      int days = Integer.parseInt(value.trim());
      return days > 0 ? days : DEFAULT_PASSWORD_MAX_AGE_DAYS;
    } catch (NumberFormatException e) {
      return DEFAULT_PASSWORD_MAX_AGE_DAYS;
    }
  }

  public static User createAccountToken(User record) {
    String newToken = UUID.randomUUID().toString();
    Timestamp expires = new Timestamp(System.currentTimeMillis() + 86_400_000L); // 24 hours
    SqlUtils updateValues = new SqlUtils()
        .add("account_token", newToken)
        .add("account_token_expires", expires)
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("user_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      record.setAccountToken(newToken);
      record.setAccountTokenExpires(expires);
      return record;
    }
    LOG.error("createAccountToken failed!");
    return null;
  }

  public static User suspendAccount(User record, String reason) {
    SqlUtils updateValues = new SqlUtils()
        .add("enabled", false)
        .add("suspension_reason", reason, 255)
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("user_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      // A fresh suspension moots any in-flight unsuspend-approval request/decision for this
      // account (issue #492 Phase 3) -- a later unsuspend starts a brand-new governance cycle.
      UnsuspendRequestRepository.supersedePendingForTarget(record.getId(), "Account was suspended again");
      return record;
    }
    LOG.error("suspendAccount failed!");
    return null;
  }

  public static User restoreAccount(User record) {
    SqlUtils updateValues = new SqlUtils()
        .add("enabled", true)
        .add("suspension_reason", (String) null)
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("user_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("restoreAccount failed!");
    return null;
  }

  public static User saveMfaSecret(User record, String secret) {
    SqlUtils updateValues = new SqlUtils()
        // Store the TOTP seed encrypted at rest (a DB dump must not yield working second factors);
        // the in-memory record keeps the plaintext for immediate use.
        .add("mfa_secret", SecretCryptoCommand.encrypt(secret))
        .add("mfa_enabled", false)
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("user_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      record.setMfaSecret(secret);
      record.setMfaEnabled(false);
      return record;
    }
    LOG.error("saveMfaSecret failed!");
    return null;
  }

  public static User enableMfa(User record) {
    SqlUtils updateValues = new SqlUtils()
        .add("mfa_enabled", true)
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("user_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      record.setMfaEnabled(true);
      return record;
    }
    LOG.error("enableMfa failed!");
    return null;
  }

  public static User disableMfa(User record) {
    SqlUtils updateValues = new SqlUtils()
        .add("mfa_secret", (String) null)
        .add("mfa_enabled", false)
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("user_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      record.setMfaSecret(null);
      record.setMfaEnabled(false);
      return record;
    }
    LOG.error("disableMfa failed!");
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

  /**
   * The full row mapper, used by single-record lookups (by id, username, email, account token,
   * etc.) where a caller may legitimately need the plaintext TOTP seed -- e.g. login/MFA
   * verification. Decrypts mfa_secret on every call.
   */
  private static User buildRecord(ResultSet rs) {
    try {
      User record = buildSummaryFields(rs);
      // Decrypt the at-rest TOTP seed so callers always see plaintext (legacy plaintext passes through unchanged)
      record.setMfaSecret(SecretCryptoCommand.decrypt(rs.getString("mfa_secret")));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }

  /**
   * The row mapper used by list-style/multi-record queries (query(), i.e. findAll() and its
   * callers such as the /admin/users list, the editorial-calendar author dropdown, and the user
   * lookup autocomplete). None of those read User#getMfaSecret() -- only the separate mfa_enabled
   * boolean -- so this skips the SecretCryptoCommand.decrypt() call that buildRecord() pays on
   * every MFA-enabled row. mfaSecret is left null on the returned records.
   */
  private static User buildSummaryRecord(ResultSet rs) {
    try {
      return buildSummaryFields(rs);
    } catch (SQLException se) {
      LOG.error("buildSummaryRecord", se);
      return null;
    }
  }

  /** Populates every User field shared by buildRecord() and buildSummaryRecord() except mfaSecret. */
  private static User buildSummaryFields(ResultSet rs) throws SQLException {
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
    record.setAccountTokenExpires(rs.getTimestamp("account_token_expires"));
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
    record.setLatitude(rs.getDouble("latitude"));
    record.setLongitude(rs.getDouble("longitude"));
    record.setMfaEnabled(rs.getBoolean("mfa_enabled"));
    record.setFailedAttemptCount(rs.getInt("failed_attempt_count"));
    record.setLockedUntil(rs.getTimestamp("locked_until"));
    record.setLastPasswordChangedAt(rs.getTimestamp("last_password_changed_at"));
    record.setSuspensionReason(rs.getString("suspension_reason"));
    return record;
  }
}
