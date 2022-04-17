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

import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.domain.model.cms.FolderCategory;
import com.simisinc.platform.domain.model.cms.FolderGroup;
import com.simisinc.platform.domain.model.items.PrivacyType;
import com.simisinc.platform.infrastructure.database.*;
import com.simisinc.platform.presentation.controller.DataConstants;
import com.simisinc.platform.presentation.controller.login.UserSession;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.*;
import java.util.List;

/**
 * Persists and retrieves folder objects
 *
 * @author matt rajkowski
 * @created 4/18/18 10:15 PM
 */
public class FolderRepository {

  private static Log LOG = LogFactory.getLog(FolderRepository.class);

  private static String TABLE_NAME = "folders";
  private static String PRIMARY_KEY[] = new String[]{"folder_id"};

  public static Folder save(Folder record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  private static Folder add(Folder record) {
    SqlUtils insertValues = new SqlUtils()
        .add("folder_unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("summary", StringUtils.trimToNull(record.getSummary()))
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy())
        .add("allows_guests", record.getGuestPrivacyType() != PrivacyType.UNDEFINED)
        .add("guest_privacy_type", record.getGuestPrivacyType());
    if (record.getPrivacyTypes() != null) {
      insertValues.add("privacy_types", String.join(", ", record.getPrivacyTypes()));
    }
    insertValues.add("has_allowed_groups", record.getFolderGroupList() != null && !record.getFolderGroupList().isEmpty());
    insertValues.add("has_categories", record.getFolderCategoryList() != null && !record.getFolderCategoryList().isEmpty());
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
        // Manage the access groups
        if (record.getFolderGroupList() != null && !record.getFolderGroupList().isEmpty()) {
          FolderGroupRepository.insertFolderGroupList(connection, record);
        }
        if (record.getFolderCategoryList() != null && !record.getFolderCategoryList().isEmpty()) {
          FolderCategoryRepository.insertFolderCategoryList(connection, record);
        }
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

  private static Folder update(Folder record) {
    SqlUtils updateValues = new SqlUtils()
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("folder_unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("summary", StringUtils.trimToNull(record.getSummary()))
        .add("allows_guests", record.getGuestPrivacyType() != PrivacyType.UNDEFINED)
        .add("guest_privacy_type", record.getGuestPrivacyType())
        .add("modified_by", record.getModifiedBy())
        .add("modified", new Timestamp(System.currentTimeMillis()));
    if (record.getPrivacyTypes() != null) {
      updateValues.add("privacy_types", String.join(", ", record.getPrivacyTypes()));
    } else {
      updateValues.add("privacy_types", (String) null);
    }
    updateValues.add("has_allowed_groups", record.getFolderGroupList() != null && !record.getFolderGroupList().isEmpty());
    updateValues.add("has_categories", record.getFolderCategoryList() != null && !record.getFolderCategoryList().isEmpty());
    SqlUtils where = new SqlUtils().add("folder_id = ?", record.getId());
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        DB.update(connection, TABLE_NAME, updateValues, where);
        // Manage the access groups
        FolderGroupRepository.removeAll(connection, record);
        FolderGroupRepository.insertFolderGroupList(connection, record);
        // Manage categories
        FolderCategoryRepository.updateFolderCategoryList(connection, record);
        // Finish the transaction
        transaction.commit();
        // Expire the cache
//        CacheManager.invalidateKey(CacheManager.COLLECTION_UNIQUE_ID_CACHE, record.getUniqueId());
        return record;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage(), se);
    }
    return null;
  }

  // Remove
  public static boolean remove(Folder record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        FileVersionRepository.removeAll(connection, record);
        FileItemRepository.removeAll(connection, record);
        SubFolderRepository.removeAll(connection, record);
        FolderGroupRepository.removeAll(connection, record);
        FolderCategoryRepository.removeAll(connection, record);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("folder_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        // Invalidate the cache
//        CacheManager.invalidateKey(CacheManager.COLLECTION_UNIQUE_ID_CACHE, record.getUniqueId());
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("The delete failed!");
    return false;
  }

  private static DataResult query(FolderSpecification specification, DataConstraints constraints) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("folder_id = ?", specification.getId(), -1)
          .addIfExists("folder_unique_id = ?", specification.getUniqueId());
      if (specification.getName() != null) {
        where.add("LOWER(name) = ?", specification.getName().toLowerCase());
      }
      if (specification.getForUserId() != DataConstants.UNDEFINED) {
        if (specification.getForUserId() == UserSession.GUEST_ID) {
          where.add("allows_guests = true");
        } else {
          // For logged out and logged in users
          where.add(
              "(allows_guests = true " +
                  "OR (has_allowed_groups = true " +
                  "AND EXISTS (SELECT 1 FROM folder_groups WHERE folder_id = folders.folder_id " +
                  "AND EXISTS (SELECT 1 FROM user_groups WHERE group_id = folder_groups.group_id AND user_id = ?))))",
              specification.getForUserId());
        }
      }
    }
    return DB.selectAllFrom(TABLE_NAME, where, constraints, FolderRepository::buildRecord);
  }

