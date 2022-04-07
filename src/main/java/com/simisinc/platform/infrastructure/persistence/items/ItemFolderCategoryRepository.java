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
import com.simisinc.platform.domain.model.items.ItemFolderCategory;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/19/2021 1:00 PM
 */
public class ItemFolderCategoryRepository {

  private static Log LOG = LogFactory.getLog(ItemFolderCategoryRepository.class);

  private static String TABLE_NAME = "item_folder_categories";
  private static String PRIMARY_KEY[] = new String[]{"category_id"};

  public static ItemFolderCategory findById(long id) {
    if (id == -1) {
      return null;
    }
    return (ItemFolderCategory) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("category_id = ?", id),
        ItemFolderCategoryRepository::buildRecord);
  }

  public static List<ItemFolderCategory> findAllByFolderId(long folderId) {
    if (folderId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("folder_id = ?", folderId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("category_id").setUseCount(false),
        ItemFolderCategoryRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<ItemFolderCategory>) result.getRecords();
    }
    return null;
  }

  public static List<ItemFolderCategory> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("category_id"),
        ItemFolderCategoryRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<ItemFolderCategory>) result.getRecords();
    }
    return null;
  }

  public static ItemFolderCategory add(ItemFolderCategory record) {
    SqlUtils insertValues = new SqlUtils()
        .add("item_id", record.getItemId())
        .add("folder_id", record.getFolderId())
        .add("name", record.getName())
        .add("enabled", record.getEnabled());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static void insertFolderCategoryList(Connection connection, ItemFolder folder) throws SQLException {
    if (folder.getFolderCategoryList() == null) {
      return;
    }
    for (ItemFolderCategory category : folder.getFolderCategoryList()) {
      SqlUtils insertValues = new SqlUtils();
      insertValues
          .add("folder_id", folder.getId())
          .add("name", category.getName())
          .add("enabled", category.getEnabled());
      DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY);
    }
  }

  public static void updateFolderCategoryList(Connection connection, ItemFolder folder) throws SQLException {
    if (folder.getFolderCategoryList() == null) {
      return;
    }
    for (ItemFolderCategory category : folder.getFolderCategoryList()) {
      // Determine if inserting or updating
      if (category.getId() == -1) {
        // New category
        SqlUtils insertValues = new SqlUtils();
        insertValues
            .add("folder_id", folder.getId())
            .add("name", category.getName())
            .add("enabled", category.getEnabled());
        DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY);
      } else {
        // Update existing
        SqlUtils updateValues = new SqlUtils();
        updateValues
            .add("name", category.getName())
            .add("enabled", category.getEnabled());
        SqlUtils where = new SqlUtils();
        where.add("category_id = ?", category.getId());
        DB.update(connection, TABLE_NAME, updateValues, where);
      }
    }
  }

  public static void removeAll(Connection connection, Item item) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("item_id = ?", item.getId()));
  }

  public static void removeAll(Connection connection, ItemFolder folder) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("folder_id = ?", folder.getId()));
  }

  public static void remove(Connection connection, ItemFolderCategory folderCategory) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("category_id = ?", folderCategory.getId()));
  }

  private static ItemFolderCategory buildRecord(ResultSet rs) {
    try {
      ItemFolderCategory record = new ItemFolderCategory();
      record.setId(rs.getLong("category_id"));
      record.setItemId(rs.getLong("item_id"));
      record.setFolderId(rs.getLong("folder_id"));
      record.setName(rs.getString("name"));
      record.setEnabled(rs.getBoolean("enabled"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
