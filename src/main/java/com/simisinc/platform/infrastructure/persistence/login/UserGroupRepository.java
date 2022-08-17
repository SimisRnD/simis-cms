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

import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.UserGroup;
import com.simisinc.platform.infrastructure.database.*;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves user group objects
 *
 * @author matt rajkowski
 * @created 6/19/18 8:36 PM
 */
public class UserGroupRepository {

  private static Log LOG = LogFactory.getLog(UserGroupRepository.class);

  private static String TABLE_NAME = "user_groups";
  private static String[] PRIMARY_KEY = new String[]{"user_group_id"};

  public static List<UserGroup> findAllByUserId(long userId) {
    if (userId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("user_id = ?", userId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("user_group_id").setUseCount(false),
        UserGroupRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<UserGroup>) result.getRecords();
    }
    return null;
  }

  public static List<UserGroup> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("user_group_id"),
        UserGroupRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<UserGroup>) result.getRecords();
    }
    return null;
  }

  public static UserGroup add(UserGroup record) {
    SqlUtils insertValues = new SqlUtils()
        .add("user_id", record.getUserId())
        .add("group_id", record.getGroupId());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    // Update the group count
    GroupRepository.updateUserCount(record.getGroupId(), 1);
    return record;
  }

  public static long insertUserGroupList(Connection connection, User user) throws SQLException {
    if (user.getGroupList() == null) {
      return 0;
    }
    long count = 0;
    for (Group group : user.getGroupList()) {
      // Add to the group
      SqlUtils insertValues = new SqlUtils();
      insertValues
          .add("user_id", user.getId())
          .add("group_id", group.getId());
      DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY);
      // Update the group count
      GroupRepository.updateUserCount(connection, group.getId(), 1);
      ++count;
    }
    return count;
  }

  public static int removeAll(Connection connection, User user) throws SQLException {
    // For each group the user is in, adjust the group count
    GroupRepository.removeUserCount(connection, user);
    // Delete the records
    return DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("user_id = ?", user.getId()));
  }

  private static UserGroup buildRecord(ResultSet rs) {
    try {
      UserGroup record = new UserGroup();
      record.setId(rs.getLong("user_group_id"));
      record.setUserId(rs.getLong("user_id"));
      record.setGroupId(rs.getLong("group_id"));
      record.setCreated(rs.getTimestamp("created"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
