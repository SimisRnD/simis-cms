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

import com.simisinc.platform.domain.model.ecommerce.Cart;
import com.simisinc.platform.domain.model.ecommerce.CartItem;
import com.simisinc.platform.domain.model.ecommerce.Product;
import com.simisinc.platform.domain.model.ecommerce.ProductSku;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Persists and retrieves cart item objects
 *
 * @author matt rajkowski
 * @created 4/14/19 9:57 PM
 */
public class CartItemRepository {

  private static Log LOG = LogFactory.getLog(CartItemRepository.class);

  private static String TABLE_NAME = "cart_items";
  private static String[] PRIMARY_KEY = new String[]{"item_id"};

  public static List<CartItem> findValidItemsByCartId(long cartId) {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("is_removed = ?", false)
            .add("cart_id = ?", cartId),
        new DataConstraints().setDefaultColumnToSortBy("item_id").setUseCount(false),
        CartItemRepository::buildRecord);
    return (List<CartItem>) result.getRecords();
  }

  public static CartItem findById(long itemId) {
    return (CartItem) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("item_id = ?", itemId),
        CartItemRepository::buildRecord);
  }


  public static void addProductToCart(Connection connection, Cart cart, Product product, ProductSku productSku, BigDecimal quantity) throws SQLException {
    if (cart == null || productSku == null) {
      throw new SQLException("Invalid request");
    }
    SqlUtils insertValues = new SqlUtils();
    insertValues
        .add("cart_id", cart.getId())
        .add("product_id", productSku.getProductId())
        .add("sku_id", productSku.getId())
        .add("quantity", quantity)
        .add("each_amount", productSku.getPrice())
        .add("total_amount", productSku.getPrice().multiply(quantity))
        .add("product_name", product.getNameWithCaption())
//    .add("product_type", product.get)
        .add("product_sku", productSku.getSku())
        .add("product_barcode", productSku.getBarcode())
        .add("is_preorder", false)
        .add("is_backordered", false)
        .add("is_removed", false)
//    .add("created_by", )
//    .add("modified_by", )
    ;
    DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY);
  }

  public static void updateCartItemList(Connection connection, List<CartItem> cartItemList) throws SQLException {
    if (cartItemList == null) {
      throw new SQLException("List is null");
    }
    for (CartItem cartItem : cartItemList) {
      SqlUtils setValues = new SqlUtils();
      setValues
          .add("quantity", cartItem.getQuantity())
          .add("each_amount", cartItem.getEachAmount())
          .add("total_amount", cartItem.getTotalAmount())
          .add("is_removed", cartItem.getRemoved());
      SqlUtils where = new SqlUtils().add("item_id = ?", cartItem.getId());
      DB.update(connection, TABLE_NAME, setValues, where);
    }
  }

  public static boolean removeItemFromCart(Cart cart, CartItem cartItem) {
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        {
          // Set as removed
          SqlUtils update = new SqlUtils()
              .add("is_removed", true)
              .add("modified", new Timestamp(System.currentTimeMillis()));
          SqlUtils where = new SqlUtils().add("item_id = ?", cartItem.getId());
          DB.update(connection, TABLE_NAME, update, where);
//          DataSource.deleteFrom(connection, TABLE_NAME, where);
        }
        {
          // Update the cart's total_items, total_qty, subtotal_amount, modified
          SqlUtils update = new SqlUtils()
              .add("total_items = total_items - 1")
              .add("total_qty = total_qty - " + cartItem.getQuantity())
              .add("subtotal_amount = subtotal_amount - " + cartItem.getQuantity().multiply(cartItem.getEachAmount()))
              .add("modified", new Timestamp(System.currentTimeMillis()));
          SqlUtils where = new SqlUtils().add("cart_id = ?", cartItem.getCartId());
          DB.update(connection, "carts", update, where);
        }
        // Finish the transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  public static void updateQuantityFree(CartItem cartItem) {
    SqlUtils setValues = new SqlUtils();
    setValues
        .add("quantity_free", cartItem.getQuantityFree());
    SqlUtils where = new SqlUtils().add("item_id = ?", cartItem.getId());
    DB.update(TABLE_NAME, setValues, where);
  }

  public static void resetQuantityFree(Cart cart) {
    if (cart == null) {
      return;
    }
    SqlUtils setValues = new SqlUtils();
    setValues
        .add("quantity_free", new BigDecimal(0));
    SqlUtils where = new SqlUtils().add("cart_id = ?", cart.getId());
    DB.update(TABLE_NAME, setValues, where);
  }

  private static CartItem buildRecord(ResultSet rs) {
    try {
      CartItem record = new CartItem();
      record.setId(rs.getLong("item_id"));
      record.setCartId(rs.getLong("cart_id"));
      record.setProductId(rs.getLong("product_id"));
      record.setSkuId(rs.getLong("sku_id"));
      record.setQuantity(rs.getBigDecimal("quantity"));
      record.setCurrency(rs.getString("currency"));
      record.setEachAmount(rs.getBigDecimal("each_amount"));
      record.setTotalAmount(rs.getBigDecimal("total_amount"));
      record.setProductName(rs.getString("product_name"));
      record.setProductType(rs.getString("product_type"));
      record.setProductSku(rs.getString("product_sku"));
      record.setPreorder(rs.getBoolean("is_preorder"));
      record.setBackordered(rs.getBoolean("is_backordered"));
      record.setCreated(rs.getTimestamp("created"));
      record.setCreatedBy(DB.getLong(rs, "created_by", -1));
      record.setModified(rs.getTimestamp("modified"));
      record.setModifiedBy(DB.getLong(rs, "modified_by", -1));
      record.setProductBarcode(rs.getString("product_barcode"));
      record.setQuantityFree(rs.getBigDecimal("quantity_free"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
