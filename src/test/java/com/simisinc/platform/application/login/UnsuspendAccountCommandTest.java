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

package com.simisinc.platform.application.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.UnsuspendRequest;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.login.UnsuspendRequestRepository;

/**
 * Covers the shared enforcement point for issue #492 Phase 3: the elevated-role gate, the
 * self-target guard, and (heaviest scrutiny) the two decision-time guards on approve --
 * separation of duties (a different admin than the requester) and the escalation check (the
 * approver's own level must be at least the target's), which close a two-lower-privileged-admins-
 * collude gap none of the original design questions asked about directly.
 *
 * @author SimIS Inc.
 */
class UnsuspendAccountCommandTest {

  private static Role role(int id, int level, String code) {
    Role role = new Role();
    role.setId(id);
    role.setLevel(level);
    role.setCode(code);
    role.setTitle(code);
    return role;
  }

  private static final Role COMMUNITY_MANAGER = role(3, 90, "community-manager");
  private static final Role ADMIN = role(6, 100, "admin");
  private static final Role CONTENT_EDITOR = role(1, 70, "content-editor");

  private static User userWithRoles(long id, boolean enabled, Role... roles) {
    User user = new User();
    user.setId(id);
    user.setEmail("user" + id + "@example.com");
    user.setEnabled(enabled);
    List<Role> roleList = new ArrayList<>(Arrays.asList(roles));
    user.setRoleList(roleList);
    return user;
  }

  // --- requiresApproval --------------------------------------------------