  public static Folder findById(long id) {
    if (id == -1) {
      return null;
    }
    Folder folder = (Folder) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("folder_id = ?", id),
        FolderRepository::buildRecord);
    populateRelatedData(folder);
    return folder;
  }

  public static Folder findByUniqueId(String uniqueId) {
    if (StringUtils.isBlank(uniqueId)) {
      return null;
    }
    Folder folder = (Folder) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("folder_unique_id = ?", uniqueId),
        FolderRepository::buildRecord);
    populateRelatedData(folder);
    return folder;
  }

  public static Folder findByName(String name) {
    if (StringUtils.isBlank(name)) {
      return null;
    }
    Folder folder = (Folder) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("LOWER(name) = ?", name.toLowerCase()),
        FolderRepository::buildRecord);
    populateRelatedData(folder);
    return folder;
  }

  public static List<Folder> findAll() {
    return findAll(null, null);
  }

  public static List<Folder> findAll(FolderSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints().setUseCount(false);
    }
    constraints.setDefaultColumnToSortBy("name");
    DataResult result = query(specification, constraints);
    List<Folder> folderList = (List<Folder>) result.getRecords();
    for (Folder folder : folderList) {
      populateRelatedData(folder);
    }
    return folderList;
  }

  private static void populateRelatedData(Folder folder) {
    if (folder == null) {
      return;
    }
    if (folder.getHasAllowedGroups()) {
      List<FolderGroup> allowedGroupList = FolderGroupRepository.findAllByFolderId(folder.getId());
      folder.setFolderGroupList(allowedGroupList);
    }
    if (folder.getHasCategories()) {
      List<FolderCategory> folderCategoryList = FolderCategoryRepository.findAllByFolderId(folder.getId());
      folder.setFolderCategoryList(folderCategoryList);
    }
  }

  public static boolean updateFileCount(Connection connection, long folderId, int value) {
    try {
      // Increment the count
      try (PreparedStatement pst = createPreparedStatementForItemCount(connection, folderId, value)) {
        return pst.execute();
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("The update failed!");
    return false;
  }

  private static PreparedStatement createPreparedStatementForItemCount(Connection connection, long folderId, int value) throws SQLException {
    String SQL_QUERY =
        "UPDATE folders " +
            "SET file_count = file_count + ? " +
            "WHERE folder_id = ?";
    int i = 0;
    PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
    pst.setInt(++i, value);
    pst.setLong(++i, folderId);
    return pst;
  }

  public static boolean updateFileCountForFileId(Connection connection, long fileId, int value) {
    try {
      // Increment the count
      try (PreparedStatement pst = createPreparedStatementForItemCountForFileId(connection, fileId, value)) {
        return pst.execute();
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("The update failed!");
    return false;
  }

  private static PreparedStatement createPreparedStatementForItemCountForFileId(Connection connection, long fileId, int value) throws SQLException {
    String SQL_QUERY =
        "UPDATE folders " +
            "SET file_count = file_count + ? " +
            "WHERE folder_id IN (SELECT folder_id FROM files WHERE file_id = ?) ";
    int i = 0;
    PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
    pst.setInt(++i, value);
    pst.setLong(++i, fileId);
    return pst;
  }

  public static Folder buildRecord(ResultSet rs) {
    try {
      Folder record = new Folder();
      record.setId(rs.getLong("folder_id"));
      record.setUniqueId(rs.getString("folder_unique_id"));
      record.setName(rs.getString("name"));
      record.setSummary(rs.getString("summary"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setModified(rs.getTimestamp("modified"));
      record.setFileCount(rs.getInt("file_count"));
      String privacyTypes = rs.getString("privacy_types");
      if (privacyTypes != null) {
        record.setPrivacyTypes(privacyTypes.split("\\s*,\\s*"));
      }
      record.setHasAllowedGroups(rs.getBoolean("has_allowed_groups"));
      record.setAllowsGuests(rs.getBoolean("allows_guests"));
      record.setGuestPrivacyType(rs.getInt("guest_privacy_type"));
      record.setEnabled(rs.getBoolean("enabled"));
      record.setHasCategories(rs.getBoolean("has_categories"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
