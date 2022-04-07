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

package com.simisinc.platform.domain.model.login;

import com.simisinc.platform.domain.model.Entity;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.User;

import java.sql.Timestamp;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 6/19/18 8:24 AM
 */
public class UserGroup extends Entity {

  private Long id = -1L;

  private long userId = -1L;
  private long groupId = -1L;
  private Timestamp created = null;

  public UserGroup() {
  }

  public UserGroup(User user, Group group) {
    userId = user.getId();
    groupId = group.getId();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
  }

  public long getGroupId() {
    return groupId;
  }

  public void setGroupId(long groupId) {
    this.groupId = groupId;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }
}
