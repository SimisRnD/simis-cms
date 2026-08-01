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

package com.simisinc.platform.rest.services.audit;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.audit.BuildAuditLogSpecificationCommand;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.audit.AuditLog;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogRepository;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogSpecification;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;
import com.simisinc.platform.rest.controller.ServiceResponseCommand;

/**
 * Queryable REST API for the security audit log (issue #754). Lets an authenticated API client filter and
 * page through audit_log records without going through the browser admin UI
 * ({@code AuditLogListWidget}). Filtering matches what that UI already supports (category, event type,
 * outcome, actor, source IP, target type, target label, and an occurred date range, either a quick
 * 1h/24h/7d/30d preset or an explicit fromDate/toDate range).
 *
 * <p>Gated the same way every other endpoint under {@code /api/} is -- {@code RestRequestFilter}'s
 * API-key + {@code site.api} toggle check runs first -- plus an admin-role check here, since the audit log
 * is sensitive and every other consumer of it (the widget, the CSV/JSON export) is admin-only too. A guest
 * (API-key-only, no bearer token) or a non-admin authenticated user gets 403.
 *
 * @author SimIS Inc.
 */
public class AuditLogListService {

  private static Log LOG = LogFactory.getLog(AuditLogListService.class);

  private static final int DEFAULT_PAGE_SIZE = 50;
  private static final int MAX_PAGE_SIZE = 200;

  // GET /audit-log?category=&eventType=&outcome=&actor=&sourceIp=&targetType=&targetLabel=
  //     &range=&fromDate=&toDate=&page=&size=
  public ServiceResponse get(ServiceContext context) {

    if (!context.hasRole("admin")) {
      LOG.debug("Non-admin API request for the audit log rejected");
      ServiceResponse response = new ServiceResponse(403);
      response.getError().put("title", "Forbidden");
      return response;
    }

    // Determine the constraints
    int pageNumber = context.getParameterAsInt("page", 1);
    if (pageNumber < 1) {
      pageNumber = 1;
    }
    int pageSize = context.getParameterAsInt("size", DEFAULT_PAGE_SIZE);
    if (pageSize < 1) {
      pageSize = DEFAULT_PAGE_SIZE;
    } else if (pageSize > MAX_PAGE_SIZE) {
      pageSize = MAX_PAGE_SIZE;
    }
    DataConstraints constraints = new DataConstraints(pageNumber, pageSize);

    // Determine the filters
    AuditLogSpecification specification = BuildAuditLogSpecificationCommand.build(
        context.getParameter("category"),
        context.getParameter("eventType"),
        context.getParameter("outcome"),
        context.getParameter("actor"),
        context.getParameter("sourceIp"),
        context.getParameter("targetType"),
        context.getParameter("targetLabel"),
        context.getParameter("range"),
        context.getParameter("fromDate"),
        context.getParameter("toDate"));

    // Retrieve the records
    List<AuditLog> auditLogList = AuditLogRepository.findAll(specification, constraints);

    // Querying the audit log via the API is itself a data-access event worth auditing, same as the
    // in-app CSV/JSON export. Never let this break the response.
    try {
      User user = context.getUser();
      SaveAuditEventCommand.recordAdminEvent(AuditEventCommand.DATA_ACCESS, "audit_log.api_query",
          AuditEventCommand.SUCCESS,
          user != null ? user.getId() : -1L,
          user != null ? user.getEmail() : null,
          context.getRequest() != null ? context.getRequest().getRemoteAddr() : null,
          null,
          "audit_log", "filtered", null,
          "page=" + pageNumber + "; size=" + pageSize);
    } catch (Exception e) {
      LOG.warn("Could not record the audit_log.api_query event: " + e.getMessage());
    }

    // Set the fields to return
    List<AuditLogEntryResponse> recordList = new ArrayList<>();
    for (AuditLog record : auditLogList) {
      recordList.add(new AuditLogEntryResponse(record));
    }

    // Prepare the response
    ServiceResponse response = new ServiceResponse(200);
    ServiceResponseCommand.addMeta(response, "audit_log", auditLogList, constraints);
    response.setData(recordList);
    return response;
  }
}
