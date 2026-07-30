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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * A durable account lockout (#295, AC-7) auto-expires, but an administrator can also clear it early from the
 * user-details page. These verify that the "Unlock Account" action clears the failed-attempt/lockout state
 * through the repository and writes an audit record, so the manual recovery path is both effective and traceable,
 * and that an unrecognized action is a no-op that touches neither.
 *
 * suspendAccountViaPostCallsRepositoryAndAudits / deleteAccountViaPostCallsRepositoryAndAudits /
 * restoreAndUnlockAlsoDispatchThroughPost guard a real regression: the user-details menu submits these actions
 * via a real HTTP POST (issue #358 moved state-changing admin actions off GET query strings), so
 * WebContainerContext routes the request to post(), not action() -- action()'s dispatch table was correct but
 * unreachable, and post() never gained matching branches, so suspend/restore/delete/unlock silently no-opped
 * (redirect back to the same page, no error, no repository call). These tests call post() directly, the same
 * method a real request now reaches, so they fail if that dispatch gap reopens.
 *
 * communityManagerCannotSuspendAccountThatOutranksThem / communityManagerCannotRestoreAccountThatOutranksThem
 * guard a separate, pre-existing gap (predates issue #358, unrelated to it): suspendAccount()/restoreAccount()
 * only ever checked "is this my own account" -- neither checked the target's role level against the acting
 * admin's, even though both /admin/users and /admin/user-details are reachable by community-manager (level 90,
 * admin-layout.xml), one level below admin (level 100). Without this guard a community-manager could suspend or
 * restore an admin account outright. Mirrors the escalation guard UserFormWidget already applies to role grants
 * (see UserFormWidgetTest).
 *
 * @author Elizabeth Houser
 */
class UserDetailsWidgetTest extends WidgetBase {

  private static Role role(int id, int level, String code, String title) {
    Role role = new Role(title, code);
    role.setId(id);
    role.setLevel(level);
    return role;
  }

  private static List<Role> allRoles() {
    List<Role> roles = new ArrayList<>();
    roles.add(role(1, 70, "content-editor", "Content Editor"));
    roles.add(role(2, 80, "content-manager", "Content Manager"));
    roles.add(role(3, 90, "community-manager", "Community Manager"));
    roles.add(role(4, 100, "admin", "System Administrator"));
    return roles;
  }

  private static User lockedUser() {
    User user = new User();
    user.setId(5L);
    user.setEmail("locked@example.com");
    user.setFailedAttemptCount(5);
    user.setLockedUntil(new Timestamp(System.currentTimeMillis() + 15 * 60_000L));
    return user;
  }

  private static User activeUser() {
    User user = new User();
    user.setId(5L);
    user.setEmail("active@example.com");
    user.setEnabled(true);
    return user;
  }

  private static User adminUser() {
    User user = activeUser();
    List<Role> held = new ArrayList<>();
    held.add(role(4, 100, "admin", "System Administrator"));
    user.setRoleList(held);
    return user;
  }

  @Test
  void unlockAccountClearsLockoutAndAudits() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "unlockAccount");

    User target = lockedUser();
    Assertions.assertTrue(target.isLocked(), "precondition: the target account is locked");

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);

      new UserDetailsWidget().action(widgetContext);

      // The lockout is cleared through the repository for exactly this user
      userRepo.verify(() -> UserRepository.resetLockout(5L), times(1));
      // ...and the administrative unlock is recorded in the audit log as a success
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT), eq("user.unlock"),
          eq(AuditEventCommand.SUCCESS), eq("user"), eq("5"), eq("locked@example.com"), any()), times(1));
    }
  }

  @Test
  void unknownActionDoesNotUnlock() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "somethingElse");

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(lockedUser());

      new UserDetailsWidget().action(widgetContext);

      userRepo.verify(() -> UserRepository.resetLockout(anyLong()), never());
      audit.verifyNoInteractions();
    }
  }

  @Test
  void actionResetPasswordIsNotHandledByTheGetActionPath() throws Exception {
    // Password reset requires step-up re-authentication (see post()). The GET/action() path must
    // never reset a password directly -- that would bypass step-up entirely, reachable via a plain
    // GET request carrying the same parameters the old pre-step-up UI link used to build.
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "resetPassword");

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(lockedUser());

      new UserDetailsWidget().action(widgetContext);

      userRepo.verify(() -> UserRepository.createAccountToken(any()), never());
      audit.verifyNoInteractions();
    }
  }

  @Test
  void suspendAccountViaPostCallsRepositoryAndAudits() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "suspendAccount");

    // An admin (level 100) acting on another admin (level 100): equal level is not "outranks" and
    // must still be permitted.
    User target = adminUser();

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      userRepo.when(() -> UserRepository.suspendAccount(target)).thenReturn(target);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.suspendAccount(target), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT), eq("user.disable"),
          eq(AuditEventCommand.SUCCESS), eq("user"), eq("5"), eq("active@example.com"), any()), times(1));
      Assertions.assertEquals("Account suspended", result.getSuccessMessage());
    }
  }

  @Test
  void restoreAccountViaPostCallsRepositoryAndAudits() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "restoreAccount");

    User target = adminUser();
    target.setEnabled(false);

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      userRepo.when(() -> UserRepository.restoreAccount(target)).thenReturn(target);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.restoreAccount(target), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT), eq("user.enable"),
          eq(AuditEventCommand.SUCCESS), eq("user"), eq("5"), eq("active@example.com"), any()), times(1));
      Assertions.assertEquals("Account restored", result.getSuccessMessage());
    }
  }

  @Test
  void communityManagerCannotSuspendAccountThatOutranksThem() throws Exception {
    setRoles(widgetContext, COMMUNITY_MANAGER);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "suspendAccount");

    // The target holds admin (level 100), above the acting community-manager (level 90).
    User target = adminUser();

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.suspendAccount(any()), never());
      audit.verifyNoInteractions();
      Assertions.assertEquals("You cannot suspend an account with a higher role level than your own",
          result.getErrorMessage());
    }
  }

  @Test
  void communityManagerCannotRestoreAccountThatOutranksThem() throws Exception {
    setRoles(widgetContext, COMMUNITY_MANAGER);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "restoreAccount");

    // The target holds admin (level 100), above the acting community-manager (level 90).
    User target = adminUser();
    target.setEnabled(false);

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.restoreAccount(any()), never());
      audit.verifyNoInteractions();
      Assertions.assertEquals("You cannot restore an account with a higher role level than your own",
          result.getErrorMessage());
    }
  }

  @Test
  void communityManagerCanSuspendAccountAtOrBelowTheirOwnLevel() throws Exception {
    setRoles(widgetContext, COMMUNITY_MANAGER);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "suspendAccount");

    // The target holds community-manager (level 90), at the acting user's own level -- not "outranks".
    User target = activeUser();
    List<Role> held = new ArrayList<>();
    held.add(role(3, 90, "community-manager", "Community Manager"));
    target.setRoleList(held);

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      userRepo.when(() -> UserRepository.suspendAccount(target)).thenReturn(target);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.suspendAccount(target), times(1));
      Assertions.assertEquals("Account suspended", result.getSuccessMessage());
    }
  }

  @Test
  void deleteAccountViaPostCallsRepositoryAndAudits() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "deleteAccount");

    User target = activeUser();

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      userRepo.when(() -> UserRepository.remove(target)).thenReturn(true);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.remove(target), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT), eq("user.delete"),
          eq(AuditEventCommand.SUCCESS), eq("user"), eq("5"), eq("active@example.com"), any()), times(1));
      Assertions.assertEquals("Account deleted", result.getSuccessMessage());
    }
  }

  @Test
  void unlockAccountViaPostDispatchesThroughAction() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "unlockAccount");

    User target = lockedUser();

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.resetLockout(5L), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT), eq("user.unlock"),
          eq(AuditEventCommand.SUCCESS), eq("user"), eq("5"), eq("locked@example.com"), any()), times(1));
      Assertions.assertEquals("Account unlocked", result.getSuccessMessage());
    }
  }

  @Test
  void suspendAccountViaPostRefusesToSuspendSelf() throws Exception {
    // The self-suspend guard lives in the shared handler that post() now correctly reaches --
    // confirm the fix didn't bypass it.
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "1"); // the logged-in test user's own id, see WidgetBase#login
    addQueryParameter(widgetContext, "action", "suspendAccount");

    User self = new User();
    self.setId(1L);
    self.setEmail("self@example.com");
    self.setEnabled(true);

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(self);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.suspendAccount(any()), never());
      audit.verifyNoInteractions();
      Assertions.assertEquals("You cannot suspend your own account", result.getErrorMessage());
    }
  }

  @Test
  void deleteAccountViaPostRefusesToDeleteSelf() throws Exception {
    // The self-delete guard lives in the shared handler that post() now correctly reaches --
    // confirm the fix didn't bypass it.
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "1"); // the logged-in test user's own id, see WidgetBase#login
    addQueryParameter(widgetContext, "action", "deleteAccount");

    User self = new User();
    self.setId(1L);
    self.setEmail("self@example.com");
    self.setEnabled(true);

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(self);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.remove(any()), never());
      audit.verifyNoInteractions();
      Assertions.assertEquals("You cannot delete your own account", result.getErrorMessage());
    }
  }

  @Test
  void restoreAndUnlockAlsoDispatchThroughPost() throws Exception {
    // restoreAccount and unlockAccount are submitted by the identical postAction() JS helper as
    // suspend/delete above, so they had the identical dispatch gap -- covering both here.
    User target = activeUser();
    target.setEnabled(false);

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      userRepo.when(() -> UserRepository.restoreAccount(target)).thenReturn(target);

      setRoles(widgetContext, ADMIN);
      addQueryParameter(widgetContext, "userId", "5");
      addQueryParameter(widgetContext, "action", "restoreAccount");
      new UserDetailsWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.restoreAccount(target), times(1));
    }

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);

      setRoles(widgetContext, ADMIN);
      addQueryParameter(widgetContext, "userId", "5");
      addQueryParameter(widgetContext, "action", "unlockAccount");
      new UserDetailsWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.resetLockout(5L), times(1));
    }
  }
}
