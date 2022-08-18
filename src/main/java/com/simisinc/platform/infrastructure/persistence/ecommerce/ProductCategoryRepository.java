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

import com.simisinc.platform.domain.model.ecommerce.ProductCategory;
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
 * Persists and retrieves product category objects
 *
 * @author matt rajkowski
 * @created 4/10/21 5:10 PM
 */
public class ProductCategoryRepository {

  private static Log LOG = LogFactory.getLog(ProductCategoryRepository.class);

  private static String TABLE_NAME = "lookup_product_categories";
  private static String[] PRIMARY_KEY = new String[]{"category_id"};

  public static List<ProductCategory> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("display_order, name").setUseCount(false),
        ProductCategoryRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<ProductCategory>) result.getRecords();
    }
    return null;
  }

  public static ProductCategory findById(long categoryId) {
    if (categoryId == -1) {
      return null;
    }
    return (ProductCategory) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("category_id = ?", categoryId),
        ProductCategoryRepository::buildRecord);
  }

  public static ProductCategory findByUniqueId(String uniqueId) {
    if (StringUtils.isBlank(uniqueId)) {
      return null;
    }
    return (ProductCategory) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("upper(category_unique_id) = ?", uniqueId.toUpperCase()),
        ProductCategoryRepository::buildRecord);
  }

  public static ProductCategory save(ProductCategory record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static ProductCategory add(ProductCategory record) {
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        SqlUtils insertValues = new SqlUtils()
            .add("category_unique_id", record.getUniqueId())
            .add("name", record.getName())
            .add("description", record.getDescription())
            .add("created_by", record.getCreatedBy(), -1)
            .add("modified_by", record.getModifiedBy(), -1)
            .add("enabled", record.getEnabled());
        if (record.getDisplayOrder() > 0) {
          insertValues.add("display_order", record.getDisplayOrder());
        }
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

  public static ProductCategory update(ProductCategory record) {
    SqlUtils updateValues = new SqlUtils()
        .add("category_unique_id", record.getUniqueId())
        .add("name", record.getName())
        .add("description", record.getDescription())
        .add("enabled", record.getEnabled())
        .add("modified_by", record.getModifiedBy(), -1)
        .add("modified", new Timestamp(System.currentTimeMillis()));
    if (record.getDisplayOrder() > 0) {
      updateValues.add("display_order", record.getDisplayOrder());
    }
    SqlUtils where = new SqlUtils()
        .add("category_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
//      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(ProductCategory record) {
    return DB.deleteFrom(TABLE_NAME, new SqlUtils().add("category_id = ?", record.getId())) > 0;
  }

  private static ProductCategory buildRecord(ResultSet rs) {
    try {
      ProductCategory record = new ProductCategory();
      record.setId(rs.getLong("category_id"));
      record.setUniqueId(rs.getString("category_unique_id"));
      record.setName(rs.getString("name"));
      record.setDescription(rs.getString("description"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setModified(rs.getTimestamp("modified"));
      record.setEnabled(rs.getBoolean("enabled"));
      record.setDisplayOrder(DB.getInt(rs, "display_order", 0));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
