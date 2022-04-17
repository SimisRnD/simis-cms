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

import com.simisinc.platform.domain.model.ecommerce.FulfillmentOption;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves fulfillment option objects
 *
 * @author matt rajkowski
 * @created 4/9/20 1:30 PM
 */
public class FulfillmentOptionRepository {

  private static Log LOG = LogFactory.getLog(FulfillmentOptionRepository.class);

  private static String TABLE_NAME = "lookup_fulfillment_options";
  private static String PRIMARY_KEY[] = new String[]{"fulfillment_id"};

  public static List<FulfillmentOption> findAll() {
    SqlUtils where = new SqlUtils().add("enabled = ?", true);
    DataResult result = DB.selectAllFrom(TABLE_NAME, where, null, FulfillmentOptionRepository::buildRecord);
    List<FulfillmentOption> recordList = (List<FulfillmentOption>) result.getRecords();
    return recordList;
  }

  public static FulfillmentOption findById(long id) {
    if (id == -1) {
      return null;
    }
    return (FulfillmentOption) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("fulfillment_id = ?", id),
        FulfillmentOptionRepository::buildRecord);
  }

  public static FulfillmentOption findByCode(String code) {
    if (StringUtils.isBlank(code)) {
      return null;
    }
    return (FulfillmentOption) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("code = ?", code),
        FulfillmentOptionRepository::buildRecord);
  }

  public static boolean remove(FulfillmentOption record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("fulfillment_id = ?", record.getId()));
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

  public static FulfillmentOption save(FulfillmentOption record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static FulfillmentOption add(FulfillmentOption record) {
    SqlUtils insertValues = new SqlUtils()
        .add("code", StringUtils.trimToNull(record.getCode()))
        .add("title", StringUtils.trimToNull(record.getTitle()))
        .add("enabled", record.getEnabled())
        .add("overrides_others", record.getOverridesOthers());
    // Use a transaction
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

  public static FulfillmentOption update(FulfillmentOption record) {
    SqlUtils updateValues = new SqlUtils()
        .add("code", StringUtils.trimToNull(record.getCode()))
        .add("title", StringUtils.trimToNull(record.getTitle()))
        .add("enabled", record.getEnabled())
        .add("overrides_others", record.getOverridesOthers());
    SqlUtils where = new SqlUtils().add("fulfillment_id = ?", record.getId());
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        DB.update(connection, TABLE_NAME, updateValues, where);
        // Finish the transaction
        transaction.commit();
        return record;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage(), se);
    }
    return null;
  }

  private static FulfillmentOption buildRecord(ResultSet rs) {
    try {
      FulfillmentOption record = new FulfillmentOption();
      record.setId(rs.getLong("fulfillment_id"));
      record.setCode(rs.getString("code"));
      record.setTitle(rs.getString("title"));
      record.setEnabled(rs.getBoolean("enabled"));
      record.setOverridesOthers(rs.getBoolean("overrides_others"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
