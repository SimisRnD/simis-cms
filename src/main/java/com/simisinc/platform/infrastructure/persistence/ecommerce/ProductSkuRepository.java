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

import com.simisinc.platform.application.cms.DateCommand;
import com.simisinc.platform.application.ecommerce.ProductInventoryCommand;
import com.simisinc.platform.application.ecommerce.ProductSkuJSONCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.ecommerce.Product;
import com.simisinc.platform.domain.model.ecommerce.ProductSku;
import com.simisinc.platform.domain.model.ecommerce.ProductSkuAttribute;
import com.simisinc.platform.infrastructure.database.*;
import com.simisinc.platform.presentation.controller.DataConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.util.List;

/**
 * Persists and retrieves product sku objects
 *
 * @author matt rajkowski
 * @created 3/17/19 4:46 PM
 */
public class ProductSkuRepository {

  private static Log LOG = LogFactory.getLog(ProductSkuRepository.class);

  private static String TABLE_NAME = "product_skus";
  private static String ADDITIONAL_SELECT =
      "products.active_date AS product_active_date," +
          "products.deactivate_on AS product_deactivate_on";
  private static String JOIN = "LEFT JOIN products ON (product_skus.product_id = products.product_id)";
  private static String[] PRIMARY_KEY = new String[]{"sku_id"};

