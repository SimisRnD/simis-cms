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

import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists and retrieves image objects
 *
 * @author matt rajkowski
 * @created 5/3/18 3:30 PM
 */
public class ImageRepository {

  private static Log LOG = LogFactory.getLog(ImageRepository.class);

  private static String TABLE_NAME = "images";
  private static String[] PRIMARY_KEY = new String[] { "image_id" };

  private static DataResult query(ImageSpecification specification, DataConstraints constraints) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("image_id = ?", specification.getId(), -1)
          .addIfExists("created_by = ?", specification.getCreatedBy(), -1);
      if (specification.getFilename() != null) {
        where.add("LOWER(filename) = ?", specification.getFilename().toLowerCase());
      }
      if (StringUtils.isNotBlank(specification.getMatchesName())) {
        // Parameterized substring search (issue #498) -- the search term is only ever bound as a
        // placeholder value, never concatenated into the SQL text, so it cannot alter the query.
        String likeValue = "%" + specification.getMatchesName().toLowerCase() + "%";
        where.add("LOWER(filename) LIKE ?", likeValue);
      }
      if (specification.getFileType() != null) {
        where.add("LOWER(file_type) = ?", specification.getFileType().toLowerCase());
      }
      if (specification.getTagId() > -1) {
        where.add("EXISTS (SELECT 1 FROM image_tag_map WHERE image_id = images.image_id AND image_tag_id = ?)",
            specification.getTagId());
      }
      if (StringUtils.isNotBlank(specification.getFileHash())) {
        where.add("file_hash = ?", specification.getFileHash());
      }
    }
    return DB.selectAllFrom(TABLE_NAME, where, constraints, ImageRepository::buildRecord);
  }

  public static Image findById(long id) {
    if (id == -1) {
      return null;
    }
    return (Image) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("image_id = ?", id),
        ImageRepository::buildRecord);
  }

  public static Image findByWebPathAndId(String versionWebPath, long id) {
    if (StringUtils.isBlank(versionWebPath) || id == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("web_path = ?", versionWebPath)
        .add("image_id = ?", id);
    return (Image) DB.selectRecordFrom(
        TABLE_NAME,
        where,
        ImageRepository::buildRecord);
  }

  public static List<Image> findAll() {
    return findAll(null, null);
  }

  public static List<Image> findAll(ImageSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("created DESC");
    DataResult result = query(specification, constraints);
    return (List<Image>) result.getRecords();
  }

  /**
   * Every {@code file_hash} shared by 2 or more images, newest-hashed-first -- the /admin/images
   * duplicates view groups images under each of these. Only images that have already been hashed
   * (see {@link #findAllUnhashed()}) can appear here; a fresh install with nothing scanned yet
   * returns an empty list, not an error.
   */
  public static List<String> findDuplicateFileHashes() {
    List<String> hashes = new ArrayList<>();
    String sql = "SELECT file_hash FROM " + TABLE_NAME
        + " WHERE file_hash IS NOT NULL GROUP BY file_hash HAVING COUNT(*) > 1 ORDER BY COUNT(*) DESC";
    try (Connection connection = DB.getConnection();
        java.sql.Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      while (rs.next()) {
        hashes.add(rs.getString("file_hash"));
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return hashes;
  }

  /**
   * Images with no {@code file_hash} yet -- existing rows from before this column existed, or any
   * upload where {@code ValidateImageCommand.checkFile} couldn't read the file. Drives the
   * admin-triggered "Scan for Duplicates" backfill (see {@code ScanForDuplicateImagesCommand}),
   * which enqueues one background job per id returned here.
   */
  public static List<Long> findAllUnhashed() {
    List<Long> ids = new ArrayList<>();
    String sql = "SELECT image_id FROM " + TABLE_NAME + " WHERE file_hash IS NULL";
    try (Connection connection = DB.getConnection();
        java.sql.Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      while (rs.next()) {
        ids.add(rs.getLong("image_id"));
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return ids;
  }

  public static Image save(Image record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  private static Image add(Image record) {
    SqlUtils insertValues = new SqlUtils()
        .add("filename", StringUtils.trimToNull(record.getFilename()))
        .add("path", StringUtils.trimToNull(record.getFileServerPath()))
        .add("web_path", StringUtils.trimToNull(record.getWebPath()))
        .add("created_by", record.getCreatedBy())
        .add("file_length", record.getFileLength())
        .add("file_type", record.getFileType())
        .add("width", record.getWidth())
        .add("height", record.getHeight())
        .add("focal_x", record.getFocalX())
        .add("focal_y", record.getFocalY())
        .add("file_hash", record.getFileHash())
        .add("alt_text", StringUtils.trimToNull(record.getAltText()));
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  private static Image update(Image record) {
    SqlUtils updateValues = new SqlUtils()
        .add("filename", StringUtils.trimToNull(record.getFilename()))
        .add("path", StringUtils.trimToNull(record.getFileServerPath()))
        .add("web_path", StringUtils.trimToNull(record.getWebPath()))
        .add("file_length", record.getFileLength())
        .add("file_type", record.getFileType())
        .add("width", record.getWidth())
        .add("height", record.getHeight())
        .add("processed", record.getProcessed())
        .add("focal_x", record.getFocalX())
        .add("focal_y", record.getFocalY())
        .add("file_hash", record.getFileHash())
        .add("alt_text", StringUtils.trimToNull(record.getAltText()));
    SqlUtils where = new SqlUtils()
        .add("image_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  /**
   * Removes the image's database row only -- the caller (see {@code DeleteImageCommand}) is
   * responsible for removing the physical file, and only after this returns {@code true}.
   *
   * @return true when a row was actually deleted, false when there was nothing to delete or the
   *         delete failed -- callers must not delete the on-disk file unless this is true.
   */
  public static boolean remove(Image record) {
    int rowsDeleted = DB.deleteFrom(TABLE_NAME, new SqlUtils().add("image_id = ?", record.getId()));
    return rowsDeleted > 0;
  }

  private static Image buildRecord(ResultSet rs) {
    try {
      Image record = new Image();
      record.setId(rs.getLong("image_id"));
      record.setFilename(rs.getString("filename"));
      record.setFileServerPath(rs.getString("path"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setProcessed(rs.getTimestamp("processed"));
      record.setFileLength(rs.getLong("file_length"));
      record.setFileType(rs.getString("file_type"));
      record.setWidth(rs.getInt("width"));
      record.setHeight(rs.getInt("height"));
      record.setWebPath(rs.getString("web_path"));
      record.setFocalX(rs.getBigDecimal("focal_x"));
      record.setFocalY(rs.getBigDecimal("focal_y"));
      record.setFileHash(rs.getString("file_hash"));
      record.setAltText(rs.getString("alt_text"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
