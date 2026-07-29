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

package com.simisinc.platform.domain.model.mailinglists;

import com.simisinc.platform.domain.model.Entity;

import java.sql.Timestamp;

/**
 * A single recipient's row within a mailing list send batch (mailing_list_history), tracked from
 * queued through sent or failed.
 *
 * @author SimIS Inc.
 */
public class MailingListSent extends Entity {

  public static final String QUEUED = "queued";
  public static final String PROCESSING = "processing";
  public static final String SENT = "sent";
  public static final String FAILED = "failed";
  /** The recipient unsubscribed (or their membership row was removed) between enqueue and send. */
  public static final String SKIPPED = "skipped";

  private Long id = -1L;

  private long emailId = -1;
  private long listId = -1;
  private long historyId = -1;
  private Timestamp created = null;
  private String status = QUEUED;
  private int attemptCount = 0;
  private String errorMessage = null;
  private Timestamp claimedAt = null;
  private Timestamp modified = null;

  public MailingListSent() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getEmailId() {
    return emailId;
  }

  public void setEmailId(long emailId) {
    this.emailId = emailId;
  }

  public long getListId() {
    return listId;
  }

  public void setListId(long listId) {
    this.listId = listId;
  }

  public long getHistoryId() {
    return historyId;
  }

  public void setHistoryId(long historyId) {
    this.historyId = historyId;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public void setAttemptCount(int attemptCount) {
    this.attemptCount = attemptCount;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Timestamp getClaimedAt() {
    return claimedAt;
  }

  public void setClaimedAt(Timestamp claimedAt) {
    this.claimedAt = claimedAt;
  }

  public Timestamp getModified() {
    return modified;
  }

  public void setModified(Timestamp modified) {
    this.modified = modified;
  }
}
