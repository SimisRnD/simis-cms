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

import com.simisinc.platform.application.ecommerce.OrderStatusCommand;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.domain.model.ecommerce.OrderItem;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import static com.simisinc.platform.application.ecommerce.OrderStatusCommand.*;

/**
 * Persists and retrieves order item objects
 *
 * @author matt rajkowski
 * @created 4/23/20 2:16 PM
 */
public class OrderItemRepository {

  private static Log LOG = LogFactory.getLog(OrderItemRepository.class);

  private static String TABLE_NAME = "order_items";
  private static String[] PRIMARY_KEY = new String[]{"item_id"};

  public static List<OrderItem> findItemsByOrderId(long orderId) {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("order_id = ?", orderId),
        new DataConstraints().setDefaultColumnToSortBy("item_id").setUseCount(false),
        OrderItemRepository::buildRecord);
    return (List<OrderItem>) result.getRecords();
  }

  public static OrderItem findById(long itemId) {
    return (OrderItem) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("item_id = ?", itemId),
        OrderItemRepository::buildRecord);
  }

  public static OrderItem add(OrderItem record) throws SQLException {
    try (Connection connection = DB.getConnection();
         AutoStartTransaction a = new AutoStartTransaction(connection);
         AutoRollback transaction = new AutoRollback(connection)) {
      // Save it
      add(connection, record);
      // Finish the transaction
      transaction.commit();
      return record;
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage(), se);
    }
    return null;
  }

  public static OrderItem add(Connection connection, OrderItem record) throws SQLException {
    SqlUtils insertValues = new SqlUtils()
        .add("order_id", record.getOrderId(), -1)
        .addIfExists("customer_id", record.getCustomerId(), -1)
        .addIfExists("product_id", record.getProductId(), -1)
        .addIfExists("sku_id", record.getSkuId(), -1)
        .addIfExists("quantity", record.getQuantity())
        .addIfExists("currency", record.getCurrency())
        .addIfExists("each_amount", record.getEachAmount())
        .addIfExists("total_amount", record.getTotalAmount())
        .addIfExists("product_name", record.getProductName())
        .addIfExists("product_type", record.getProductType())
        .addIfExists("product_sku", record.getProductSku())
        .add("is_preorder", record.getPreorder())
        .add("is_backordered", record.getBackordered())
        .add("paid", record.getPaid())
        .add("processed", record.getProcessed())
        .add("shipped", record.getShipped())
        .add("canceled", record.getCanceled())
        .add("refunded", record.getRefunded())
        .addIfExists("created", record.getCreated())
        .addIfExists("created_by", record.getCreatedBy(), -1)
        .addIfExists("modified_by", record.getModifiedBy(), -1)
        .addIfExists("product_barcode", record.getProductBarcode())
        .add("payment_date", record.getPaymentDate())
        .add("processing_date", record.getProcessingDate())
        .add("fulfillment_date", record.getFulfillmentDate())
        .add("shipped_date", record.getShippedDate())
        .add("canceled_date", record.getCanceledDate())
        .add("refunded_date", record.getRefundedDate())
        .add("status", record.getStatusId(), -1);
    record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
    return record;
  }

  private static OrderItem buildRecord(ResultSet rs) {
    try {
      OrderItem record = new OrderItem();
      record.setId(rs.getLong("item_id"));
      record.setOrderId(rs.getLong("order_id"));
      record.setCustomerId(DB.getLong(rs, "customer_id", -1));
      record.setProductId(DB.getLong(rs, "product_id", -1));
      record.setSkuId(DB.getLong(rs, "sku_id", -1));
      record.setQuantity(rs.getBigDecimal("quantity"));
      record.setCurrency(rs.getString("currency"));
      record.setEachAmount(rs.getBigDecimal("each_amount"));
      record.setTotalAmount(rs.getBigDecimal("total_amount"));
      record.setProductName(rs.getString("product_name"));
      record.setProductType(rs.getString("product_type"));
      record.setProductSku(rs.getString("product_sku"));
      record.setPreorder(rs.getBoolean("is_preorder"));
      record.setBackordered(rs.getBoolean("is_backordered"));
      record.setPaid(rs.getBoolean("paid"));
      record.setProcessed(rs.getBoolean("processed"));
      record.setShipped(rs.getBoolean("shipped"));
      record.setCanceled(rs.getBoolean("canceled"));
      record.setRefunded(rs.getBoolean("refunded"));
      record.setCreated(rs.getTimestamp("created"));
      record.setCreatedBy(DB.getLong(rs, "created_by", -1));
      record.setModified(rs.getTimestamp("modified"));
      record.setModifiedBy(DB.getLong(rs, "modified_by", -1));
      record.setProductBarcode(rs.getString("product_barcode"));
      record.setPaymentDate(rs.getTimestamp("payment_date"));
      record.setProcessingDate(rs.getTimestamp("processing_date"));
      record.setFulfillmentDate(rs.getTimestamp("fulfillment_date"));
      record.setShippedDate(rs.getTimestamp("shipped_date"));
      record.setCanceledDate(rs.getTimestamp("canceled_date"));
      record.setRefundedDate(rs.getTimestamp("refunded_date"));
      record.setStatusId(DB.getInt(rs, "status", -1));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }

  public static void markStatusAsPaid(Connection connection, OrderItem orderItem, Timestamp paymentDate) throws SQLException {
    // Determine the new status value
    int statusId = OrderStatusCommand.retrieveStatusId(PAID);
    Timestamp now = new Timestamp(System.currentTimeMillis());
    // Update the order item status
    SqlUtils updateValues = new SqlUtils()
        .add("paid", true)
        .add("payment_date", paymentDate)
        .add("status", statusId)
        .add("modified", now);
    SqlUtils where = new SqlUtils()
        .add("item_id = ?", orderItem.getId());
    DB.update(connection, TABLE_NAME, updateValues, where);
    // @todo Append to the order_history (PAID)
    // Update the object
    orderItem.setModified(now);
    orderItem.setPaid(true);
    orderItem.setPaymentDate(now);
    orderItem.setStatusId(statusId);
  }

  public static void markStatusAsPreparing(OrderItem orderItem) {
    // Determine the new status value
    int statusId = OrderStatusCommand.retrieveStatusId(PREPARING);
    Timestamp now = new Timestamp(System.currentTimeMillis());
    // Update the order item status
    SqlUtils updateValues = new SqlUtils()
        .add("processed", true)
        .add("processing_date", now)
        .add("status", statusId)
        .add("modified", now);
    SqlUtils where = new SqlUtils()
        .add("item_id = ?", orderItem.getId());
    DB.update(TABLE_NAME, updateValues, where);
    // @todo Append to the order_history (PREPARING)
    // Update the object
    orderItem.setModified(now);
    orderItem.setProcessed(true);
    orderItem.setProcessingDate(now);
    orderItem.setStatusId(statusId);
  }

  public static void markStatusAsShipped(OrderItem orderItem) {
    // Determine the new status value
    int statusId = OrderStatusCommand.retrieveStatusId(SHIPPED);
    Timestamp now = new Timestamp(System.currentTimeMillis());
    // Update the order item status
    SqlUtils updateValues = new SqlUtils()
        .add("shipped", true)
        .add("shipped_date", now)
        .add("status", statusId)
        .add("modified", now);
    SqlUtils where = new SqlUtils()
        .add("item_id = ?", orderItem.getId());
    DB.update(TABLE_NAME, updateValues, where);
    // @todo Append to the order_history (SHIPPED)
    // Update the object
    orderItem.setModified(now);
    orderItem.setShipped(true);
    orderItem.setShippedDate(now);
    orderItem.setStatusId(statusId);
  }

  public static void markStatusAsCanceled(Connection connection, Order order) throws SQLException {
    // Determine the new status value
    int statusId = OrderStatusCommand.retrieveStatusId(CANCELED);
    Timestamp now = new Timestamp(System.currentTimeMillis());
    // Update the order item status
    SqlUtils updateValues = new SqlUtils()
        .add("canceled", true)
        .add("canceled_date", now)
        .add("status", statusId)
        .add("modified", now);
    SqlUtils where = new SqlUtils()
        .add("order_id = ?", order.getId());
    DB.update(connection, TABLE_NAME, updateValues, where);
  }

  public static void markStatusAsRefunded(Connection connection, Order order) throws SQLException {
    // Determine the new status value
    int statusId = OrderStatusCommand.retrieveStatusId(REFUNDED);
    Timestamp now = new Timestamp(System.currentTimeMillis());
    // Update the order item status
    SqlUtils updateValues = new SqlUtils()
        .add("refunded", true)
        .add("refunded_date", now)
        .add("status", statusId)
        .add("modified", now);
    SqlUtils where = new SqlUtils()
        .add("order_id = ?", order.getId());
    DB.update(connection, TABLE_NAME, updateValues, where);
  }
}
