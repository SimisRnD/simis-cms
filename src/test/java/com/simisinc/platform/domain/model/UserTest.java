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

package com.simisinc.platform.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;

/**
 * Verifies {@link User#getAccountStatus()}'s priority ordering (issue #492): enabled/isLocked()/
 * validated are three independent signals, so the interesting cases are the ones where more than
 * one applies at once, not just the four states in isolation.
 *
 * @author SimIS Inc.
 */
class UserTest {

  private static User validatedActiveUser() {
    User user = new User();
    user.setEnabled(true);
    user.setValidated(new Timestamp(System.currentTimeMillis()));
    return user;
  }

  @Test
  void anEnabledLockedValidatedUserIsActive() {
    User user = validatedActiveUser();
    assertEquals(User.STATUS_ACTIVE, user.getAccountStatus());
  }

  @Test
  void aDisabledUserIsSuspendedRegardlessOfOtherState() {
    User user = validatedActiveUser();
    user.setEnabled(false);
    assertEquals(User.STATUS_SUSPENDED, user.getAccountStatus());
  }

  @Test
  void anEnabledLockedUserIsLocked() {
    User user = validatedActiveUser();
    user.setLockedUntil(new Timestamp(System.currentTimeMillis() + 15 * 60_000L));
    assertEquals(User.STATUS_LOCKED, user.getAccountStatus());
  }

  @Test
  void aPastLockoutTimestampNoLongerCountsAsLocked() {
    User user = validatedActiveUser();
    user.setLockedUntil(new Timestamp(System.currentTimeMillis() - 60_000L));
    assertEquals(User.STATUS_ACTIVE, user.getAccountStatus());
  }

  @Test
  void anEnabledUnlockedNeverValidatedUserIsInactive() {
    User user = new User();
    user.setEnabled(true);
    assertEquals(User.STATUS_INACTIVE, user.getAccountStatus());
  }

  @Test
  void suspendedTakesPriorityOverLocked() {
    // A single-person unsuspend action is what matters here -- the lock clears on its own.
    User user = new User();
    user.setEnabled(false);
    user.setLockedUntil(new Timestamp(System.currentTimeMillis() + 15 * 60_000L));
    assertEquals(User.STATUS_SUSPENDED, user.getAccountStatus());
  }

  @Test
  void suspendedTakesPriorityOverNeverValidated() {
    User user = new User();
    user.setEnabled(false);
    assertEquals(User.STATUS_SUSPENDED, user.getAccountStatus());
  }

  @Test
  void lockedTakesPriorityOverNeverValidated() {
    User user = new User();
    user.setEnabled(true);
    user.setLockedUntil(new Timestamp(System.currentTimeMillis() + 15 * 60_000L));
    assertEquals(User.STATUS_LOCKED, user.getAccountStatus());
  }
}
