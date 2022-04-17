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

import com.simisinc.platform.domain.model.items.Activity;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Persists and retrieves activity objects
 *
 * @author matt rajkowski
 * @created 8/20/18 11:32 AM
 */
public class ActivityRepository {

  private static Log LOG = LogFactory.getLog(ActivityRepository.class);

  private static String TABLE_NAME = "item_activity_stream";
  private static String PRIMARY_KEY[] = new String[]{"activity_id"};


  public static Activity save(Activity record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  private static Activity add(Activity record) {
    SqlUtils insertValues = new SqlUtils()
        .add("item_id", record.getItemId())
        .add("collection_id", record.getCollectionId())
        .add("activity_type", record.getActivityType())
        .add("message_text", StringUtils.trimToNull(record.getMessageText()))
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy());
    // Use a transaction for related data
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
        // Finish the transaction
        transaction.commit();
        return record;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("An id was not set!");
    return null;
  }


  private static Activity update(Activity record) {
    SqlUtils updateValues = new SqlUtils()
        .add("message_text", StringUtils.trimToNull(record.getMessageText()))
        .add("modified_by", record.getModifiedBy())
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("activity_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(Activity record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("activity_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  public static void removeAll(Connection connection, Item record) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("item_id = ?", record.getId()));
  }

  public static void removeAll(Connection connection, Collection record) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("collection_id = ?", record.getId()));
  }

  private static DataResult query(ActivitySpecification specification, DataConstraints constraints) {
    SqlUtils select = new SqlUtils();
    SqlUtils where = new SqlUtils();
    SqlUtils orderBy = new SqlUtils();
    if (specification != null) {
      where
          .addIfExists("item_id = ?", specification.getItemId(), -1)
          .addIfExists("collection_id = ?", specification.getCollectionId(), -1)
          .addIfExists("created_by = ?", specification.getCreatedBy(), -1);
      if (specification.getActivityType() != null) {
        where.add("upper(activity_type) = ?", specification.getActivityType().trim().toUpperCase());
      }
      if (specification.getMinTimestamp() > 0) {
        where.add("created >= ?", new Timestamp(specification.getMinTimestamp()));
      }
      if (specification.getMaxTimestamp() > 0) {
        where.add("created <= ?", new Timestamp(specification.getMaxTimestamp()));
      }
    }
    return DB.selectAllFrom(
        TABLE_NAME, select, where, orderBy, constraints, ActivityRepository::buildRecord);
  }

  public static Activity findById(long id) {
    if (id == -1) {
      return null;
    }
    return (Activity) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("activity_id = ?", id),
        ActivityRepository::buildRecord);
  }

  public static List<Activity> findAll(ActivitySpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("created desc");
    DataResult result = query(specification, constraints);
    return (List<Activity>) result.getRecords();
  }

  private static Activity buildRecord(ResultSet rs) {
    try {
      Activity record = new Activity();
      record.setId(rs.getLong("activity_id"));
      record.setItemId(rs.getLong("item_id"));
      record.setCollectionId(rs.getLong("collection_id"));
      record.setActivityType(rs.getString("activity_type"));
      record.setMessageText(rs.getString("message_text"));
      record.setSource(rs.getString("source"));
      record.setSourceLink(rs.getString("source_link"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setModified(rs.getTimestamp("modified"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
