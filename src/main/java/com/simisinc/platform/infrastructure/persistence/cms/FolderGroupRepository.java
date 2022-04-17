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

package com.simisinc.platform.infrastructure.persistence.cms;

import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.domain.model.cms.FolderGroup;
import com.simisinc.platform.domain.model.items.PrivacyType;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Properties for querying objects from the repository
 *
 * @author matt rajkowski
 * @created 12/12/18 2:02 PM
 */
public class FolderGroupRepository {

  private static Log LOG = LogFactory.getLog(FolderGroupRepository.class);

  private static String TABLE_NAME = "folder_groups";
  private static String PRIMARY_KEY[] = new String[]{"allowed_id"};

  public static List<FolderGroup> findAllByFolderId(long folderId) {
    if (folderId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("folder_id = ?", folderId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("allowed_id").setUseCount(false),
        FolderGroupRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<FolderGroup>) result.getRecords();
    }
    return null;
  }

  public static List<FolderGroup> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("allowed_id"),
        FolderGroupRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<FolderGroup>) result.getRecords();
    }
    return null;
  }

  public static FolderGroup add(FolderGroup record) {
    SqlUtils insertValues = new SqlUtils()
        .add("folder_id", record.getFolderId())
        .add("group_id", record.getGroupId())
        .add("privacy_type", record.getPrivacyType())
        .add("view_all", (record.getPrivacyType() == PrivacyType.PUBLIC || record.getPrivacyType() == PrivacyType.PUBLIC_READ_ONLY))
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

  public static void insertFolderGroupList(Connection connection, Folder folder) throws SQLException {
    if (folder.getFolderGroupList() == null) {
      return;
    }
    for (FolderGroup allowedGroup : folder.getFolderGroupList()) {
      SqlUtils insertValues = new SqlUtils();
      insertValues
          .add("folder_id", folder.getId())
          .add("group_id", allowedGroup.getGroupId())
          .add("privacy_type", allowedGroup.getPrivacyType())
          .add("view_all", (allowedGroup.getPrivacyType() == PrivacyType.PUBLIC || allowedGroup.getPrivacyType() == PrivacyType.PUBLIC_READ_ONLY))
          .add("add_permission", allowedGroup.getAddPermission())
          .add("edit_permission", allowedGroup.getEditPermission())
          .add("delete_permission", allowedGroup.getDeletePermission())
      ;
      DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY);
    }
  }

  public static void removeAll(Connection connection, Folder folder) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("folder_id = ?", folder.getId()));
  }

  public static void removeAll(Connection connection, Group group) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("group_id = ?", group.getId()));
  }

  private static FolderGroup buildRecord(ResultSet rs) {
    try {
      FolderGroup record = new FolderGroup();
      record.setId(rs.getLong("allowed_id"));
      record.setFolderId(rs.getLong("folder_id"));
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