  @Test
  void requiresApprovalIsTrueForACommunityManagerTarget() {
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class)) {
      roleRepo.when(() -> RoleRepository.findByCode("community-manager")).thenReturn(COMMUNITY_MANAGER);
      User target = userWithRoles(5L, false, COMMUNITY_MANAGER);
      assertEquals(true, UnsuspendAccountCommand.requiresApproval(target));
    }
  }

  @Test
  void requiresApprovalIsFalseForAContentEditorTarget() {
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class)) {
      roleRepo.when(() -> RoleRepository.findByCode("community-manager")).thenReturn(COMMUNITY_MANAGER);
      User target = userWithRoles(5L, false, CONTENT_EDITOR);
      assertEquals(false, UnsuspendAccountCommand.requiresApproval(target));
    }
  }

  @Test
  void requiresApprovalFallsBackTo90IfTheRoleSeedIsMissing() {
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class)) {
      roleRepo.when(() -> RoleRepository.findByCode("community-manager")).thenReturn(null);
      User target = userWithRoles(5L, false, ADMIN);
      assertEquals(true, UnsuspendAccountCommand.requiresApproval(target));
    }
  }

  // --- requestOrRestore ---------------------------------------------------

  @Test
  void nonElevatedTargetIsRestoredDirectlyExactlyAsBefore() throws DataException {
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<UnsuspendRequestRepository> requestRepo = mockStatic(UnsuspendRequestRepository.class)) {
      roleRepo.when(() -> RoleRepository.findByCode("community-manager")).thenReturn(COMMUNITY_MANAGER);
      User target = userWithRoles(5L, false, CONTENT_EDITOR);
      User actingAdmin = userWithRoles(1L, true, ADMIN);
      userRepo.when(() -> UserRepository.restoreAccount(target)).thenReturn(target);

      UnsuspendAccountCommand.Outcome outcome = UnsuspendAccountCommand.requestOrRestore(target, actingAdmin, null);

      assertEquals(UnsuspendAccountCommand.Outcome.RESTORED, outcome);
      userRepo.verify(() -> UserRepository.restoreAccount(target), times(1));
      requestRepo.verify(() -> UnsuspendRequestRepository.add(any()), never());
    }
  }

  @Test
  void anAlreadyEnabledTargetReturnsNotSuspendedAndMutatesNothing() throws DataException {
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      User target = userWithRoles(5L, true, CONTENT_EDITOR);
      User actingAdmin = userWithRoles(1L, true, ADMIN);

      UnsuspendAccountCommand.Outcome outcome = UnsuspendAccountCommand.requestOrRestore(target, actingAdmin, null);

      assertEquals(UnsuspendAccountCommand.Outcome.NOT_SUSPENDED, outcome);
      userRepo.verify(() -> UserRepository.restoreAccount(any()), never());
    }
  }

  @Test
  void requestingYourOwnUnsuspendIsRejected() {
    User target = userWithRoles(5L, false, CONTENT_EDITOR);
    User actingAdmin = userWithRoles(5L, false, CONTENT_EDITOR); // same id as target

    assertThrows(DataException.class, () -> UnsuspendAccountCommand.requestOrRestore(target, actingAdmin, null));
  }

  @Test
  void elevatedTargetFilesARequestInsteadOfRestoringDirectly() throws DataException {
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<UnsuspendRequestRepository> requestRepo = mockStatic(UnsuspendRequestRepository.class)) {
      roleRepo.when(() -> RoleRepository.findByCode("community-manager")).thenReturn(COMMUNITY_MANAGER);
      requestRepo.when(() -> UnsuspendRequestRepository.findPendingByTargetUserId(5L)).thenReturn(null);
      requestRepo.when(() -> UnsuspendRequestRepository.add(any())).thenAnswer(inv -> inv.getArgument(0));

      User target = userWithRoles(5L, false, ADMIN);
      User actingAdmin = userWithRoles(1L, true, COMMUNITY_MANAGER);

      UnsuspendAccountCommand.Outcome outcome = UnsuspendAccountCommand.requestOrRestore(target, actingAdmin, "incident review cleared");

      assertEquals(UnsuspendAccountCommand.Outcome.REQUESTED, outcome);
      userRepo.verify(() -> UserRepository.restoreAccount(any()), never());
      requestRepo.verify(() -> UnsuspendRequestRepository.add(any()), times(1));
    }
  }

  @Test
  void elevatedTargetWithABlankReasonIsRejected() {
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<UnsuspendRequestRepository> requestRepo = mockStatic(UnsuspendRequestRepository.class)) {
      roleRepo.when(() -> RoleRepository.findByCode("community-manager")).thenReturn(COMMUNITY_MANAGER);
      requestRepo.when(() -> UnsuspendRequestRepository.findPendingByTargetUserId(5L)).thenReturn(null);

      User target = userWithRoles(5L, false, ADMIN);
      User actingAdmin = userWithRoles(1L, true, COMMUNITY_MANAGER);

      assertThrows(DataException.class, () -> UnsuspendAccountCommand.requestOrRestore(target, actingAdmin, "  "));
      requestRepo.verify(() -> UnsuspendRequestRepository.add(any()), never());
    }
  }

  @Test
  void elevatedTargetWithAnAlreadyPendingRequestIsANoOp() throws DataException {
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<UnsuspendRequestRepository> requestRepo = mockStatic(UnsuspendRequestRepository.class)) {
      roleRepo.when(() -> RoleRepository.findByCode("community-manager")).thenReturn(COMMUNITY_MANAGER);
      UnsuspendRequest existing = new UnsuspendRequest();
      requestRepo.when(() -> UnsuspendRequestRepository.findPendingByTargetUserId(5L)).thenReturn(existing);

      User target = userWithRoles(5L, false, ADMIN);
      User actingAdmin = userWithRoles(1L, true, COMMUNITY_MANAGER);

      UnsuspendAccountCommand.Outcome outcome = UnsuspendAccountCommand.requestOrRestore(target, actingAdmin, "reason");

      assertEquals(UnsuspendAccountCommand.Outcome.ALREADY_PENDING, outcome);
      requestRepo.verify(() -> UnsuspendRequestRepository.add(any()), never());
    }
  }

  // --- approve -------------------------------------------------------------

  private static UnsuspendRequest pendingRequest(long id, long targetUserId, long requestedBy) {
    UnsuspendRequest request = new UnsuspendRequest();
    request.setId(id);
    request.setTargetUserId(targetUserId);
    request.setTargetEmail("target@example.com");
    request.setRequestedBy(requestedBy);
    request.setReason("reason");
    return request;
  }

  @Test
  void approveRestoresAndInvalidatesThePasswordInOrder() throws DataException {
    try (MockedStatic<UnsuspendRequestRepository> requestRepo = mockStatic(UnsuspendRequestRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      UnsuspendRequest request = pendingRequest(42L, 5L, 1L);
      requestRepo.when(() -> UnsuspendRequestRepository.findById(42L)).thenReturn(request);

      User approver = userWithRoles(2L, true, ADMIN); // different from requester (1L)
      User target = userWithRoles(5L, false, COMMUNITY_MANAGER);
      loadCmd.when(() -> LoadUserCommand.loadUser(2L)).thenReturn(approver);
      loadCmd.when(() -> LoadUserCommand.loadUser(5L)).thenReturn(target);

      requestRepo.when(() -> UnsuspendRequestRepository.claimForApproval(42L, 2L, approver.getEmail())).thenReturn(true);
      userRepo.when(() -> UserRepository.updatePassword(target)).thenReturn(target);
      userRepo.when(() -> UserRepository.createAccountToken(target)).thenReturn(target);
      userRepo.when(() -> UserRepository.restoreAccount(target)).thenReturn(target);

      UnsuspendRequest result = UnsuspendAccountCommand.approve(42L, 2L);

      assertEquals(UnsuspendRequest.STATUS_APPROVED, result.getStatus());
      // Fail-safe order: invalidate password, THEN mint the token, THEN restore
      userRepo.verify(() -> UserRepository.updatePassword(target), times(1));
      userRepo.verify(() -> UserRepository.createAccountToken(target), times(1));
      userRepo.verify(() -> UserRepository.restoreAccount(target), times(1));
    }
  }

  @Test
  void theRequesterCannotApproveTheirOwnRequest() {
    try (MockedStatic<UnsuspendRequestRepository> requestRepo = mockStatic(UnsuspendRequestRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      UnsuspendRequest request = pendingRequest(42L, 5L, 1L);
      requestRepo.when(() -> UnsuspendRequestRepository.findById(42L)).thenReturn(request);
      User requester = userWithRoles(1L, true, ADMIN);
      loadCmd.when(() -> LoadUserCommand.loadUser(1L)).thenReturn(requester);

      assertThrows(DataException.class, () -> UnsuspendAccountCommand.approve(42L, 1L));

      userRepo.verify(() -> UserRepository.restoreAccount(any()), never());
      requestRepo.verify(() -> UnsuspendRequestRepository.claimForApproval(anyLong(), anyLong(), any()), never());
    }
  }

  @Test
  void anApproverBelowTheTargetsRoleLevelIsBlocked() {
    // Closes a real gap: two community-managers (level 90) could otherwise jointly reactivate an
    // admin (level 100) target, since both individually pass the page-level role gate.
    try (MockedStatic<UnsuspendRequestRepository> requestRepo = mockStatic(UnsuspendRequestRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      UnsuspendRequest request = pendingRequest(42L, 5L, 1L);
      requestRepo.when(() -> UnsuspendRequestRepository.findById(42L)).thenReturn(request);

      User approver = userWithRoles(2L, true, COMMUNITY_MANAGER); // level 90
      User target = userWithRoles(5L, false, ADMIN); // level 100
      loadCmd.when(() -> LoadUserCommand.loadUser(2L)).thenReturn(approver);
      loadCmd.when(() -> LoadUserCommand.loadUser(5L)).thenReturn(target);

      DataException e = assertThrows(DataException.class, () -> UnsuspendAccountCommand.approve(42L, 2L));
      assertEquals(true, e.getMessage().contains("above your own level"));

      requestRepo.verify(() -> UnsuspendRequestRepository.claimForApproval(anyLong(), anyLong(), any()), never());
      userRepo.verify(() -> UserRepository.restoreAccount(any()), never());
    }
  }

  @Test
  void approveFailsCleanlyWhenAnotherAdminAlreadyDecidedTheRequest() {
    // The atomic claim is the race guard -- a losing concurrent approve must not mutate the account.
    try (MockedStatic<UnsuspendRequestRepository> requestRepo = mockStatic(UnsuspendRequestRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      UnsuspendRequest request = pendingRequest(42L, 5L, 1L);
      requestRepo.when(() -> UnsuspendRequestRepository.findById(42L)).thenReturn(request);

      User approver = userWithRoles(2L, true, ADMIN);
      User target = userWithRoles(5L, false, COMMUNITY_MANAGER);
      loadCmd.when(() -> LoadUserCommand.loadUser(2L)).thenReturn(approver);
      loadCmd.when(() -> LoadUserCommand.loadUser(5L)).thenReturn(target);
      requestRepo.when(() -> UnsuspendRequestRepository.claimForApproval(42L, 2L, approver.getEmail())).thenReturn(false);

      assertThrows(DataException.class, () -> UnsuspendAccountCommand.approve(42L, 2L));

      userRepo.verify(() -> UserRepository.updatePassword(any()), never());
      userRepo.verify(() -> UserRepository.restoreAccount(any()), never());
    }
  }

  @Test
  void approvingARequestThatIsNotPendingIsRejected() {
    try (MockedStatic<UnsuspendRequestRepository> requestRepo = mockStatic(UnsuspendRequestRepository.class)) {
      UnsuspendRequest decided = pendingRequest(42L, 5L, 1L);
      decided.setStatus(UnsuspendRequest.STATUS_DENIED);
      requestRepo.when(() -> UnsuspendRequestRepository.findById(42L)).thenReturn(decided);

      assertThrows(DataException.class, () -> UnsuspendAccountCommand.approve(42L, 2L));
    }
  }

  // --- deny ------------------------------------------------------------------

  @Test
  void denyRequiresAReason() {
    assertThrows(DataException.class, () -> UnsuspendAccountCommand.deny(42L, 2L, "  "));
  }

  @Test
  void theRequesterCannotDenyTheirOwnRequest() {
    try (MockedStatic<UnsuspendRequestRepository> requestRepo = mockStatic(UnsuspendRequestRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class)) {
      UnsuspendRequest request = pendingRequest(42L, 5L, 1L);
      requestRepo.when(() -> UnsuspendRequestRepository.findById(42L)).thenReturn(request);
      User requester = userWithRoles(1L, true, ADMIN);
      loadCmd.when(() -> LoadUserCommand.loadUser(1L)).thenReturn(requester);

      assertThrows(DataException.class, () -> UnsuspendAccountCommand.deny(42L, 1L, "not convinced"));

      requestRepo.verify(() -> UnsuspendRequestRepository.claimForDenial(anyLong(), anyLong(), any(), any()), never());
    }
  }

  @Test
  void denyByADifferentAdminSucceedsWithNoRoleLevelCheck() throws DataException {
    // Unlike approve, deny grants nothing -- any eligible admin who isn't the requester may deny,
    // regardless of their own role level relative to the target's.
    try (MockedStatic<UnsuspendRequestRepository> requestRepo = mockStatic(UnsuspendRequestRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class)) {
      UnsuspendRequest request = pendingRequest(42L, 5L, 1L);
      requestRepo.when(() -> UnsuspendRequestRepository.findById(42L)).thenReturn(request);
      User decider = userWithRoles(2L, true, COMMUNITY_MANAGER); // lower level than an admin target, still allowed
      loadCmd.when(() -> LoadUserCommand.loadUser(2L)).thenReturn(decider);
      requestRepo.when(() -> UnsuspendRequestRepository.claimForDenial(42L, 2L, decider.getEmail(), "not convinced"))
          .thenReturn(true);

      UnsuspendRequest result = UnsuspendAccountCommand.deny(42L, 2L, "not convinced");

      assertEquals(UnsuspendRequest.STATUS_DENIED, result.getStatus());
      assertEquals("not convinced", result.getDecisionReason());
    }
  }
}
