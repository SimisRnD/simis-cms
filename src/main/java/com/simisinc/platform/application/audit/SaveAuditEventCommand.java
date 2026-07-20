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

package com.simisinc.platform.application.audit;

import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.audit.AuditLog;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records a security audit event to both sinks: the audit_log database table (the source of record,
 * for the in-app review and offline/in-boundary deployments) and a single line of structured JSON to a
 * dedicated audit log stream. On a deployment whose container stdout is collected by Azure Monitor, that
 * JSON flows into Log Analytics and Microsoft Sentinel with no agent; the schema aligns with Sentinel's
 * ASIM audit model.
 *
 * <p>Auditing is a side effect and must never break the action it observes -- a failure in either sink is
 * caught and logged, never thrown, so a failed audit write can never turn a successful login into an error.
 *
 * @author SimIS Inc.
 */
public class SaveAuditEventCommand {

  public static final int SCHEMA_VERSION = 1;
  public static final String SCHEMA_NAME = "simis.audit.v1";

  private static Log LOG = LogFactory.getLog(SaveAuditEventCommand.class);

  // A dedicated stream so audit records are separable from ordinary application logging (e.g. for a
  // Sentinel parser). The JSON payload also carries the "schema" marker for reliable identification.
  private static Log AUDIT = LogFactory.getLog("AUDIT");

  /** Records an event to the database and the JSON audit stream. Never throws. */
  public static AuditLog record(AuditLog event) {
    if (event == null) {
      return null;
    }
    if (event.getOccurred() == null) {
      event.setOccurred(Timestamp.from(Instant.now()));
    }
    event.setSchemaVersion(SCHEMA_VERSION);

    // Sink 1 -- the database (source of record)
    AuditLog saved = null;
    try {
      saved = AuditLogRepository.save(event);
    } catch (Exception e) {
      LOG.error("Audit database write failed for event: " + event.getEventType(), e);
    }

    // Sink 2 -- the structured JSON stream (Azure Monitor / Log Analytics / Sentinel)
    try {
      AUDIT.info(toJson(event));
    } catch (Exception e) {
      LOG.error("Audit JSON emit failed for event: " + event.getEventType(), e);
    }

    return saved;
  }

  /**
   * Builds the single-line JSON representation of an event. Field order is stable and null fields are
   * omitted. Kept package-visible-plus so it can be unit tested without a database or logger.
   */
  public static String toJson(AuditLog event) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("schema", SCHEMA_NAME);
    map.put("ts", event.getOccurred() != null ? event.getOccurred().toInstant().toString() : Instant.now().toString());
    map.put("category", event.getEventCategory());
    map.put("event", event.getEventType());
    map.put("result", event.getOutcome());
    // Omit the actor id entirely when there is no known actor (e.g. a failed login for an unknown user)
    if (event.getActorUserId() > -1L) {
      map.put("actorUserId", event.getActorUserId());
    }
    map.put("actorUsername", event.getActorUsername());
    map.put("sourceIp", event.getSourceIp());
    map.put("targetType", event.getTargetType());
    map.put("targetId", event.getTargetId());
    map.put("targetLabel", event.getTargetLabel());
    map.put("sessionId", event.getSessionId());
    map.put("details", event.getDetails());
    // createJsonNode skips null values and escapes string values
    return JsonCommand.createJsonNode(map).toString();
  }

  /**
   * Convenience for an authentication-category event (login, logout, MFA). Pass an actorUserId of -1 when
   * the acting user is unknown (a failed login). Never throws.
   */
  public static AuditLog recordAuthentication(String eventType, String outcome, long actorUserId,
      String actorUsername, String ipAddress, String sessionId, String details) {
    AuditLog event = new AuditLog();
    event.setEventCategory("authentication");
    event.setEventType(eventType);
    event.setOutcome(outcome);
    event.setActorUserId(actorUserId);
    event.setActorUsername(actorUsername);
    event.setSourceIp(ipAddress);
    event.setSessionId(sessionId);
    event.setDetails(details);
    return record(event);
  }
}
