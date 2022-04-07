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

package com.simisinc.platform.domain.model.xapi;

import com.simisinc.platform.domain.model.Entity;

import java.sql.Timestamp;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/9/21 5:00 PM
 */
public class XapiStatement extends Entity {

  private long id = -1;
  private String message = null;
  private String messageSnapshot = null;
  private long actorId = -1;
  private String verb = null;
  private String object = null;
  private long objectId = -1;
  private Timestamp occurredAt = null;
  private Timestamp created = null;
  private String authority = null;
  private long contextUserId = -1;
  private long contextItemId = -1;
  private long contextProjectId = -1;
  private long contextIssueId = -1;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getMessageSnapshot() {
    return messageSnapshot;
  }

  public void setMessageSnapshot(String messageSnapshot) {
    this.messageSnapshot = messageSnapshot;
  }

  public long getActorId() {
    return actorId;
  }

  public void setActorId(long actorId) {
    this.actorId = actorId;
  }

  public String getVerb() {
    return verb;
  }

  public void setVerb(String verb) {
    this.verb = verb;
  }

  public String getObject() {
    return object;
  }

  public void setObject(String object) {
    this.object = object;
  }

  public long getObjectId() {
    return objectId;
  }

  public void setObjectId(long objectId) {
    this.objectId = objectId;
  }

  public Timestamp getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Timestamp occurredAt) {
    this.occurredAt = occurredAt;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }

  public String getAuthority() {
    return authority;
  }

  public void setAuthority(String authority) {
    this.authority = authority;
  }

  public long getContextUserId() {
    return contextUserId;
  }

  public void setContextUserId(long contextUserId) {
    this.contextUserId = contextUserId;
  }

  public long getContextItemId() {
    return contextItemId;
  }

  public void setContextItemId(long contextItemId) {
    this.contextItemId = contextItemId;
  }

  public long getContextProjectId() {
    return contextProjectId;
  }

  public void setContextProjectId(long contextProjectId) {
    this.contextProjectId = contextProjectId;
  }

  public long getContextIssueId() {
    return contextIssueId;
  }

  public void setContextIssueId(long contextIssueId) {
    this.contextIssueId = contextIssueId;
  }
}
