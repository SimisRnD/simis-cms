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

import com.simisinc.platform.domain.model.ecommerce.ShippingCarrier;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves shipping carrier objects
 *
 * @author matt rajkowski
 * @created 4/23/20 7:00 AM
 */
public class ShippingCarrierRepository {

  private static Log LOG = LogFactory.getLog(ShippingCarrierRepository.class);

  private static String TABLE_NAME = "lookup_shipping_carrier";
  private static String[] PRIMARY_KEY = new String[]{"carrier_id"};

  public static List<ShippingCarrier> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("level"),
        ShippingCarrierRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<ShippingCarrier>) result.getRecords();
    }
    return null;
  }

  public static ShippingCarrier findById(long carrierId) {
    if (carrierId == -1) {
      return null;
    }
    return (ShippingCarrier) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("carrier_id = ?", carrierId),
        ShippingCarrierRepository::buildRecord);
  }

  public static ShippingCarrier findByCode(String code) {
    if (StringUtils.isBlank(code)) {
      return null;
    }
    return (ShippingCarrier) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("upper(code) = ?", code.toUpperCase()),
        ShippingCarrierRepository::buildRecord);
  }

  public static ShippingCarrier save(ShippingCarrier record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static ShippingCarrier add(ShippingCarrier record) {
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

  public static ShippingCarrier update(ShippingCarrier record) {
    SqlUtils updateValues = new SqlUtils()
        .add("level", record.getLevel())
        .add("code", record.getCode())
        .add("title", record.getTitle())
        .add("enabled", record.getEnabled());
//        .add("modified_by", record.getModifiedBy(), -1)
//        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("carrier_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
//      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  private static ShippingCarrier buildRecord(ResultSet rs) {
    try {
      ShippingCarrier record = new ShippingCarrier();
      record.setId(rs.getLong("carrier_id"));
      record.setLevel(rs.getInt("level"));
      record.setCode(rs.getString("code"));
      record.setTitle(rs.getString("title"));
      record.setEnabled(rs.getBoolean("enabled"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
