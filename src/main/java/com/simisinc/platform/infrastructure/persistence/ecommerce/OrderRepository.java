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

import com.simisinc.platform.application.ecommerce.OrderItemCommand;
import com.simisinc.platform.application.ecommerce.OrderStatusCommand;
import com.simisinc.platform.domain.model.Session;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.domain.model.ecommerce.Address;
import com.simisinc.platform.domain.model.ecommerce.CartItem;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.domain.model.ecommerce.OrderItem;
import com.simisinc.platform.infrastructure.database.*;
import com.simisinc.platform.presentation.controller.DataConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static com.simisinc.platform.application.ecommerce.OrderCommand.generateUniqueId;
import static com.simisinc.platform.application.ecommerce.OrderStatusCommand.*;

/**
 * Persists and retrieves order objects
 *
 * @author matt rajkowski
 * @created 5/2/19 6:45 AM
 */
public class OrderRepository {

  private static Log LOG = LogFactory.getLog(OrderRepository.class);

  private static String TABLE_NAME = "orders";
  private static String PRIMARY_KEY[] = new String[]{"order_id"};
  private static String STATUS_JOIN = "LEFT JOIN lookup_order_status los ON (orders.status = los.status_id)";

  private static DataResult query(OrderSpecification specification, DataConstraints constraints) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("order_id = ?", specification.getId(), -1)
          .addIfExists("customer_id = ?", specification.getCustomerId(), -1)
          .addIfExists("LOWER(email) = ?", specification.getEmail() != null ? specification.getEmail().toLowerCase() : null)
          .addIfExists("created_by = ?", specification.getCreatedBy(), -1);
      if (StringUtils.isNotBlank(specification.getUniqueId())) {
        where.add("(LOWER(order_unique_id) = LOWER(?) OR LOWER(order_unique_id) LIKE LOWER(?))", new String[]{specification.getUniqueId(), specification.getUniqueId() + "%"});
      }
      if (StringUtils.isNotBlank(specification.getCustomerNumber())) {
        where.add("EXISTS (SELECT 1 FROM customers WHERE orders.customer_id = customers.customer_id AND LOWER(customer_unique_id) = ?)", specification.getCustomerNumber().toLowerCase());
      }
      if (StringUtils.isNotBlank(specification.getPhoneNumber())) {
        where.add("(billing_phone_number = ? OR shipping_phone_number = ?)", new String[]{specification.getPhoneNumber(), specification.getPhoneNumber()});
      }
      if (StringUtils.isNotBlank(specification.getName())) {
        where.add(
            "(LOWER(concat_ws(' ', first_name, last_name)) LIKE LOWER(?) ESCAPE '!' OR LOWER(concat_ws(' ', shipping_first_name, shipping_last_name)) LIKE LOWER(?) ESCAPE '!')",
            new String[]{"%" + specification.getName() + "%", "%" + specification.getName() + "%"});
      }
      if (specification.getShowSandbox() != DataConstants.UNDEFINED) {
        where.add("live_mode = ?", specification.getShowSandbox() == DataConstants.FALSE);
      }
      if (specification.getShowIncompleteOrders() != DataConstants.UNDEFINED) {
        // Completed orders have paid = true
        where.add("paid = ?", specification.getShowIncompleteOrders() == DataConstants.FALSE);
      }
      if (specification.getShowCanceledOrders() != DataConstants.UNDEFINED) {
        where.add("canceled = ?", specification.getShowCanceledOrders() == DataConstants.TRUE);
      }
      if (specification.getShowProcessedOrders() != DataConstants.UNDEFINED) {
        where.add("processed = ?", specification.getShowProcessedOrders() == DataConstants.TRUE);
      }
      if (specification.getShowShippedOrders() != DataConstants.UNDEFINED) {
        where.add("shipped = ?", specification.getShowShippedOrders() == DataConstants.TRUE);
      }
    }
    return DB.selectAllFrom(TABLE_NAME, where, constraints, OrderRepository::buildRecord);
  }

  public static Order findById(long orderId) {
    return (Order) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("order_id = ?", orderId),
        OrderRepository::buildRecord);
  }

  public static Order findByUniqueId(String orderUniqueId) {
    return (Order) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("order_unique_id = ?", orderUniqueId),
        OrderRepository::buildRecord);
  }

  public static List<Order> findAll(OrderSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("order_id desc");
    DataResult result = query(specification, constraints);
    return (List<Order>) result.getRecords();
  }

  public static List<Session> findDailyUniqueLocations(int daysToLimit) {
    String SQL_QUERY =
        "SELECT DISTINCT shipping_country AS country, " +
            "shipping_state AS state, " +
            "shipping_city AS city, " +
            "latitude, longitude " +
            "FROM orders " +
            "WHERE payment_date IS NOT NULL " +
            "AND payment_date > NOW() - INTERVAL '" + daysToLimit + " days' " +
            "AND latitude IS NOT NULL " +
            "AND live_mode = true AND processed = true " +
            "ORDER BY country, state, city, latitude, longitude";
    List<Session> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        Session data = new Session();
//        data.setContinent(rs.getString("continent"));
        data.setCountry(rs.getString("country"));
        data.setState(rs.getString("state"));
        data.setCity(rs.getString("city"));
        data.setLatitude(rs.getDouble("latitude"));
        data.setLongitude(rs.getDouble("longitude"));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  public static List<StatisticsData> findTopLocations(int daysToLimit, int recordLimit) {
    String SQL_QUERY =
        "SELECT UPPER(shipping_country) AS country, " +
            "UPPER(shipping_state) AS state, " +
            "COUNT(order_id) AS location_count " +
            "FROM orders " +
            "WHERE payment_date IS NOT NULL " +
            "AND payment_date > NOW() - INTERVAL '" + daysToLimit + " days' " +
            "AND live_mode = true AND processed = true " +
            "GROUP BY UPPER(shipping_country), UPPER(shipping_state) " +
            "ORDER BY location_count desc " +
            "LIMIT " + recordLimit;
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        String label =
            rs.getString("country") + ", " +
                rs.getString("state");
        data.setLabel(label);
        data.setValue(String.valueOf(rs.getLong("location_count")));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  public static List<StatisticsData> findDailyOrdersCount(int daysToLimit) {
    String SQL_QUERY =
        "SELECT DATE_TRUNC('day', day)::VARCHAR(10) AS date_column, COUNT(order_id) AS daily_count " +
            "FROM (SELECT generate_series(NOW() - INTERVAL '" + daysToLimit + " days', NOW(), INTERVAL '1 day')::date) d(day) " +
            "LEFT JOIN orders ON DATE_TRUNC('day', payment_date) = DATE_TRUNC('day', day) AND live_mode = true AND processed = true " +
            "GROUP BY d.day " +
            "ORDER BY d.day";
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("date_column"));
        data.setValue(String.valueOf(rs.getLong("daily_count")));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  public static List<StatisticsData> findDailyItemsSold(int daysToLimit) {
    String SQL_QUERY =
        "SELECT DATE_TRUNC('day', day)::VARCHAR(10) AS date_column, SUM(total_items) AS daily_count " +
            "FROM (SELECT generate_series(NOW() - INTERVAL '" + daysToLimit + " days', NOW(), INTERVAL '1 day')::date) d(day) " +
            "LEFT JOIN orders ON DATE_TRUNC('day', payment_date) = DATE_TRUNC('day', day) AND live_mode = true AND processed = true " +
            "GROUP BY d.day " +
            "ORDER BY d.day";
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("date_column"));
        data.setValue(String.valueOf(rs.getLong("daily_count")));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  public static List<StatisticsData> findDailyAmountSold(int daysToLimit) {
    String SQL_QUERY =
        "SELECT DATE_TRUNC('day', day)::VARCHAR(10) AS date_column, SUM(total_paid) AS daily_count " +
            "FROM (SELECT generate_series(NOW() - INTERVAL '" + daysToLimit + " days', NOW(), INTERVAL '1 day')::date) d(day) " +
            "LEFT JOIN orders ON DATE_TRUNC('day', payment_date) = DATE_TRUNC('day', day) AND live_mode = true AND processed = true " +
            "GROUP BY d.day " +
            "ORDER BY d.day";
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("date_column"));
        data.setValue(String.valueOf(rs.getLong("daily_count")));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  public static long countTotalOrders(int daysToLimit) {
    long count = -1;
    String SQL_QUERY =
        "SELECT COUNT(order_id) AS order_count " +
            "FROM orders " +
            "WHERE live_mode = true AND paid = true AND canceled = false " +
            "AND created > NOW() - INTERVAL '" + daysToLimit + " days'";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        count = rs.getLong("order_count");
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return count;
  }

  public static long countTotalOrders() {
    long count = -1;
    String SQL_QUERY =
        "SELECT COUNT(order_id) AS order_count " +
            "FROM orders " +
            "WHERE live_mode = true AND paid = true AND canceled = false";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        count = rs.getLong("order_count");
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return count;
  }

  public static long countTotalOrdersNotShipped() {
    long count = -1;
    String SQL_QUERY =
        "SELECT COUNT(order_id) AS order_count " +
            "FROM orders " +
            "WHERE live_mode = true AND paid = true AND shipped = false AND canceled = false";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        count = rs.getLong("order_count");
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return count;
  }

  public static long countTotalOrdersShipped() {
    long count = -1;
    String SQL_QUERY =
        "SELECT COUNT(order_id) AS order_count " +
            "FROM orders " +
            "WHERE live_mode = true AND paid = true AND processed = true AND shipped = true";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        count = rs.getLong("order_count");
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return count;
  }

  public static Order create(Order record, List<CartItem> cartItemList) {
    return add(record, cartItemList);
  }

  public static Order save(Order record) {
    if (record.getId() > -1) {
      return update(record);
    } else {
      return add(record, null);
    }
  }

  public static Order add(Order record, List<CartItem> cartItemList) {
    // Use a transaction
    try {
      // Generate the order number
      record.setUniqueId(generateUniqueId());
      // Save the order
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        SqlUtils insertValues = new SqlUtils()
            .add("order_unique_id", record.getUniqueId())
            .addIfExists("customer_id", record.getCustomerId(), -1)
            .add("email", record.getEmail())
            .add("first_name", record.getFirstName())
            .add("last_name", record.getLastName())
            .add("customer_note", record.getCustomerNote())
            .add("barcode", record.getBarcode())
            .add("remote_order_id", record.getRemoteOrderId())
            .add("shipping_method", record.getShippingMethodId(), -1)
            .add("shipping_rate_id", record.getShippingRateId(), -1)
            .add("total_items", record.getTotalItems())
            .addIfExists("currency", record.getCurrency())
            .add("subtotal_amount", record.getSubtotalAmount())
            .addIfExists("discount_amount", record.getDiscountAmount())
            .add("promo_code", record.getPromoCode())
            .add("pricing_rule_1", record.getPricingRuleId(), -1)
            .addIfExists("fee_amount", record.getHandlingFee())
            .addIfExists("fee_tax_amount", record.getHandlingFeeTaxAmount())
            .addIfExists("shipping_amount", record.getShippingFee())
            .addIfExists("shipping_tax_amount", record.getShippingTaxAmount())
            .addIfExists("tax_amount", record.getTaxAmount())
            .addIfExists("tax_rate", record.getTaxRate())
            .addIfExists("total_amount", record.getTotalAmount())
            .addIfExists("total_paid", record.getTotalPaid())
            .addIfExists("total_pending", record.getTotalPending())
            .addIfExists("total_refunded", record.getTotalRefunded())
            .add("status", record.getStatusId(), -1)
            .add("has_preorder", record.getHasPreOrder())
            .add("has_backorder", record.getHasBackOrder())
            .add("paid", record.getPaid())
            .add("processed", record.getProcessed())
            .add("shipped", record.getShipped())
            .add("canceled", record.getCanceled())
            .add("refunded", record.getRefunded())
            .add("tax_id", record.getTaxId())
            .add("cart_id", record.getCartId())
            .add("payment_processor", record.getPaymentProcessor())
            .add("payment_token", record.getPaymentToken())
            .add("payment_type", record.getPaymentType())
            .add("payment_brand", record.getPaymentBrand())
            .add("payment_last4", record.getPaymentLast4())
            .add("payment_fingerprint", record.getPaymentFingerprint())
            .add("payment_country", record.getPaymentCountry())
            .add("charge_token", record.getChargeToken())
            .add("ip_address", record.getIpAddress())
            .add("session_id", record.getSessionId())
            .add("country_iso", record.getCountryIso())
            .add("country", record.getCountry())
            .add("city", record.getCity())
            .add("state_iso", record.getStateIso())
            .add("state", record.getState())
            .add("latitude", record.getLatitude(), 0)
            .add("longitude", record.getLongitude(), 0)
            .add("payment_date", record.getPaymentDate())
            .add("processing_date", record.getProcessingDate())
            .add("fulfillment_date", record.getFulfillmentDate())
            .add("shipped_date", record.getShippedDate())
            .add("canceled_date", record.getCanceledDate())
            .add("refunded_date", record.getRefundedDate())
            .addIfExists("tracking_numbers", record.getTrackingNumbers())
            .addIfExists("square_order_id", record.getSquareOrderId())
            .addIfExists("created_by", record.getCreatedBy(), -1)
            .addIfExists("modified_by", record.getModifiedBy(), -1);
        if (record.getBillingAddress() != null) {
          insertValues
              .add("billing_first_name", record.getBillingAddress().getFirstName())
              .add("billing_last_name", record.getBillingAddress().getLastName())
              .add("billing_organization", record.getBillingAddress().getOrganization())
              .add("billing_street_address", record.getBillingAddress().getStreet())
              .add("billing_address_line_2", record.getBillingAddress().getAddressLine2())
              .add("billing_address_line_3", record.getBillingAddress().getAddressLine3())
              .add("billing_city", record.getBillingAddress().getCity())
              .add("billing_state", record.getBillingAddress().getState())
              .add("billing_country", record.getBillingAddress().getCountry())
              .add("billing_postal_code", record.getBillingAddress().getPostalCode())
              .add("billing_county", record.getBillingAddress().getCounty())
              .add("billing_phone_number", record.getBillingAddress().getPhoneNumber())
              .addIfExists("billing_latitude", record.getBillingAddress().getLatitude(), 0)
              .addIfExists("billing_longitude", record.getBillingAddress().getLongitude(), 0);
        }
        if (record.getShippingAddress() != null) {
          insertValues
              .add("shipping_first_name", record.getShippingAddress().getFirstName())
              .add("shipping_last_name", record.getShippingAddress().getLastName())
              .add("shipping_organization", record.getShippingAddress().getOrganization())
              .add("shipping_street_address", record.getShippingAddress().getStreet())
              .add("shipping_address_line_2", record.getShippingAddress().getAddressLine2())
              .add("shipping_address_line_3", record.getShippingAddress().getAddressLine3())
              .add("shipping_city", record.getShippingAddress().getCity())
              .add("shipping_state", record.getShippingAddress().getState())
              .add("shipping_country", record.getShippingAddress().getCountry())
              .add("shipping_postal_code", record.getShippingAddress().getPostalCode())
              .add("shipping_county", record.getShippingAddress().getCounty())
              .add("shipping_phone_number", record.getShippingAddress().getPhoneNumber())
              .addIfExists("shipping_latitude", record.getShippingAddress().getLatitude(), 0)
              .addIfExists("shipping_longitude", record.getShippingAddress().getLongitude(), 0);
        }
        record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
        // Update the cart's link
        if (record.getCartId() > 0) {
          SqlUtils update = new SqlUtils()
              .add("order_id", record.getId())
              .add("order_date", new Timestamp(System.currentTimeMillis()));
          SqlUtils where = new SqlUtils().add("cart_id = ?", record.getCartId());
          DB.update(connection, "carts", update, where);
        }
        // Make a copy of the cart items
        if (cartItemList != null && !cartItemList.isEmpty()) {
          for (CartItem cartItem : cartItemList) {
            OrderItem orderItem = OrderItemCommand.generateOrderItem(record, cartItem);
            OrderItemRepository.add(connection, orderItem);
          }
        }
        // Finish the transaction
        transaction.commit();
        return record;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage(), se);
    }
    return null;
  }

  public static Order update(Order record) {
    // Use a transaction
    try (Connection connection = DB.getConnection();
         AutoStartTransaction a = new AutoStartTransaction(connection);
         AutoRollback transaction = new AutoRollback(connection)) {
      // In a transaction (use the existing connection)
      SqlUtils updateValues = new SqlUtils()
          .addIfExists("customer_id", record.getCustomerId(), -1)
          .add("email", record.getEmail())
          .add("first_name", record.getFirstName())
          .add("last_name", record.getLastName())
          .add("barcode", record.getBarcode())
          .add("remote_order_id", record.getRemoteOrderId())
          .addIfExists("square_order_id", record.getSquareOrderId())
          .addWhen("live_mode", record.getLive(), true)
          .addWhen("paid", record.getPaid(), true)
          .addIfExists("total_paid", record.getTotalPaid())
          .addWhen("processed", record.getProcessed(), true)
          .addWhen("shipped", record.getShipped(), true)
          .addWhen("canceled", record.getCanceled(), true)
          .addWhen("refunded", record.getRefunded(), true)
          .addIfExists("status", record.getStatusId(), -1)
          .addIfExists("payment_processor", record.getPaymentProcessor())
          .addIfExists("payment_token", record.getPaymentToken())
          .addIfExists("payment_type", record.getPaymentType())
          .addIfExists("payment_brand", record.getPaymentBrand())
          .addIfExists("payment_last4", record.getPaymentLast4())
          .addIfExists("payment_fingerprint", record.getPaymentFingerprint())
          .addIfExists("payment_country", record.getPaymentCountry())
          .addIfExists("payment_date", record.getPaymentDate())
          .add("charge_token", record.getChargeToken())
          .add("ip_address", record.getIpAddress())
          .addIfExists("session_id", record.getSessionId())
          .add("country_iso", record.getCountryIso())
          .add("country", record.getCountry())
          .add("city", record.getCity())
          .add("state_iso", record.getStateIso())
          .add("state", record.getState())
          .add("latitude", record.getLatitude(), 0)
          .add("longitude", record.getLongitude(), 0)
          .addIfExists("modified_by", record.getModifiedBy(), -1)
          .add("modified", new Timestamp(System.currentTimeMillis()));
      if (record.getBillingAddress() != null) {
        updateValues
            .add("billing_first_name", record.getBillingAddress().getFirstName())
            .add("billing_last_name", record.getBillingAddress().getLastName())
            .add("billing_organization", record.getBillingAddress().getOrganization())
            .add("billing_street_address", record.getBillingAddress().getStreet())
            .add("billing_address_line_2", record.getBillingAddress().getAddressLine2())
            .add("billing_address_line_3", record.getBillingAddress().getAddressLine3())
            .add("billing_city", record.getBillingAddress().getCity())
            .add("billing_state", record.getBillingAddress().getState())
            .add("billing_country", record.getBillingAddress().getCountry())
            .add("billing_postal_code", record.getBillingAddress().getPostalCode())
            .add("billing_county", record.getBillingAddress().getCounty())
            .add("billing_phone_number", record.getBillingAddress().getPhoneNumber())
            .add("billing_latitude", record.getBillingAddress().getLatitude(), 0)
            .add("billing_longitude", record.getBillingAddress().getLongitude(), 0);
      } else {
        updateValues
            .add("billing_first_name", (String) null)
            .add("billing_last_name", (String) null)
            .add("billing_organization", (String) null)
            .add("billing_street_address", (String) null)
            .add("billing_address_line_2", (String) null)
            .add("billing_address_line_3", (String) null)
            .add("billing_city", (String) null)
            .add("billing_state", (String) null)
            .add("billing_country", (String) null)
            .add("billing_postal_code", (String) null)
            .add("billing_county", (String) null)
            .add("billing_phone_number", (String) null)
            .add("billing_latitude", 0d, 0d)
            .add("billing_longitude", 0d, 0d);
      }
      if (record.getShippingAddress() != null) {
        updateValues
            .add("shipping_first_name", record.getShippingAddress().getFirstName())
            .add("shipping_last_name", record.getShippingAddress().getLastName())
            .add("shipping_organization", record.getShippingAddress().getOrganization())
            .add("shipping_street_address", record.getShippingAddress().getStreet())
            .add("shipping_address_line_2", record.getShippingAddress().getAddressLine2())
            .add("shipping_address_line_3", record.getShippingAddress().getAddressLine3())
            .add("shipping_city", record.getShippingAddress().getCity())
            .add("shipping_state", record.getShippingAddress().getState())
            .add("shipping_country", record.getShippingAddress().getCountry())
            .add("shipping_postal_code", record.getShippingAddress().getPostalCode())
            .add("shipping_county", record.getShippingAddress().getCounty())
            .add("shipping_phone_number", record.getShippingAddress().getPhoneNumber())
            .add("shipping_latitude", record.getShippingAddress().getLatitude(), 0)
            .add("shipping_longitude", record.getShippingAddress().getLongitude(), 0);
      } else {
        updateValues
            .add("shipping_first_name", (String) null)
            .add("shipping_last_name", (String) null)
            .add("shipping_organization", (String) null)
            .add("shipping_street_address", (String) null)
            .add("shipping_address_line_2", (String) null)
            .add("shipping_address_line_3", (String) null)
            .add("shipping_city", (String) null)
            .add("shipping_state", (String) null)
            .add("shipping_country", (String) null)
            .add("shipping_postal_code", (String) null)
            .add("shipping_county", (String) null)
            .add("shipping_phone_number", (String) null)
            .add("shipping_latitude", 0d, 0d)
            .add("shipping_longitude", 0d, 0d);
      }
      SqlUtils where = new SqlUtils()
          .add("order_id = ?", record.getId());
      if (DB.update(connection, TABLE_NAME, updateValues, where)) {
        // The order was successfully charged, disable the cart
        if (record.getPaid()) {
          // Update the cart reference so it cannot be reused
          SqlUtils cartUpdate = new SqlUtils()
              .add("enabled", false)
              .add("order_date", new Timestamp(System.currentTimeMillis()));
          SqlUtils cartWhere = new SqlUtils().add("cart_id = ?", record.getCartId());
          DB.update(connection, "carts", cartUpdate, cartWhere);

          // @todo Append to the order_history (PAID)

          // On Update, the OrderItemList needs to be updated with the Paid status
          List<OrderItem> orderItemList = OrderItemRepository.findItemsByOrderId(record.getId());
          if (orderItemList != null && !orderItemList.isEmpty()) {
            for (OrderItem orderItem : orderItemList) {
              // Update the inventory
              if (record.getLive()) {
                // @todo consider service type products
                // Decrease the inventory if this is a good or has a limit
                ProductSkuRepository.updateInventoryCount(connection, orderItem.getSkuId(), -orderItem.getQuantity().intValue());
              }
              // Update the status
              OrderItemRepository.markStatusAsPaid(connection, orderItem, record.getPaymentDate());
            }
          }
        }
        // Finish the transaction
        transaction.commit();
        return record;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage(), se);
    }
    return null;
  }

  private static Order buildRecord(ResultSet rs) {
    try {
      Order record = new Order();
      Address billingAddress = new Address();
      Address shippingAddress = new Address();
      record.setId(rs.getLong("order_id"));
      record.setUniqueId(rs.getString("order_unique_id"));
      record.setCustomerId(rs.getLong("customer_id"));
      record.setEmail(rs.getString("email"));
      record.setCustomerNote(rs.getString("customer_note"));
      billingAddress.setFirstName(rs.getString("billing_first_name"));
      billingAddress.setLastName(rs.getString("billing_last_name"));
      billingAddress.setOrganization(rs.getString("billing_organization"));
      billingAddress.setStreet(rs.getString("billing_street_address"));
      billingAddress.setAddressLine2(rs.getString("billing_address_line_2"));
      billingAddress.setAddressLine3(rs.getString("billing_address_line_3"));
      billingAddress.setCity(rs.getString("billing_city"));
      billingAddress.setState(rs.getString("billing_state"));
      billingAddress.setCountry(rs.getString("billing_country"));
      billingAddress.setPostalCode(rs.getString("billing_postal_code"));
      billingAddress.setCounty(rs.getString("billing_county"));
      billingAddress.setPhoneNumber(rs.getString("billing_phone_number"));
      shippingAddress.setFirstName(rs.getString("shipping_first_name"));
      shippingAddress.setLastName(rs.getString("shipping_last_name"));
      shippingAddress.setOrganization(rs.getString("shipping_organization"));
      shippingAddress.setStreet(rs.getString("shipping_street_address"));
      shippingAddress.setAddressLine2(rs.getString("shipping_address_line_2"));
      shippingAddress.setAddressLine3(rs.getString("shipping_address_line_3"));
      shippingAddress.setCity(rs.getString("shipping_city"));
      shippingAddress.setState(rs.getString("shipping_state"));
      shippingAddress.setCountry(rs.getString("shipping_country"));
      shippingAddress.setPostalCode(rs.getString("shipping_postal_code"));
      shippingAddress.setCounty(rs.getString("shipping_county"));
      shippingAddress.setPhoneNumber(rs.getString("shipping_phone_number"));
      record.setShippingMethodId(DB.getInt(rs, "shipping_method", -1));
      record.setTotalItems(rs.getInt("total_items"));
      record.setCurrency(rs.getString("currency"));
      record.setSubtotalAmount(rs.getBigDecimal("subtotal_amount"));
      record.setDiscountAmount(rs.getBigDecimal("discount_amount"));
      record.setHandlingFee(rs.getBigDecimal("fee_amount"));
      record.setHandlingFeeTaxAmount(rs.getBigDecimal("fee_tax_amount"));
      record.setShippingFee(rs.getBigDecimal("shipping_amount"));
      record.setShippingTaxAmount(rs.getBigDecimal("shipping_tax_amount"));
      record.setTaxAmount(rs.getBigDecimal("tax_amount"));
      record.setTotalAmount(rs.getBigDecimal("total_amount"));
      record.setTotalPaid(rs.getBigDecimal("total_paid"));
      record.setTotalPending(rs.getBigDecimal("total_pending"));
      record.setTotalRefunded(rs.getBigDecimal("total_refunded"));
      record.setStatusId(DB.getInt(rs, "status", -1));
      record.setHasPreOrder(rs.getBoolean("has_preorder"));
      record.setHasBackOrder(rs.getBoolean("has_backorder"));
      record.setPaid(rs.getBoolean("paid"));
      record.setProcessed(rs.getBoolean("processed"));
      record.setShipped(rs.getBoolean("shipped"));
      record.setCanceled(rs.getBoolean("canceled"));
      record.setRefunded(rs.getBoolean("refunded"));
      record.setCreated(rs.getTimestamp("created"));
      record.setCreatedBy(DB.getLong(rs, "created_by", -1));
      record.setModified(rs.getTimestamp("modified"));
      record.setModifiedBy(DB.getLong(rs, "modified_by", -1));
      record.setBarcode(rs.getString("barcode"));
      record.setTaxId(rs.getString("tax_id"));
      billingAddress.setLatitude(rs.getDouble("billing_latitude"));
      billingAddress.setLongitude(rs.getDouble("billing_longitude"));
      shippingAddress.setLatitude(rs.getDouble("shipping_latitude"));
      shippingAddress.setLongitude(rs.getDouble("shipping_longitude"));
      record.setCartId(rs.getLong("cart_id"));
      record.setRemoteOrderId(rs.getString("remote_order_id"));
      record.setShippingRateId(DB.getInt(rs, "shipping_rate_id", -1));
      record.setPaymentToken(rs.getString("payment_token"));
      record.setPaymentType(rs.getString("payment_type"));
      record.setPaymentBrand(rs.getString("payment_brand"));
      record.setPaymentLast4(rs.getString("payment_last4"));
      record.setPaymentFingerprint(rs.getString("payment_fingerprint"));
      record.setPaymentCountry(rs.getString("payment_country"));
      record.setChargeToken(rs.getString("charge_token"));
      record.setLive(rs.getBoolean("live_mode"));
      record.setIpAddress(rs.getString("ip_address"));
      record.setCountryIso(rs.getString("country_iso"));
      record.setCountry(rs.getString("country"));
      record.setCity(rs.getString("city"));
      record.setStateIso(rs.getString("state_iso"));
      record.setState(rs.getString("state"));
      record.setLatitude(rs.getDouble("latitude"));
      record.setLongitude(rs.getDouble("longitude"));
      record.setPaymentDate(rs.getTimestamp("payment_date"));
      record.setProcessingDate(rs.getTimestamp("processing_date"));
      record.setFulfillmentDate(rs.getTimestamp("fulfillment_date"));
      record.setShippedDate(rs.getTimestamp("shipped_date"));
      record.setCanceledDate(rs.getTimestamp("canceled_date"));
      record.setRefundedDate(rs.getTimestamp("refunded_date"));
      record.setPaymentProcessor(rs.getString("payment_processor"));
      record.setTrackingNumbers(rs.getString("tracking_numbers"));
      record.setPromoCode(rs.getString("promo_code"));
      record.setPricingRuleId(DB.getLong(rs, "pricing_rule_1", -1));
      record.setTaxRate(rs.getBigDecimal("tax_rate"));
      record.setSquareOrderId(rs.getString("square_order_id"));
      record.setFirstName(rs.getString("first_name"));
      record.setLastName(rs.getString("last_name"));
      record.setSessionId(rs.getString("session_id"));
      // Update the aggregate
      record.setBillingAddress(billingAddress);
      record.setShippingAddress(shippingAddress);
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }

  public static void markStatusAsPreparing(Order order) {
    // Determine the new status value
    int statusId = OrderStatusCommand.retrieveStatusId(PREPARING);
    Timestamp now = new Timestamp(System.currentTimeMillis());
    // Update the order status
    SqlUtils updateValues = new SqlUtils()
        .add("processed", true)
        .add("processing_date", now)
        .add("status", statusId)
        .add("modified", now);
    SqlUtils where = new SqlUtils()
        .add("order_id = ?", order.getId());
    DB.update(TABLE_NAME, updateValues, where);
    // @todo Append to the order_history (PREPARING)
    // Update the object
    order.setModified(now);
    order.setProcessed(true);
    order.setProcessingDate(now);
    order.setStatusId(statusId);
  }

  public static void markStatusAsPartiallyPrepared(Order order) {
    // Determine the new status value
    int statusId = OrderStatusCommand.retrieveStatusId(PARTIALLY_PREPARED);
    Timestamp now = new Timestamp(System.currentTimeMillis());
    // Update the order status
    SqlUtils updateValues = new SqlUtils()
        .add("status", statusId)
        .add("modified", now);
    SqlUtils where = new SqlUtils()
        .add("order_id = ?", order.getId());
    DB.update(TABLE_NAME, updateValues, where);
    // @todo Append to the order_history (PARTIALLY_PREPARED)
    // Update the object
    order.setModified(now);
    order.setProcessingDate(now);
    order.setStatusId(statusId);
  }

  public static void markStatusAsCanceled(Order order) {
    // Determine the new status value
    int statusId = OrderStatusCommand.retrieveStatusId(CANCELED);
    Timestamp now = new Timestamp(System.currentTimeMillis());
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Update the order status
        SqlUtils updateValues = new SqlUtils()
            .add("canceled", true)
            .add("canceled_date", now)
            .add("status", statusId)
            .add("modified", now);
        SqlUtils where = new SqlUtils()
            .add("order_id = ?", order.getId());
        DB.update(connection, TABLE_NAME, updateValues, where);
        // Mark the order items as canceled too
        OrderItemRepository.markStatusAsCanceled(connection, order);
        // @todo Append to the order_history (CANCELED)
        // Finish the transaction
        transaction.commit();
      }
      // Update the object
      order.setCanceled(true);
      order.setCanceledDate(now);
      order.setStatusId(statusId);
      order.setModified(now);
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage(), se);
    }
  }

  public static void markStatusAsRefunded(Order order, BigDecimal amountRefunded) {
    // Determine the new status value
    int statusId = OrderStatusCommand.retrieveStatusId(REFUNDED);
    Timestamp now = new Timestamp(System.currentTimeMillis());
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Update the order status
        SqlUtils updateValues = new SqlUtils()
            .add("refunded", true)
            .add("refunded_date", now)
            .add("total_refunded = total_refunded + ?", amountRefunded)
            .add("status", statusId)
            .add("modified", new Timestamp(System.currentTimeMillis()));
        SqlUtils where = new SqlUtils()
            .add("order_id = ?", order.getId());
        DB.update(connection, TABLE_NAME, updateValues, where);
        // Mark the order items as refunded too
        OrderItemRepository.markStatusAsRefunded(connection, order);
        // @todo Append to the order_history (REFUNDED)
        // Finish the transaction
        transaction.commit();
      }
      // Update the object
      order.setRefunded(true);
      order.setRefundedDate(now);
      order.setTotalRefunded(amountRefunded);
      order.setStatusId(statusId);
      order.setModified(now);
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage(), se);
    }
  }

  public static void markStatusAsPartiallyShipped(Order order) {
    // Determine the new status value
    int statusId = OrderStatusCommand.retrieveStatusId(PARTIALLY_SHIPPED);
    Timestamp now = new Timestamp(System.currentTimeMillis());
    // Update the order status
    SqlUtils updateValues = new SqlUtils()
        .add("status", statusId)
//        .add("shipped", true)
        .add("shipped_date", now)
        .add("modified", now);
    SqlUtils where = new SqlUtils()
        .add("order_id = ?", order.getId());
    DB.update(TABLE_NAME, updateValues, where);
    // @todo Append to the order_history (PARTIALLY_SHIPPED)
    // Update the object
    order.setModified(now);
    order.setShippedDate(now);
    order.setStatusId(statusId);
  }

  public static void markStatusAsShipped(Order order) {
    // Determine the new status value
    int statusId = OrderStatusCommand.retrieveStatusId(SHIPPED);
    // Determine the date
    Timestamp now = new Timestamp(System.currentTimeMillis());
    if (order.getShippedDate() == null) {
      order.setShippedDate(now);
    }
    // Update the order status
    SqlUtils updateValues = new SqlUtils()
        .add("status", statusId)
        .add("shipped", true)
        .add("shipped_date", order.getShippedDate())
        .add("modified", now);
    SqlUtils where = new SqlUtils()
        .add("order_id = ?", order.getId());
    DB.update(TABLE_NAME, updateValues, where);
    // @todo Append to the order_history (SHIPPED)
    // Update the object
    order.setModified(now);
    order.setShipped(true);
    order.setShippedDate(now);
    order.setStatusId(statusId);
  }

  public static void updateUserOrders(User user) {
    // Require user record
    if (user == null || StringUtils.isBlank(user.getEmail())) {
      return;
    }
    // Update unlinked orders
    SqlUtils updateValues = new SqlUtils()
        .add("created_by", user.getId());
    SqlUtils where = new SqlUtils()
        .add("created_by IS NULL")
        .add("LOWER(email) = LOWER(?)", user.getEmail());
    DB.update(TABLE_NAME, updateValues, where);
    // @todo Append to the order_history (USER ASSOCIATED)
  }

  public static void export(DataConstraints constraints, File file) {
    SqlUtils selectFields = new SqlUtils()
        .addNames(
            "order_unique_id AS \"Order Number\"",
            "live_mode AS \"Live Mode\"",
            "payment_date AS \"Date Ordered\"",
            "processing_date AS \"Date Processed\"",
            "shipped_date AS \"Date Shipped\"",
            "los.title AS \"Status\"",
            "concat_ws(' ', shipping_first_name, shipping_last_name) AS \"Customer Name\"",
            "shipping_city AS \"City\"",
            "shipping_state AS \"State\"",
            "shipping_country AS \"Country\"",
            "shipping_postal_code AS \"Postal Code\"",
            "currency AS \"Currency\"",
            "subtotal_amount AS \"Subtotal\"",
            "-discount_amount AS \"Discount\"",
            "shipping_amount AS \"Shipping\"",
            "subtotal_amount + shipping_amount + fee_amount - discount_amount AS \"Sales Total\"",
            "tax_amount AS \"Sales Tax\"",
            "tax_rate AS \"Sales Tax Rate\"",
            "total_amount AS \"Total\"",
            "-total_refunded AS \"Refunded\"",
            "promo_code AS \"Promo Code\"",
            "payment_processor AS \"Processor\""
        );
    SqlJoins joins = new SqlJoins().add(STATUS_JOIN);
    // show paid orders, and only refunded ones that have shipped
    SqlUtils where = new SqlUtils()
        .add("live_mode = ?", true)
        .add("paid = ?", true)
        .add("canceled = ?", false)
        .add("(refunded = false OR (refunded = true and shipped = true))");
    // Use the specification to filter results
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("order_id");
    DB.exportToCsvAllFrom(TABLE_NAME, selectFields, joins, where, null, constraints, file);
  }

  public static void exportForTaxJar(DataConstraints constraints, File file) {
    SqlUtils selectFields = new SqlUtils()
        .addNames(
            "'web' AS provider", // web/Square/etc.
            "charge_token AS \"order_id\"",
            "'Order' AS transaction_type", // Order or Refund
            "'' AS transaction_reference_id", // for Refunds
            "payment_date AS \"completed_at\"",
            "concat_ws(' ', shipping_first_name, shipping_last_name) AS \"customer_name\"",
            "'' AS shiptostreet",
            "shipping_city AS \"shiptocity\"",
            "shipping_state AS \"shiptostate\"",
            "shipping_postal_code AS \"shiptozip\"",
            "shipping_country AS \"Country\"",
            "'' AS shiptocountrycode",
            "'' AS from_street",
            "'' AS from_city",
            "'' AS from_state",
            "'' AS from_zip",
            "'' AS from_country",
            "shipping_amount AS \"shipping_amount\"",
            "fee_amount AS \"handling_amount\"",
            "discount_amount AS \"discount_amount\"",
            // without tax; subtotal + shipping + handling - discount
            "subtotal_amount + shipping_amount + fee_amount - discount_amount AS \"total_sale\"",
            "tax_amount AS \"sales_tax\"",
            "total_amount AS \"Total\"",
            "-total_refunded AS \"Refunded\""
        );
    SqlJoins joins = new SqlJoins().add(STATUS_JOIN);
    // show paid orders, and only refunded ones that have shipped
    SqlUtils where = new SqlUtils()
        .add("live_mode = ?", true)
        .add("paid = ?", true)
        .add("canceled = ?", false)
        .add("(refunded = false OR (refunded = true and shipped = true))");
    // Use the specification to filter results
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("order_id");
    DB.exportToCsvAllFrom(TABLE_NAME, selectFields, joins, where, null, constraints, file);
  }
}
