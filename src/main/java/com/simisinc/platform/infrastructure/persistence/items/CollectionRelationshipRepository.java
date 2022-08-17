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
import com.simisinc.platform.domain.model.items.CollectionRelationship;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves collection relationship objects
 *
 * @author matt rajkowski
 * @created 7/26/18 1:09 PM
 */
public class CollectionRelationshipRepository {

  private static Log LOG = LogFactory.getLog(CollectionRelationshipRepository.class);

  private static String TABLE_NAME = "collection_relationships";
  private static String[] PRIMARY_KEY = new String[]{"relationship_id"};

  public static List<CollectionRelationship> findAllParentsByCollectionId(long collectionId) {
    if (collectionId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("related_collection_id = ?", collectionId)
        .add("collection_id != related_collection_id");
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("relationship_id"),
        CollectionRelationshipRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<CollectionRelationship>) result.getRecords();
    }
    return null;
  }

  public static List<CollectionRelationship> findAllSelfByCollectionId(long collectionId) {
    if (collectionId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("collection_id = ?", collectionId)
        .add("related_collection_id = ?", collectionId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("relationship_id"),
        CollectionRelationshipRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<CollectionRelationship>) result.getRecords();
    }
    return null;
  }

  public static List<CollectionRelationship> findAllChildrenByCollectionId(long collectionId) {
    if (collectionId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("collection_id = ?", collectionId)
        .add("collection_id != related_collection_id");
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("relationship_id"),
        CollectionRelationshipRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<CollectionRelationship>) result.getRecords();
    }
    return null;
  }

  public static CollectionRelationship findById(long relationshipId) {
    if (relationshipId == -1) {
      return null;
    }
    CollectionRelationship relationship = (CollectionRelationship) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("relationship_id = ?", relationshipId),
        CollectionRelationshipRepository::buildRecord);
    return relationship;
  }

  public static CollectionRelationship save(CollectionRelationship record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static CollectionRelationship add(CollectionRelationship record) {
    SqlUtils insertValues = new SqlUtils()
        .add("collection_id", record.getCollectionId())
        .add("related_collection_id", record.getRelatedCollectionId())
        .add("is_active", record.getIsActive())
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  private static CollectionRelationship update(CollectionRelationship record) {
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

  public static void remove(CollectionRelationship collectionRelationship) {
    DB.deleteFrom(TABLE_NAME, new SqlUtils().add("relationship_id = ?", collectionRelationship.getId()));
  }

  public static void remove(Connection connection, CollectionRelationship collectionRelationship) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("relationship_id = ?", collectionRelationship.getId()));
  }

  public static void removeAll(Connection connection, Collection collection) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("collection_id = ?", collection.getId()));
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("related_collection_id = ?", collection.getId()));
  }

  private static CollectionRelationship buildRecord(ResultSet rs) {
    try {
      CollectionRelationship record = new CollectionRelationship();
      record.setId(rs.getLong("relationship_id"));
      record.setCollectionId(rs.getLong("collection_id"));
      record.setRelatedCollectionId(rs.getLong("related_collection_id"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setIsActive(rs.getBoolean("is_active"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setModified(rs.getTimestamp("modified"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
