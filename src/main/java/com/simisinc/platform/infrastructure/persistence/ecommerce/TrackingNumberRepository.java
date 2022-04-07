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

import com.simisinc.platform.domain.model.ecommerce.TrackingNumber;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/22/20 7:59 PM
 */
public class TrackingNumberRepository {

  private static Log LOG = LogFactory.getLog(TrackingNumberRepository.class);

  private static String TABLE_NAME = "order_tracking_numbers";
  private static String PRIMARY_KEY[] = new String[]{"tracking_id"};

  public static TrackingNumber findById(long trackingId) {
    return (TrackingNumber) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("tracking_id = ?", trackingId),
        TrackingNumberRepository::buildRecord);
  }

  public static List<TrackingNumber> findAllForOrderId(long orderId) {
    SqlUtils where = new SqlUtils()
        .add("order_id = ?", orderId);
    DataResult result = DB.selectAllFrom(TABLE_NAME, where, new DataConstraints().setDefaultColumnToSortBy("created").setUseCount(false), TrackingNumberRepository::buildRecord);
    return (List<TrackingNumber>) result.getRecords();
  }

  public static boolean exists(TrackingNumber record) {
    SqlUtils where = new SqlUtils()
        .add("order_id = ?", record.getOrderId())
        .add("tracking_number = ?", StringUtils.trimToNull(record.getTrackingNumber()));
    return DB.selectCountFrom(TABLE_NAME, where) > 0;
  }

  public static TrackingNumber save(TrackingNumber record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  private static TrackingNumber add(TrackingNumber record) {
    // Use a transaction
    try {
      // Save the tracking number, update the order
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Add the record
        SqlUtils insertValues = new SqlUtils()
            .add("order_id", record.getOrderId())
            .add("tracking_number", StringUtils.trimToNull(record.getTrackingNumber()))
            .addIfExists("shipping_carrier", record.getShippingCarrierId(), -1)
            .addIfExists("created_by", record.getCreatedBy(), -1)
            .addIfExists("ship_date", record.getShipDate())
            .addIfExists("delivery_date", record.getDeliveryDate())
            .add("cart_item_id_list", StringUtils.trimToNull(record.getCartItemIdList()))
            .add("order_item_id_list", StringUtils.trimToNull(record.getOrderItemIdList()));
        record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
        // Maintain an updated field in the order table
        updateOrderField(connection, record);
        // Finish the transaction
        transaction.commit();
        return record;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage(), se);
    }
    return null;
  }

  private static TrackingNumber update(TrackingNumber record) {
    // Use a transaction
    try {
      // Save the tracking number, update the order
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Update the record
        SqlUtils updateValues = new SqlUtils()
            .add("tracking_number", StringUtils.trimToNull(record.getTrackingNumber()))
            .addIfExists("shipping_carrier", record.getShippingCarrierId(), -1)
            .addIfExists("ship_date", record.getShipDate())
            .addIfExists("delivery_date", record.getDeliveryDate())
            .add("cart_item_id_list", StringUtils.trimToNull(record.getCartItemIdList()))
            .add("order_item_id_list", StringUtils.trimToNull(record.getOrderItemIdList()));
        SqlUtils where = new SqlUtils()
            .add("tracking_id = ?", record.getId());
        if (DB.update(connection, TABLE_NAME, updateValues, where)) {
          // Maintain an updated field in the order table
          updateOrderField(connection, record);
          // Finish the transaction
          transaction.commit();
          return record;
        }
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage(), se);
    }
    return null;
  }

  private static void updateOrderField(Connection connection, TrackingNumber record) throws SQLException {
    // Update the order field
    SqlUtils update = new SqlUtils()
        .add("tracking_numbers = sub_q.agg_value " +
            "FROM " +
            "(" +
            "SELECT string_agg(tracking_number, ',') AS agg_value " +
            "FROM order_tracking_numbers AS tn " +
            "WHERE tn.order_id = ?" +
            ") AS sub_q", record.getOrderId());
    SqlUtils where = new SqlUtils()
        .add("orders.order_id = ?", record.getOrderId());
    DB.update(connection, "orders", update, where);
  }

  private static TrackingNumber buildRecord(ResultSet rs) {
    try {
      TrackingNumber record = new TrackingNumber();
      record.setId(rs.getLong("tracking_id"));
      record.setOrderId(rs.getLong("order_id"));
      record.setTrackingNumber(rs.getString("tracking_number"));
      record.setShippingCarrierId(rs.getInt("shipping_carrier"));
      record.setShipDate(rs.getTimestamp("ship_date"));
      record.setCreated(rs.getTimestamp("created"));
      record.setCreatedBy(DB.getLong(rs, "created_by", -1));
      record.setDeliveryDate(rs.getTimestamp("delivery_date"));
      record.setCartItemIdList(rs.getString("cart_item_id_list"));
      record.setOrderItemIdList(rs.getString("order_item_id_list"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
