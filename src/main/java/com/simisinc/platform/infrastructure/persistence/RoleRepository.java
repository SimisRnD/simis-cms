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
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists and retrieves role objects
 *
 * @author matt rajkowski
 * @created 4/9/18 11:44 AM
 */
public class RoleRepository {

  private static Log LOG = LogFactory.getLog(RoleRepository.class);

  private static String TABLE_NAME = "lookup_role";

  public static Role findByCode(String code) {
    if (StringUtils.isBlank(code)) {
      return null;
    }
    return (Role) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("code = ?", code),
        RoleRepository::buildRecord);
  }

  public static Role findByOAuthPath(String oAuthPath) {
    if (StringUtils.isBlank(oAuthPath)) {
      return null;
    }
    return (Role) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("oauth_path = ?", oAuthPath),
        RoleRepository::buildRecord);
  }

  public static Role findById(int id) {
    if (id == -1) {
      return null;
    }
    return (Role) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("role_id = ?", id),
        RoleRepository::buildRecord);
  }

  public static List<Role> findAllByUserId(long userId) {
    if (userId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("EXISTS (SELECT 1 FROM user_roles WHERE role_id = lookup_role.role_id AND user_id = ?)", userId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("role_id").setUseCount(false),
        RoleRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<Role>) result.getRecords();
    }
    return null;
  }

  /**
   * Batch form of {@link #findAllByUserId} for a widget rendering many users on one page (e.g.
   * /admin/users) -- one query instead of one-per-row. Modeled on the IN-list pattern in
   * {@code ImageVariantRepository#findByImageIds}, but plain JDBC rather than {@code DB.selectAllFrom}:
   * the roles returned here come from a join against {@code user_roles}, and {@link #buildRecord}
   * (reused as-is) maps a {@code lookup_role} row to a {@link Role} that has no {@code userId} field
   * of its own, so the owning user id has to be read off the result set separately, per row, to know
   * which map entry each role belongs to.
   */
  public static Map<Long, List<Role>> findAllByUserIds(Collection<Long> userIds) {
    Map<Long, List<Role>> roleListByUserId = new LinkedHashMap<>();
    if (userIds == null || userIds.isEmpty()) {
      return roleListByUserId;
    }
    StringBuilder placeholders = new StringBuilder();
    for (int i = 0; i < userIds.size(); i++) {
      if (i > 0) {
        placeholders.append(",");
      }
      placeholders.append("?");
    }
    String sql = "SELECT lookup_role.role_id, lookup_role.level, lookup_role.code, lookup_role.title, "
        + "user_roles.user_id AS ur_user_id "
        + "FROM lookup_role "
        + "JOIN user_roles ON user_roles.role_id = lookup_role.role_id "
        + "WHERE user_roles.user_id IN (" + placeholders + ") "
        + "ORDER BY user_roles.user_id, lookup_role.role_id";
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(sql)) {
      int fieldIdx = 1;
      for (Long userId : userIds) {
        pst.setLong(fieldIdx++, userId);
      }
      try (ResultSet rs = pst.executeQuery()) {
        while (rs.next()) {
          Role role = buildRecord(rs);
          long userId = rs.getLong("ur_user_id");
          roleListByUserId.computeIfAbsent(userId, k -> new ArrayList<>()).add(role);
        }
      }
    } catch (SQLException se) {
      LOG.error("findAllByUserIds SQLException: " + se.getMessage());
    }
    return roleListByUserId;
  }

  public static List<Role> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("level"),
        RoleRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<Role>) result.getRecords();
    }
    return null;
  }

  /**
   * Roles with at least one MFA-enrolled member -- used by the MFA Enforcement Settings page so
   * an admin can see, before turning enforcement on for a role, whether that role already has
   * anyone enrolled (the page's own help text warns that enabling enforcement for a role with no
   * enrolled member locks out every member of it). A role with SOME but not all members enrolled
   * still appears here; this answers "has anyone", not "has everyone".
   */
  public static List<Role> findAllWithMfaEnrolledMember() {
    SqlUtils where = new SqlUtils()
        .add("EXISTS (SELECT 1 FROM user_roles ur JOIN users u ON u.user_id = ur.user_id "
            + "WHERE ur.role_id = lookup_role.role_id AND u.mfa_enabled = true)");
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("level").setUseCount(false),
        RoleRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<Role>) result.getRecords();
    }
    return null;
  }

  /**
   * Build the record from the database
   *
   * @param rs
   * @return
   * @throws SQLException
   */
  private static Role buildRecord(ResultSet rs) {
    try {
      Role record = new Role();
      record.setId(rs.getInt("role_id"));
      record.setLevel(rs.getInt("level"));
      record.setCode(rs.getString("code"));
      record.setTitle(rs.getString("title"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
