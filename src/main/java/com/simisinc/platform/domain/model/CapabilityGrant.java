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

package com.simisinc.platform.domain.model;

import java.sql.Timestamp;

/**
 * A direct, individually-trackable capability grant to a specific user (issue #702) - independent
 * of role_capabilities. expiresAt null means permanent. See CapabilityGrantExpirationJob for how
 * expiration is enforced (a scheduled sweep, not a live per-check evaluation).
 *
 * @author elizabeth houser
 */
public class CapabilityGrant extends Entity {

  private Long id = -1L;
  private Long userId = -1L;
  private Long capabilityId = -1L;
  private Long grantedBy = -1L;
  private Timestamp granted = null;
  private String reason = null;
  private Timestamp expiresAt = null;
  private Timestamp revokedAt = null;
  private Timestamp expirationNotifiedAt = null;

  // Populated by CapabilityGrantRepository's join queries for display - not a stored column.
  private String capabilityCode = null;

  public CapabilityGrant() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public Long getCapabilityId() {
    return capabilityId;
  }

  public void setCapabilityId(Long capabilityId) {
    this.capabilityId = capabilityId;
  }

  public Long getGrantedBy() {
    return grantedBy;
  }

  public void setGrantedBy(Long grantedBy) {
    this.grantedBy = grantedBy;
  }

  public Timestamp getGranted() {
    return granted;
  }

  public void setGranted(Timestamp granted) {
    this.granted = granted;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public Timestamp getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Timestamp expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Timestamp getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(Timestamp revokedAt) {
    this.revokedAt = revokedAt;
  }

  public Timestamp getExpirationNotifiedAt() {
    return expirationNotifiedAt;
  }

  public void setExpirationNotifiedAt(Timestamp expirationNotifiedAt) {
    this.expirationNotifiedAt = expirationNotifiedAt;
  }

  public String getCapabilityCode() {
    return capabilityCode;
  }

  public void setCapabilityCode(String capabilityCode) {
    this.capabilityCode = capabilityCode;
  }
}
