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

import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.User;
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
import java.util.List;

/**
 * Persists and retrieves group objects
 *
 * @author matt rajkowski
 * @created 4/24/18 8:40 AM
 */
public class GroupRepository {

  private static Log LOG = LogFactory.getLog(GroupRepository.class);

  private static String TABLE_NAME = "groups";
  private static String PRIMARY_KEY[] = new String[]{"group_id"};

  public static Group findById(long id) {
    if (id == -1) {
      return null;
    }
    return (Group) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("group_id = ?", id),
        GroupRepository::buildRecord);
  }

  public static Group findByUniqueId(String uniqueId) {
    if (StringUtils.isBlank(uniqueId)) {
      return null;
    }
    return (Group) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("unique_id = ?", uniqueId),
        GroupRepository::buildRecord);
  }

  public static Group findByOAuthPath(String oAuthPath) {
    if (StringUtils.isBlank(oAuthPath)) {
      return null;
    }
    return (Group) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("oauth_path = ?", oAuthPath),
        GroupRepository::buildRecord);
  }

  public static Group findByName(String name) {
    if (StringUtils.isBlank(name)) {
      return null;
    }
    return (Group) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("LOWER(name) = ?", name.toLowerCase().trim()),
        GroupRepository::buildRecord);
  }

  public static List<Group> findAllByUserId(long userId) {
    if (userId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("EXISTS (SELECT 1 FROM user_groups WHERE group_id = groups.group_id AND user_id = ?)", userId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("group_id").setUseCount(false),
        GroupRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<Group>) result.getRecords();
    }
    return null;
  }

  public static List<Group> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("name"),
        GroupRepository::buildRecord);
    return (List<Group>) result.getRecords();
  }

  public static Group save(Group record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  private static Group add(Group record) {
    SqlUtils insertValues = new SqlUtils()
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("description", StringUtils.trimToNull(record.getDescription()));
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  private static Group update(Group record) {
    SqlUtils updateValues = new SqlUtils()
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("description", StringUtils.trimToNull(record.getDescription()));
    SqlUtils where = new SqlUtils()
        .add("group_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static void remove(Group record) {
    DB.deleteFrom(TABLE_NAME, new SqlUtils().add("group_id = ?", record.getId()));
  }

  private static PreparedStatement createPreparedStatementForUserCount(Connection connection, long groupId, int value) throws SQLException {
    String SQL_QUERY =
        "UPDATE groups " +
            "SET user_count = user_count + ? " +
            "WHERE group_id = ?";
    int i = 0;
    PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
    pst.setInt(++i, value);
    pst.setLong(++i, groupId);
    return pst;
  }

  public static boolean updateUserCount(long groupId, int value) {
    try {
      // Adjust the count
      try (Connection connection = DB.getConnection()) {
        return updateUserCount(connection, groupId, value);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("The update failed!");
    return false;

  }

  public static boolean updateUserCount(Connection connection, long groupId, int value) {
    try {
      // Adjust the count
      try (PreparedStatement pst = createPreparedStatementForUserCount(connection, groupId, value)) {
        return pst.execute();
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("The update failed!");
    return false;
  }

  private static PreparedStatement createPreparedStatementForRemoveUserCount(Connection connection, long userId) throws SQLException {
    String SQL_QUERY =
        "UPDATE groups " +
            "SET user_count = user_count - 1 " +
            "WHERE EXISTS (SELECT 1 FROM user_groups WHERE group_id = groups.group_id AND user_id = ?)";
    int i = 0;
    PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
    pst.setLong(++i, userId);
    return pst;
  }

  public static boolean removeUserCount(Connection connection, User user) {
    try {
      // Adjust the count
      try (PreparedStatement pst = createPreparedStatementForRemoveUserCount(connection, user.getId())) {
        return pst.execute();
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("The update failed!");
    return false;
  }

  private static Group buildRecord(ResultSet rs) {
    try {
      Group record = new Group();
      record.setId(rs.getLong("group_id"));
      record.setName(rs.getString("name"));
      record.setDescription(rs.getString("description"));
      record.setUserCount(rs.getLong("user_count"));
      record.setUniqueId(rs.getString("unique_id"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
