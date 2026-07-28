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

package com.simisinc.platform.presentation.widgets.admin.audit;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.audit.AuditLog;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogRepository;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogSpecification;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.MultipartFileSender;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * The in-app security audit review UI (NIST 800-53 AU-6). Lists audit_log records with filters for
 * category, event type, outcome, actor, source IP, target type, and an occurred-date range (either an
 * explicit range or a quick 1h/24h/7d/30d preset). Read-only and admin-only, plus CSV/JSON export of the
 * currently filtered results.
 *
 * @author SimIS Inc.
 */
public class AuditLogListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  private static Log LOG = LogFactory.getLog(AuditLogListWidget.class);

  static String JSP = "/admin/audit-log-list.jsp";

  // The event categories the application emits (for the filter drop-down). Must be a real
  // java.util.ArrayList, not the List returned by Arrays.asList() -- the JSP's
  // <jsp:useBean id="categoryList" class="java.util.ArrayList" .../> casts the request attribute
  // directly to that concrete class, and Arrays.asList() returns java.util.Arrays$ArrayList, a
  // different class despite the name. That mismatch threw a ClassCastException on every single
  // load of this page (with or without filters) -- this admin page has never actually rendered.
  static final List<String> CATEGORY_LIST = new ArrayList<>(Arrays.asList(
      "authentication", "user_management", "authorization", "configuration", "content", "data_access"));

  public WidgetContext execute(WidgetContext context) {

    // The audit log is sensitive; require admin even if the page were mis-configured (defense in depth)
    if (!context.hasRole("admin")) {
      return context;
    }

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "50"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    constraints.setColumnToSortBy("occurred", "desc");
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    AuditLogSpecification specification = buildSpecification(context);

    // Load the list
    List<AuditLog> auditLogList = AuditLogRepository.findAll(specification, constraints);
    context.getRequest().setAttribute("auditLogList", auditLogList);

    // Echo the filter values back so the forms keep their state, plus the category options
    context.getRequest().setAttribute("categoryList", CATEGORY_LIST);
    echoFilterParameters(context);

    // Carry the filters through pagination (paging_control.jspf appends this to each page link).
    // URL-encoded here so the free-text values (actor, eventType) cannot break the query string or the href.
    StringBuilder pagingParams = new StringBuilder();
    appendParam(pagingParams, "category", context.getParameter("category"));
    appendParam(pagingParams, "eventType", context.getParameter("eventType"));
    appendParam(pagingParams, "outcome", context.getParameter("outcome"));
    appendParam(pagingParams, "actor", context.getParameter("actor"));
    appendParam(pagingParams, "sourceIp", context.getParameter("sourceIp"));
    appendParam(pagingParams, "targetType", context.getParameter("targetType"));
    appendParam(pagingParams, "range", context.getParameter("range"));
    appendParam(pagingParams, "fromDate", context.getParameter("fromDate"));
    appendParam(pagingParams, "toDate", context.getParameter("toDate"));
    context.getRequest().setAttribute("recordPagingParams", pagingParams.toString());

    // How long events are retained before the nightly job purges them (NIST AU-11); display-only here,
    // the value itself is configured as a site property and enforced by AuditLogRetentionJob.
    int retentionDays = AuditLogRepository.resolveRetentionDays(LoadSitePropertyCommand.loadByName("audit.retentionDays"));
    context.getRequest().setAttribute("retentionDays", retentionDays);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    context.setJsp(JSP);
    return context;
  }

  /** Handles CSV/JSON export of the currently filtered (unpaginated) results. */
  public WidgetContext post(WidgetContext context) {
    if (!context.hasRole("admin")) {
      return context;
    }
    String command = context.getParameter("command");
    if ("downloadCSVFile".equals(command)) {
      return downloadFile(context, "csv");
    } else if ("downloadJSONFile".equals(command)) {
      return downloadFile(context, "json");
    }
    return null;
  }

  private WidgetContext downloadFile(WidgetContext context, String extension) {
    AuditLogSpecification specification = buildSpecification(context);
    String displayFilename = "audit-log-" + new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date()) + "." + extension;
    File tempFile = FileSystemCommand.generateTempFile("exports", context.getUserId(), extension);
    try {
      if ("csv".equals(extension)) {
        AuditLogRepository.exportCsv(specification, tempFile);
      } else {
        AuditLogRepository.exportJson(specification, tempFile);
      }
      String mimeType = "csv".equals(extension) ? "text/csv" : "application/json";
      MultipartFileSender.fromFile(tempFile)
          .with(context.getRequest())
          .with(context.getResponse())
          .withMimeType(mimeType)
          .withFilename(displayFilename)
          .serveResource();
      // Exporting the audit log is itself a data-access event worth auditing.
      AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, "audit_log.export", AuditEventCommand.SUCCESS,
          "audit_log", "filtered", displayFilename, "format=" + extension);
    } catch (Exception e) {
      LOG.error("Audit log export failed", e);
      AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, "audit_log.export", AuditEventCommand.FAILURE,
          "audit_log", "filtered", displayFilename, "format=" + extension);
    } finally {
      if (tempFile.exists()) {
        tempFile.delete();
      }
    }
    context.setHandledResponse(true);
    return context;
  }

  /** Builds the filter specification from request parameters; shared by execute() and the export actions. */
  private AuditLogSpecification buildSpecification(WidgetContext context) {
    String category = context.getParameter("category");
    String eventType = context.getParameter("eventType");
    String outcome = context.getParameter("outcome");
    String actor = context.getParameter("actor");
    String sourceIp = context.getParameter("sourceIp");
    String targetType = context.getParameter("targetType");
    String range = context.getParameter("range");
    String fromDate = context.getParameter("fromDate");
    String toDate = context.getParameter("toDate");

    AuditLogSpecification specification = new AuditLogSpecification();
    if (StringUtils.isNotBlank(category)) {
      specification.setEventCategory(category);
    }
    if (StringUtils.isNotBlank(eventType)) {
      specification.setEventType(eventType);
    }
    if (StringUtils.isNotBlank(outcome)) {
      specification.setOutcome(outcome);
    }
    if (StringUtils.isNotBlank(actor)) {
      specification.setActorUsername(actor.trim());
    }
    if (StringUtils.isNotBlank(sourceIp)) {
      specification.setSourceIp(sourceIp.trim());
    }
    if (StringUtils.isNotBlank(targetType)) {
      specification.setTargetType(targetType.trim());
    }

    // A quick range preset (1h/24h/7d/30d) takes precedence over an explicit date range -- it is finer
    // grained (hour precision) than the date-only fromDate/toDate inputs can express.
    Timestamp rangeCutoff = resolveRangeCutoff(range);
    if (rangeCutoff != null) {
      specification.setOccurredAfter(rangeCutoff);
    } else {
      // Parse the yyyy-MM-dd date range: from = start of that day, to = start of the day AFTER (half-open)
      Timestamp from = parseDate(fromDate, 0);
      Timestamp to = parseDate(toDate, 1);
      if (from != null) {
        specification.setOccurredAfter(from);
      }
      if (to != null) {
        specification.setOccurredBefore(to);
      }
    }
    return specification;
  }

  /** Echoes the raw filter parameters back to the request so the filter forms keep their state. */
  private void echoFilterParameters(WidgetContext context) {
    context.getRequest().setAttribute("category", context.getParameter("category"));
    context.getRequest().setAttribute("eventType", context.getParameter("eventType"));
    context.getRequest().setAttribute("outcome", context.getParameter("outcome"));
    context.getRequest().setAttribute("actor", context.getParameter("actor"));
    context.getRequest().setAttribute("sourceIp", context.getParameter("sourceIp"));
    context.getRequest().setAttribute("targetType", context.getParameter("targetType"));
    context.getRequest().setAttribute("range", context.getParameter("range"));
    context.getRequest().setAttribute("fromDate", context.getParameter("fromDate"));
    context.getRequest().setAttribute("toDate", context.getParameter("toDate"));
  }

  /** Appends {@code name=urlEncoded(value)} to the paging query string when the value is present. */
  private void appendParam(StringBuilder sb, String name, String value) {
    if (StringUtils.isBlank(value)) {
      return;
    }
    if (sb.length() > 0) {
      sb.append("&");
    }
    sb.append(name).append("=").append(URLEncoder.encode(value, StandardCharsets.UTF_8));
  }

  /** Parses a yyyy-MM-dd string to a start-of-day Timestamp plus {@code plusDays}; null when blank/invalid. */
  private Timestamp parseDate(String value, int plusDays) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    try {
      LocalDate date = LocalDate.parse(value.trim()).plusDays(plusDays);
      return Timestamp.valueOf(date.atStartOfDay());
    } catch (Exception e) {
      return null;
    }
  }

  /** Resolves a quick range preset to an "occurred after" cutoff; null when absent or unrecognized. */
  static Timestamp resolveRangeCutoff(String range) {
    if (StringUtils.isBlank(range)) {
      return null;
    }
    Instant now = Instant.now();
    switch (range.trim()) {
      case "1h":
        return Timestamp.from(now.minus(1, ChronoUnit.HOURS));
      case "24h":
        return Timestamp.from(now.minus(24, ChronoUnit.HOURS));
      case "7d":
        return Timestamp.from(now.minus(7, ChronoUnit.DAYS));
      case "30d":
        return Timestamp.from(now.minus(30, ChronoUnit.DAYS));
      default:
        return null;
    }
  }
}
