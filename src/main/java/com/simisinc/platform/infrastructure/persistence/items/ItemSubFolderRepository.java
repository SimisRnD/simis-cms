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

import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemFolder;
import com.simisinc.platform.domain.model.items.ItemSubFolder;
import com.simisinc.platform.infrastructure.database.*;
import com.simisinc.platform.presentation.controller.DataConstants;
import com.simisinc.platform.presentation.controller.UserSession;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves item sub-folder objects
 *
 * @author matt rajkowski
 * @created 4/19/2021 1:00 PM
 */
public class ItemSubFolderRepository {

  private static Log LOG = LogFactory.getLog(ItemSubFolderRepository.class);

  private static String TABLE_NAME = "item_sub_folders";
  private static String PRIMARY_KEY[] = new String[]{"sub_folder_id"};

  private static DataResult query(ItemSubFolderSpecification specification, DataConstraints constraints) {
    SqlUtils select = new SqlUtils();
    SqlJoins joins = new SqlJoins();
    SqlUtils where = new SqlUtils();
    SqlUtils orderBy = new SqlUtils();
    if (specification != null) {

      joins.add("LEFT JOIN item_folders ON (item_sub_folders.folder_id = item_folders.folder_id)");

      where
          .addIfExists("item_id = ?", specification.getItemId(), -1)
          .addIfExists("sub_folder_id = ?", specification.getId(), -1)
          .addIfExists("item_folders.folder_id = ?", specification.getFolderId(), -1);

      // For user id
      // User must be in a user group with folder access
      if (specification.getForUserId() != DataConstants.UNDEFINED) {
        if (specification.getForUserId() == UserSession.GUEST_ID) {
          where.add("item_folders.allows_guests = true");
        } else {
          // For logged out and logged in users
          where.add(
              "(allows_guests = true " +
                  "OR (has_allowed_groups = true " +
                  "AND EXISTS (SELECT 1 FROM item_folder_groups WHERE item_folder_groups.folder_id = item_folders.folder_id AND view_all = true " +
                  "AND EXISTS (SELECT 1 FROM user_groups WHERE user_groups.group_id = item_folder_groups.group_id AND user_id = ?))" +
                  ")" +
                  ")",
              specification.getForUserId());
        }
      }

      if (specification.getHasFiles() != DataConstants.UNDEFINED) {
        if (specification.getHasFiles() == DataConstants.TRUE) {
          where.add("item_sub_folders.file_count > 0");
        } else {
          where.add("item_sub_folders.file_count = 0");
        }
      }

      if (specification.getYear() > 0) {
        where.add("EXTRACT(YEAR FROM start_date) = ?", specification.getYear());
      }

    }
    return DB.selectAllFrom(
        TABLE_NAME, select, joins, where, orderBy, constraints, ItemSubFolderRepository::buildRecord);
  }

  public static ItemSubFolder findById(long id) {
    if (id == -1) {
      return null;
    }
    return (ItemSubFolder) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("sub_folder_id = ?", id),
        ItemSubFolderRepository::buildRecord);
  }

  public static List<ItemSubFolder> findAll() {
    return findAll(null, null);
  }

  public static List<ItemSubFolder> findAll(ItemSubFolderSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("start_date DESC");
    DataResult result = query(specification, constraints);
    return (List<ItemSubFolder>) result.getRecords();
  }

  public static List<Long> queryDistinctStartDateAsYearForFolder(ItemFolder folder) {
    String SQL_FIELDS =
        "DISTINCT(EXTRACT(YEAR FROM start_date)) AS year";
    SqlUtils where = new SqlUtils().add("folder_id = ?", folder.getId());
    SqlUtils orderBy = new SqlUtils().add("year DESC");
    return DB.selectFunctionAsLongList(SQL_FIELDS, TABLE_NAME, where, orderBy);
  }

  public static ItemSubFolder save(ItemSubFolder record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  private static ItemSubFolder add(ItemSubFolder record) {
    SqlUtils insertValues = new SqlUtils()
        .add("folder_id", record.getFolderId())
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("summary", StringUtils.trimToNull(record.getSummary()))
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy())
        .add("end_date", record.getEndDate());
    if (record.getStartDate() != null) {
      insertValues.add("start_date", record.getStartDate());
    }

    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
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

  private static ItemSubFolder update(ItemSubFolder record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Update the count in case the folder changed
        ItemFolderRepository.updateFileCountForFileId(connection, record.getId(), -1);
        // Update the record
        SqlUtils updateValues = new SqlUtils()
            .add("name", StringUtils.trimToNull(record.getName()))
            .add("summary", StringUtils.trimToNull(record.getSummary()))
            .add("modified_by", record.getModifiedBy())
            .add("start_date", record.getStartDate())
            .add("end_date", record.getEndDate());
        SqlUtils where = new SqlUtils()
            .add("sub_folder_id = ?", record.getId());
        if (DB.update(connection, TABLE_NAME, updateValues, where)) {
          // Finish transaction
          transaction.commit();
          return record;
        }
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(ItemSubFolder record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        ItemFileVersionRepository.removeAll(connection, record);
        int deleteCount = ItemFileItemRepository.removeAll(connection, record);
        // Update the folder count
        ItemFolderRepository.updateFileCount(connection, record.getFolderId(), -deleteCount);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("sub_folder_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  public static void removeAll(Connection connection, Item record) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("item_id = ?", record.getId()));
  }

  public static void removeAll(Connection connection, ItemFolder record) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("folder_id = ?", record.getId()));
  }

  public static boolean updateFileCount(Connection connection, long subFolderId, int value) throws SQLException {
    // Update the totals
    SqlUtils update = new SqlUtils()
        .add("file_count = file_count + " + value);
    SqlUtils where = new SqlUtils().add("sub_folder_id = ?", subFolderId);
    return DB.update(connection, TABLE_NAME, update, where);
  }

  public static boolean updateFileCountForFileId(Connection connection, long fileId, int value) throws SQLException {
    // Update the totals
    SqlUtils update = new SqlUtils()
        .add("file_count = file_count + " + value);
    SqlUtils where = new SqlUtils().add("sub_folder_id IN (SELECT sub_folder_id FROM files WHERE file_id = ?)", fileId);
    return DB.update(connection, TABLE_NAME, update, where);
  }

  private static ItemSubFolder buildRecord(ResultSet rs) {
    try {
      ItemSubFolder record = new ItemSubFolder();
      record.setId(rs.getLong("sub_folder_id"));
      record.setItemId(rs.getLong("item_id"));
      record.setFolderId(rs.getLong("folder_id"));
      record.setName(rs.getString("name"));
      record.setSummary(rs.getString("summary"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setModified(rs.getTimestamp("modified"));
      record.setStartDate(rs.getTimestamp("start_date"));
      record.setEndDate(rs.getTimestamp("end_date"));
      record.setFileCount(rs.getInt("file_count"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
