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
 * Event details for when a maker-checker unsuspend request is filed for an elevated-role account
 * (issue #492 Phase 3) -- triggers a notification to the pool of eligible approvers.
 *
 * @author SimIS Inc.
 */
@NoArgsConstructor
public class UnsuspendRequestedEvent extends Event {

  public static final String ID = "unsuspend-requested";

  private User target = null;
  private User requestedBy = null;
  private String reason = null;

  public UnsuspendRequestedEvent(User target, User requestedBy, String reason) {
    this.target = target;
    this.requestedBy = requestedBy;
    this.reason = reason;
  }

  @Override
  public String getDomainEventType() {
    return ID;
  }

  public void setTarget(User target) {
    this.target = target;
  }

  public User getTarget() {
    return target;
  }

  public void setRequestedBy(User requestedBy) {
    this.requestedBy = requestedBy;
  }

  public User getRequestedBy() {
    return requestedBy;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getReason() {
    return reason;
  }
}
