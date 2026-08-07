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
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemTag;
import com.simisinc.platform.domain.model.items.Tag;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;
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
 * Persists and retrieves item tag objects (issue #632)
 *
 * @author SimIS
 * @created 8/2/2026
 */
public class ItemTagRepository {

  private static Log LOG = LogFactory.getLog(ItemTagRepository.class);

  private static String TABLE_NAME = "item_tags";
  private static String[] PRIMARY_KEY = new String[] { "id" };

  public static void insertItemTagList(Connection connection, Item item) throws SQLException {
    if (item.getTagIdList() == null) {
      return;
    }
    for (Long tagId : item.getTagIdList()) {
      insertItemTagId(connection, item, tagId);
    }
  }

  public static void insertItemTagId(Connection connection, Item item, long tagId) throws SQLException {
    if (item == null) {
      return;
    }
    SqlUtils insertValues = new SqlUtils()
        .add("item_id", item.getId())
        .add("collection_id", item.getCollectionId())
        .add("tag_id", tagId);
    DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY);
  }

  public static void removeItemTagId(Connection connection, Item item, long tagId) throws SQLException {
    SqlUtils where = new SqlUtils();
    where.add("item_id = ?", item.getId());
    where.add("tag_id = ?", tagId);
    DB.deleteFrom(connection, TABLE_NAME, where);
  }

  public static void removeAll(Connection connection, Item item) throws SQLException {
    SqlUtils where = new SqlUtils();
    where.add("item_id = ?", item.getId());
    DB.deleteFrom(connection, TABLE_NAME, where);
  }

  public static void removeAll(Connection connection, Tag tag) throws SQLException {
    SqlUtils where = new SqlUtils();
    where.add("tag_id = ?", tag.getId());
    DB.deleteFrom(connection, TABLE_NAME, where);
  }

  public static void removeAll(Connection connection, Collection collection) throws SQLException {
    SqlUtils where = new SqlUtils();
    where.add("collection_id = ?", collection.getId());
    DB.deleteFrom(connection, TABLE_NAME, where);
  }

  public static List<ItemTag> findAllByItemId(long itemId) {
    if (itemId == -1) {
      return null;
    }
    return (List<ItemTag>) DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils().add("item_id = ?", itemId),
        null,
        ItemTagRepository::buildRecord).getRecords();
  }

  /**
   * Batch form of {@link #findAllByItemId} for a page of items rendered together (e.g. the Records
   * tab of a Collection at /admin/collection-records) -- one query for the whole page instead of a
   * per-row round trip. Mirrors {@code ItemCategoryRepository#findAllByItemIds}' IN-list pattern
   * (itself mirroring {@code ImageVariantRepository#findByImageIds}, issue #411 PR2): one {@code ?}
   * per id, ids bound as real PreparedStatement parameters via SqlUtils' {@code Long[]} overload,
   * never string-concatenated into the SQL text. An id with no matching rows is simply absent from
   * the returned map (not present with an empty list).
   */
  public static Map<Long, List<ItemTag>> findAllByItemIds(List<Long> itemIds) {
    Map<Long, List<ItemTag>> tagsByItemId = new LinkedHashMap<>();
    if (itemIds == null || itemIds.isEmpty()) {
      return tagsByItemId;
    }
    StringBuilder placeholders = new StringBuilder();
    for (int i = 0; i < itemIds.size(); i++) {
      if (i > 0) {
        placeholders.append(",");
      }
      placeholders.append("?");
    }
    List<ItemTag> tags = (List<ItemTag>) DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils().add("item_id IN (" + placeholders + ")", itemIds.toArray(new Long[0])),
        null,
        ItemTagRepository::buildRecord).getRecords();
    for (ItemTag tag : tags) {
      tagsByItemId.computeIfAbsent(tag.getItemId(), k -> new ArrayList<>()).add(tag);
    }
    return tagsByItemId;
  }

  private static ItemTag buildRecord(ResultSet rs) {
    try {
      ItemTag record = new ItemTag();
      record.setId(rs.getLong("id"));
      record.setItemId(rs.getLong("item_id"));
      record.setTagId(rs.getLong("tag_id"));
      record.setCollectionId(rs.getLong("collection_id"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
