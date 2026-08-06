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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.application.login.StepUpAuthCommand;
import com.simisinc.platform.application.register.SaveUserCommand;
import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Covers the 4 bulk actions on /admin/users (issue #492 Phase 2), specifically the guards that
 * make bulk safe to run against N accounts in one request: the acting-admin's own account is
 * skipped (never rejects the whole batch) for Suspend, a role above the actor's own level is
 * rejected for the WHOLE batch before any account is touched, a batch over MAX_BULK_SELECTION is
 * rejected outright rather than truncated, one bad id never aborts the rest of the batch, and
 * Reset Password / Assign Roles both require a fresh step-up re-authentication once per batch --
 * exactly the same bar the single-user forms already hold each of these actions to.
 *
 * @author SimIS Inc.
 */
class UsersListWidgetBulkActionsTest extends WidgetBase {

  private static User userWithId(long id) {
    User user = new User();
    user.setId(id);
    user.setEmail("user" + id + "@example.com");
    user.setEnabled(true);
    return user;
  }

  private static Role role(int id, int level, String code) {
    Role role = new Role();
    role.setId(id);
    role.setLevel(level);
    role.setCode(code);
    role.setTitle(code);
    return role;
  }

  private void multiValue(String name, String... values) {
    widgetContext.getParameterMap().put(name, values);
  }

  @Test
  void bulkSuspendOverCapIsRejectedWithNoRepositoryCalls() throws Exception {
    setRoles(widgetContext, ADMIN);
    String[] tooMany = new String[UsersListWidget.MAX_BULK_SELECTION + 1];
    for (int i = 0; i < tooMany.length; i++) {
      tooMany[i] = String.valueOf(i + 100);
    }
    multiValue("userId", tooMany);
    addQueryParameter(widgetContext, "command", "bulkSuspend");
    addQueryParameter(widgetContext, "reason", "incident response");

    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class)) {
      WidgetContext result = new UsersListWidget().post(widgetContext);

      loadCmd.verifyNoInteractions();
      userRepo.verify(() -> UserRepository.suspendAccount(any(), any()), never());
      assertTrue(result.getErrorMessage().contains("Too many accounts"));
    }
  }

  @Test
  void bulkSuspendSkipsSelfButContinuesTheRestOfTheBatch() throws Exception {
    // The logged-in test user's own id is 1L, see WidgetBase#login
    setRoles(widgetContext, ADMIN);
    multiValue("userId", "1", "5");
    addQueryParameter(widgetContext, "command", "bulkSuspend");
    addQueryParameter(widgetContext, "reason", "compromised credentials");

    User other = userWithId(5L);

    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(5L)).thenReturn(other);
      userRepo.when(() -> UserRepository.suspendAccount(other, "compromised credentials")).thenReturn(other);

      WidgetContext result = new UsersListWidget().post(widgetContext);

      // The self-suspend guard fires on the raw id, before any load of the target -- suspendAccount
      // is called exactly once, for the other account, never for the acting admin's own id. (Note:
      // LoadUserCommand.loadUser(1L) IS invoked once, but only incidentally via UserSession.getUser()
      // resolving the actor's own audit identity -- unrelated to the self-suspend guard itself.)
      userRepo.verify(() -> UserRepository.suspendAccount(other, "compromised credentials"), times(1));
      userRepo.verify(() -> UserRepository.suspendAccount(any(), any()), times(1));
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("user_management"), eq("user.disable"),
          eq("success"), anyLong(), any(), any(), any(), eq("user"), eq("5"), eq("user5@example.com"), any()),
          times(1));
      // A self-skip is still a partial result (1 of the 2 selected didn't go through), so this is a
      // warning, not a plain success -- matching the same "partial" bucket as a not-found/failed id.
      assertTrue(result.getWarningMessage().contains("1 of 2"));
      assertTrue(result.getWarningMessage().contains("your own account"));
    }
  }

  @Test
  void bulkUnsuspendSkipsAnIdThatNoLongerResolvesButContinues() throws Exception {
    setRoles(widgetContext, ADMIN);
    multiValue("userId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkUnsuspend");

    User found = userWithId(5L);
    found.setEnabled(false); // a genuinely suspended (and non-elevated) account, restorable directly

    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(5L)).thenReturn(found);
      loadCmd.when(() -> LoadUserCommand.loadUser(6L)).thenReturn(null); // deleted concurrently / tampered id
      loadCmd.when(() -> LoadUserCommand.loadUser(1L)).thenReturn(userWithId(1L));
      roleRepo.when(() -> RoleRepository.findByCode("community-manager")).thenReturn(role(3, 90, "community-manager"));
      userRepo.when(() -> UserRepository.restoreAccount(found)).thenReturn(found);

      WidgetContext result = new UsersListWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.restoreAccount(found), times(1));
      userRepo.verify(() -> UserRepository.restoreAccount(any()), times(1));
      assertTrue(result.getWarningMessage().contains("1 of 2"));
      assertTrue(result.getWarningMessage().contains("Not found: 1"));
    }
  }

  @Test
  void bulkResetPasswordWithoutStepUpIsRejectedWithNoRepositoryCalls() throws Exception {
    setRoles(widgetContext, ADMIN);
    multiValue("userId", "5");
    addQueryParameter(widgetContext, "command", "bulkResetPassword");
    // No stepUpCredential provided, and the session has no valid step-up token

    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class)) {
      WidgetContext result = new UsersListWidget().post(widgetContext);

      loadCmd.verifyNoInteractions();
      userRepo.verify(() -> UserRepository.createAccountToken(any()), never());
      workflow.verifyNoInteractions();
      assertTrue(result.getErrorMessage().contains("Re-authentication is required"));
    }
  }

  @Test
  void bulkAssignRolesWithoutStepUpIsRejectedWithNoRepositoryCalls() throws Exception {
    setRoles(widgetContext, ADMIN);
    multiValue("userId", "5");
    addQueryParameter(widgetContext, "command", "bulkAssignRoles");
    addQueryParameter(widgetContext, "roleId", "3");
    // No stepUpCredential provided, and the session has no valid step-up token

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class)) {
      WidgetContext result = new UsersListWidget().post(widgetContext);

      // Rejected before the role is even resolved -- step-up is checked first
      roleRepo.verifyNoInteractions();
      loadCmd.verifyNoInteractions();
      assertTrue(result.getErrorMessage().contains("Re-authentication is required"));
    }
  }

  @Test
  void bulkAssignRolesBlocksTheWholeBatchWhenTheRoleIsAboveTheActorsLevel() throws Exception {
    // A community-manager (level 90) attempting to bulk-grant 'admin' (level 100) -- the exact
    // escalation UserFormWidget's single-user editor already blocks; bulk must not be a weaker path.
    setRoles(widgetContext, COMMUNITY_MANAGER);
    grantStepUp(widgetContext);
    multiValue("userId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkAssignRoles");
    addQueryParameter(widgetContext, "roleId", "3");

    Role communityManagerRole = role(2, 90, COMMUNITY_MANAGER);
    Role adminRole = role(3, 100, ADMIN);

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<SaveUserCommand> saveUser = mockStatic(SaveUserCommand.class)) {
      roleRepo.when(() -> RoleRepository.findById(3)).thenReturn(adminRole);
      roleRepo.when(RoleRepository::findAll).thenReturn(Arrays.asList(communityManagerRole, adminRole));

      WidgetContext result = new UsersListWidget().post(widgetContext);

      // Rejected up front -- no account is ever loaded or touched
      loadCmd.verifyNoInteractions();
      saveUser.verifyNoInteractions();
      assertTrue(result.getErrorMessage().contains("cannot grant a role above your own level"));
    }
  }

  @Test
  void bulkAssignRolesIsAdditiveAndDoesNotStripAnUnrelatedExistingRole() throws Exception {
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    multiValue("userId", "5");
    addQueryParameter(widgetContext, "command", "bulkAssignRoles");
    addQueryParameter(widgetContext, "roleId", "3");

    Role existingRole = role(2, 70, "content-editor");
    Role grantedRole = role(3, 93, "data-manager");
    Role adminRole = role(1, 100, ADMIN);

    User target = userWithId(5L);
    List<Role> existingRoleList = new ArrayList<>();
    existingRoleList.add(existingRole);
    target.setRoleList(existingRoleList);

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<SaveUserCommand> saveUser = mockStatic(SaveUserCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      roleRepo.when(() -> RoleRepository.findById(3)).thenReturn(grantedRole);
      roleRepo.when(RoleRepository::findAll).thenReturn(Arrays.asList(adminRole, existingRole, grantedRole));
      loadCmd.when(() -> LoadUserCommand.loadUser(5L)).thenReturn(target);
      saveUser.when(() -> SaveUserCommand.saveUser(target)).thenReturn(target);

      WidgetContext result = new UsersListWidget().post(widgetContext);

      // The target's original role is still present, plus the newly granted one -- nothing was
      // replaced or stripped, only added.
      assertEquals(2, target.getRoleList().size());
      assertTrue(target.getRoleList().contains(existingRole));
      assertTrue(target.getRoleList().contains(grantedRole));
      saveUser.verify(() -> SaveUserCommand.saveUser(target), times(1));
      assertTrue(result.getSuccessMessage().contains("1 of 1"));
    }
  }

  @Test
  void bulkAssignRolesTreatsAnAccountThatAlreadyHasTheRoleAsANoOpNotAFailure() throws Exception {
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    multiValue("userId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkAssignRoles");
    addQueryParameter(widgetContext, "roleId", "3");

    Role adminRole = role(1, 100, ADMIN);
    Role grantedRole = role(3, 93, "data-manager");

    User alreadyHasIt = userWithId(5L);
    List<Role> alreadyHasItRoles = new ArrayList<>();
    alreadyHasItRoles.add(grantedRole);
    alreadyHasIt.setRoleList(alreadyHasItRoles);

    User needsIt = userWithId(6L);
    needsIt.setRoleList(new ArrayList<>());

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<SaveUserCommand> saveUser = mockStatic(SaveUserCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      roleRepo.when(() -> RoleRepository.findById(3)).thenReturn(grantedRole);
      roleRepo.when(RoleRepository::findAll).thenReturn(Arrays.asList(adminRole, grantedRole));
      loadCmd.when(() -> LoadUserCommand.loadUser(5L)).thenReturn(alreadyHasIt);
      loadCmd.when(() -> LoadUserCommand.loadUser(6L)).thenReturn(needsIt);
      saveUser.when(() -> SaveUserCommand.saveUser(needsIt)).thenReturn(needsIt);

      WidgetContext result = new UsersListWidget().post(widgetContext);

      // Only the account that didn't already have the role is saved
      saveUser.verify(() -> SaveUserCommand.saveUser(any()), times(1));
      saveUser.verify(() -> SaveUserCommand.saveUser(needsIt), times(1));
      assertTrue(result.getSuccessMessage().contains("1 of 2"));
      assertTrue(result.getSuccessMessage().contains("Already had it: 1"));
    }
  }

  @Test
  void bulkAssignRolesRejectsAnUnresolvableRoleWithNoRepositoryCalls() throws Exception {
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    multiValue("userId", "5");
    addQueryParameter(widgetContext, "command", "bulkAssignRoles");
    addQueryParameter(widgetContext, "roleId", "999");

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class)) {
      roleRepo.when(() -> RoleRepository.findById(999)).thenReturn(null);

      WidgetContext result = new UsersListWidget().post(widgetContext);

      loadCmd.verifyNoInteractions();
      assertEquals("The selected role was not found", result.getErrorMessage());
    }
  }

  @Test
  void bulkResetPasswordWithValidStepUpTriggersAResetForEachResolvedAccount() throws Exception {
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    multiValue("userId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkResetPassword");

    User first = userWithId(5L);
    User second = userWithId(6L);

    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(5L)).thenReturn(first);
      loadCmd.when(() -> LoadUserCommand.loadUser(6L)).thenReturn(second);
      userRepo.when(() -> UserRepository.createAccountToken(first)).thenReturn(first);
      userRepo.when(() -> UserRepository.createAccountToken(second)).thenReturn(second);

      WidgetContext result = new UsersListWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.createAccountToken(first), times(1));
      userRepo.verify(() -> UserRepository.createAccountToken(second), times(1));
      workflow.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), times(2));
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("user_management"), eq("user.password.reset"),
          eq("success"), anyLong(), any(), any(), any(), eq("user"), any(), any(), any()), times(2));
      // Plus one summary event for the batch
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("user_management"), eq("user.bulk_password_reset"),
          eq("success"), anyLong(), any(), any(), any(), eq("user"), isNull(), isNull(), any()), times(1));
      assertTrue(result.getSuccessMessage().contains("2 of 2"));
    }
  }

  @Test
  void emptySelectionIsRejectedWithNoRepositoryCalls() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "bulkSuspend");
    addQueryParameter(widgetContext, "reason", "n/a");
    // No userId parameters at all

    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class)) {
      WidgetContext result = new UsersListWidget().post(widgetContext);

      loadCmd.verifyNoInteractions();
      userRepo.verify(() -> UserRepository.suspendAccount(any(), any()), never());
      assertEquals("No accounts were selected", result.getErrorMessage());
    }
  }

  @Test
  void nonAdminNonCommunityManagerCannotReachBulkActionsAtAll() throws Exception {
    setRoles(widgetContext, CONTENT_MANAGER);
    multiValue("userId", "5");
    addQueryParameter(widgetContext, "command", "bulkSuspend");
    addQueryParameter(widgetContext, "reason", "n/a");

    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class)) {
      new UsersListWidget().post(widgetContext);

      loadCmd.verifyNoInteractions();
      userRepo.verify(() -> UserRepository.suspendAccount(any(), any()), never());
    }
  }

  // --- users:manage (issue #733 follow-up): the same permission gate this class already covers
  // for hasRole(), now also reachable via a direct capability grant with no legacy role at all ---

  @Test
  void capabilityOnlyUserWithUsersManageCanExecuteABulkAction() throws Exception {
    // No role set at all -- WidgetBase's default session already has an empty role list; the
    // only thing granting access here is the users:manage capability (issue #733 follow-up).
    Capability usersManage = new Capability();
    usersManage.setCode("users:manage");
    widgetContext.getUserSession().setCapabilityList(List.of(usersManage));
    multiValue("userId", "5");
    addQueryParameter(widgetContext, "command", "bulkSuspend");
    addQueryParameter(widgetContext, "reason", "capability-only access check");

    User other = userWithId(5L);

    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(5L)).thenReturn(other);
      userRepo.when(() -> UserRepository.suspendAccount(other, "capability-only access check")).thenReturn(other);

      WidgetContext result = new UsersListWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.suspendAccount(other, "capability-only access check"), times(1));
      assertTrue(result.getSuccessMessage().contains("1 of 1"));
    }
  }

  @Test
  void userWithNeitherRoleNorUsersManageCapabilityCannotReachBulkActionsAtAll() throws Exception {
    // WidgetBase's default session has neither a role nor a capability list populated
    multiValue("userId", "5");
    addQueryParameter(widgetContext, "command", "bulkSuspend");
    addQueryParameter(widgetContext, "reason", "n/a");

    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class)) {
      new UsersListWidget().post(widgetContext);

      loadCmd.verifyNoInteractions();
      userRepo.verify(() -> UserRepository.suspendAccount(any(), any()), never());
    }
  }
}
