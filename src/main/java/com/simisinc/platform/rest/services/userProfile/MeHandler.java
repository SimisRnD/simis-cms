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

package com.simisinc.platform.rest.services.userProfile;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class MeHandler {

  String firstName;
  String lastName;
  String fullName;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String nickname;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  String email;
  List<String> roleList = null;
  List<String> groupList = null;

  public MeHandler(User thisUser) {
    firstName = thisUser.getFirstName();
    lastName = thisUser.getLastName();
    fullName = thisUser.getFullName();
    nickname = thisUser.getNickname();
    if (thisUser.getEmail() != null && thisUser.getEmail().contains("@")) {
      email = thisUser.getEmail();
    } else {
      email = null;
    }

    // Just return the names
    List<Role> serverRoleList = thisUser.getRoleList();
    if (serverRoleList != null) {
      roleList = new ArrayList<>();
      for (Role role : serverRoleList) {
        roleList.add(role.getTitle());
      }
    }

    // // Just return the names
    List<Group> serverGroupList = thisUser.getGroupList();
    if (serverGroupList != null) {
      groupList = new ArrayList<>();
      for (Group group : serverGroupList) {
        groupList.add(group.getName());
      }
    }
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getNickname() {
    return nickname;
  }

  public void setNickname(String nickname) {
    this.nickname = nickname;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public List<String> getRoleList() {
    return roleList;
  }

  public void setRoleList(List<String> roleList) {
    this.roleList = roleList;
  }

  public List<String> getGroupList() {
    return groupList;
  }

  public void setGroupList(List<String> groupList) {
    this.groupList = groupList;
  }

}
