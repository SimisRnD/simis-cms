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

import java.sql.Timestamp;

import com.simisinc.platform.domain.model.Entity;

/**
 * A single multi-factor authentication recovery code for a user. Only the SHA-256 hash of the code is stored; the
 * plaintext is shown to the user once at generation and never persisted. Each code is single-use.
 *
 * @author SimIS Inc.
 * @created 2026-07-17
 */
public class UserMfaRecoveryCode extends Entity {

  static final long serialVersionUID = 8484048371911908895L;

  private long id = -1;
  private long userId = -1;
  private String codeHash;
  private boolean used = false;
  private Timestamp created;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
  }

  public String getCodeHash() {
    return codeHash;
  }

  public void setCodeHash(String codeHash) {
    this.codeHash = codeHash;
  }

  public boolean getUsed() {
    return used;
  }

  public void setUsed(boolean used) {
    this.used = used;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }
}
