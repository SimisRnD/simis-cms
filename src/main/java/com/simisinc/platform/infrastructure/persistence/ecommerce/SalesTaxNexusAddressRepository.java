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

import com.simisinc.platform.domain.model.ecommerce.SalesTaxNexusAddress;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/29/19 1:36 PM
 */
public class SalesTaxNexusAddressRepository {

  private static Log LOG = LogFactory.getLog(SalesTaxNexusAddressRepository.class);

  private static String TABLE_NAME = "sales_tax_nexus_addresses";
  private static String PRIMARY_KEY[] = new String[]{"address_id"};

  public static List<SalesTaxNexusAddress> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("address_id"),
        SalesTaxNexusAddressRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<SalesTaxNexusAddress>) result.getRecords();
    }
    return null;
  }

  public static SalesTaxNexusAddress findById(long addressId) {
    return (SalesTaxNexusAddress) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("address_id = ?", addressId),
        SalesTaxNexusAddressRepository::buildRecord);
  }

  public static SalesTaxNexusAddress save(SalesTaxNexusAddress record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static boolean remove(SalesTaxNexusAddress record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("address_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("The delete failed!");
    return false;
  }

  public static SalesTaxNexusAddress add(SalesTaxNexusAddress record) {
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        SqlUtils insertValues = new SqlUtils()
            .add("street_address", record.getStreet())
            .add("address_line_2", record.getAddressLine2())
            .add("city", record.getCity())
            .add("state", record.getState())
            .add("country", record.getCountry())
            .add("postal_code", record.getPostalCode())
            .add("latitude", record.getLatitude())
            .add("longitude", record.getLongitude())
            .add("created_by", record.getCreatedBy())
            .add("modified_by", record.getModifiedBy());
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

  public static SalesTaxNexusAddress update(SalesTaxNexusAddress record) {
    SqlUtils updateValues = new SqlUtils()
        .add("street_address", record.getStreet())
        .add("address_line_2", record.getAddressLine2())
        .add("city", record.getCity())
        .add("state", record.getState())
        .add("country", record.getCountry())
        .add("postal_code", record.getPostalCode())
        .add("latitude", record.getLatitude())
        .add("longitude", record.getLongitude())
        .add("modified_by", record.getModifiedBy())
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("address_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
//      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  private static SalesTaxNexusAddress buildRecord(ResultSet rs) {
    try {
      SalesTaxNexusAddress record = new SalesTaxNexusAddress();
      record.setId(rs.getLong("address_id"));
      record.setStreet(rs.getString("street_address"));
      record.setAddressLine2(rs.getString("address_line_2"));
      record.setCity(rs.getString("city"));
      record.setState(rs.getString("state"));
      record.setCountry(rs.getString("country"));
      record.setPostalCode(rs.getString("postal_code"));
      record.setLatitude(rs.getDouble("latitude"));
      record.setLongitude(rs.getDouble("longitude"));
      record.setCreated(rs.getTimestamp("created"));
      record.setCreatedBy(DB.getLong(rs, "created_by", -1));
      record.setModified(rs.getTimestamp("modified"));
      record.setModifiedBy(DB.getLong(rs, "modified_by", -1));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
