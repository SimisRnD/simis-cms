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

package com.simisinc.platform.presentation.rest.userProfile;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.simisinc.platform.domain.model.User;

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
}
