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

import com.simisinc.platform.application.ecommerce.ProductJSONCommand;
import com.simisinc.platform.domain.model.ecommerce.Product;
import com.simisinc.platform.domain.model.ecommerce.ProductSku;
import com.simisinc.platform.infrastructure.database.*;
import com.simisinc.platform.presentation.controller.DataConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Persists and retrieves product objects
 *
 * @author matt rajkowski
 * @created 3/17/19 4:46 PM
 */
public class ProductRepository {

  private static Log LOG = LogFactory.getLog(ProductRepository.class);

  private static String TABLE_NAME = "products";
  private static String[] PRIMARY_KEY = new String[]{"product_id"};

  private static DataResult query(ProductSpecification specification, DataConstraints constraints) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("products.product_id = ?", specification.getId(), -1)
          .addIfExists("products.product_unique_id = ?", specification.getProductUniqueId());
      if (specification.getWithProductUniqueIdList() != null && !specification.getWithProductUniqueIdList().isEmpty()) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < specification.getWithProductUniqueIdList().size(); i++) {
          if (i > 0) {
            sb.append(",");
          }
          sb.append("?");
        }
        if (sb.length() > 0) {
          where.add("products.product_unique_id IN (" + sb.toString() + ")", specification.getWithProductUniqueIdList().toArray(new String[0]));
        }
      }
      if (specification.getIsForSale() != DataConstants.UNDEFINED) {
        if (specification.getIsForSale() == DataConstants.TRUE) {
          where.add("products.enabled = true");
          // @todo add the dates
          // today >= activeDate
          // today < deactivateOnDate
          // has a product_sku with at least 1 visible sku.getEnabled()
          where.add("EXISTS (SELECT 1 FROM product_skus WHERE product_id = products.product_id AND enabled = " + (specification.getIsForSale() == DataConstants.TRUE ? "true" : "false") + ")");
        }
      }
    }
    return DB.selectAllFrom(TABLE_NAME, null, where, constraints, ProductRepository::buildRecord);
  }

  public static List<Product> findAll() {
    return findAll(null, null);
  }

  public static List<Product> findAll(ProductSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("product_order, name, caption");
    DataResult result = query(specification, constraints);
    List<Product> productList = (List<Product>) result.getRecords();
    for (Product product : productList) {
      populateRelatedData(product);
    }
    return productList;
  }

  public static Product findById(long id) {
    return findById(id, true);
  }

  public static Product findById(long id, boolean includeRelatedData) {
    if (id == -1) {
      return null;
    }
    Product product = (Product) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("product_id = ?", id),
        ProductRepository::buildRecord);
    if (includeRelatedData) {
      populateRelatedData(product);
    }
    return product;
  }

  public static Product findByName(String name) {
    if (StringUtils.isBlank(name)) {
      return null;
    }
    Product product = (Product) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("LOWER(name) = ?", name.toLowerCase()),
        ProductRepository::buildRecord);
    populateRelatedData(product);
    return product;
  }

  public static Product findByUniqueId(String uniqueId) {
    if (StringUtils.isBlank(uniqueId)) {
      return null;
    }
    Product product = (Product) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("product_unique_id = ?", uniqueId),
        ProductRepository::buildRecord);
    populateRelatedData(product);
    return product;
  }

  public static Product findBySku(String sku) {
    if (StringUtils.isBlank(sku)) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("EXISTS (SELECT 1 FROM product_skus WHERE product_id = products.product_id AND sku = ?)", sku.toUpperCase().trim());
    Product product = (Product) DB.selectRecordFrom(
        TABLE_NAME,
        where,
        ProductRepository::buildRecord);
    populateRelatedData(product);
    return product;
  }

  private static void populateRelatedData(Product product) {
    if (product == null) {
      return;
    }
    ProductSkuSpecification specification = new ProductSkuSpecification();
    specification.setProductId(product.getId());
    List<ProductSku> productSKUList = ProductSkuRepository.findAll(specification, null);
    product.setProducts(productSKUList);
  }

  public static boolean remove(Product record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        ProductSkuRepository.removeAll(connection, record);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("product_id = ?", record.getId()));
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

  public static Product save(Product record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static Product add(Product record) {
    SqlUtils insertValues = new SqlUtils()
        .add("product_unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("product_order", record.getOrder())
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("description", StringUtils.trimToNull(record.getDescription()))
        .add("caption", StringUtils.trimToNull(record.getCaption()))
        .add("is_good", record.getIsGood())
        .add("is_service", record.getIsService())
        .add("is_virtual", record.getIsVirtual())
        .add("is_download", record.getIsDownload())
        .add("fulfillment_id", record.getFulfillmentId(), -1)
        .add("taxable", record.getTaxable())
        .add("tax_code", StringUtils.trimToNull(record.getTaxCode()))
        .add("active_date", record.getActiveDate())
        .add("deactivate_on", record.getDeactivateOnDate())
        .add("available_date", record.getAvailableDate())
        .add("shippable", record.getShippable())
        .add("package_height", record.getPackageHeight())
        .add("package_length", record.getPackageLength())
        .add("package_width", record.getPackageWidth())
        .add("package_weight_lbs", record.getPackageWeightPounds())
        .add("package_weight_ozs", record.getPackageWeightOunces())
        .add("image_url", StringUtils.trimToNull(record.getImageUrl()))
        .add("product_url", StringUtils.trimToNull(record.getProductUrl()))
        .add("exclude_us_states", StringUtils.trimToNull(record.getExcludeUsStates()))
        .add("created_by", record.getCreatedBy(), -1)
        .add("modified_by", record.getModifiedBy(), -1)
//        .add("enabled", record.getEnabled());
        .add("enabled", true);
    insertValues.add(new SqlValue("sku_attributes", SqlValue.JSONB_TYPE, ProductJSONCommand.createJSONString(record)));
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
        // Manage the Product SKUs
        ProductSkuRepository.saveProductSKUList(connection, record);
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

  public static Product update(Product record) {
    SqlUtils updateValues = new SqlUtils()
        .add("product_unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("product_order", record.getOrder())
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("description", StringUtils.trimToNull(record.getDescription()))
        .add("caption", StringUtils.trimToNull(record.getCaption()))
        .add("is_good", record.getIsGood())
        .add("is_service", record.getIsService())
        .add("is_virtual", record.getIsVirtual())
        .add("is_download", record.getIsDownload())
        .add("fulfillment_id", record.getFulfillmentId(), -1)
        .add("taxable", record.getTaxable())
        .add("tax_code", StringUtils.trimToNull(record.getTaxCode()))
        .add("active_date", record.getActiveDate())
        .add("deactivate_on", record.getDeactivateOnDate())
        .add("available_date", record.getAvailableDate())
        .add("shippable", record.getShippable())
        .add("package_height", record.getPackageHeight())
        .add("package_length", record.getPackageLength())
        .add("package_width", record.getPackageWidth())
        .add("package_weight_lbs", record.getPackageWeightPounds())
        .add("package_weight_ozs", record.getPackageWeightOunces())
        .add("image_url", StringUtils.trimToNull(record.getImageUrl()))
        .add("product_url", StringUtils.trimToNull(record.getProductUrl()))
        .add("exclude_us_states", StringUtils.trimToNull(record.getExcludeUsStates()))
        .add("modified_by", record.getModifiedBy())
        .add("modified", new Timestamp(System.currentTimeMillis()));
    updateValues.add(new SqlValue("sku_attributes", SqlValue.JSONB_TYPE, ProductJSONCommand.createJSONString(record)));
    SqlUtils where = new SqlUtils().add("product_id = ?", record.getId());
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        DB.update(connection, TABLE_NAME, updateValues, where);
        // Manage the Product SKUs
        ProductSkuRepository.saveProductSKUList(connection, record);
        // Finish the transaction
        transaction.commit();
        return record;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage(), se);
    }
    return null;
  }

  public static boolean updateSquareCatalogIdForProductId(long productId, String squareCatalogId) {
    SqlUtils updateValues = new SqlUtils()
        .add("square_catalog_id", squareCatalogId)
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("product_id = ?", productId);
    DB.update(TABLE_NAME, updateValues, where);
    return true;
  }

  private static Product buildRecord(ResultSet rs) {
    try {
      Product record = new Product();
      record.setId(rs.getLong("product_id"));
      record.setOrder(rs.getInt("product_order"));
      record.setName(rs.getString("name"));
      record.setUniqueId(rs.getString("product_unique_id"));
      record.setDescription(rs.getString("description"));
      record.setCaption(rs.getString("caption"));
      record.setIsGood(rs.getBoolean("is_good"));
      record.setIsService(rs.getBoolean("is_service"));
      record.setIsVirtual(rs.getBoolean("is_virtual"));
      record.setIsDownload(rs.getBoolean("is_download"));
      record.setActiveDate(rs.getTimestamp("active_date"));
      record.setDeactivateOnDate(rs.getTimestamp("deactivate_on"));
      record.setAvailableDate(rs.getTimestamp("available_date"));
      record.setShippable(rs.getBoolean("shippable"));
      record.setPackageHeight(rs.getBigDecimal("package_height"));
      record.setPackageLength(rs.getBigDecimal("package_length"));
      record.setPackageWidth(rs.getBigDecimal("package_width"));
      record.setPackageWeightPounds(rs.getInt("package_weight_lbs"));
      record.setPackageWeightOunces(rs.getInt("package_weight_ozs"));
      ProductJSONCommand.populateFromJSONString(record, rs.getString("sku_attributes"));
      record.setImageUrl(rs.getString("image_url"));
      record.setProductUrl(rs.getString("product_url"));
      record.setCreated(rs.getTimestamp("created"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setModified(rs.getTimestamp("modified"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setEnabled(rs.getBoolean("enabled"));
      record.setTaxable(rs.getBoolean("taxable"));
      record.setTaxCode(rs.getString("tax_code"));
      record.setSquareCatalogId(rs.getString("square_catalog_id"));
      record.setFulfillmentId(DB.getInt(rs, "fulfillment_id", -1));
      record.setExcludeUsStates(rs.getString("exclude_us_states"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
