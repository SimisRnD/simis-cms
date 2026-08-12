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

package com.simisinc.platform.presentation.widgets.admin.cms;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.cms.FunnelEventCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataSpecification;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.MultipartFileSender;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

import java.util.Date;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/24/18 7:33 PM
 */
public class FormDataListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/form-data-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "10"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage, "created", "desc");
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Determine the filter criteria (issue #563 -- this page previously had zero filter controls)
    String formUniqueId = context.getParameter("formUniqueId");
    String status = context.getParameter("status");
    String spam = context.getParameter("spam");
    String fromDate = context.getParameter("fromDate");
    String toDate = context.getParameter("toDate");

    // issue #1162 -- a direct link from the submission-notification email carries formDataId and
    // must show that one record regardless of its current status/form/date, rather than silently
    // returning zero rows because it no longer matches whatever the other filters would default to
    boolean singleSubmissionView = context.getParameterAsLong("formDataId") > -1;
    context.getRequest().setAttribute("singleSubmissionView", singleSubmissionView);

    FormDataSpecification specification = buildSpecificationFromParameters(context);

    // Load the latest form data
    List<FormData> formDataList = FormDataRepository.findAll(specification, constraints);
    context.getRequest().setAttribute("formDataList", formDataList);

    // Echo the filter values back so the form keeps its state
    context.getRequest().setAttribute("formUniqueId", formUniqueId);
    context.getRequest().setAttribute("status", StringUtils.isBlank(status) ? "awaiting" : status);
    context.getRequest().setAttribute("spam", spam);
    context.getRequest().setAttribute("fromDate", fromDate);
    context.getRequest().setAttribute("toDate", toDate);

    // Carry the filters through pagination (paging_control.jspf appends this to each page link)
    StringBuilder pagingParams = new StringBuilder();
    appendParam(pagingParams, "formUniqueId", formUniqueId);
    appendParam(pagingParams, "status", status);
    appendParam(pagingParams, "spam", spam);
    appendParam(pagingParams, "fromDate", fromDate);
    appendParam(pagingParams, "toDate", toDate);
    context.getRequest().setAttribute("recordPagingParams", pagingParams.toString());

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  /**
   * Builds the on-screen list's filter criteria from the current request parameters (issue #563's
   * formUniqueId/status/fromDate/toDate filters). Shared by {@link #execute} (the on-screen list)
   * and {@link #downloadCSVFile} (the CSV export) so the export can never drift from what's actually
   * filtered on screen -- previously downloadCSVFile() ignored these parameters entirely and always
   * exported the whole form_data table regardless of the active filters.
   */
  private FormDataSpecification buildSpecificationFromParameters(WidgetContext context) {
    // issue #1162 -- a formDataId (from an emailed direct link) always wins and scopes to exactly
    // that record; the other filters below don't apply and are skipped so a submission that's
    // already moved out of the "awaiting" default (or any other filter) is still found
    long formDataId = context.getParameterAsLong("formDataId");
    if (formDataId > -1) {
      FormDataSpecification specification = new FormDataSpecification();
      specification.setId(formDataId);
      return specification;
    }

    String formUniqueId = context.getParameter("formUniqueId");
    String status = context.getParameter("status");
    String spam = context.getParameter("spam");
    String fromDate = context.getParameter("fromDate");
    String toDate = context.getParameter("toDate");

    FormDataSpecification specification = new FormDataSpecification();
    if (StringUtils.isNotBlank(formUniqueId)) {
      specification.setFormUniqueId(formUniqueId);
    }
    if ("claimed".equalsIgnoreCase(status)) {
      specification.setClaimed(true);
    } else if ("processed".equalsIgnoreCase(status)) {
      specification.setProcessed(true);
    } else if ("dismissed".equalsIgnoreCase(status)) {
      specification.setDismissed(true);
    } else {
      // Default view: the original hardcoded behavior -- awaiting review
      specification.setDismissed(false);
      specification.setProcessed(false);
    }
    if ("flagged".equalsIgnoreCase(spam)) {
      specification.setFlaggedAsSpam(true);
    } else if ("excluded".equalsIgnoreCase(spam)) {
      specification.setFlaggedAsSpam(false);
    }
    // Else "All" (blank/missing) -- flaggedAsSpam stays DataConstants.UNDEFINED, no WHERE clause added
    Timestamp from = parseDate(fromDate, 0);
    Timestamp to = parseDate(toDate, 1);
    if (from != null) {
      specification.setOccurredAfter(from);
    }
    if (to != null) {
      specification.setOccurredBefore(to);
    }
    return specification;
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

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {
    // CSV export (issue #483). Permission is required -- this streams a raw file response,
    // bypassing normal template rendering, so it gets its own explicit role check rather than
    // relying solely on the page-level access control that gates this widget in the first place.
    if ("downloadCSVFile".equals(context.getParameter("command"))) {
      if (!(context.hasRole("admin") || context.hasRole("community-manager"))) {
        return context;
      }
      return downloadCSVFile(context);
    }

    // These are submitted via a real POST (issue #358 moved state-changing admin actions off
    // GET query strings), so they arrive here rather than in action() below. Dispatch through
    // the same table action() uses for a GET caller.
    String action = context.getParameter("action");
    if ("archive".equals(action) || "claim".equals(action) || "markAsProcessed".equals(action)) {
      return action(context);
    }
    return context;
  }

  public WidgetContext action(WidgetContext context) {
    // Find the user record
    long dataId = context.getParameterAsLong("dataId");
    FormData formData = FormDataRepository.findById(dataId);
    if (formData == null) {
      context.setErrorMessage("The form record was not found");
      return context;
    }
    // Execute the action
    context.setRedirect("/admin/form-data");
    String action = context.getParameter("action");
    if ("archive".equals(action)) {
      return archiveFormData(context, formData);
    } else if ("claim".equals(action)) {
      return claimFormData(context, formData);
    } else if ("markAsProcessed".equals(action)) {
      return markAsProcessed(context, formData);
    }
    return context;
  }

  private WidgetContext archiveFormData(WidgetContext context, FormData formData) {
    FormDataRepository.markAsArchived(formData, context.getUserId());
    return context;
  }

  private WidgetContext claimFormData(WidgetContext context, FormData formData) {
    FormDataRepository.tryToMarkAsClaimed(formData, context.getUserId());
    return context;
  }

  private WidgetContext markAsProcessed(WidgetContext context, FormData formData) {
    if (FormDataRepository.markAsProcessed(formData, context.getUserId())) {
      // Conversion funnel tracking (issue #565, phase 1) -- a no-op unless this formUniqueId is the
      // site's admin-configured contact form. This fires from the admin's own session, days after the
      // original submission, so it must reuse the submission's own stored session_id, not the admin's.
      FunnelEventCommand.recordContactFormProcessed(formData.getFormUniqueId(), formData.getSessionId());
    }
    return context;
  }

  private WidgetContext downloadCSVFile(WidgetContext context) {
    String extension = "csv";
    String displayFilename = "form-data-" + new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date()) + "." + extension;
    File tempFile = FileSystemCommand.generateTempFile("exports", context.getUserId(), extension);
    try {
      // Scope the export to the same filters currently applied to the on-screen list, instead of
      // unconditionally dumping the whole form_data table
      FormDataSpecification specification = buildSpecificationFromParameters(context);
      FormDataRepository.export(specification, null, tempFile);
      String mimeType = "text/csv";
      MultipartFileSender.fromFile(tempFile)
          .with(context.getRequest())
          .with(context.getResponse())
          .withMimeType(mimeType)
          .withFilename(displayFilename)
          .serveResource();
      AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, "data.export", AuditEventCommand.SUCCESS,
          "form_data", "all", displayFilename, "format=" + extension);
    } catch (Exception e) {
      LOG.error("Download CSV Error", e);
      AuditEventCommand.record(context, AuditEventCommand.DATA_ACCESS, "data.export", AuditEventCommand.FAILURE,
          "form_data", "all", displayFilename, "format=" + extension);
    } finally {
      if (tempFile.exists()) {
        tempFile.delete();
      }
    }
    context.setHandledResponse(true);
    return context;
  }
}
