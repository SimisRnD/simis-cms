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

package com.simisinc.platform.application.ecommerce;

import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.domain.model.ecommerce.OrderItem;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderItemRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/14/19 6:54 PM
 */
public class OrderStatusCommand {

  public static final String CREATED = "CREATED";
  public static final String PAID = "PAID";
  public static final String PREPARING = "PREPARING";
  public static final String PARTIALLY_PREPARED = "PARTIALLY PREPARED";
  public static final String FULFILLED = "FULFILLED";
  public static final String SHIPPED = "SHIPPED";
  public static final String PARTIALLY_SHIPPED = "PARTIALLY SHIPPED";
  public static final String COMPLETED = "COMPLETED";
  public static final String ON_HOLD = "ON HOLD";
  public static final String CANCELED = "CANCELED";
  public static final String RETURNED = "RETURNED";
  public static final String REFUNDED = "REFUNDED";

  private static Log LOG = LogFactory.getLog(OrderStatusCommand.class);

  public static int retrieveStatusId(String code) {
    if (StringUtils.isBlank(code)) {
      return -1;
    }
    SqlUtils where = new SqlUtils().add("LOWER(code) = ?", code.toLowerCase());
    return (int) DB.selectFunction("status_id", "lookup_order_status", where);
  }

  public static String status(int statusId) {
    if (statusId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils().add("status_id = ?", statusId);
    return DB.selectStringValue("title", "lookup_order_status", where);
  }

  public static String currentStatus(int statusId) {
    if (statusId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils().add("status_id = ?", statusId);
    String code = DB.selectStringValue("code", "lookup_order_status", where).toUpperCase();
    if (CREATED.equals(code)) {
      return "New order";
    } else if (PAID.equals(code)) {
      return "Paid";
    } else if (PREPARING.equals(code)) {
      return "Preparing";
    } else if (PARTIALLY_PREPARED.equals(code)) {
      return "Partially Prepared";
    } else if (FULFILLED.equals(code)) {
      return "Fulfilled";
    } else if (SHIPPED.equals(code)) {
      return "Shipped";
    } else if (PARTIALLY_SHIPPED.equals(code)) {
      return "Partially Shipped";
    } else if (COMPLETED.equals(code)) {
      return "Completed";
    } else if (ON_HOLD.equals(code)) {
      return "On Hold";
    } else if (CANCELED.equals(code)) {
      return "Canceled";
    } else if (RETURNED.equals(code)) {
      return "Returned";
    } else if (REFUNDED.equals(code)) {
      return "Refunded";
    }
    return null;
  }

  public static boolean isFullyPrepared(Order order) {
    List<OrderItem> orderItemList = OrderItemRepository.findItemsByOrderId(order.getId());
    for (OrderItem orderItem : orderItemList) {
      if (!orderItem.getProcessed()) {
        return false;
      }
    }
    return true;
  }

  public static boolean isFullyShipped(Order order) {
    List<OrderItem> orderItemList = OrderItemRepository.findItemsByOrderId(order.getId());
    for (OrderItem orderItem : orderItemList) {
      if (!orderItem.getShipped()) {
        return false;
      }
    }
    return true;
  }
}
