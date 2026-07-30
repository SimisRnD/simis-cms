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

package com.simisinc.platform.domain.model.login;

import com.simisinc.platform.domain.model.Entity;

import java.sql.Timestamp;

/**
 * A maker-checker request to unsuspend an elevated-role account (issue #492 Phase 3). No foreign
 * keys on the referenced user ids -- this row is a governance/audit record and must survive the
 * deletion of any user it references, matching how audit_log has no foreign key on actor_user_id.
 *
 * @author SimIS Inc.
 */
public class UnsuspendRequest extends Entity {

  public static final String STATUS_PENDING = "pending";
  public static final String STATUS_APPROVED = "approved";
  public static final String STATUS_DENIED = "denied";
  public static final String STATUS_SUPERSEDED = "superseded";
  public static final String STATUS_REVERIFIED = "reverified";

  private Long id = -1L;
  private long targetUserId = -1L;
  private String targetEmail = null;
  private String targetRoleSnapshot = null;
  private long requestedBy = -1L;
  private String requestedByEmail = null;
  private String reason = null;
  private Timestamp requestedAt = null;
  private String status = STATUS_PENDING;
  private Long decidedBy = null;
  private String decidedByEmail = null;
  private Timestamp decidedAt = null;
  private String decisionReason = null;

  public UnsuspendRequest() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getTargetUserId() {
    return targetUserId;
  }

  public void setTargetUserId(long targetUserId) {
    this.targetUserId = targetUserId;
  }

  public String getTargetEmail() {
    return targetEmail;
  }

  public void setTargetEmail(String targetEmail) {
    this.targetEmail = targetEmail;
  }

  public String getTargetRoleSnapshot() {
    return targetRoleSnapshot;
  }

  public void setTargetRoleSnapshot(String targetRoleSnapshot) {
    this.targetRoleSnapshot = targetRoleSnapshot;
  }

  public long getRequestedBy() {
    return requestedBy;
  }

  public void setRequestedBy(long requestedBy) {
    this.requestedBy = requestedBy;
  }

  public String getRequestedByEmail() {
    return requestedByEmail;
  }

  public void setRequestedByEmail(String requestedByEmail) {
    this.requestedByEmail = requestedByEmail;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public Timestamp getRequestedAt() {
    return requestedAt;
  }

  public void setRequestedAt(Timestamp requestedAt) {
    this.requestedAt = requestedAt;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Long getDecidedBy() {
    return decidedBy;
  }

  public void setDecidedBy(Long decidedBy) {
    this.decidedBy = decidedBy;
  }

  public String getDecidedByEmail() {
    return decidedByEmail;
  }

  public void setDecidedByEmail(String decidedByEmail) {
    this.decidedByEmail = decidedByEmail;
  }

  public Timestamp getDecidedAt() {
    return decidedAt;
  }

  public void setDecidedAt(Timestamp decidedAt) {
    this.decidedAt = decidedAt;
  }

  public String getDecisionReason() {
    return decisionReason;
  }

  public void setDecisionReason(String decisionReason) {
    this.decisionReason = decisionReason;
  }

  public boolean isPending() {
    return STATUS_PENDING.equals(status);
  }

  public boolean isApproved() {
    return STATUS_APPROVED.equals(status);
  }
}
