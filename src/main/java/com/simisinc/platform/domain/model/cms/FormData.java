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

package com.simisinc.platform.domain.model.cms;

import com.simisinc.platform.domain.model.Entity;

import java.sql.Timestamp;
import java.util.List;

/**
 * A form that a user has submitted in the website, with a list of fields provided and responded to
 *
 * @author matt rajkowski
 * @created 6/1/18 2:38 PM
 */
public class FormData extends Entity {

  private Long id = -1L;

  private String formUniqueId = null;
  private List<FormField> formFieldList = null;
  private String ipAddress = null;
  private String sessionId = null;
  private String url = null;
  private String queryParameters = null;
  private long createdBy = -1;
  private long modifiedBy = -1;
  private Timestamp created = null;
  private Timestamp modified = null;
  private Timestamp claimed = null;
  private long claimedBy = -1;
  private Timestamp dismissed = null;
  private long dismissedBy = -1;
  private Timestamp processed = null;
  private long processedBy = -1;
  private String processedSystem = null;
  private boolean flaggedAsSpam = false;

  public FormData() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getFormUniqueId() {
    return formUniqueId;
  }

  public void setFormUniqueId(String formUniqueId) {
    this.formUniqueId = formUniqueId;
  }

  public List<FormField> getFormFieldList() {
    return formFieldList;
  }

  public void setFormFieldList(List<FormField> formFieldList) {
    this.formFieldList = formFieldList;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getQueryParameters() {
    return queryParameters;
  }

  public void setQueryParameters(String queryParameters) {
    this.queryParameters = queryParameters;
  }

  public long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(long createdBy) {
    this.createdBy = createdBy;
  }

  public long getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(long modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }

  public Timestamp getModified() {
    return modified;
  }

  public void setModified(Timestamp modified) {
    this.modified = modified;
  }

  public Timestamp getClaimed() {
    return claimed;
  }

  public void setClaimed(Timestamp claimed) {
    this.claimed = claimed;
  }

  public void setClaimed(boolean claimed) {
    if (claimed) {
      this.claimed = new Timestamp(System.currentTimeMillis());
    } else {
      this.claimed = null;
    }
  }

  public long getClaimedBy() {
    return claimedBy;
  }

  public void setClaimedBy(long claimedBy) {
    this.claimedBy = claimedBy;
  }

  public Timestamp getDismissed() {
    return dismissed;
  }

  public void setDismissed(Timestamp dismissed) {
    this.dismissed = dismissed;
  }

  public void setDismissed(boolean dismissed) {
    if (dismissed) {
      this.dismissed = new Timestamp(System.currentTimeMillis());
    } else {
      this.dismissed = null;
    }
  }

  public long getDismissedBy() {
    return dismissedBy;
  }

  public void setDismissedBy(long dismissedBy) {
    this.dismissedBy = dismissedBy;
  }

  public Timestamp getProcessed() {
    return processed;
  }

  public void setProcessed(Timestamp processed) {
    this.processed = processed;
  }

  public void setProcessed(boolean processed) {
    if (processed) {
      this.processed = new Timestamp(System.currentTimeMillis());
    } else {
      this.processed = null;
    }
  }

  public long getProcessedBy() {
    return processedBy;
  }

  public void setProcessedBy(long processedBy) {
    this.processedBy = processedBy;
  }

  public String getProcessedSystem() {
    return processedSystem;
  }

  public void setProcessedSystem(String processedSystem) {
    this.processedSystem = processedSystem;
  }

  public boolean getFlaggedAsSpam() {
    return flaggedAsSpam;
  }

  public void setFlaggedAsSpam(boolean flaggedAsSpam) {
    this.flaggedAsSpam = flaggedAsSpam;
  }
}
