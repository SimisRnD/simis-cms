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

package com.simisinc.platform.presentation.widgets.admin.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.UserSpecification;
import com.simisinc.platform.infrastructure.persistence.login.UserLoginRepository;
import com.simisinc.platform.presentation.controller.DataConstants;

/**
 * Verifies each statusFilter/mfaFilter/agingPasswordFilter value on /admin/users (issue #492)
 * translates into the same compound UserSpecification conditions User.getAccountStatus() itself
 * derives from -- so the filter and the badge can never disagree about which bucket an account is
 * in. RoleRepository/GroupRepository/UserLoginRepository are stubbed to empty since execute()
 * always loads them regardless of the filter under test.
 *
 * @author SimIS Inc.
 */
class UsersListWidgetTest extends WidgetBase {

  private UserSpecification runWithStatusFilter(String statusFilterValue) {
    return captureSpecification(() -> addQueryParameter(widgetContext, "statusFilter", statusFilterValue));
  }

  private UserSpecification captureSpecification(Runnable setUpParams) {
    setUpParams.run();
    setRoles(widgetContext, ADMIN);

    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<UserLoginRepository> loginRepo = mockStatic(UserLoginRepository.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(Collections.emptyList());
      groupRepo.when(GroupRepository::findAll).thenReturn(Collections.emptyList());
      // mockStatic(UserRepository.class) stubs every static method, including this pure helper --
      // without this, Mockito's default int answer (0) would silently override the real threshold
      // parsing for every test here, not just the one that cares about it.
      userRepo.when(() -> UserRepository.resolvePasswordMaxAgeDays(any())).thenCallRealMethod();

      ArgumentCaptor<UserSpecification> specCaptor = ArgumentCaptor.forClass(UserSpecification.class);
      userRepo.when(() -> UserRepository.findAll(specCaptor.capture(), any(DataConstraints.class)))
          .thenReturn(Collections.<User>emptyList());

      new UsersListWidget().execute(widgetContext);

      return specCaptor.getValue();
    }
  }

  @Test
  void activeFilterRequiresEnabledUnlockedAndVerified() {
    UserSpecification spec = runWithStatusFilter("active");
    assertEquals(DataConstants.TRUE, spec.getIsEnabled());
    assertEquals(DataConstants.FALSE, spec.getIsLocked());
    assertEquals(DataConstants.TRUE, spec.getIsVerified());
  }

  @Test
  void suspendedFilterOnlyRequiresDisabled() {
    UserSpecification spec = runWithStatusFilter("suspended");
    assertEquals(DataConstants.FALSE, spec.getIsEnabled());
    // Locked/verified must not be constrained -- a suspended account can be in either state.
    assertEquals(DataConstants.UNDEFINED, spec.getIsLocked());
    assertEquals(DataConstants.UNDEFINED, spec.getIsVerified());
  }

  @Test
  void lockedFilterRequiresEnabledAndLocked() {
    UserSpecification spec = runWithStatusFilter("locked");
    assertEquals(DataConstants.TRUE, spec.getIsEnabled());
    assertEquals(DataConstants.TRUE, spec.getIsLocked());
    assertEquals(DataConstants.UNDEFINED, spec.getIsVerified());
  }

  @Test
  void inactiveFilterRequiresEnabledUnlockedAndNotVerified() {
    UserSpecification spec = runWithStatusFilter("inactive");
    assertEquals(DataConstants.TRUE, spec.getIsEnabled());
    assertEquals(DataConstants.FALSE, spec.getIsLocked());
    assertEquals(DataConstants.FALSE, spec.getIsVerified());
  }

  @Test
  void anyFilterLeavesStatusUnconstrained() {
    UserSpecification spec = runWithStatusFilter("any");
    assertEquals(DataConstants.UNDEFINED, spec.getIsEnabled());
    assertEquals(DataConstants.UNDEFINED, spec.getIsLocked());
    assertEquals(DataConstants.UNDEFINED, spec.getIsVerified());
  }

  @Test
  void anUnrecognizedStatusFilterValueFallsBackToAny() {
    // Defends against a tampered/stale query string selecting an arbitrary condition.
    UserSpecification spec = runWithStatusFilter("'; DROP TABLE users; --");
    assertEquals(DataConstants.UNDEFINED, spec.getIsEnabled());
    assertEquals(DataConstants.UNDEFINED, spec.getIsLocked());
  }

  @Test
  void mfaEnabledFilterSetsIsMfaEnabledTrue() {
    UserSpecification spec = captureSpecification(() -> addQueryParameter(widgetContext, "mfaFilter", "enabled"));
    assertEquals(DataConstants.TRUE, spec.getIsMfaEnabled());
  }

  @Test
  void mfaDisabledFilterSetsIsMfaEnabledFalse() {
    UserSpecification spec = captureSpecification(() -> addQueryParameter(widgetContext, "mfaFilter", "disabled"));
    assertEquals(DataConstants.FALSE, spec.getIsMfaEnabled());
  }

  @Test
  void agingPasswordFilterUsesTheConfiguredThreshold() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("password.maxAgeDays")).thenReturn("180");

      UserSpecification spec = captureSpecification(() -> addQueryParameter(widgetContext, "agingPasswordFilter", "1"));

      assertEquals(180, spec.getPasswordOlderThanDays());
    }
  }

  @Test
  void agingPasswordFilterIsUnsetByDefault() {
    UserSpecification spec = captureSpecification(() -> { });
    assertEquals(-1, spec.getPasswordOlderThanDays());
  }
}
