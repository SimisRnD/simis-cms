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
import com.simisinc.platform.domain.model.items.ItemFileItem;
import com.simisinc.platform.domain.model.items.ItemFolder;
import com.simisinc.platform.domain.model.items.ItemSubFolder;
import com.simisinc.platform.infrastructure.database.*;
import com.simisinc.platform.presentation.controller.DataConstants;
import com.simisinc.platform.presentation.controller.UserSession;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.*;
import java.util.List;

/**
 * Persists and retrieves item file item objects
 *
 * @author matt rajkowski
 * @created 4/19/2021 1:00 PM
 */
public class ItemFileItemRepository {

  private static Log LOG = LogFactory.getLog(ItemFileItemRepository.class);

  private static String TABLE_NAME = "item_files";
  private static String[] PRIMARY_KEY = new String[]{"file_id"};

  private static DataResult query(ItemFileSpecification specification, DataConstraints constraints) {
    SqlUtils select = new SqlUtils();
    SqlJoins joins = new SqlJoins();
    SqlUtils where = new SqlUtils();
    SqlUtils orderBy = new SqlUtils();
    if (specification != null) {

      joins.add("LEFT JOIN item_folders ON (item_files.folder_id = item_folders.folder_id)");

      where
          .addIfExists("file_id = ?", specification.getId(), -1)
          .addIfExists("item_files.item_id = ?", specification.getItemId(), -1)
          .addIfExists("item_folders.folder_id = ?", specification.getFolderId(), -1)
          .addIfExists("sub_folder_id = ?", specification.getSubFolderId(), -1)
          .addIfExists("barcode = ?", specification.getBarcode());
      if (specification.getFilename() != null) {
        where.add("LOWER(item_files.filename) = ?", specification.getFilename().trim().toLowerCase());
      }
      if (specification.getFileType() != null) {
        where.add("LOWER(item_files.file_type) = ?", specification.getFileType().trim().toLowerCase());
      }
      if (specification.getMatchesName() != null) {
        String likeValue = specification.getMatchesName().trim()
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_")
            .replace("[", "![");
        where.add("LOWER(item_files.title) LIKE LOWER(?) ESCAPE '!'", likeValue + "%");
      }
      if (specification.getWithinLastDays() > 0) {
        where.add("item_files.created > NOW() - INTERVAL '" + specification.getWithinLastDays() + " days'");
      }
      if (specification.getInASubFolder() != DataConstants.UNDEFINED) {
        if (specification.getInASubFolder() == DataConstants.TRUE) {
          where.add("sub_folder_id IS NOT NULL");
        } else {
          where.add("sub_folder_id IS NULL");
        }
      }

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

      // Use the search engine
      if (StringUtils.isNotBlank(specification.getSearchName())) {
        select.add("TS_RANK_CD(tsv, PLAINTO_TSQUERY('item_file_stem', ?)) AS rank", specification.getSearchName().trim());
        where.add("tsv @@ PLAINTO_TSQUERY('item_file_stem', ?)", specification.getSearchName().trim());
        // Override the order by for rank first
        orderBy.add("rank DESC, file_id");
      }
    }
    return DB.selectAllFrom(
        TABLE_NAME, select, joins, where, orderBy, constraints, ItemFileItemRepository::buildRecord);
  }

