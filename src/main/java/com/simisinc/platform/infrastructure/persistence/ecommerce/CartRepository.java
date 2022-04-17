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

import com.simisinc.platform.domain.model.ecommerce.*;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Persists and retrieves cart objects
 *
 * @author matt rajkowski
 * @created 4/12/19 8:00 AM
 */
public class CartRepository {

  private static Log LOG = LogFactory.getLog(CartRepository.class);

  private static String TABLE_NAME = "carts";
  private static String PRIMARY_KEY[] = new String[]{"cart_id"};

  public static List<Cart> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("cart_id desc"),
        CartRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<Cart>) result.getRecords();
    }
    return null;
  }

  public static Cart findById(long cartId) {
    return (Cart) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("cart_id = ?", cartId),
        CartRepository::buildRecord);
  }

  public static Cart findValidCartByToken(String token) {
    if (StringUtils.isBlank(token)) {
      return null;
    }
    return (Cart) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("cart_unique_id = ?", token)
            .add("(expires IS NULL OR expires > ?)", new Timestamp(System.currentTimeMillis()))
            .add("enabled = ?", true),
        CartRepository::buildRecord);
  }

  public static Cart add(Cart record) {
    SqlUtils insertValues = new SqlUtils()
        .add("cart_unique_id", record.getToken())
        .addIfExists("visitor_id", record.getVisitorId(), -1)
        .addIfExists("session_id", record.getSessionId())
        .addIfExists("customer_id", record.getCustomerId(), -1)
//        .addIfExists("shipping_method", record.getShippingMethodId(), -1)
        .addIfExists("user_id", record.getUserId(), -1)
        .addIfExists("total_items", record.getTotalItems(), 0)
        .add("total_qty", record.getTotalQty())
        .addIfExists("currency", record.getCurrency())
        .add("subtotal_amount", record.getSubtotalAmount())
        .addIfExists("order_id", record.getOrderId(), -1)
        .addIfExists("order_date", record.getOrderDate())
        .addIfExists("expires", record.getExpires())
        .addIfExists("discount_amount", record.getDiscount())
        .addIfExists("promo_code", record.getPromoCode())
        .addIfExists("pricing_rule_1", record.getPricingRuleId(), -1)
        .addIfExists("created_by", record.getCreatedBy(), -1)
        .addIfExists("modified_by", record.getModifiedBy(), -1);
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static boolean addProductToCart(Cart cart, Product product, ProductSku productSku, BigDecimal quantity) {
    if (cart == null || productSku == null) {
      return false;
    }
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        CartItemRepository.addProductToCart(connection, cart, product, productSku, quantity);
        // Update the totals
        SqlUtils update = new SqlUtils()
            .add("total_items = total_items + 1")
            .add("total_qty = total_qty + " + quantity)
            .add("subtotal_amount = subtotal_amount + " + productSku.getPrice().multiply(quantity))
            .add("modified", new Timestamp(System.currentTimeMillis()));
        SqlUtils where = new SqlUtils().add("cart_id = ?", cart.getId());
        DB.update(connection, TABLE_NAME, update, where);
        // Finish the transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  public static boolean updateCart(Cart cart, List<CartItem> cartItemList) {
    if (cart == null || cartItemList == null) {
      return false;
    }
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        CartItemRepository.updateCartItemList(connection, cartItemList);
        // Update the totals (and reset invalid items)
        SqlUtils update = new SqlUtils()
            .add("total_items", cart.getTotalItems())
            .add("total_qty", cart.getTotalQty())
            .add("subtotal_amount", cart.getSubtotalAmount())
            .add("modified", new Timestamp(System.currentTimeMillis()));
        SqlUtils where = new SqlUtils().add("cart_id = ?", cart.getId());
        DB.update(connection, TABLE_NAME, update, where);
        // Finish the transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  public static boolean updateDiscount(Cart cart) {
    if (cart == null) {
      return false;
    }
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Update the rates and taxes
        SqlUtils update = new SqlUtils()
            .add("discount_amount", cart.getDiscount())
            .add("promo_code", cart.getPromoCode())
            .add("pricing_rule_1", cart.getPricingRuleId(), -1)
            .add("modified", new Timestamp(System.currentTimeMillis()));
        SqlUtils where = new SqlUtils().add("cart_id = ?", cart.getId());
        DB.update(connection, TABLE_NAME, update, where);
        // Finish the transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  public static boolean updateShippingRateAndTaxes(Cart cart) {
    if (cart == null) {
      return false;
    }
    long shippingMethod = -1;
    ShippingRate shippingRate = ShippingRateRepository.findById(cart.getShippingRateId());
    if (shippingRate != null) {
      shippingMethod = shippingRate.getShippingMethodId();
    }
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Update the rates and taxes
        SqlUtils update = new SqlUtils()
            .add("shipping_method", shippingMethod, -1)
            .add("shipping_rate_id", cart.getShippingRateId(), -1)
            .add("handling_fee_amount", cart.getHandlingFee())
            .add("handling_fee_tax_amount", cart.getHandlingTax())
            .add("shipping_amount", cart.getShippingFee())
            .add("shipping_tax_amount", cart.getShippingTax())
            .add("tax_amount", cart.getTaxAmount())
            .add("tax_rate", cart.getTaxRate())
            .add("modified", new Timestamp(System.currentTimeMillis()));
        SqlUtils where = new SqlUtils().add("cart_id = ?", cart.getId());
        DB.update(connection, TABLE_NAME, update, where);
        // Finish the transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  public static Cart updateCustomer(Cart cart) {
    SqlUtils updateValues = new SqlUtils()
        .add("customer_id", cart.getCustomerId(), -1)
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("cart_id = ?", cart.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return cart;
    }
    LOG.error("updateCustomer failed!");
    return null;
  }

  private static Cart buildRecord(ResultSet rs) {
    try {
      Cart record = new Cart();
      record.setId(rs.getLong("cart_id"));
      record.setToken(rs.getString("cart_unique_id"));
      record.setVisitorId(rs.getLong("visitor_id"));
      record.setSessionId(rs.getString("session_id"));
      record.setCustomerId(DB.getLong(rs, "customer_id", -1));
      if (record.getCustomerId() == 0) {
        record.setCustomerId(-1);
      }
      record.setUserId(DB.getLong(rs, "user_id", -1));
      record.setTotalItems(rs.getInt("total_items"));
      record.setTotalQty(rs.getBigDecimal("total_qty"));
      // @todo currency
      record.setSubtotalAmount(rs.getBigDecimal("subtotal_amount"));
      record.setOrderId(DB.getLong(rs, "order_id", -1));
      record.setOrderDate(rs.getTimestamp("order_date"));
      record.setCreated(rs.getTimestamp("created"));
      record.setCreatedBy(DB.getLong(rs, "created_by", -1));
      record.setModified(rs.getTimestamp("modified"));
      record.setModifiedBy(DB.getLong(rs, "modified_by", -1));
      record.setExpires(rs.getTimestamp("expires"));
//      record.setShippingMethodId(rs.getInt("shipping_method"));
      record.setShippingRateId(DB.getInt(rs, "shipping_rate_id", -1));
      record.setHandlingFee(rs.getBigDecimal("handling_fee_amount"));
      record.setHandlingTax(rs.getBigDecimal("handling_fee_tax_amount"));
      record.setShippingFee(rs.getBigDecimal("shipping_amount"));
      record.setShippingTax(rs.getBigDecimal("shipping_tax_amount"));
      record.setTaxAmount(rs.getBigDecimal("tax_amount"));
      record.setEnabled(rs.getBoolean("enabled"));
      record.setPromoCode(rs.getString("promo_code"));
      record.setPricingRuleId(DB.getLong(rs, "pricing_rule_1", -1));
      record.setDiscount(rs.getBigDecimal("discount_amount"));
      record.setTaxRate(rs.getBigDecimal("tax_rate"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
