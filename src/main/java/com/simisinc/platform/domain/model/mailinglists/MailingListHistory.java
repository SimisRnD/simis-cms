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

package com.simisinc.platform.domain.model.mailinglists;

import com.simisinc.platform.domain.model.Entity;

import java.sql.Timestamp;

/**
 * Mailing list history
 *
 * @author matt rajkowski
 * @created 3/24/19 8:48 PM
 */
public class MailingListHistory extends Entity {

  private Long id = -1L;

  private long listId = -1;
  private long createdBy = -1;
  private Timestamp created = null;
  private String service = null;
  private int emailCount = 0;

  public MailingListHistory() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getListId() {
    return listId;
  }

  public void setListId(long listId) {
    this.listId = listId;
  }

  public long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(long createdBy) {
    this.createdBy = createdBy;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }

  public String getService() {
    return service;
  }

  public void setService(String service) {
    this.service = service;
  }

  public int getEmailCount() {
    return emailCount;
  }

  public void setEmailCount(int emailCount) {
    this.emailCount = emailCount;
  }
}
