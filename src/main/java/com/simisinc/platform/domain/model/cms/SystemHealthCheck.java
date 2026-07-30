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

package com.simisinc.platform.domain.model.cms;

import com.simisinc.platform.domain.model.Entity;

import java.sql.Timestamp;

/**
 * One row per health check run, recorded by SystemHealthJob for a single service (database,
 * filesystem). See issue #466.
 *
 * @author SimIS
 * @created 7/30/2026
 */
public class SystemHealthCheck extends Entity {

  public static final String STATUS_UP = "UP";
  public static final String STATUS_DOWN = "DOWN";

  private long id = -1;
  private String serviceName = null;
  private String status = null;
  private int responseTimeMs = 0;
  private String errorMessage = null;
  private Timestamp checkedAt = null;

  public SystemHealthCheck() {
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  /** A display-friendly label for the admin dashboard, since serviceName is a stable identifier
   * ("filesystem") rather than something meant to be shown to a user as-is ("File Store"). */
  public String getServiceLabel() {
    if ("database".equals(serviceName)) {
      return "Database";
    }
    if ("filesystem".equals(serviceName)) {
      return "File Store";
    }
    return serviceName;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public boolean isUp() {
    return STATUS_UP.equals(status);
  }

  public int getResponseTimeMs() {
    return responseTimeMs;
  }

  public void setResponseTimeMs(int responseTimeMs) {
    this.responseTimeMs = responseTimeMs;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Timestamp getCheckedAt() {
    return checkedAt;
  }

  public void setCheckedAt(Timestamp checkedAt) {
    this.checkedAt = checkedAt;
  }
}
