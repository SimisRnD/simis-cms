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

package com.simisinc.platform.infrastructure.persistence.items;

import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Tag;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.*;
import java.util.List;

/**
 * Persists and retrieves tag objects (issue #632)
 *
 * @author SimIS
 * @created 8/2/2026
 */
public class TagRepository {

  private static final String[] PRIMARY_KEY = new String[] { "tag_id" };
  private static String TABLE_NAME = "tags";

  private static Log LOG = LogFactory.getLog(TagRepository.class);

  public static Tag findById(long id) {
    if (id == -1) {
      return null;
    }
    return (Tag) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("tag_id = ?", id),
        TagRepository::buildRecord);
  }

  public static Tag findByNameWithinCollection(String name, long collectionId) {
    if (collectionId == -1) {
      return null;
    }
    if (StringUtils.isBlank(name)) {
      return null;
    }
    return (Tag) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("collection_id = ?", collectionId)
            .add("LOWER(name) = ?", name.trim().toLowerCase()),
        TagRepository::buildRecord);
  }

  public static List<Tag> findAllByItemId(long itemId) {
    if (itemId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("EXISTS (SELECT 1 FROM item_tags WHERE tag_id = tags.tag_id AND item_id = ?)",
            itemId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("name").setUseCount(false),
        TagRepository::buildRecord);
    return (List<Tag>) result.getRecords();
  }

  public static List<Tag> findAllByCollectionId(long collectionId) {
    if (collectionId == -1) {
      return null;
    }
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils().add("collection_id = ?", collectionId),
        new DataConstraints().setDefaultColumnToSortBy("name").setUseCount(false),
        TagRepository::buildRecord);
    return (List<Tag>) result.getRecords();
  }

  public static Tag save(Tag record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return insert(record);
  }

  public static boolean remove(Tag record) {
    try {
      try (Connection connection = DB.getConnection();
          AutoStartTransaction a = new AutoStartTransaction(connection);
          AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        ItemTagRepository.removeAll(connection, record);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("tag_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  public static void removeAll(Connection connection, Collection record) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("collection_id = ?", record.getId()));
  }

  private static Tag insert(Tag record) {
    SqlUtils insertValues = new SqlUtils()
        .add("collection_id", record.getCollectionId())
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

  private static Tag update(Tag record) {
    SqlUtils updateValues = new SqlUtils()
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("tag_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean updateItemCount(Connection connection, long tagId, int value) {
    String SQL_QUERY = "UPDATE tags " +
        "SET item_count = item_count + ? " +
        "WHERE tag_id = ?";
    try (PreparedStatement pst = connection.prepareStatement(SQL_QUERY)) {
      int i = 0;
      pst.setInt(++i, value);
      pst.setLong(++i, tagId);
      return pst.execute();
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("The update failed!");
    return false;
  }

  private static Tag buildRecord(ResultSet rs) {
    try {
      Tag record = new Tag();
      record.setId(rs.getLong("tag_id"));
      record.setCollectionId(rs.getLong("collection_id"));
      record.setName(rs.getString("name"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModified(rs.getTimestamp("modified"));
      record.setItemCount(rs.getLong("item_count"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
