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

package com.simisinc.platform.infrastructure.persistence.cms;

import com.simisinc.platform.presentation.controller.DataConstants;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 6/1/18 2:43 PM
 */
public class FormDataSpecification {

  private long id = -1L;
  private String formUniqueId = null;
  private String sessionId = null;
  private int flaggedAsSpam = DataConstants.UNDEFINED;
  private int claimed = DataConstants.UNDEFINED;
  private long claimedBy = -1L;
  private int dismissed = DataConstants.UNDEFINED;
  private int processed = DataConstants.UNDEFINED;

  public FormDataSpecification() {
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getFormUniqueId() {
    return formUniqueId;
  }

  public void setFormUniqueId(String formUniqueId) {
    this.formUniqueId = formUniqueId;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public int getFlaggedAsSpam() {
    return flaggedAsSpam;
  }

  public void setFlaggedAsSpam(int flaggedAsSpam) {
    this.flaggedAsSpam = flaggedAsSpam;
  }

  public void setFlaggedAsSpam(boolean flaggedAsSpam) {
    this.flaggedAsSpam = (flaggedAsSpam ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getClaimed() {
    return claimed;
  }

  public void setClaimed(int claimed) {
    this.claimed = claimed;
  }

  public void setClaimed(boolean claimed) {
    this.claimed = (claimed ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public long getClaimedBy() {
    return claimedBy;
  }

  public void setClaimedBy(long claimedBy) {
    this.claimedBy = claimedBy;
  }

  public int getDismissed() {
    return dismissed;
  }

  public void setDismissed(int dismissed) {
    this.dismissed = dismissed;
  }

  public void setDismissed(boolean dismissed) {
    this.dismissed = (dismissed ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getProcessed() {
    return processed;
  }

  public void setProcessed(int processed) {
    this.processed = processed;
  }

  public void setProcessed(boolean processed) {
    this.processed = (processed ? DataConstants.TRUE : DataConstants.FALSE);
  }
}
