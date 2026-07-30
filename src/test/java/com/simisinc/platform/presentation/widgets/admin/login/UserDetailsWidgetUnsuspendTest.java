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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.login.UnsuspendAccountCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.login.UnsuspendRequestRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Wiring-level coverage for issue #492 Phase 3 on top of {@link UnsuspendAccountCommandTest}'s
 * pure logic coverage: proves the maker-checker gate is actually reached from the real HTTP
 * dispatch paths, and -- the specific regression this codebase has hit before with resetPassword
 * -- that approveUnsuspend can NEVER be reached through the plain GET/action() path, only through
 * post()'s step-up-gated branch.
 *
 * @author SimIS Inc.
 */
class UserDetailsWidgetUnsuspendTest extends WidgetBase {

  private static User userWithId(long id, boolean enabled) {
    User user = new User();
    user.setId(id);
    user.setEmail("user" + id + "@example.com");
    user.setEnabled(enabled);
    return user;
  }

  @Test
  void nonElevatedRestoreStillCallsUserRepositoryDirectly() throws Exception {
    // Regression guard: routing restoreAccount() through UnsuspendAccountCommand must not change
    // behavior for the common (non-elevated) case.
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "restoreAccount");

    User target = userWithId(5L, false);

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(5L)).thenReturn(target);
      loadCmd.when(() -> LoadUserCommand.loadUser(1L)).thenReturn(userWithId(1L, true));
      roleRepo.when(() -> RoleRepository.findByCode("community-manager"))
          .thenReturn(roleWithLevel(90));
      userRepo.when(() -> UserRepository.restoreAccount(target)).thenReturn(target);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.restoreAccount(target), times(1));
      assertTrue(result.getSuccessMessage().contains("restored"));
    }
  }

  @Test
  void elevatedRestoreFilesARequestInsteadOfCallingUserRepositoryDirectly() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "restoreAccount");
    addQueryParameter(widgetContext, "reason", "cleared by incident response");

    User target = userWithId(5L, false);
    target.setRoleList(java.util.Collections.singletonList(roleWithLevel(100)));

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<UnsuspendRequestRepository> requestRepo = mockStatic(UnsuspendRequestRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(5L)).thenReturn(target);
      loadCmd.when(() -> LoadUserCommand.loadUser(1L)).thenReturn(userWithId(1L, true));
      // UserDetailsWidget.restoreAccount() checks targetOutranksActor() BEFORE the maker-checker
      // logic below -- that check separately calls RoleRepository.findAll() (not findByCode) to
      // resolve the acting admin's own level via the session's "admin" role. Leaving this
      // unstubbed makes the acting level default to 0, so the elevated (level 100) target always
      // looks like it outranks the actor and the request is blocked before ever reaching
      // UnsuspendAccountCommand.
      com.simisinc.platform.domain.model.Role adminRole = new com.simisinc.platform.domain.model.Role();
      adminRole.setId(4);
      adminRole.setLevel(100);
      adminRole.setCode("admin");
      adminRole.setTitle("System Administrator");
      roleRepo.when(RoleRepository::findAll).thenReturn(java.util.Collections.singletonList(adminRole));
      roleRepo.when(() -> RoleRepository.findByCode("community-manager")).thenReturn(roleWithLevel(90));
      requestRepo.when(() -> UnsuspendRequestRepository.findPendingByTargetUserId(5L)).thenReturn(null);
      requestRepo.when(() -> UnsuspendRequestRepository.add(any())).thenAnswer(inv -> inv.getArgument(0));

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.restoreAccount(any()), never());
      requestRepo.verify(() -> UnsuspendRequestRepository.add(any()), times(1));
      assertTrue(result.getSuccessMessage().contains("second administrator"));
    }
  }

  @Test
  void approveUnsuspendIsNeverReachableThroughThePlainGetActionPath() throws Exception {
    // The exact regression shape this codebase has hit before (resetPassword): a sensitive,
    // step-up-gated action must not be reachable by calling action() directly.
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "approveUnsuspend");
    addQueryParameter(widgetContext, "requestId", "42");

    User target = userWithId(5L, false);

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UnsuspendRequestRepository> requestRepo = mockStatic(UnsuspendRequestRepository.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);

      new UserDetailsWidget().action(widgetContext);

      requestRepo.verify(() -> UnsuspendRequestRepository.findById(anyLong()), never());
    }
  }

  @Test
  void approveUnsuspendViaPostWithoutStepUpIsRejected() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "approveUnsuspend");
    addQueryParameter(widgetContext, "requestId", "42");
    // No stepUpCredential, and the session has no valid step-up token

    User target = userWithId(5L, false);

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UnsuspendRequestRepository> requestRepo = mockStatic(UnsuspendRequestRepository.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(5L)).thenReturn(target);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      requestRepo.verify(() -> UnsuspendRequestRepository.findById(anyLong()), never());
      assertTrue("true".equals(result.getSharedRequestValue("stepUpRequired")));
    }
  }

  @Test
  void denyUnsuspendIsDispatchedThroughTheGetActionPathWithNoStepUp() throws Exception {
    // Unlike approve, deny grants nothing -- it's dispatched through action() like
    // suspendAccount/deleteAccount/unlockAccount, matching the ContentHtmlCommand precedent.
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "denyUnsuspend");
    addQueryParameter(widgetContext, "requestId", "42");
    addQueryParameter(widgetContext, "denialReason", "not convinced");

    User target = userWithId(5L, false);
    // The logged-in test user's own id is 1L (see WidgetBase#login) -- the request's requester
    // must be someone else (9L) to satisfy the separation-of-duties check.
    com.simisinc.platform.domain.model.login.UnsuspendRequest request =
        new com.simisinc.platform.domain.model.login.UnsuspendRequest();
    request.setId(42L);
    request.setTargetUserId(5L);
    request.setTargetEmail(target.getEmail());
    request.setRequestedBy(9L);

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UnsuspendRequestRepository> requestRepo = mockStatic(UnsuspendRequestRepository.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(5L)).thenReturn(target);
      loadCmd.when(() -> LoadUserCommand.loadUser(1L)).thenReturn(userWithId(1L, true));
      requestRepo.when(() -> UnsuspendRequestRepository.findById(42L)).thenReturn(request);
      requestRepo.when(() -> UnsuspendRequestRepository.claimForDenial(42L, 1L, "user1@example.com", "not convinced"))
          .thenReturn(true);

      WidgetContext result = new UserDetailsWidget().action(widgetContext);

      requestRepo.verify(
          () -> UnsuspendRequestRepository.claimForDenial(42L, 1L, "user1@example.com", "not convinced"), times(1));
      assertTrue(result.getSuccessMessage().contains("denied"));
    }
  }

  private static com.simisinc.platform.domain.model.Role roleWithLevel(int level) {
    com.simisinc.platform.domain.model.Role role = new com.simisinc.platform.domain.model.Role();
    role.setId(1);
    role.setLevel(level);
    role.setCode(level >= 90 ? "community-manager" : "content-editor");
    role.setTitle("role");
    return role;
  }
}