  private static DataResult query(ProductSkuSpecification specification, DataConstraints constraints) {
    SqlUtils select = new SqlUtils().add(ADDITIONAL_SELECT);
    SqlJoins joins = new SqlJoins().add(JOIN);
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("sku_id = ?", specification.getId(), -1)
          .addIfExists("sku = ?", specification.getSku())
          .addIfExists("product_skus.product_id = ?", specification.getProductId(), -1)
          .addIfExists("products.product_unique_id = ?", specification.getProductUniqueId());
      if (specification.getIsNotId() != -1) {
        where.add("sku_id <> ?", specification.getIsNotId());
      }
      if (specification.getShowOnline() != DataConstants.UNDEFINED) {
        where.add("product_skus.enabled = " + (specification.getShowOnline() == DataConstants.TRUE ? "true" : "false"));
      }
      if (specification.getWithProductSkuAttributeList() != null && !specification.getWithProductSkuAttributeList().isEmpty()) {
        // SELECT * FROM product_skus WHERE attributes @> '[{"name":"attribute0", "value":"0.5 oz"}]' AND attributes @> '[{"name":"attribute1", "value":"Jasmine"}]';
        // SELECT * FROM product_skus WHERE attributes @> '[{"name":"attribute0", "value":"1.7 oz"}, {"name":"attribute1", "value":"Jasmine"}]';
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (ProductSkuAttribute skuAttribute : specification.getWithProductSkuAttributeList()) {
          if (count > 0) {
            sb.append(",");
          }
          ++count;
          sb.append("{");
          sb.append("\"").append("name").append("\"").append(":").append("\"").append(JsonCommand.toJson(skuAttribute.getName())).append("\"").append(",");
          sb.append("\"").append("value").append("\"").append(":").append("\"").append(JsonCommand.toJson(skuAttribute.getValue())).append("\"");
          sb.append("}");
        }
        if (sb.length() > 0) {
          where.add("attributes @> '[" + sb.toString() + "]'");
        }
      }
    }
    return DB.selectAllFrom(TABLE_NAME, select, joins, where, null, constraints, ProductSkuRepository::buildRecord);
  }

  public static List<ProductSku> findAll(ProductSkuSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("sku_order, sku");
    DataResult result = query(specification, constraints);
    return (List<ProductSku>) result.getRecords();
  }

  public static ProductSku findById(long id) {
    if (id == -1) {
      return null;
    }
    SqlUtils select = new SqlUtils().add(ADDITIONAL_SELECT);
    SqlJoins joins = new SqlJoins().add(JOIN);
    return (ProductSku) DB.selectRecordFrom(
        TABLE_NAME,
        select,
        joins,
        new SqlUtils().add("sku_id = ?", id),
        ProductSkuRepository::buildRecord);
  }

  public static List<ProductSku> findAllByProductId(long productId) {
    if (productId == -1) {
      return null;
    }
    SqlUtils select = new SqlUtils().add(ADDITIONAL_SELECT);
    SqlJoins joins = new SqlJoins().add(JOIN);
    SqlUtils where = new SqlUtils()
        .add("product_skus.product_id = ?", productId);

    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        select,
        joins,
        where,
        null,
        new DataConstraints().setDefaultColumnToSortBy("sku_id").setUseCount(false),
        ProductSkuRepository::buildRecord);
    return (List<ProductSku>) result.getRecords();
  }

  public static void saveProductSKUList(Connection connection, Product product) throws SQLException {
    if (product.getProducts() == null) {
      return;
    }
    for (ProductSku record : product.getProducts()) {
      // Pass values from the product
      record.setProductId(product.getId());
      record.setCreatedBy(product.getCreatedBy());
      record.setModifiedBy(product.getModifiedBy());
      // Determine the action
      if (StringUtils.isBlank(record.getSku())) {
        if (record.getId() > -1) {
          // @Delete the SKU
          // Skip it
        }
        continue;
      }
      save(connection, record);
    }
  }

  public static void remove(ProductSku record) {
    DB.deleteFrom(TABLE_NAME, new SqlUtils().add("sku_id = ?", record.getId()));
  }

  public static void removeAll(Connection connection, Product product) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("product_id = ?", product.getId()));
  }

  public static ProductSku save(Connection connection, ProductSku record) throws SQLException {
    if (record.getId() > -1) {
      return update(connection, record);
    }
    return add(connection, record);
  }

  public static ProductSku add(Connection connection, ProductSku record) throws SQLException {
    SqlUtils insertValues = new SqlUtils()
        .add("product_id", record.getProductId())
        .add("sku", StringUtils.trimToNull(record.getSku()))
        .add("currency", record.getCurrency())
        .add("price", record.getPrice())
        .add("strike_price", record.getStrikePrice())
        .add("cost_of_good", record.getCostOfGood())
        .add("barcode", record.getBarcode())
        .add("active_date", record.getActiveDate())
        .add("deactivate_on", record.getDeactivateOnDate())
        .add("available_date", record.getAvailableDate())
        .add("inventory_qty", record.getInventoryQty())
        .add("inventory_qty_low", record.getInventoryLow())
        .add("inventory_qty_incoming", record.getInventoryIncoming())
        .add("minimum_purchase_qty", record.getMinimumPurchaseQty())
        .add("maximum_purchase_qty", record.getMaximumPurchaseQty())
        .add("allow_backorders", record.getAllowBackorders())
        .add("package_height", record.getPackageHeight())
        .add("package_length", record.getPackageLength())
        .add("package_width", record.getPackageWidth())
        .add("package_weight_lbs", record.getPackageWeightPounds())
        .add("package_weight_ozs", record.getPackageWeightOunces())
        .add("enabled", record.getEnabled())
        .add("created_by", record.getCreatedBy(), -1)
        .add("modified_by", record.getModifiedBy(), -1);
    insertValues.add(new SqlValue("attributes", SqlValue.JSONB_TYPE, ProductSkuJSONCommand.createJSONString(record)));
    record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static ProductSku update(Connection connection, ProductSku record) throws SQLException {
    SqlUtils updateValues = new SqlUtils()
        .add("sku", StringUtils.trimToNull(record.getSku()))
        .add("currency", record.getCurrency())
        .add("price", record.getPrice())
        .add("strike_price", record.getStrikePrice())
        .add("cost_of_good", record.getCostOfGood())
        .add("barcode", record.getBarcode())
        .add("active_date", record.getActiveDate())
        .add("deactivate_on", record.getDeactivateOnDate())
        .add("available_date", record.getAvailableDate())
        .add("inventory_qty_low", record.getInventoryLow())
        .add("inventory_qty_incoming", record.getInventoryIncoming())
        .add("minimum_purchase_qty", record.getMinimumPurchaseQty())
        .add("maximum_purchase_qty", record.getMaximumPurchaseQty())
        .add("allow_backorders", record.getAllowBackorders())
        .add("package_height", record.getPackageHeight())
        .add("package_length", record.getPackageLength())
        .add("package_width", record.getPackageWidth())
        .add("package_weight_lbs", record.getPackageWeightPounds())
        .add("package_weight_ozs", record.getPackageWeightOunces())
        .add("enabled", record.getEnabled())
        .add("modified_by", record.getModifiedBy())
        .add("modified", new Timestamp(System.currentTimeMillis()));
    updateValues.add(new SqlValue("attributes", SqlValue.JSONB_TYPE, ProductSkuJSONCommand.createJSONString(record)));
    // Determine if Qty State is being used
    if (record.getInventoryQtyState() > -1) {
      // The update will use an offset, not a setter for the inventory in case an order
      // modifies the available amount
      int difference = record.getInventoryQty() - record.getInventoryQtyState();
      updateInventoryCount(connection, record.getId(), difference);
    } else {
      // state is not being used, so set the value
      updateValues.add("inventory_qty", record.getInventoryQty(), 0);
    }
    SqlUtils where = new SqlUtils()
        .add("sku_id = ?", record.getId());
    if (DB.update(connection, TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  private static PreparedStatement createPreparedStatementForInventoryCount(Connection connection, long productSkuId, int value) throws SQLException {
    String SQL_QUERY =
        "UPDATE product_skus " +
            "SET inventory_qty = inventory_qty + ? " +
            "WHERE sku_id = ?";
    int i = 0;
    PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
    pst.setInt(++i, value);
    pst.setLong(++i, productSkuId);
    return pst;
  }

  public static boolean updateInventoryCount(Connection connection, long productSkuId, int value) throws SQLException {
    try {
      // Adjust the count
      try (PreparedStatement pst = createPreparedStatementForInventoryCount(connection, productSkuId, value)) {
        return pst.execute();
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    throw new SQLException("Inventory could not be updated");
  }

  public static boolean updateSquareVariationIdForProductSkuId(long productSkuId, String squareVariationId) {
    SqlUtils updateValues = new SqlUtils()
        .add("square_variation_id", squareVariationId)
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("sku_id = ?", productSkuId);
    DB.update(TABLE_NAME, updateValues, where);
    return true;
  }

  private static ProductSku buildRecord(ResultSet rs) {
    try {
      ProductSku record = new ProductSku();
      record.setId(rs.getLong("sku_id"));
      record.setProductId(rs.getLong("product_id"));
      record.setSku(rs.getString("sku"));
      record.setCurrency(rs.getString("currency"));
      record.setPrice(rs.getBigDecimal("price"));
      record.setStrikePrice(rs.getBigDecimal("strike_price"));
      record.setCostOfGood(rs.getBigDecimal("cost_of_good"));
      record.setBarcode(rs.getString("barcode"));
      ProductSkuJSONCommand.populateFromJSONString(record, rs.getString("attributes"));
      record.setActiveDate(rs.getTimestamp("active_date"));
      record.setDeactivateOnDate(rs.getTimestamp("deactivate_on"));
      record.setAvailableDate(rs.getTimestamp("available_date"));
      record.setInventoryQty(rs.getInt("inventory_qty"));
      record.setInventoryLow(rs.getInt("inventory_qty_low"));
      record.setInventoryIncoming(rs.getInt("inventory_qty_incoming"));
      record.setMinimumPurchaseQty(rs.getInt("minimum_purchase_qty"));
      record.setMaximumPurchaseQty(rs.getInt("maximum_purchase_qty"));
      record.setAllowBackorders(rs.getBoolean("allow_backorders"));
      record.setPackageHeight(rs.getBigDecimal("package_height"));
      record.setPackageLength(rs.getBigDecimal("package_length"));
      record.setPackageWidth(rs.getBigDecimal("package_width"));
      record.setPackageWeightPounds(rs.getInt("package_weight_lbs"));
      record.setPackageWeightOunces(rs.getInt("package_weight_ozs"));
      record.setCreated(rs.getTimestamp("created"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setModified(rs.getTimestamp("modified"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setEnabled(rs.getBoolean("enabled"));
      record.setSquareVariationId(rs.getString("square_variation_id"));
      // joined tables
      Timestamp productActiveDate = rs.getTimestamp("product_active_date");
      Timestamp productDeactiveDate = rs.getTimestamp("product_deactivate_on");
      // helpers
      if (ProductInventoryCommand.isAvailable(record, new BigDecimal(1))) {
        // There's at least 1
        record.setStatus(ProductSku.STATUS_AVAILABLE);
      } else {
        // None, so see if there's any on the way
        if (ProductInventoryCommand.hasMoreOnTheWay(record)) {
          record.setStatus(ProductSku.STATUS_MORE_ON_THE_WAY);
        } else {
          record.setStatus(ProductSku.STATUS_SOLD_OUT);
        }
      }
      // Override the status if there is a date condition
      if (productActiveDate != null) {
        // Now see if the product status can be seen
        if (DateCommand.isAfterNow(productActiveDate)) {
          // The date hasn't been reached yet, so it's coming soon
          record.setStatus(ProductSku.STATUS_COMING_SOON);
        } else if (DateCommand.isAfterNow(productDeactiveDate)) {
          // The date has already passed, so it's unavailable/expired
          record.setStatus(ProductSku.STATUS_UNAVAILABLE);
        }
      }
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }

  public static void export(DataConstraints constraints, File file) {
    SqlUtils selectFields = new SqlUtils()
        .addNames(
            "sku AS \"SKU\"",
            "products.name AS \"Name\"",
            "products.caption AS \"Caption\"",
            "TRIM(concat_ws(' ', products.name, products.caption)) AS \"ItemName\"",
            "price AS \"Value\"",
            "(SELECT JSONB_AGG(t -> 'value') FROM JSONB_ARRAY_ELEMENTS(attributes) AS x(t) WHERE t ->> 'value' <> '') AS \"Attributes\"",
            "products.description AS \"Description\"",
//            "short_description AS \"ShortDescription\"",
            "barcode AS \"UPC\"",
            "product_skus.enabled AS \"Enabled\"");
    SqlJoins joins = new SqlJoins().add(JOIN);
    SqlUtils where = new SqlUtils();
    // Use the specification to filter results
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("sku");
    DB.exportToCsvAllFrom(TABLE_NAME, selectFields, joins, where, null, constraints, file);
  }
}
