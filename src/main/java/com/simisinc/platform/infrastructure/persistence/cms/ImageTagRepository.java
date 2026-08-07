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

package com.simisinc.platform.infrastructure.persistence.cms;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.ImageTag;
import com.simisinc.platform.infrastructure.database.AutoRollback;
import com.simisinc.platform.infrastructure.database.AutoStartTransaction;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and retrieves image tag objects. Mirrors items' TagRepository, minus the
 * per-collection scoping -- image tags are one flat, site-wide pool.
 * <p>
 * Unlike items' Tag (which carries a maintained {@code item_count} column, kept in sync at every
 * mutation site in ItemRepository), an {@link com.simisinc.platform.domain.model.cms.ImageTag}
 * has no such counter -- see {@link #countAllByImageTagId()}, which computes it live instead.
 *
 * @author SimIS
 * @created 8/5/2026
 */
public class ImageTagRepository {

  private static final String[] PRIMARY_KEY = new String[] { "image_tag_id" };
  private static String TABLE_NAME = "image_tags";

  private static Log LOG = LogFactory.getLog(ImageTagRepository.class);

  public static ImageTag findById(long id) {
    if (id == -1) {
      return null;
    }
    return (ImageTag) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("image_tag_id = ?", id),
        ImageTagRepository::buildRecord);
  }

  public static ImageTag findByName(String name) {
    if (StringUtils.isBlank(name)) {
      return null;
    }
    return (ImageTag) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("LOWER(name) = ?", name.trim().toLowerCase()),
        ImageTagRepository::buildRecord);
  }

  /**
   * Every image tag, sorted by name -- mirrors TagRepository.findAll().
   */
  public static List<ImageTag> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("name").setUseCount(false),
        ImageTagRepository::buildRecord);
    return (List<ImageTag>) result.getRecords();
  }

  /**
   * How many images carry each tag -- computed live from image_tag_map (see the class javadoc on
   * why there is no maintained counter column to read this from instead) for the "Manage Tags"
   * panel. A tag with no entry in the returned map has zero images.
   */
  public static Map<Long, Long> countAllByImageTagId() {
    Map<Long, Long> counts = new HashMap<>();
    String sql = "SELECT image_tag_id, COUNT(*) AS c FROM image_tag_map GROUP BY image_tag_id";
    try (Connection connection = DB.getConnection();
        java.sql.Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      while (rs.next()) {
        counts.put(rs.getLong("image_tag_id"), rs.getLong("c"));
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return counts;
  }

  /**
   * Batch-loads every listed image's tags in one query (mirrors ImageVariantRepository.findByImageIds(),
   * used the same way by AdminImageBrowserWidget) rather than one EXISTS-subquery call per row.
   */
  public static Map<Long, List<ImageTag>> findByImageIds(List<Long> imageIds) {
    Map<Long, List<ImageTag>> result = new HashMap<>();
    if (imageIds == null || imageIds.isEmpty()) {
      return result;
    }
    String placeholders = String.join(",", imageIds.stream().map(id -> "?").toArray(String[]::new));
    String sql = "SELECT m.image_id, t.image_tag_id, t.name, t.created_by, t.created, t.modified " +
        "FROM image_tag_map m JOIN image_tags t ON t.image_tag_id = m.image_tag_id " +
        "WHERE m.image_id IN (" + placeholders + ") ORDER BY t.name";
    try (Connection connection = DB.getConnection();
        java.sql.PreparedStatement pst = connection.prepareStatement(sql)) {
      int i = 0;
      for (Long imageId : imageIds) {
        pst.setLong(++i, imageId);
      }
      try (ResultSet rs = pst.executeQuery()) {
        while (rs.next()) {
          long imageId = rs.getLong("image_id");
          ImageTag tag = buildRecord(rs);
          result.computeIfAbsent(imageId, k -> new java.util.ArrayList<>()).add(tag);
        }
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return result;
  }

  public static ImageTag save(ImageTag record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return insert(record);
  }

  public static boolean remove(ImageTag record) {
    try {
      try (Connection connection = DB.getConnection();
          AutoStartTransaction a = new AutoStartTransaction(connection);
          AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        ImageTagMapRepository.removeAll(connection, record);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("image_tag_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  private static ImageTag insert(ImageTag record) {
    SqlUtils insertValues = new SqlUtils()
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("created_by", record.getCreatedBy());
    try {
      record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
      if (record.getId() == -1) {
        LOG.error("An id was not set!");
        return null;
      }
      return record;
    } catch (Exception e) {
      LOG.error("Exception: " + e.getMessage());
    }
    return null;
  }

  private static ImageTag update(ImageTag record) {
    SqlUtils updateValues = new SqlUtils()
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("modified", new java.sql.Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("image_tag_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  private static ImageTag buildRecord(ResultSet rs) {
    try {
      ImageTag record = new ImageTag();
      record.setId(rs.getLong("image_tag_id"));
      record.setName(rs.getString("name"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModified(rs.getTimestamp("modified"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
