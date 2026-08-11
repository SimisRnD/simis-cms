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

import java.sql.Timestamp;

import com.simisinc.platform.domain.model.Entity;

/**
 * One row per uncaught exception recorded by ServiceErrorLoggingFilter. See issue #556.
 *
 * @author SimIS
 * @created 8/10/2026
 */
public class ServiceError extends Entity {

  private long id = -1;
  private String requestUri = null;
  private String exceptionClass = null;
  private String message = null;
  private String stackTrace = null;
  private Timestamp occurredAt = null;

  public ServiceError() {
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getRequestUri() {
    return requestUri;
  }

  public void setRequestUri(String requestUri) {
    this.requestUri = requestUri;
  }

  public String getExceptionClass() {
    return exceptionClass;
  }

  public void setExceptionClass(String exceptionClass) {
    this.exceptionClass = exceptionClass;
  }

  /** The exception's simple class name (no package), for a compact dashboard column. */
  public String getExceptionSimpleName() {
    if (exceptionClass == null) {
      return null;
    }
    int lastDot = exceptionClass.lastIndexOf('.');
    return lastDot == -1 ? exceptionClass : exceptionClass.substring(lastDot + 1);
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getStackTrace() {
    return stackTrace;
  }

  public void setStackTrace(String stackTrace) {
    this.stackTrace = stackTrace;
  }

  public Timestamp getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Timestamp occurredAt) {
    this.occurredAt = occurredAt;
  }
}
