/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.presentation.widgets.admin;

import com.simisinc.platform.application.admin.ProcessBotListCSVFileCommand;
import com.simisinc.platform.application.cms.DeleteBotUserAgentListCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.BotUserAgent;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.BotUserAgentRepository;
import com.simisinc.platform.presentation.controller.MultipartFileSender;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Manages the admin-facing, database-backed bot user-agent list
 *
 * @author elizabeth houser
 */
public class BotUserAgentListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908895L;

  static String JSP = "/admin/bot-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "20"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Load the list
    constraints.setColumnToSortBy("created", "desc");
    List<BotUserAgent> botUserAgentList = BotUserAgentRepository.findAll(constraints);
    context.getRequest().setAttribute("botUserAgentList", botUserAgentList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {
    // Determine what's being deleted
    long recordId = context.getParameterAsLong("botListId");
    if (recordId > -1) {
      BotUserAgent botUserAgent = BotUserAgentRepository.findById(recordId);
      if (botUserAgent == null) {
        // Already gone -- e.g. a double-click, or another admin removed it first. Report this
        // distinctly rather than letting the delete attempt below NPE into a generic error
        context.setWarningMessage("This entry was already removed.");
        return context;
      }
      // Capture the value before removal
      String targetLabel = botUserAgent.getUserAgent();
      try {
        boolean removed = DeleteBotUserAgentListCommand.delete(botUserAgent);
        AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "bot_user_agent.remove",
            removed ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
            "bot_user_agent", String.valueOf(recordId), targetLabel, null);
        context.setSuccessMessage("Record deleted");
        return context;
      } catch (Exception e) {
        AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "bot_user_agent.remove",
            AuditEventCommand.FAILURE, "bot_user_agent", String.valueOf(recordId), targetLabel, e.getMessage());
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
    String displayFilename = "bot-list-" + new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date()) + "." + extension;
    File tempFile = FileSystemCommand.generateTempFile("exports", context.getUserId(), extension);
    try {
      // Export the data to the file
      BotUserAgentRepository.export(null, tempFile);
      // Send it
      String mimeType = "text/csv";
      MultipartFileSender.fromFile(tempFile)
          .with(context.getRequest())
          .with(context.getResponse())
          .withMimeType(mimeType)
          .withFilename(displayFilename)
          .serveResource();
      // Record the export of the bot list
      AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, "data.export", AuditEventCommand.SUCCESS,
          "bot_user_agent_list", "all", displayFilename, "format=" + extension);
    } catch (Exception e) {
      LOG.error("Download CSV Error", e);
      AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, "data.export", AuditEventCommand.FAILURE,
          "bot_user_agent_list", "all", displayFilename, "format=" + extension);
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
    LOG.info("User is uploading a bot-list CSV file...");
    try {
      ProcessBotListCSVFileCommand.ImportResult result = ProcessBotListCSVFileCommand.processCSV(context);
      AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "bot_user_agent.import", AuditEventCommand.SUCCESS,
          "bot_user_agent", null, null, "records=" + result.getRecordCount() + ", skipped=" + result.getSkippedCount());
      context.setSuccessMessage(buildImportSummaryMessage(result));
    } catch (Exception e) {
      AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "bot_user_agent.import", AuditEventCommand.FAILURE,
          "bot_user_agent", null, null, e.getMessage());
      context.setErrorMessage(e.getMessage());
    }
    return context;
  }

  private String buildImportSummaryMessage(ProcessBotListCSVFileCommand.ImportResult result) {
    // Every row falls into exactly one bucket: added, removed (Remove=true), skipped as a
    // duplicate (silently correct, not an error), or skipped due to an error. Only the last two
    // get folded into "skipped" below since neither the admin nor this message can tell them
    // apart from the ImportResult alone -- what matters is stating added/removed counts
    // explicitly rather than letting them go unmentioned, which previously made a fully
    // successful bulk-removal (0 added, N removed) read as "0 records added", i.e. as if nothing
    // happened.
    StringBuilder message = new StringBuilder();
    message.append(result.getRecordCount()).append(" record").append(result.getRecordCount() != 1 ? "s" : "")
        .append(" added");
    if (result.getRemovedCount() > 0) {
      message.append(", ").append(result.getRemovedCount()).append(" removed");
    }
    if (result.getSkippedCount() > 0) {
      message.append(", ").append(result.getSkippedCount()).append(" skipped -- see the application log");
    }
    message.append(" (").append(result.getTotalRowCount()).append(" row")
        .append(result.getTotalRowCount() != 1 ? "s" : "").append(" total)");
    return message.toString();
  }
}
