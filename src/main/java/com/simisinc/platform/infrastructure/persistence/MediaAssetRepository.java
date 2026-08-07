/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

import com.simisinc.platform.domain.model.MediaAsset;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Persists and retrieves media asset objects
 *
 * @author claude
 * @created 7/26/26
 */
public class MediaAssetRepository {

  private static Log LOG = LogFactory.getLog(MediaAssetRepository.class);

  private static String TABLE_NAME = "media_assets";
  private static String[] PRIMARY_KEY = new String[]{"id"};

  public static MediaAsset findById(long id) {
    if (id == -1) {
      return null;
    }
    return (MediaAsset) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("id = ?", id),
        MediaAssetRepository::buildRecord);
  }

  public static MediaAsset findByAssetId(String assetId) {
    if (StringUtils.isBlank(assetId)) {
      return null;
    }
    return (MediaAsset) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("asset_id = ?", assetId),
        MediaAssetRepository::buildRecord);
  }

  public static List<MediaAsset> findAll(DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("created_at");
    // Excludes soft-deleted rows -- softDelete() only sets deleted_at, it never removes the row, so
    // without this a "deleted" asset kept reappearing in the media library listing.
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils().add("deleted_at IS NULL"),
        constraints,
        MediaAssetRepository::buildRecord);
    return (List<MediaAsset>) result.getRecords();
  }

  public static List<MediaAsset> findByType(String assetType, DataConstraints constraints) {
    if (StringUtils.isBlank(assetType)) {
      return null;
    }
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("created_at");
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils().add("asset_type = ?", assetType),
        constraints,
        MediaAssetRepository::buildRecord);
    return (List<MediaAsset>) result.getRecords();
  }

  public static MediaAsset save(MediaAsset record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  private static MediaAsset add(MediaAsset record) {
    SqlUtils insertValues = new SqlUtils()
        .add("asset_id", record.getAssetId())
        .add("asset_name", StringUtils.trimToNull(record.getAssetName()))
        .add("asset_type", record.getAssetType())
        .add("mime_type", record.getMimeType())
        .add("file_size_bytes", record.getFileSizeBytes())
        .add("storage_path", record.getStoragePath())
        .add("alt_text", StringUtils.trimToNull(record.getAltText()))
        .add("tags", StringUtils.trimToNull(record.getTags()))
        .add("created_by", record.getCreatedBy())
        .add("created_at", record.getCreatedAt() != null ? Timestamp.valueOf(record.getCreatedAt()) : null);

    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  private static MediaAsset update(MediaAsset record) {
    SqlUtils updateValues = new SqlUtils()
        .add("asset_name", StringUtils.trimToNull(record.getAssetName()))
        .add("alt_text", StringUtils.trimToNull(record.getAltText()))
        .add("tags", StringUtils.trimToNull(record.getTags()))
        .add("updated_at", record.getUpdatedAt() != null ? Timestamp.valueOf(record.getUpdatedAt()) : null);

    SqlUtils where = new SqlUtils()
        .add("id = ?", record.getId());

    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean softDelete(long id) {
    SqlUtils updateValues = new SqlUtils()
        .add("deleted_at", Timestamp.valueOf(LocalDateTime.now()));
    SqlUtils where = new SqlUtils()
        .add("id = ?", id);

    if (DB.update(TABLE_NAME, updateValues, where)) {
      return true;
    }
    LOG.error("The soft delete failed!");
    return false;
  }

  /**
   * Build the record from the database
   */
  private static MediaAsset buildRecord(ResultSet rs) {
    try {
      MediaAsset record = new MediaAsset();
      record.setId(rs.getLong("id"));
      record.setAssetId(rs.getString("asset_id"));
      record.setAssetName(rs.getString("asset_name"));
      record.setAssetType(rs.getString("asset_type"));
      record.setMimeType(rs.getString("mime_type"));
      record.setFileSizeBytes(rs.getLong("file_size_bytes"));
      record.setStoragePath(rs.getString("storage_path"));
      record.setAltText(rs.getString("alt_text"));
      record.setTags(rs.getString("tags"));
      record.setCreatedBy(rs.getLong("created_by"));

      Timestamp createdAt = rs.getTimestamp("created_at");
      if (createdAt != null) {
        record.setCreatedAt(createdAt.toLocalDateTime());
      }

      Timestamp updatedAt = rs.getTimestamp("updated_at");
      if (updatedAt != null) {
        record.setUpdatedAt(updatedAt.toLocalDateTime());
      }

      Timestamp deletedAt = rs.getTimestamp("deleted_at");
      if (deletedAt != null) {
        record.setDeletedAt(deletedAt.toLocalDateTime());
      }

      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
