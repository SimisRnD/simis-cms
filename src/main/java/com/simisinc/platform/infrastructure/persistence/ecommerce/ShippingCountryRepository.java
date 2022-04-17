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

import com.simisinc.platform.domain.model.ecommerce.ShippingCountry;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves shipping country objects
 *
 * @author matt rajkowski
 * @created 6/27/19 11:52 AM
 */
public class ShippingCountryRepository {

  private static Log LOG = LogFactory.getLog(ShippingCountryRepository.class);

  private static String TABLE_NAME = "lookup_shipping_countries";
  private static String PRIMARY_KEY[] = new String[]{"country_id"};

  public static List<ShippingCountry> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("level"),
        ShippingCountryRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<ShippingCountry>) result.getRecords();
    }
    return null;
  }

  public static ShippingCountry findById(long countryId) {
    return (ShippingCountry) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("country_id = ?", countryId),
        ShippingCountryRepository::buildRecord);
  }

  public static ShippingCountry findByEnabledCountry(String name) {
    return (ShippingCountry) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("LOWER(title) = ?", name.toLowerCase())
            .add("enabled = ?", true),
        ShippingCountryRepository::buildRecord);
  }

  public static ShippingCountry save(ShippingCountry record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static ShippingCountry add(ShippingCountry record) {
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

  public static ShippingCountry update(ShippingCountry record) {
    SqlUtils updateValues = new SqlUtils()
        .add("level", record.getLevel())
        .add("code", record.getCode())
        .add("title", record.getTitle())
        .add("enabled", record.getEnabled());
//        .add("modified_by", record.getModifiedBy(), -1)
//        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("country_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
//      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  private static ShippingCountry buildRecord(ResultSet rs) {
    try {
      ShippingCountry record = new ShippingCountry();
      record.setId(rs.getLong("country_id"));
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
