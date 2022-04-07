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

import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.CollectionGroup;
import com.simisinc.platform.domain.model.items.PrivacyType;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/19/18 9:29 AM
 */
public class CollectionGroupRepository {

  private static Log LOG = LogFactory.getLog(CollectionGroupRepository.class);

  private static String TABLE_NAME = "collection_groups";
  private static String PRIMARY_KEY[] = new String[]{"allowed_id"};

  public static List<CollectionGroup> findAllByCollectionId(long collectionId) {
    if (collectionId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("collection_id = ?", collectionId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("allowed_id").setUseCount(false),
        CollectionGroupRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<CollectionGroup>) result.getRecords();
    }
    return null;
  }

  public static List<CollectionGroup> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("allowed_id"),
        CollectionGroupRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<CollectionGroup>) result.getRecords();
    }
    return null;
  }

  public static CollectionGroup add(CollectionGroup record) {
    SqlUtils insertValues = new SqlUtils()
        .add("collection_id", record.getCollectionId())
        .add("group_id", record.getGroupId())
        .add("privacy_type", record.getPrivacyType())
        .add("view_all", record.getPrivacyType() != PrivacyType.PRIVATE)
        .add("add_permission", record.getAddPermission())
        .add("edit_permission", record.getEditPermission())
        .add("delete_permission", record.getDeletePermission());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static void insertCollectionGroupList(Connection connection, Collection collection) throws SQLException {
    if (collection.getCollectionGroupList() == null) {
      return;
    }
    for (CollectionGroup allowedGroup : collection.getCollectionGroupList()) {
      SqlUtils insertValues = new SqlUtils();
      insertValues
          .add("collection_id", collection.getId())
          .add("group_id", allowedGroup.getGroupId())
          .add("privacy_type", allowedGroup.getPrivacyType())
          .add("view_all", allowedGroup.getPrivacyType() != PrivacyType.PRIVATE)
          .add("add_permission", allowedGroup.getAddPermission())
          .add("edit_permission", allowedGroup.getEditPermission())
          .add("delete_permission", allowedGroup.getDeletePermission())
      ;
      DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY);
    }
  }

  public static void removeAll(Connection connection, Collection collection) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("collection_id = ?", collection.getId()));
  }

  public static void removeAll(Connection connection, Group group) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("group_id = ?", group.getId()));
  }

  private static CollectionGroup buildRecord(ResultSet rs) {
    try {
      CollectionGroup record = new CollectionGroup();
      record.setId(rs.getLong("allowed_id"));
      record.setCollectionId(rs.getLong("collection_id"));
      record.setGroupId(rs.getLong("group_id"));
      record.setPrivacyType(rs.getInt("privacy_type"));
      record.setAddPermission(rs.getBoolean("add_permission"));
      record.setEditPermission(rs.getBoolean("edit_permission"));
      record.setDeletePermission(rs.getBoolean("delete_permission"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
