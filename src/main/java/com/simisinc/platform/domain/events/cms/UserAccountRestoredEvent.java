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

package com.simisinc.platform.domain.events.cms;

import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.domain.model.User;
import lombok.NoArgsConstructor;

/**
 * Event details for when a second administrator approves a maker-checker unsuspend request
 * (issue #492 Phase 3). The target's OLD password is already invalidated by the time this fires --
 * the notification must tell the account holder a new password is required, not just that a reset
 * "was requested" (unlike {@link UserPasswordResetEvent}'s copy, this is not optional/disregardable).
 *
 * @author SimIS Inc.
 */
@NoArgsConstructor
public class UserAccountRestoredEvent extends Event {

  public static final String ID = "user-account-restored";

  private User user = null;
  private User approvedBy = null;

  public UserAccountRestoredEvent(User user, User approvedBy) {
    this.user = user;
    this.approvedBy = approvedBy;
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

  public void setApprovedBy(User approvedBy) {
    this.approvedBy = approvedBy;
  }

  public User getApprovedBy() {
    return approvedBy;
  }
}
