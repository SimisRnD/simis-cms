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
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemCategory;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists and retrieves item category objects
 *
 * @author matt rajkowski
 * @created 5/29/18 12:04 PM
 */
public class ItemCategoryRepository {

  private static Log LOG = LogFactory.getLog(ItemCategoryRepository.class);

  private static String TABLE_NAME = "item_categories";
  private static String[] PRIMARY_KEY = new String[]{"id"};


  public static ItemCategory save(ItemCategory record) {
    if (record.getId() > -1) {
      // not implemented
      return null;
    }
    return add(record);
  }

  private static ItemCategory add(ItemCategory record) {
    SqlUtils insertValues = new SqlUtils()
        .add("item_id", record.getItemId())
        .add("category_id", record.getCategoryId(), -1)
        .add("collection_id", record.getCollectionId())
        .add("dataset_id", record.getDatasetId(), -1);
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static void insertItemCategoryList(Connection connection, Item item) throws SQLException {
    if (item.getCategoryIdList() == null) {
      return;
    }
    for (Long categoryId : item.getCategoryIdList()) {
      SqlUtils insertValues = new SqlUtils();
      // @todo reuse insertValues once values can be replaced
      insertValues.add("item_id", item.getId())
          .add("collection_id", item.getCollectionId())
          .addIfExists("dataset_id", item.getDatasetId(), -1);
      insertValues.add("category_id", categoryId);
      DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY);
    }
  }

  public static void insertItemCategoryId(Connection connection, Item item, long categoryId) throws SQLException {
    if (item == null) {
      return;
    }
    SqlUtils insertValues = new SqlUtils();
    insertValues.add("item_id", item.getId())
        .add("collection_id", item.getCollectionId())
        .addIfExists("dataset_id", item.getDatasetId(), -1);
    insertValues.add("category_id", categoryId);
    DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY);
  }

  public static void removeAll(Connection connection, Item item) throws SQLException {
    SqlUtils where = new SqlUtils();
    where.add("item_id = ?", item.getId());
    DB.deleteFrom(connection, TABLE_NAME, where);
  }

  public static void removeAll(Connection connection, Category category) throws SQLException {
    SqlUtils where = new SqlUtils();
    where.add("category_id = ?", category.getId());
    DB.deleteFrom(connection, TABLE_NAME, where);
  }

  public static void removeAll(Connection connection, Collection collection) throws SQLException {
    SqlUtils where = new SqlUtils();
    where.add("collection_id = ?", collection.getId());
    DB.deleteFrom(connection, TABLE_NAME, where);
  }

  public static void removeItemCategoryId(Connection connection, Item item, long categoryId) throws SQLException {
    SqlUtils where = new SqlUtils();
    where.add("item_id = ?", item.getId());
    where.add("category_id = ?", categoryId);
    DB.deleteFrom(connection, TABLE_NAME, where);
  }

  private static DataResult query(ItemCategorySpecification specification, DataConstraints constraints) {
    SqlUtils select = new SqlUtils();
    SqlUtils where = new SqlUtils();
    SqlUtils orderBy = new SqlUtils();
    if (specification != null) {
      where.addIfExists("item_id = ?", specification.getItemId(), -1);
    }
    return DB.selectAllFrom(
        TABLE_NAME, select, where, orderBy, constraints, ItemCategoryRepository::buildRecord);
  }

  public static ItemCategory findById(long id) {
    if (id == -1) {
      return null;
    }
    return (ItemCategory) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("id = ?", id),
        ItemCategoryRepository::buildRecord);
  }

  public static List<ItemCategory> findAll(ItemCategorySpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("id");
    DataResult result = query(specification, constraints);
    if (result.hasRecords()) {
      return (List<ItemCategory>) result.getRecords();
    }
    return null;
  }

  public static List<ItemCategory> findAllByItemId(long itemId) {
    if (itemId == -1) {
      return null;
    }
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils().add("item_id = ?", itemId),
        null,
        ItemCategoryRepository::buildRecord);
    return (List<ItemCategory>) result.getRecords();
  }

  /**
   * Batch form of {@link #findAllByItemId} for a page of items rendered together (e.g. the Records
   * tab of a Collection at /admin/collection-records) -- one query for the whole page instead of a
   * per-row round trip. Mirrors {@code ImageVariantRepository#findByImageIds}' IN-list pattern
   * (issue #411 PR2): one {@code ?} per id, ids bound as real PreparedStatement parameters via
   * SqlUtils' {@code Long[]} overload, never string-concatenated into the SQL text. An id with no
   * matching rows is simply absent from the returned map (not present with an empty list).
   */
  public static Map<Long, List<ItemCategory>> findAllByItemIds(List<Long> itemIds) {
    Map<Long, List<ItemCategory>> categoriesByItemId = new LinkedHashMap<>();
    if (itemIds == null || itemIds.isEmpty()) {
      return categoriesByItemId;
    }
    StringBuilder placeholders = new StringBuilder();
    for (int i = 0; i < itemIds.size(); i++) {
      if (i > 0) {
        placeholders.append(",");
      }
      placeholders.append("?");
    }
    List<ItemCategory> categories = (List<ItemCategory>) DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils().add("item_id IN (" + placeholders + ")", itemIds.toArray(new Long[0])),
        null,
        ItemCategoryRepository::buildRecord).getRecords();
    for (ItemCategory category : categories) {
      categoriesByItemId.computeIfAbsent(category.getItemId(), k -> new ArrayList<>()).add(category);
    }
    return categoriesByItemId;
  }

  private static ItemCategory buildRecord(ResultSet rs) {
    try {
      ItemCategory record = new ItemCategory();
      record.setId(rs.getLong("id"));
      record.setItemId(rs.getLong("item_id"));
      record.setCategoryId(rs.getLong("category_id"));
      record.setCollectionId(rs.getLong("collection_id"));
      record.setDatasetId(rs.getLong("dataset_id"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
