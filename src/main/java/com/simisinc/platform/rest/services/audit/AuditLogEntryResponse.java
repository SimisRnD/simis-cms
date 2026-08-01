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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.simisinc.platform.domain.model.audit.AuditLog;

/**
 * One audit_log record as returned by the REST API (see AuditLogListService). Field selection mirrors the
 * existing CSV/JSON export ({@code AuditLogRepository#exportJson}) plus the record id -- the tamper-evidence
 * hash chain fields (previousHash/recordHash) are an internal integrity-verification detail
 * (see AuditLogIntegrityCommand) and are not exposed here.
 *
 * @author SimIS Inc.
 */
public class AuditLogEntryResponse {

  Long id;
  String occurred;
  String eventCategory;
  String eventType;
  String outcome;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  Long actorUserId;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String actorUsername;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String sourceIp;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String targetType;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String targetId;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String targetLabel;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String sessionId;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String details;

  public AuditLogEntryResponse(AuditLog record) {
    id = record.getId();
    occurred = record.getOccurred() != null ? record.getOccurred().toInstant().toString() : null;
    eventCategory = record.getEventCategory();
    eventType = record.getEventType();
    outcome = record.getOutcome();
    if (record.getActorUserId() > -1L) {
      actorUserId = record.getActorUserId();
    }
    actorUsername = record.getActorUsername();
    sourceIp = record.getSourceIp();
    targetType = record.getTargetType();
    targetId = record.getTargetId();
    targetLabel = record.getTargetLabel();
    sessionId = record.getSessionId();
    details = record.getDetails();
  }

  public Long getId() {
    return id;
  }

  public String getOccurred() {
    return occurred;
  }

  public String getEventCategory() {
    return eventCategory;
  }

  public String getEventType() {
    return eventType;
  }

  public String getOutcome() {
    return outcome;
  }

  public Long getActorUserId() {
    return actorUserId;
  }

  public String getActorUsername() {
    return actorUsername;
  }

  public String getSourceIp() {
    return sourceIp;
  }

  public String getTargetType() {
    return targetType;
  }

  public String getTargetId() {
    return targetId;
  }

  public String getTargetLabel() {
    return targetLabel;
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getDetails() {
    return details;
  }
}
