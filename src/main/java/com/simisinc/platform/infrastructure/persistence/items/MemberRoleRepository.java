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

package com.simisinc.platform.infrastructure.persistence.items;

import com.simisinc.platform.domain.model.items.CollectionRole;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.Member;
import com.simisinc.platform.domain.model.items.MemberRole;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Properties for querying objects from the member role repository
 *
 * @author matt rajkowski
 * @created 8/24/18 9:53 AM
 */
public class MemberRoleRepository {

  private static Log LOG = LogFactory.getLog(MemberRoleRepository.class);

  private static String TABLE_NAME = "member_roles";
  private static String[] PRIMARY_KEY = new String[]{"member_role_id"};

  public static List<MemberRole> findAllByUserIdAndItemId(long userId, long itemId) {
    if (userId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("user_id = ?", userId)
        .add("item_id = ?", itemId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("member_role_id"),
        MemberRoleRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<MemberRole>) result.getRecords();
    }
    return null;
  }

  public static List<MemberRole> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("member_role_id"),
        MemberRoleRepository::buildRecord);
    return (List<MemberRole>) result.getRecords();
  }

  public static MemberRole add(MemberRole record) {
    SqlUtils insertValues = new SqlUtils()
        .add("member_id", record.getId())
        .add("role_id", record.getItemRoleId())
        .add("item_id", record.getItemId())
        .add("user_id", record.getUserId())
        .add("created_by", record.getCreatedBy());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static long insertMemberRoleList(Connection connection, Member member) throws SQLException {
    if (member.getRoleList() == null) {
      return 0;
    }
    long count = 0;
    for (CollectionRole collectionRole : member.getRoleList()) {
      SqlUtils insertValues = new SqlUtils();
      insertValues
          .add("member_id", member.getId())
          .add("role_id", collectionRole.getId())
          .add("item_id", member.getItemId())
          .add("user_id", member.getUserId())
          .add("created_by", member.getCreatedBy());
      DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY);
      ++count;
    }
    return count;
  }

  public static int removeAll(Connection connection, Member member) throws SQLException {
    // Delete the records
    return DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("member_id = ?", member.getId()));
  }

  public static void removeAll(Connection connection, Item item) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("item_id = ?", item.getId()));
  }

  private static MemberRole buildRecord(ResultSet rs) {
    try {
      MemberRole record = new MemberRole();
      record.setId(rs.getLong("member_role_id"));
      record.setMemberId(rs.getLong("member_id"));
      record.setItemRoleId(rs.getLong("role_id"));
      record.setItemId(rs.getLong("item_id"));
      record.setUserId(rs.getLong("user_id"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
