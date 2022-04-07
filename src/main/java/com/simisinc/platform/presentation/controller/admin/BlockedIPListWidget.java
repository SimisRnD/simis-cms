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

package com.simisinc.platform.presentation.controller.admin;

import com.simisinc.platform.application.admin.ProcessBlockListCSVFileCommand;
import com.simisinc.platform.application.cms.DeleteBlockedIPListCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.BlockedIP;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.BlockedIPRepository;
import com.simisinc.platform.presentation.controller.MultipartFileSender;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/30/18 8:30 AM
 */
public class BlockedIPListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/blocked-ip-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "20"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Load the list
    constraints.setColumnToSortBy("created", "desc");
    List<BlockedIP> blockedIPList = BlockedIPRepository.findAll(constraints);
    context.getRequest().setAttribute("blockedIPList", blockedIPList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {
    // Determine what's being deleted
    long recordId = context.getParameterAsLong("blockedIPListId");
    if (recordId > -1) {
      BlockedIP blockedIP = BlockedIPRepository.findById(recordId);
      try {
        DeleteBlockedIPListCommand.delete(blockedIP);
        context.setSuccessMessage("Record deleted");
        return context;
      } catch (Exception e) {
        context.setErrorMessage("Error. Record could not be deleted.");
        return context;
      }
    }
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {
    // Permission is required
    if (!context.hasRole("admin")) {
      return context;
    }
    // Determine the action
    String command = context.getParameter("command");
    if ("downloadCSVFile".equals(command)) {
      return downloadCSVFile(context);
    } else if ("uploadCSVFile".equals(command)) {
      return uploadCSVFileAction(context);
    }
    // Default to nothing
    return null;
  }

  private WidgetContext downloadCSVFile(WidgetContext context) {
    // Prepare to save the temporary file
    String extension = "csv";
    String displayFilename = "blocked-ip-list-" + new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date()) + "." + extension;
    File tempFile = FileSystemCommand.generateTempFile("exports", context.getUserId(), extension);
    try {
      // Export the data to the file
      BlockedIPRepository.export(null, tempFile);
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

  private WidgetContext uploadCSVFileAction(WidgetContext context) {
    LOG.info("User is uploading a CSV file...");
    try {
      int recordCount = ProcessBlockListCSVFileCommand.processCSV(context);
      context.setSuccessMessage(recordCount + " record" + (recordCount != 1 ? "s" : "") + " added");
    } catch (Exception e) {
      context.setErrorMessage(e.getMessage());
    }
    return context;
  }
}
