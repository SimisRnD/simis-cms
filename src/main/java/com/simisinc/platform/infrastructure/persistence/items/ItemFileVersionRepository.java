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

import com.simisinc.platform.domain.model.items.*;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves item file version objects
 *
 * @author matt rajkowski
 * @created 4/19/2021 1:00 PM
 */
public class ItemFileVersionRepository {

  private static Log LOG = LogFactory.getLog(ItemFileVersionRepository.class);

  private static String TABLE_NAME = "item_file_versions";
  private static String[] PRIMARY_KEY = new String[]{"version_id"};

  private static DataResult query(ItemFileVersionSpecification specification, DataConstraints constraints) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("version_id = ?", specification.getId(), -1)
          .addIfExists("file_id = ?", specification.getFileId(), -1)
          .addIfExists("item_id = ?", specification.getItemId(), -1)
          .addIfExists("folder_id = ?", specification.getFolderId(), -1)
          .addIfExists("sub_folder_id = ?", specification.getSubFolderId(), -1);
    }
    return DB.selectAllFrom(TABLE_NAME, where, constraints, ItemFileVersionRepository::buildRecord);
  }

  public static ItemFileVersion findById(long id) {
    if (id == -1) {
      return null;
    }
    return (ItemFileVersion) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("version_id = ?", id),
        ItemFileVersionRepository::buildRecord);
  }

  public static List<ItemFileVersion> findAll(ItemFileVersionSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("created DESC");
    DataResult result = query(specification, constraints);
    return (List<ItemFileVersion>) result.getRecords();
  }

  public static ItemFileVersion save(ItemFileVersion record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return null;
  }

  public static ItemFileItem add(Connection connection, ItemFileItem record) throws SQLException {
    SqlUtils insertValues = new SqlUtils()
        .add("file_id", record.getId())
        .add("item_id", record.getItemId())
        .add("folder_id", record.getFolderId())
        .addIfExists("sub_folder_id", record.getSubFolderId(), -1L)
        .addIfExists("category_id", record.getCategoryId(), -1L)
        .add("filename", StringUtils.trimToNull(record.getFilename()))
        .add("title", StringUtils.trimToNull(record.getTitle()))
        .add("version", StringUtils.trimToNull(record.getVersion()))
        .add("extension", StringUtils.trimToNull(record.getExtension()))
        .add("path", StringUtils.trimToNull(record.getFileServerPath()))
        .add("web_path", StringUtils.trimToNull(record.getWebPath()))
        .add("file_length", record.getFileLength())
        .add("file_type", record.getFileType())
        .add("mime_type", record.getMimeType())
        .add("file_hash", record.getFileHash())
        .addIfExists("width", record.getWidth(), -1)
        .addIfExists("height", record.getHeight(), -1)
        .add("summary", StringUtils.trimToNull(record.getSummary()))
        .add("created_by", record.getCreatedBy());
    record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
    // Manage a few related tables
    // Finish the transaction
    return record;
  }

  private static ItemFileVersion update(ItemFileVersion record) {
    SqlUtils updateValues = new SqlUtils()
        .add("folder_id", record.getFolderId())
        .add("sub_folder_id", record.getSubFolderId(), -1L)
        .add("category_id", record.getCategoryId(), -1L)
//        .add("filename", StringUtils.trimToNull(record.getFilename()))
        .add("title", StringUtils.trimToNull(record.getTitle()))
        .add("version", StringUtils.trimToNull(record.getVersion()))
//        .add("extension", StringUtils.trimToNull(record.getExtension()))
//        .add("path", StringUtils.trimToNull(record.getFileServerPath()))
//        .add("file_length", record.getFileLength())
//        .add("file_type", record.getFileType())
//        .add("mime_type", record.getMimeType())
//        .add("file_hash", record.getFileHash())
        .addIfExists("width", record.getWidth(), -1)
        .addIfExists("height", record.getHeight(), -1)
        .add("summary", StringUtils.trimToNull(record.getSummary()))
//        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy());
    SqlUtils where = new SqlUtils()
        .add("version_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static ItemFileItem update(Connection connection, ItemFileItem record) throws SQLException {
    SqlUtils updateValues = new SqlUtils()
        .add("folder_id", record.getFolderId())
        .add("sub_folder_id", record.getSubFolderId(), -1L)
        .add("category_id", record.getCategoryId(), -1L);
    SqlUtils where = new SqlUtils()
        .add("file_id = ?", record.getId());
    if (DB.update(connection, TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update fileItem failed!");
    return null;
  }

  public static void remove(ItemFileVersion record) {
    DB.deleteFrom(TABLE_NAME, new SqlUtils().add("version_id = ?", record.getId()));
  }

  public static void removeAll(Connection connection, Item record) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("item_id = ?", record.getId()));
  }

  public static void removeAll(Connection connection, ItemFolder record) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("folder_id = ?", record.getId()));
  }

  public static void removeAll(Connection connection, ItemSubFolder record) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("sub_folder_id = ?", record.getId()));
  }

  public static void removeAll(Connection connection, ItemFileItem record) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("file_id = ?", record.getId()));
  }

  private static ItemFileVersion buildRecord(ResultSet rs) {
    try {
      ItemFileVersion record = new ItemFileVersion();
      record.setId(rs.getLong("version_id"));
      record.setFileId(rs.getLong("file_id"));
      record.setItemId(rs.getLong("item_id"));
      record.setFolderId(rs.getLong("folder_id"));
      record.setFilename(rs.getString("filename"));
      record.setTitle(rs.getString("title"));
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
      record.setDownloadCount(rs.getLong("download_count"));
      record.setSubFolderId(DB.getLong(rs, "sub_folder_id", -1L));
      record.setCategoryId(DB.getLong(rs, "category_id", -1L));
      record.setWebPath(rs.getString("web_path"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
