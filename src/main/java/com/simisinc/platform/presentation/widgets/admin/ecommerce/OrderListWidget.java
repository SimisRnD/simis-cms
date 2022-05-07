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

package com.simisinc.platform.presentation.widgets.admin.ecommerce;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.simisinc.platform.application.admin.EcommerceCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderSpecification;
import com.simisinc.platform.presentation.controller.MultipartFileSender;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

import org.apache.commons.lang3.StringUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/17/19 7:22 PM
 */
public class OrderListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/order-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "20"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Determine criteria (order number, email, phone, name)
    OrderSpecification specification = new OrderSpecification();

    // Show either the sandbox or the real ones depending on site status (change to session variable)
    specification.setShowSandbox(!EcommerceCommand.isProductionEnabled());
    specification.setShowIncompleteOrders(false);

    String orderNumber = context.getParameter("searchOrderNumber");
    if (StringUtils.isNotBlank(orderNumber)) {
      specification.setUniqueId(orderNumber);
    }
    String customerNumber = context.getParameter("searchCustomerNumber");
    if (StringUtils.isNotBlank(customerNumber)) {
      specification.setCustomerNumber(customerNumber);
    }
    String email = context.getParameter("searchEmail");
    if (StringUtils.isNotBlank(email)) {
      specification.setEmail(email);
    }
    String phoneNumber = context.getParameter("searchPhoneNumber");
    if (StringUtils.isNotBlank(phoneNumber)) {
      specification.setPhoneNumber(phoneNumber);
    }
    String name = context.getParameter("searchName");
    if (StringUtils.isNotBlank(name)) {
      specification.setName(name);
    }

    // Load the latest orders
    List<Order> orderList = OrderRepository.findAll(specification, constraints);
    context.getRequest().setAttribute("orderList", orderList);

    // Show the list
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {
    // Permission is required
    if (!(context.hasRole("admin") || context.hasRole("ecommerce-manager"))) {
      return context;
    }
    // Determine the action
    String command = context.getParameter("command");
    if ("downloadCSVFile".equals(command)) {
      return downloadCSVFile(context, "csv", "orders");
    } else if ("downloadTaxJarCSVFile".equals(command)) {
      return downloadCSVFile(context, "taxjar", "taxjar");
    }
    // Default to nothing
    return null;
  }

  private WidgetContext downloadCSVFile(WidgetContext context, String exportType, String prefix) {
    // Prepare to save the temporary file
    String extension = "csv";
    String displayFilename = prefix + "-" + new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date()) + "." + extension;
    File tempFile = FileSystemCommand.generateTempFile("exports", context.getUserId(), extension);
    try {
      if ("taxjar".equals(exportType)) {
        // Export the data to the file
        OrderRepository.exportForTaxJar(null, tempFile);
      } else {
        // Export the data to the file
        OrderRepository.export(null, tempFile);
      }
      // Send it
      String mimeType = "text/csv";
      MultipartFileSender.fromFile(tempFile)
          .with(context.getRequest())
          .with(context.getResponse())
          .withMimeType(mimeType)
          .withFilename(displayFilename)
          .serveResource();
    } catch (Exception e) {
      LOG.error("Download CSV Error", e);
    } finally {
      if (tempFile.exists()) {
        LOG.warn("Deleting a temporary file: " + tempFile.getAbsolutePath());
        tempFile.delete();
      }
    }
    context.setHandledResponse(true);
    return context;
  }
}
