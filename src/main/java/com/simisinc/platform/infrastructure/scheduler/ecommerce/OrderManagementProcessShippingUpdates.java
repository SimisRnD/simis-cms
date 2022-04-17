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

package com.simisinc.platform.infrastructure.scheduler.ecommerce;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.EcommerceCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.ecommerce.OrderShipmentStatusCommand;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderSpecification;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;

import java.util.List;

/**
 * Retrieves shipping status updates for orders
 *
 * @author matt rajkowski
 * @created 12/15/19 11:37 AM
 */
public class OrderManagementProcessShippingUpdates {

  private static Log LOG = LogFactory.getLog(OrderManagementProcessShippingUpdates.class);

  @Job(name = "Check on the shipping status of orders and send notifications")
  public static void execute() {

    // Determine the processor
    String service = LoadSitePropertyCommand.loadByName("ecommerce.orderFulfillment");
    if (StringUtils.isBlank(service) || "None".equalsIgnoreCase(service)) {
      LOG.debug("Order fulfillment is not configured");
      return;
    }

    // Process 10 at a time?
    int page = 1;
    int itemsPerPage = -1;
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);

    // Determine criteria for retrieving processed, but not shipped, orders
    OrderSpecification specification = new OrderSpecification();
    specification.setShowSandbox(!EcommerceCommand.isProductionEnabled());
    specification.setShowIncompleteOrders(false);
    // @note being done per item right now
    // @todo change to setShowOrdersWithProcessedItems(true);
    specification.setShowCanceledOrders(false);
    specification.setShowShippedOrders(false);

    // Load the latest orders
    List<Order> orderList = OrderRepository.findAll(specification, constraints);
    if (LOG.isDebugEnabled()) {
      if (!orderList.isEmpty()) {
        LOG.debug("Orders found needing shipping updates? " + orderList.size());
      }
    }
    for (Order order : orderList) {
      try {
        if (OrderShipmentStatusCommand.checkAndUpdateStatus(order)) {
          LOG.info("Updated status for an order: " + order.getUniqueId());
        }
      } catch (DataException de) {
        LOG.error("Order status error: " + de.getMessage(), de);
      }
    }
  }
}
