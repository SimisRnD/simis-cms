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

package com.simisinc.platform.infrastructure.persistence.audit;

import java.sql.Timestamp;

/**
 * Filter criteria for querying the security audit log (see AuditLogRepository). Every field is optional;
 * an unset field (null, or -1 for the actor id) does not constrain the query. Used by the in-app audit
 * review UI to filter by category, event type, outcome, actor, source IP, and an occurred-date range.
 *
 * @author SimIS Inc.
 */
public class AuditLogSpecification {

  private String eventCategory = null;
  private String eventType = null;
  private String outcome = null;
  private long actorUserId = -1L;
  private String actorUsername = null;   // partial, case-insensitive match
  private String sourceIp = null;
  private Timestamp occurredAfter = null;   // occurred >= this
  private Timestamp occurredBefore = null;  // occurred < this (use the start of the day after the "to" date)

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

  public Timestamp getOccurredAfter() {
    return occurredAfter;
  }

  public void setOccurredAfter(Timestamp occurredAfter) {
    this.occurredAfter = occurredAfter;
  }

  public Timestamp getOccurredBefore() {
    return occurredBefore;
  }

  public void setOccurredBefore(Timestamp occurredBefore) {
    this.occurredBefore = occurredBefore;
  }
}
