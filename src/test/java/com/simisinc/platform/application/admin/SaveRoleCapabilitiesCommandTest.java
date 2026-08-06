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

package com.simisinc.platform.application.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.sql.Connection;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.persistence.CapabilityRepository;
import com.simisinc.platform.infrastructure.persistence.RoleCapabilityRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * The only runtime path that mutates role_capabilities (issue #704) - these tests exist
 * specifically to prove the self-lockout guard actually blocks the one unrecoverable case
 * (admin losing admin:manage with no other role holding it) without over-blocking legitimate
 * changes.
 *
 * @author elizabeth houser
 */
class SaveRoleCapabilitiesCommandTest {

  private static Role role(String code, int id) {
    Role role = new Role();
    role.setId(id);
    role.setCode(code);
    role.setTitle(code);
    return role;
  }

  private static Capability capability(String code, long id) {
    Capability capability = new Capability();
    capability.setId(id);
    capability.setCode(code);
    return capability;
  }

  @Test
  void requiresAReason() {
    Role adminRole = role("admin", 5);
    try (MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class)) {
      DataException e = assertThrows(DataException.class,
          () -> SaveRoleCapabilitiesCommand.save(null, adminRole, Set.of("admin:manage"), "  "));
      assertEquals("A reason is required when changing role permissions", e.getMessage());
      capabilityRepo.verifyNoInteractions();
    }
  }

  @Test
  void grantsANewlyCheckedCapability() throws Exception {
    Capability contentManage = capability("content:manage", 1L);
    Capability dataManage = capability("data:manage", 2L);
    Role dataManagerRole = role("data-manager", 4);

    try (MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class);
        MockedStatic<RoleCapabilityRepository> roleCapabilityRepo = mockStatic(RoleCapabilityRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      capabilityRepo.when(CapabilityRepository::findAll).thenReturn(List.of(contentManage, dataManage));
      capabilityRepo.when(() -> CapabilityRepository.findAllByRoleId(4)).thenReturn(List.of(dataManage));
      roleCapabilityRepo.when(() -> RoleCapabilityRepository.grant(4, 1L)).thenReturn(true);

      SaveRoleCapabilitiesCommand.save(null, dataManagerRole, Set.of("data:manage", "content:manage"),
          "Needs to help with content too");

      roleCapabilityRepo.verify(() -> RoleCapabilityRepository.grant(4, 1L));
      roleCapabilityRepo.verify(() -> RoleCapabilityRepository.revoke(anyLong(), anyLong()), never());
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.AUTHORIZATION),
          eq("role_capability.grant"), eq(AuditEventCommand.SUCCESS), eq("role_capability"),
          eq("content:manage"), eq("data-manager"), eq("Needs to help with content too")));
    }
  }

  @Test
  void grantDoesNotLogSuccessWhenTheUnderlyingInsertAffectsNoRows() throws Exception {
    // A concurrent-edit race (e.g. two admins saving the same role at once) can make the INSERT a
    // no-op; the audit trail must not claim a change happened when RoleCapabilityRepository itself
    // reports it didn't.
    Capability contentManage = capability("content:manage", 1L);
    Role dataManagerRole = role("data-manager", 4);

    try (MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class);
        MockedStatic<RoleCapabilityRepository> roleCapabilityRepo = mockStatic(RoleCapabilityRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      capabilityRepo.when(CapabilityRepository::findAll).thenReturn(List.of(contentManage));
      capabilityRepo.when(() -> CapabilityRepository.findAllByRoleId(4)).thenReturn(List.of());
      roleCapabilityRepo.when(() -> RoleCapabilityRepository.grant(4, 1L)).thenReturn(false);

      SaveRoleCapabilitiesCommand.save(null, dataManagerRole, Set.of("content:manage"), "Adding access");

      roleCapabilityRepo.verify(() -> RoleCapabilityRepository.grant(4, 1L));
      audit.verifyNoInteractions();
    }
  }

  @Test
  void revokesAnUncheckedCapabilityWhenAnotherRoleStillHasIt() throws Exception {
    Capability adminManage = capability("admin:manage", 5L);
    Role contentManagerRole = role("content-manager", 2);
    Connection connection = mock(Connection.class);

    try (MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class);
        MockedStatic<RoleCapabilityRepository> roleCapabilityRepo = mockStatic(RoleCapabilityRepository.class);
        MockedStatic<DB> db = mockStatic(DB.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      capabilityRepo.when(CapabilityRepository::findAll).thenReturn(List.of(adminManage));
      capabilityRepo.when(() -> CapabilityRepository.findAllByRoleId(2)).thenReturn(List.of(adminManage));
      db.when(DB::getConnection).thenReturn(connection);
      // The guard still runs (capability code is admin:manage), but excluding this role still
      // leaves 1 effective holder (e.g. another role with real members, or a direct grant), so the
      // revoke is allowed to proceed. Both the count and the revoke run on the same connection
      // the admin:manage guard's advisory lock was acquired on - see
      // RoleCapabilityRepository#acquireAdminManageGuardLock.
      roleCapabilityRepo.when(() -> RoleCapabilityRepository.countDistinctUsersHoldingCapability(connection, 5L, 2L, -1L))
          .thenReturn(1L);
      roleCapabilityRepo.when(() -> RoleCapabilityRepository.revoke(connection, 2, 5L)).thenReturn(true);

      SaveRoleCapabilitiesCommand.save(null, contentManagerRole, Set.of(), "Accidentally granted, removing");

      roleCapabilityRepo.verify(() -> RoleCapabilityRepository.acquireAdminManageGuardLock(connection));
      roleCapabilityRepo.verify(() -> RoleCapabilityRepository.revoke(connection, 2, 5L));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.AUTHORIZATION),
          eq("role_capability.revoke"), eq(AuditEventCommand.SUCCESS), eq("role_capability"),
          eq("admin:manage"), eq("content-manager"), eq("Accidentally granted, removing")));
    }
  }

  @Test
  void revokeDoesNotLogSuccessWhenTheUnderlyingDeleteAffectsNoRows() throws Exception {
    Capability contentManage = capability("content:manage", 1L);
    Role contentManagerRole = role("content-manager", 2);

    try (MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class);
        MockedStatic<RoleCapabilityRepository> roleCapabilityRepo = mockStatic(RoleCapabilityRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      capabilityRepo.when(CapabilityRepository::findAll).thenReturn(List.of(contentManage));
      capabilityRepo.when(() -> CapabilityRepository.findAllByRoleId(2)).thenReturn(List.of(contentManage));
      roleCapabilityRepo.when(() -> RoleCapabilityRepository.revoke(2, 1L)).thenReturn(false);

      SaveRoleCapabilitiesCommand.save(null, contentManagerRole, Set.of(), "Accidentally granted, removing");

      roleCapabilityRepo.verify(() -> RoleCapabilityRepository.revoke(2, 1L));
      audit.verifyNoInteractions();
    }
  }

  @Test
  void refusesToRevokeAdminManageWhenItIsTheLastEffectiveHolderEvenWithAnEmptyDecoyRoleAlsoListed() {
    // Reproduces the exact gap this guard exists to close: role A (id 5, being revoked here) has
    // real members and holds admin:manage; role B is an orphaned/unused role that also technically
    // lists admin:manage in role_capabilities but has zero assigned users. The *count of roles*
    // granting the capability would be 2, which the old guard treated as safe -- but the
    // *effective* holder count (what countDistinctUsersHoldingCapability actually computes, by
    // joining through user_roles) correctly comes back 0 once role A's contribution is excluded,
    // because role B contributes no real users. The guard must refuse based on that, not the stale
    // role-count. (Role B itself is only implied here, via the mocked return value -- proving the
    // join genuinely filters out a zero-member role belongs in a RoleCapabilityRepository-level
    // integration test against a real database, which is out of this command test's scope.)
    Capability adminManage = capability("admin:manage", 5L);
    Role roleWithRealMembers = role("admin", 5);
    Connection connection = mock(Connection.class);

    try (MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class);
        MockedStatic<RoleCapabilityRepository> roleCapabilityRepo = mockStatic(RoleCapabilityRepository.class);
        MockedStatic<DB> db = mockStatic(DB.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      capabilityRepo.when(CapabilityRepository::findAll).thenReturn(List.of(adminManage));
      capabilityRepo.when(() -> CapabilityRepository.findAllByRoleId(5)).thenReturn(List.of(adminManage));
      db.when(DB::getConnection).thenReturn(connection);
      roleCapabilityRepo.when(() -> RoleCapabilityRepository.countDistinctUsersHoldingCapability(connection, 5L, 5L, -1L))
          .thenReturn(0L);

      DataException e = assertThrows(DataException.class,
          () -> SaveRoleCapabilitiesCommand.save(null, roleWithRealMembers, Set.of(),
              "Trying to remove admin access"));

      assertEquals(true, e.getMessage().contains("no one would be left holding it"));
      roleCapabilityRepo.verify(() -> RoleCapabilityRepository.acquireAdminManageGuardLock(connection));
      roleCapabilityRepo.verify(() -> RoleCapabilityRepository.revoke(any(Connection.class), anyLong(), anyLong()),
          never());
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.AUTHORIZATION),
          eq("role_capability.revoke"), eq(AuditEventCommand.FAILURE), eq("role_capability"),
          eq("admin:manage"), eq("admin"), any()));
    }
  }

  @Test
  void allowsRevokingAnOtherCapabilityDownToZeroHolders() throws Exception {
    // Only admin:manage is guarded - retiring a role's access to a non-critical capability,
    // even down to zero holders, is a legitimate admin decision this command must not block.
    Capability dataManage = capability("data:manage", 2L);
    Role dataManagerRole = role("data-manager", 4);

    try (MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class);
        MockedStatic<RoleCapabilityRepository> roleCapabilityRepo = mockStatic(RoleCapabilityRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      capabilityRepo.when(CapabilityRepository::findAll).thenReturn(List.of(dataManage));
      capabilityRepo.when(() -> CapabilityRepository.findAllByRoleId(4)).thenReturn(List.of(dataManage));
      roleCapabilityRepo.when(() -> RoleCapabilityRepository.revoke(4, 2L)).thenReturn(true);

      SaveRoleCapabilitiesCommand.save(null, dataManagerRole, Set.of(), "Retiring this feature area");

      roleCapabilityRepo.verify(
          () -> RoleCapabilityRepository.countDistinctUsersHoldingCapability(anyLong(), anyLong(), anyLong()),
          never());
      roleCapabilityRepo.verify(() -> RoleCapabilityRepository.revoke(4, 2L));
    }
  }

  @Test
  void unchangedCapabilitiesAreNeitherGrantedNorRevoked() throws Exception {
    Capability contentManage = capability("content:manage", 1L);
    Role contentManagerRole = role("content-manager", 2);

    try (MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class);
        MockedStatic<RoleCapabilityRepository> roleCapabilityRepo = mockStatic(RoleCapabilityRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      capabilityRepo.when(CapabilityRepository::findAll).thenReturn(List.of(contentManage));
      capabilityRepo.when(() -> CapabilityRepository.findAllByRoleId(2)).thenReturn(List.of(contentManage));

      SaveRoleCapabilitiesCommand.save(null, contentManagerRole, Set.of("content:manage"), "No actual change");

      roleCapabilityRepo.verifyNoInteractions();
      audit.verifyNoInteractions();
    }
  }
}
