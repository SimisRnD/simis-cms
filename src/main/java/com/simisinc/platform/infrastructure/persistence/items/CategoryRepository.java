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

import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.*;
import java.util.List;

/**
 * Persists and retrieves category objects
 *
 * @author matt rajkowski
 * @created 4/18/18 10:15 PM
 */
public class CategoryRepository {

  private static final String[] PRIMARY_KEY = new String[] { "category_id" };
  private static String TABLE_NAME = "categories";

  private static Log LOG = LogFactory.getLog(CategoryRepository.class);

  public static Category findById(long id) {
    if (id == -1) {
      return null;
    }
    return (Category) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("category_id = ?", id),
        CategoryRepository::buildRecord);
  }

  public static Category findByNameWithinCollection(String name, long collectionId) {
    if (collectionId == -1) {
      return null;
    }
    if (StringUtils.isBlank(name)) {
      return null;
    }
    return (Category) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("collection_id = ?", collectionId)
            .add("LOWER(name) = ?", name.trim().toLowerCase()),
        CategoryRepository::buildRecord);
  }

  public static List<Category> findAllByItemId(long itemId) {
    if (itemId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("EXISTS (SELECT 1 FROM item_categories WHERE category_id = categories.category_id AND item_id = ?)",
            itemId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("name").setUseCount(false),
        CategoryRepository::buildRecord);
    return (List<Category>) result.getRecords();
  }

  public static List<Category> findAllByCollectionId(long collectionId) {
    return findAllByCollectionId(collectionId, false);
  }

  public static List<Category> findAllByCollectionId(long collectionId, boolean basedOnItems) {
    if (collectionId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils();
    where.add("collection_id = ?", collectionId);
    if (basedOnItems) {
      where.add("item_count > 0");
      //      where.add("EXISTS (SELECT 1 FROM items WHERE category_id = items.category_id AND collection_id = ?)", collectionId);
    }
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("name").setUseCount(false),
        CategoryRepository::buildRecord);
    return (List<Category>) result.getRecords();
  }

  public static List<Category> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("name"),
        CategoryRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<Category>) result.getRecords();
    }
    return null;
  }

  public static Category save(Category record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return insert(record);
  }

  public static boolean remove(Category record) {
    try {
      try (Connection connection = DB.getConnection();
          AutoStartTransaction a = new AutoStartTransaction(connection);
          AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        ItemCategoryRepository.removeAll(connection, record);
        // Update pointers
        CollectionRepository.updateCategoryCount(connection, record.getCollectionId(), -1);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("category_id = ?", record.getId()));
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

  private static Category insert(Category record) {
    SqlUtils insertValues = new SqlUtils()
        .add("collection_id", record.getCollectionId())
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("description", StringUtils.trimToNull(record.getDescription()))
        .add("created_by", record.getCreatedBy())
        .add("icon", StringUtils.trimToNull(record.getIcon()))
        .addIfExists("header_text_color", record.getHeaderTextColor())
        .addIfExists("header_bg_color", record.getHeaderBgColor())
        .add("item_url_text", StringUtils.trimToNull(record.getItemUrlText()));
    try {
      try (Connection connection = DB.getConnection();
          AutoStartTransaction a = new AutoStartTransaction(connection);
          AutoRollback transaction = new AutoRollback(connection)) {
        // Insert the record
        record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
        // Update the pointer
        CollectionRepository.updateCategoryCount(connection, record.getCollectionId(), 1);
        // Finish transaction
        transaction.commit();
        return record;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return null;
  }

  private static Category update(Category record) {
    SqlUtils updateValues = new SqlUtils()
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("description", StringUtils.trimToNull(record.getDescription()))
        .add("icon", StringUtils.trimToNull(record.getIcon()))
        .add("header_text_color", StringUtils.trimToNull(record.getHeaderTextColor()))
        .add("header_bg_color", StringUtils.trimToNull(record.getHeaderBgColor()))
        .add("item_url_text", StringUtils.trimToNull(record.getItemUrlText()))
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("category_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  private static PreparedStatement createPreparedStatementForItemCount(Connection connection, long categoryId,
      int value) throws SQLException {
    String SQL_QUERY = "UPDATE categories " +
        "SET item_count = item_count + ? " +
        "WHERE category_id = ?";
    int i = 0;
    PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
    pst.setInt(++i, value);
    pst.setLong(++i, categoryId);
    return pst;
  }

  public static boolean updateItemCount(Connection connection, long categoryId, int value) {
    try {
      // Increment the count
      try (PreparedStatement pst = createPreparedStatementForItemCount(connection, categoryId, value)) {
        return pst.execute();
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("The update failed!");
    return false;
  }

  private static Category buildRecord(ResultSet rs) {
    try {
      Category record = new Category();
      record.setId(rs.getLong("category_id"));
      record.setCollectionId(rs.getLong("collection_id"));
      record.setName(rs.getString("name"));
      record.setDescription(rs.getString("description"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModified(rs.getTimestamp("modified"));
      record.setItemCount(rs.getLong("item_count"));
      record.setIcon(rs.getString("icon"));
      record.setHeaderTextColor(rs.getString("header_text_color"));
      record.setHeaderBgColor(rs.getString("header_bg_color"));
      record.setItemUrlText(rs.getString("item_url_text"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
