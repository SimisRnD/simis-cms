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

package com.simisinc.platform.domain.model.audit;

import com.simisinc.platform.domain.model.Entity;

import java.sql.Timestamp;

/**
 * A security audit record: who did what, when, and from where. Written to the audit_log table and
 * emitted as structured JSON for a SIEM (see SaveAuditEventCommand). The actor is denormalized
 * (actor_username stored, no foreign key on actor_user_id) so records survive a user deletion, and
 * the full source IP is retained for forensics.
 *
 * @author SimIS Inc.
 */
public class AuditLog extends Entity {

  private Long id = -1L;

  private Timestamp occurred = null;
  private String eventCategory = null;
  private String eventType = null;
  private String outcome = null;
  private long actorUserId = -1L;
  private String actorUsername = null;
  private String sourceIp = null;
  private String targetType = null;
  private String targetId = null;
  private String targetLabel = null;
  private String details = null;
  private String sessionId = null;
  private int schemaVersion = 1;

  public AuditLog() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Timestamp getOccurred() {
    return occurred;
  }

  public void setOccurred(Timestamp occurred) {
    this.occurred = occurred;
  }

  public String getEventCategory() {
    return eventCategory;
  }

  public void setEventCategory(String eventCategory) {
    this.eventCategory = eventCategory;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getOutcome() {
    return outcome;
  }

  public void setOutcome(String outcome) {
    this.outcome = outcome;
  }

  public long getActorUserId() {
    return actorUserId;
  }

  public void setActorUserId(long actorUserId) {
    this.actorUserId = actorUserId;
  }

  public String getActorUsername() {
    return actorUsername;
  }

  public void setActorUsername(String actorUsername) {
    this.actorUsername = actorUsername;
  }

  public String getSourceIp() {
    return sourceIp;
  }

  public void setSourceIp(String sourceIp) {
    this.sourceIp = sourceIp;
  }

  public String getTargetType() {
    return targetType;
  }

  public void setTargetType(String targetType) {
    this.targetType = targetType;
  }

  public String getTargetId() {
    return targetId;
  }

  public void setTargetId(String targetId) {
    this.targetId = targetId;
  }

  public String getTargetLabel() {
    return targetLabel;
  }

  public void setTargetLabel(String targetLabel) {
    this.targetLabel = targetLabel;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(int schemaVersion) {
    this.schemaVersion = schemaVersion;
  }
}
