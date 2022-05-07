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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.ecommerce.BoxzookaProductCommand;
import com.simisinc.platform.application.ecommerce.DeleteProductCommand;
import com.simisinc.platform.application.ecommerce.SquareProductCatalogCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.ecommerce.Product;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductSkuRepository;
import com.simisinc.platform.presentation.controller.MultipartFileSender;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/17/19 7:23 PM
 */
public class ProductListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/product-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Load the product list
    List<Product> productList = ProductRepository.findAll();
    context.getRequest().setAttribute("productList", productList);

    // Show the editor
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
      return downloadCSVFile(context);
    } else if ("syncProducts".equals(command)) {
      return syncProducts(context);
    }
    // Default to nothing
    return null;
  }

  private WidgetContext downloadCSVFile(WidgetContext context) {
    // Prepare to save the temporary file
    String extension = "csv";
    String displayFilename = "products-" + new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date()) + "." + extension;
    File tempFile = FileSystemCommand.generateTempFile("exports", context.getUserId(), extension);
    try {
      // Export the data to the file
      ProductSkuRepository.export(null, tempFile);
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

  private WidgetContext syncProducts(WidgetContext context) {

    StringBuilder successMessage = new StringBuilder();

    // Sync to Payment Processor service
    try {
      if (SquareProductCatalogCommand.syncProducts()) {
        if (successMessage.length() > 0) {
          successMessage.append("; ");
        }
        successMessage.append("Products have been sync'd with Square");
      }
    } catch (DataException de) {
      context.setWarningMessage(de.getMessage());
    } catch (Exception e) {
      LOG.error("Square sync error", e);
      context.setErrorMessage("An error syncing Square occurred: " + e.getMessage());
    }

    // Sync to Fulfillment service
    try {
      if (BoxzookaProductCommand.syncProducts()) {
        if (successMessage.length() > 0) {
          successMessage.append("; ");
        }
        successMessage.append("Products have been sync'd with Boxzooka");
      }
    } catch (DataException de) {
      context.setWarningMessage(de.getMessage());
    } catch (Exception e) {
      context.setErrorMessage("An error syncing to Boxzooka occurred: " + e.getMessage());
    }

    // Determine the user message
    if (successMessage.length() > 0) {
      context.setSuccessMessage(successMessage.toString());
    } else {
      context.setWarningMessage("Sync did not finish");
    }
    return context;
  }

  public WidgetContext delete(WidgetContext context) {
    // Permission is required
    if (!(context.hasRole("admin") || context.hasRole("ecommerce-manager"))) {
      return context;
    }
    // Determine what's being deleted
    long productId = context.getParameterAsLong("productId");
    if (productId > -1) {
      Product product = ProductRepository.findById(productId);
      if (product == null) {
        context.setErrorMessage("Product not found");
      } else {
        if (product.getOrderCount() > 0) {
          context.setWarningMessage("Product cannot be deleted, there are related records");
        } else if (DeleteProductCommand.delete(product)) {
          context.setSuccessMessage("Product deleted");
        } else {
          context.setWarningMessage("Product could not be deleted, there are dependencies");
        }
      }
    }
    context.setRedirect("/admin/products");
    return context;
  }
}
