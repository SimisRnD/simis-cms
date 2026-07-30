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

package com.simisinc.platform.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.domain.model.CapabilityGrant;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.CapabilityGrantRepository;
import com.simisinc.platform.infrastructure.persistence.CapabilityRepository;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserLoginRepository;

/**
 * Issue #702 adds direct capability grants alongside #701's role-derived ones; a user can hold
 * a capability either way, so LoadUserCommand.populateUserRecord must merge both sources into one
 * effective, de-duplicated capabilityList before hasPermission() ever sees it. These tests are the
 * only coverage of that merge - hasPermission() itself just checks list membership, so getting the
 * merge right here is the entire correctness burden of #702's runtime authorization path.
 *
 * @author elizabeth houser
 */
class LoadUserCommandTest {

  private static Capability capability(String code, long id) {
    Capability capability = new Capability();
    capability.setId(id);
    capability.setCode(code);
    return capability;
  }

  private static CapabilityGrant grant(long capabilityId) {
    CapabilityGrant grant = new CapabilityGrant();
    grant.setCapabilityId(capabilityId);
    return grant;
  }

  private static User user(long id) {
    User user = new User();
    user.setId(id);
    return user;
  }

  @Test
  void mergesRoleDerivedAndDirectlyGrantedCapabilities() {
    Capability contentManage = capability("content:manage", 1L);
    Capability reportsExport = capability("reports:export", 3L);

    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<UserLoginRepository> userLoginRepo = mockStatic(UserLoginRepository.class);
        MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class);
        MockedStatic<CapabilityGrantRepository> grantRepo = mockStatic(CapabilityGrantRepository.class)) {
      userRepo.when(() -> UserRepository.findByUserId(10L)).thenReturn(user(10L));
      roleRepo.when(() -> RoleRepository.findAllByUserId(10L)).thenReturn(null);
      groupRepo.when(() -> GroupRepository.findAllByUserId(10L)).thenReturn(null);
      userLoginRepo.when(() -> UserLoginRepository.queryLastLogin(10L)).thenReturn(null);
      capabilityRepo.when(() -> CapabilityRepository.findAllByUserId(10L)).thenReturn(List.of(contentManage));
      capabilityRepo.when(CapabilityRepository::findAll).thenReturn(List.of(contentManage, reportsExport));
      grantRepo.when(() -> CapabilityGrantRepository.findActiveByUserId(10L)).thenReturn(List.of(grant(3L)));

      User result = LoadUserCommand.loadUser(10L);

      Set<String> codes = result.getCapabilityList().stream().map(Capability::getCode).collect(Collectors.toSet());
      assertEquals(Set.of("content:manage", "reports:export"), codes);
    }
  }

  @Test
  void aCapabilityHeldBothWaysAppearsOnlyOnce() {
    Capability adminManage = capability("admin:manage", 5L);

    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<UserLoginRepository> userLoginRepo = mockStatic(UserLoginRepository.class);
        MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class);
        MockedStatic<CapabilityGrantRepository> grantRepo = mockStatic(CapabilityGrantRepository.class)) {
      userRepo.when(() -> UserRepository.findByUserId(10L)).thenReturn(user(10L));
      roleRepo.when(() -> RoleRepository.findAllByUserId(10L)).thenReturn(null);
      groupRepo.when(() -> GroupRepository.findAllByUserId(10L)).thenReturn(null);
      userLoginRepo.when(() -> UserLoginRepository.queryLastLogin(10L)).thenReturn(null);
      // Held via role AND via a redundant direct grant of the same capability
      capabilityRepo.when(() -> CapabilityRepository.findAllByUserId(10L)).thenReturn(List.of(adminManage));
      capabilityRepo.when(CapabilityRepository::findAll).thenReturn(List.of(adminManage));
      grantRepo.when(() -> CapabilityGrantRepository.findActiveByUserId(10L)).thenReturn(List.of(grant(5L)));

      User result = LoadUserCommand.loadUser(10L);

      assertEquals(1, result.getCapabilityList().size());
      assertTrue(result.hasPermission("admin:manage"));
    }
  }

  @Test
  void aDirectGrantAloneIsSufficientWithoutAnyRole() {
    Capability reportsExport = capability("reports:export", 3L);

    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<UserLoginRepository> userLoginRepo = mockStatic(UserLoginRepository.class);
        MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class);
        MockedStatic<CapabilityGrantRepository> grantRepo = mockStatic(CapabilityGrantRepository.class)) {
      userRepo.when(() -> UserRepository.findByUserId(10L)).thenReturn(user(10L));
      roleRepo.when(() -> RoleRepository.findAllByUserId(10L)).thenReturn(null);
      groupRepo.when(() -> GroupRepository.findAllByUserId(10L)).thenReturn(null);
      userLoginRepo.when(() -> UserLoginRepository.queryLastLogin(10L)).thenReturn(null);
      // No roles at all, so the role-derived capability list is empty/null
      capabilityRepo.when(() -> CapabilityRepository.findAllByUserId(10L)).thenReturn(null);
      capabilityRepo.when(CapabilityRepository::findAll).thenReturn(List.of(reportsExport));
      grantRepo.when(() -> CapabilityGrantRepository.findActiveByUserId(10L)).thenReturn(List.of(grant(3L)));

      User result = LoadUserCommand.loadUser(10L);

      assertTrue(result.hasPermission("reports:export"));
    }
  }

  @Test
  void noActiveGrantsLeavesTheRoleDerivedListUntouched() {
    Capability contentManage = capability("content:manage", 1L);

    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<UserLoginRepository> userLoginRepo = mockStatic(UserLoginRepository.class);
        MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class);
        MockedStatic<CapabilityGrantRepository> grantRepo = mockStatic(CapabilityGrantRepository.class)) {
      userRepo.when(() -> UserRepository.findByUserId(10L)).thenReturn(user(10L));
      roleRepo.when(() -> RoleRepository.findAllByUserId(10L)).thenReturn(null);
      groupRepo.when(() -> GroupRepository.findAllByUserId(10L)).thenReturn(null);
      userLoginRepo.when(() -> UserLoginRepository.queryLastLogin(10L)).thenReturn(null);
      capabilityRepo.when(() -> CapabilityRepository.findAllByUserId(10L)).thenReturn(List.of(contentManage));
      grantRepo.when(() -> CapabilityGrantRepository.findActiveByUserId(10L)).thenReturn(null);

      User result = LoadUserCommand.loadUser(10L);

      assertEquals(List.of(contentManage), result.getCapabilityList());
      capabilityRepo.verify(CapabilityRepository::findAll, never());
    }
  }
}
