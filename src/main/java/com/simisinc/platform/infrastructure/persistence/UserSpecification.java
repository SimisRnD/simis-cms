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

package com.simisinc.platform.infrastructure.persistence;

import com.simisinc.platform.presentation.controller.DataConstants;

/**
 * Encapsulates the records being returned and the record count for paging
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class UserSpecification {

  private long id = -1L;
  private String username = null;
  private String accountToken = null;
  private long roleId = -1;
  private long groupId = -1;
  private int isEnabled = DataConstants.UNDEFINED;
  private int isVerified = DataConstants.UNDEFINED;
  private String matchesName = null;

  public UserSpecification() {
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getAccountToken() {
    return accountToken;
  }

  public void setAccountToken(String accountToken) {
    this.accountToken = accountToken;
  }

  public long getRoleId() {
    return roleId;
  }

  public void setRoleId(long roleId) {
    this.roleId = roleId;
  }

  public long getGroupId() {
    return groupId;
  }

  public void setGroupId(long groupId) {
    this.groupId = groupId;
  }

  public int getIsEnabled() {
    return isEnabled;
  }

  public void setIsEnabled(int isEnabled) {
    this.isEnabled = isEnabled;
  }

  public void setIsEnabled(boolean isEnabled) {
    this.isEnabled = (isEnabled ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getIsVerified() {
    return isVerified;
  }

  public void setIsVerified(boolean isVerified) {
    this.isVerified = (isVerified ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public void setIsVerified(int isVerified) {
    this.isVerified = isVerified;
  }

  public String getMatchesName() {
    return matchesName;
  }

  public void setMatchesName(String matchesName) {
    this.matchesName = matchesName;
  }
}
