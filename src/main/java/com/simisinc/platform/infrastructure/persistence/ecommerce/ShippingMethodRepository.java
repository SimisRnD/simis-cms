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

package com.simisinc.platform.infrastructure.persistence.ecommerce;

import com.simisinc.platform.domain.model.ecommerce.ShippingMethod;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves shipping method objects
 *
 * @author matt rajkowski
 * @created 6/27/19 9:14 AM
 */
public class ShippingMethodRepository {

  private static Log LOG = LogFactory.getLog(ShippingMethodRepository.class);

  private static String TABLE_NAME = "lookup_shipping_method";
  private static String PRIMARY_KEY[] = new String[]{"method_id"};

  public static List<ShippingMethod> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("level"),
        ShippingMethodRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<ShippingMethod>) result.getRecords();
    }
    return null;
  }

  public static ShippingMethod findById(long methodId) {
    return (ShippingMethod) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("method_id = ?", methodId),
        ShippingMethodRepository::buildRecord);
  }

  public static ShippingMethod save(ShippingMethod record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static ShippingMethod add(ShippingMethod record) {
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        SqlUtils insertValues = new SqlUtils()
            .add("level", record.getLevel())
            .add("code", record.getCode())
            .add("title", record.getTitle())
            .add("enabled", record.getEnabled());
//            .addIfExists("created_by", record.getCreatedBy(), -1)
//            .addIfExists("modified_by", record.getModifiedBy(), -1);
        record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
        // Finish the transaction
        transaction.commit();
        return record;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage(), se);
    }
    return null;
  }

  public static ShippingMethod update(ShippingMethod record) {
    SqlUtils updateValues = new SqlUtils()
        .add("level", record.getLevel())
        .add("code", record.getCode())
        .add("title", record.getTitle())
        .add("enabled", record.getEnabled());
//        .add("modified_by", record.getModifiedBy(), -1)
//        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("method_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
//      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  private static ShippingMethod buildRecord(ResultSet rs) {
    try {
      ShippingMethod record = new ShippingMethod();
      record.setId(rs.getLong("method_id"));
      record.setLevel(rs.getInt("level"));
      record.setCode(rs.getString("code"));
      record.setTitle(rs.getString("title"));
      record.setEnabled(rs.getBoolean("enabled"));
      record.setBoxzookaCode(rs.getString("boxzooka_code"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
