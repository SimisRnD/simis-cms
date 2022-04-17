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

package com.simisinc.platform.domain.events.cms;

import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.domain.model.User;
import lombok.NoArgsConstructor;

/**
 * Event details for when a user's password is reset
 *
 * @author matt rajkowski
 * @created 4/30/21 2:36 PM
 */
@NoArgsConstructor
public class UserPasswordResetEvent extends Event {

  public static final String ID = "user-password-reset";

  private User user = null;
  private User resetBy = null;

  public UserPasswordResetEvent(User user, User resetBy) {
    this.user = user;
    this.resetBy = resetBy;
  }

  @Override
  public String getDomainEventType() {
    return ID;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public User getUser() {
    return user;
  }

  public void setResetBy(User resetBy) {
    this.resetBy = resetBy;
  }

  public User getResetBy() {
    return resetBy;
  }
}
