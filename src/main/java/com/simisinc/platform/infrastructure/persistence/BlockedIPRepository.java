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

package com.simisinc.platform.infrastructure.persistence;

import com.simisinc.platform.domain.model.BlockedIP;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves blocked IP objects
 *
 * @author matt rajkowski
 * @created 3/25/20 10:10 AM
 */
public class BlockedIPRepository {

  private static Log LOG = LogFactory.getLog(BlockedIPRepository.class);

  private static String TABLE_NAME = "block_list";
  private static String[] PRIMARY_KEY = new String[]{"block_list_id"};

  private static DataResult query(DataConstraints constraints) {
    return DB.selectAllFrom(TABLE_NAME, null, constraints, BlockedIPRepository::buildRecord);
  }

  public static List<BlockedIP> findAll() {
    return findAll(null);
  }

  public static List<BlockedIP> findAll(DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("created DESC");
    DataResult result = query(constraints);
    return (List<BlockedIP>) result.getRecords();
  }

  public static BlockedIP findById(long id) {
    if (id == -1) {
      return null;
    }
    return (BlockedIP) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("block_list_id = ?", id),
        BlockedIPRepository::buildRecord);
  }

  public static BlockedIP findByIpAddress(String ipAddress) {
    if (StringUtils.isBlank(ipAddress)) {
      return null;
    }
    return (BlockedIP) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("ip_address = ?", ipAddress),
        BlockedIPRepository::buildRecord);
  }

  public static BlockedIP save(BlockedIP record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static BlockedIP add(BlockedIP record) {
    SqlUtils insertValues = new SqlUtils()
        .add("ip_address", StringUtils.trimToNull(record.getIpAddress()))
        .addIfExists("reason", StringUtils.trimToNull(record.getReason()))
        .addIfExists("created", record.getCreated());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static BlockedIP update(BlockedIP record) {
    SqlUtils updateValues = new SqlUtils()
        .add("ip_address", StringUtils.trimToNull(record.getIpAddress()))
        .addIfExists("reason", StringUtils.trimToNull(record.getReason()));
    SqlUtils where = new SqlUtils()
        .add("block_list_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
//      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(BlockedIP record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
//        ItemCategoryRepository.removeAll(connection, record);
//        CollectionRepository.updateItemCount(connection, record.getCollectionId(), -1);
//        CategoryRepository.updateItemCount(connection, record.getCategoryId(), -1);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("block_list_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  private static BlockedIP buildRecord(ResultSet rs) {
    try {
      BlockedIP record = new BlockedIP();
      record.setId(rs.getLong("block_list_id"));
      record.setIpAddress(rs.getString("ip_address"));
      record.setCreated(rs.getTimestamp("created"));
      record.setReason(rs.getString("reason"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }

  public static void export(DataConstraints constraints, File file) {
    SqlUtils selectFields = new SqlUtils()
        .addNames(
            "ip_address AS \"IP Address\"",
            "created AS \"Date\"",
            "reason AS \"Reason\""
        );
    // Use the specification to filter results
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("block_list_id");
    DB.exportToCsvAllFrom(TABLE_NAME, selectFields, null, null, null, constraints, file);
  }
}
