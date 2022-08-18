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

import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemRelationship;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves item relationship objects
 *
 * @author matt rajkowski
 * @created 7/27/18 4:54 PM
 */
public class ItemRelationshipRepository {

  private static Log LOG = LogFactory.getLog(ItemRelationshipRepository.class);

  private static String TABLE_NAME = "item_relationships";
  private static String[] PRIMARY_KEY = new String[]{"relationship_id"};

  public static ItemRelationship findById(long relationshipId) {
    if (relationshipId == -1) {
      return null;
    }
    ItemRelationship relationship = (ItemRelationship) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("relationship_id = ?", relationshipId),
        ItemRelationshipRepository::buildRecord);
    return relationship;
  }

  public static List<ItemRelationship> findRelatedItemsForItemId(long itemId) {
    if (itemId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("item_id = ?", itemId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("relationship_id").setUseCount(false),
        ItemRelationshipRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<ItemRelationship>) result.getRecords();
    }
    return null;
  }

  public static List<ItemRelationship> findRelatedItemsForItemIdInCollection(Item item, Collection collection) {
    if (item == null || collection == null) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("item_id = ?", item.getId())
        .add("related_collection_id = ?", collection.getId());
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("relationship_id"),
        ItemRelationshipRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<ItemRelationship>) result.getRecords();
    }
    return null;
  }

  public static boolean isAuthorizedForUser(Item item, Collection relatedCollection, long userId) {
    if (item == null || relatedCollection == null || userId < 1) {
      return false;
    }
    SqlUtils where = new SqlUtils()
        .add("item_id = ?", item.getId())
        .add("related_collection_id = ?", relatedCollection.getId())
        .add("EXISTS (SELECT 1 FROM members WHERE members.item_id = item_relationships.related_item_id AND members.user_id = ? AND members.approved IS NOT NULL AND archived IS NULL)", userId);
    long count = DB.selectCountFrom(TABLE_NAME, where);
    if (count > 0) {
      return true;
    }
    return false;
  }

  public static boolean isAuthorizedForUser(Item item, Collection relatedCollection, long userId, long collectionRoleId) {
    if (item == null || relatedCollection == null || userId < 1) {
      return false;
    }
    SqlUtils where = new SqlUtils()
        .add("item_id = ?", item.getId())
        .add("related_collection_id = ?", relatedCollection.getId())
        .add("EXISTS (" +
            "SELECT 1 FROM members WHERE members.item_id = item_relationships.related_item_id AND members.user_id = ? AND members.approved IS NOT NULL AND archived IS NULL " +
            "AND EXISTS (SELECT 1 FROM member_roles WHERE members.member_id = member_roles.member_id AND role_id = ?)" +
            ")", new Long[]{userId, collectionRoleId});
    long count = DB.selectCountFrom(TABLE_NAME, where);
    if (count > 0) {
      return true;
    }
    return false;
  }

  public static ItemRelationship save(ItemRelationship record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static ItemRelationship add(ItemRelationship record) {
    // Save First Relationship record
    SqlUtils insertValues = new SqlUtils()
        .add("item_id", record.getItemId())
        .add("collection_id", record.getCollectionId())
        .add("related_item_id", record.getRelatedItemId())
        .add("related_collection_id", record.getRelatedCollectionId())
//        .add("relationship_type", record.getRelationshipTypeId())
        .add("is_active", record.getIsActive())
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy())
        .add("start_date", record.getStartDate())
        .add("end_date", record.getEndDate());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    // Save Second Relationship record
    SqlUtils reciprocalInsertValues = new SqlUtils()
        .add("item_id", record.getRelatedItemId())
        .add("collection_id", record.getRelatedCollectionId())
        .add("related_item_id", record.getItemId())
        .add("related_collection_id", record.getCollectionId())
//        .add("relationship_type", record.getRelationshipTypeId())
        .add("is_active", record.getIsActive())
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy())
        .add("start_date", record.getStartDate())
        .add("end_date", record.getEndDate());
    DB.insertInto(TABLE_NAME, reciprocalInsertValues, PRIMARY_KEY);

    return record;
  }

  private static ItemRelationship update(ItemRelationship record) {
    SqlUtils updateValues = new SqlUtils()
        .add("is_active", record.getIsActive())
        .add("modified_by", record.getModifiedBy());
    SqlUtils where = new SqlUtils()
        .add("relationship_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static void remove(ItemRelationship itemRelationship) {
    DB.deleteFrom(TABLE_NAME, new SqlUtils().add("relationship_id = ?", itemRelationship.getId()));
  }

  public static void remove(Connection connection, ItemRelationship itemRelationship) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("relationship_id = ?", itemRelationship.getId()));
  }

  public static void removeAll(Connection connection, Item item) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("item_id = ?", item.getId()));
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("related_item_id = ?", item.getId()));
  }

  public static void removeRelationship(Item item, Item relatedItem) {
    DB.deleteFrom(TABLE_NAME,
        new SqlUtils().add("((item_id = ? AND related_item_id = ?) OR (item_id = ? AND related_item_id = ?))",
            new Long[]{item.getId(), relatedItem.getId(), relatedItem.getId(), item.getId()}));
  }

  private static ItemRelationship buildRecord(ResultSet rs) {
    try {
      ItemRelationship record = new ItemRelationship();
      record.setId(rs.getLong("relationship_id"));
      record.setItemId(rs.getLong("item_id"));
      record.setRelatedItemId(rs.getLong("related_item_id"));
      record.setRelatedCollectionId(rs.getLong("related_collection_id"));
      record.setRelationshipTypeId(rs.getLong("relationship_type"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setIsActive(rs.getBoolean("is_active"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setModified(rs.getTimestamp("modified"));
      record.setStartDate(rs.getTimestamp("start_date"));
      record.setEndDate(rs.getTimestamp("end_date"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