  public static ItemFileItem findById(long id) {
    if (id == -1) {
      return null;
    }
    return (ItemFileItem) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("file_id = ?", id),
        ItemFileItemRepository::buildRecord);
  }

  public static List<ItemFileItem> findAll() {
    return findAll(null, null);
  }

  public static List<ItemFileItem> findAll(ItemFileSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("created DESC");
    DataResult result = query(specification, constraints);
    return (List<ItemFileItem>) result.getRecords();
  }

  public static ItemFileItem save(ItemFileItem record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  private static ItemFileItem add(ItemFileItem record) {
    SqlUtils insertValues = new SqlUtils()
        .add("item_id", record.getItemId())
        .add("folder_id", record.getFolderId())
        .addIfExists("sub_folder_id", record.getSubFolderId(), -1L)
        .addIfExists("category_id", record.getCategoryId(), -1L)
        .add("filename", StringUtils.trimToNull(record.getFilename()))
        .add("title", StringUtils.trimToNull(record.getTitle()))
        .add("barcode", StringUtils.trimToNull(record.getBarcode()))
        .add("version", StringUtils.trimToNull(record.getVersion()))
        .add("extension", StringUtils.trimToNull(record.getExtension()))
        .add("path", StringUtils.trimToNull(record.getFileServerPath()))
        .add("file_length", record.getFileLength())
        .add("file_type", record.getFileType())
        .add("mime_type", record.getMimeType())
        .add("file_hash", record.getFileHash())
        .add("width", record.getWidth(), -1)
        .add("height", record.getHeight(), -1)
        .add("summary", StringUtils.trimToNull(record.getSummary()))
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy())
        .add("processed", record.getProcessed())
        .add("expiration_date", record.getExpirationDate())
        .add("privacy_type", record.getPrivacyType())
        .add("default_token", StringUtils.trimToNull(record.getDefaultToken()));

    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
        // Create a version record
        ItemFileVersionRepository.add(connection, record);
        // Update the file counts
        ItemFolderRepository.updateFileCount(connection, record.getFolderId(), 1);
        ItemSubFolderRepository.updateFileCount(connection, record.getSubFolderId(), 1);
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

  private static ItemFileItem update(ItemFileItem record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Update the counts in case the folder changed
        ItemFolderRepository.updateFileCountForFileId(connection, record.getId(), -1);
        ItemSubFolderRepository.updateFileCountForFileId(connection, record.getId(), -1);
        // Update the record
        SqlUtils updateValues = new SqlUtils()
            .add("folder_id", record.getFolderId())
            .add("sub_folder_id", record.getSubFolderId(), -1L)
            .add("category_id", record.getCategoryId(), -1L)
            .addIfExists("filename", StringUtils.trimToNull(record.getFilename()))
            .add("title", StringUtils.trimToNull(record.getTitle()))
            .add("barcode", StringUtils.trimToNull(record.getBarcode()))
            .add("version", StringUtils.trimToNull(record.getVersion()))
            .addIfExists("width", record.getWidth(), -1)
            .addIfExists("height", record.getHeight(), -1)
            .add("summary", StringUtils.trimToNull(record.getSummary()))
            .add("modified_by", record.getModifiedBy())
            .add("processed", record.getProcessed())
            .add("expiration_date", record.getExpirationDate())
            .add("privacy_type", record.getPrivacyType());
//        .add("default_token", StringUtils.trimToNull(record.getDefaultToken()));
        SqlUtils where = new SqlUtils()
            .add("file_id = ?", record.getId());
        if (DB.update(connection, TABLE_NAME, updateValues, where)) {
          // Update related records
          ItemFileVersionRepository.update(connection, record);
          // Update the folder count
          ItemFolderRepository.updateFileCountForFileId(connection, record.getId(), 1);
          ItemSubFolderRepository.updateFileCountForFileId(connection, record.getId(), 1);
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

  public static ItemFileItem saveVersion(ItemFileItem record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Update the counts in case the folder changed
        ItemFolderRepository.updateFileCountForFileId(connection, record.getId(), -1);
        ItemSubFolderRepository.updateFileCountForFileId(connection, record.getId(), -1);
        // Update the record
        SqlUtils updateValues = new SqlUtils()
            .add("item_id", record.getItemId())
            .add("folder_id", record.getFolderId())
            .add("sub_folder_id", record.getSubFolderId(), -1L)
            .add("category_id", record.getCategoryId(), -1L)
            .add("filename", StringUtils.trimToNull(record.getFilename()))
            .add("title", StringUtils.trimToNull(record.getTitle()))
            .add("barcode", StringUtils.trimToNull(record.getBarcode()))
            .add("version", StringUtils.trimToNull(record.getVersion()))
            .add("extension", StringUtils.trimToNull(record.getExtension()))
            .add("path", StringUtils.trimToNull(record.getFileServerPath()))
            .add("file_length", record.getFileLength())
            .add("file_type", record.getFileType())
            .add("mime_type", record.getMimeType())
            .add("file_hash", record.getFileHash())
            .add("width", record.getWidth(), -1)
            .add("height", record.getHeight(), -1)
            .add("summary", StringUtils.trimToNull(record.getSummary()))
            .add("modified_by", record.getModifiedBy())
            .add("modified", new Timestamp(System.currentTimeMillis()))
            .add("processed", record.getProcessed())
            .add("expiration_date", record.getExpirationDate())
            .add("privacy_type", record.getPrivacyType())
            .add("default_token", StringUtils.trimToNull(record.getDefaultToken()));
        SqlUtils where = new SqlUtils()
            .add("file_id = ?", record.getId());
        if (DB.update(connection, TABLE_NAME, updateValues, where)) {
          // Update related records
          ItemFileVersionRepository.update(connection, record);
          // Update the folder counts
          ItemFolderRepository.updateFileCountForFileId(connection, record.getId(), 1);
          ItemSubFolderRepository.updateFileCountForFileId(connection, record.getId(), 1);
          // Create a version record
          ItemFileVersionRepository.add(connection, record);
          // Finish transaction
          transaction.commit();
          return record;
        }
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("The version update failed!");
    return null;
  }

  public static boolean remove(ItemFileItem record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        ItemFileVersionRepository.removeAll(connection, record);
        ItemFolderRepository.updateFileCount(connection, record.getFolderId(), -1);
        ItemSubFolderRepository.updateFileCount(connection, record.getSubFolderId(), -1);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("file_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
      }
      // @note file cleanup is done in the command, consider moving here
      return true;
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

  public static int removeAll(Connection connection, ItemSubFolder record) throws SQLException {
    return DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("sub_folder_id = ?", record.getId()));
  }

  private static PreparedStatement createPreparedStatementForUpdateDownloadCount(Connection connection, ItemFileItem record) throws SQLException {
    String SQL_QUERY =
        "UPDATE item_files " +
            "SET download_count = download_count + 1 " +
            "WHERE file_id = ?";
    int i = 0;
    PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
    pst.setLong(++i, record.getId());
    return pst;
  }

  public static boolean incrementDownloadCount(ItemFileItem record) {
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = createPreparedStatementForUpdateDownloadCount(connection, record)) {
      if (pst.executeUpdate() > 0) {
        return true;
      }
    } catch (SQLException se) {
      LOG.error("Update SQLException: [" + TABLE_NAME + "]: " + se.getMessage());
    }
    return false;
  }

  private static ItemFileItem buildRecord(ResultSet rs) {
    try {
      ItemFileItem record = new ItemFileItem();
      record.setId(rs.getLong("file_id"));
      record.setItemId(rs.getLong("item_id"));
      record.setFolderId(rs.getLong("folder_id"));
      record.setFilename(rs.getString("filename"));
      record.setTitle(rs.getString("title"));
      record.setBarcode(rs.getString("barcode"));
      record.setVersion(rs.getString("version"));
      record.setExtension(rs.getString("extension"));
      record.setFileServerPath(rs.getString("path"));
      record.setFileLength(rs.getLong("file_length"));
      record.setFileType(rs.getString("file_type"));
      record.setMimeType(rs.getString("mime_type"));
      record.setFileHash(rs.getString("file_hash"));
      record.setWidth(rs.getInt("width"));
      record.setHeight(rs.getInt("height"));
      record.setSummary(rs.getString("summary"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setModified(rs.getTimestamp("modified"));
      record.setProcessed(rs.getTimestamp("processed"));
      record.setExpirationDate(rs.getTimestamp("expiration_date"));
      record.setPrivacyType(rs.getInt("privacy_type"));
      record.setDefaultToken(rs.getString("default_token"));
      record.setVersionCount(rs.getInt("version_count"));
      record.setDownloadCount(rs.getLong("download_count"));
      record.setSubFolderId(DB.getLong(rs, "sub_folder_id", -1L));
      record.setCategoryId(DB.getLong(rs, "category_id", -1L));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
