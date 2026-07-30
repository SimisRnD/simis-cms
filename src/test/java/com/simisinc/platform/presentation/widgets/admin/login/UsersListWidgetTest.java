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
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.register.SaveUserCommand;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.AuditEventCommand;

/**
 * The New User form (the "reveal" modal on /admin/users) is reachable by both admin and
 * community-manager (admin-layout.xml), the same audience as the user-edit form. Unlike the edit
 * form, addUserAction() had no level check at all -- every checked role box was added to the new
 * account, so a community-manager (level 90) could create a user with Data Manager (93) or
 * E-commerce Manager (95), both above their own level. These verify addUserAction() now enforces
 * the same at-or-below-own-level rule UserFormWidget.post() already enforces when editing an
 * existing user (see UserFormWidget.highestRoleLevel(), reused here).
 *
 * @author Elizabeth Houser
 */
class UsersListWidgetTest extends WidgetBase {

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
    roles.add(role(4, 93, "data-manager", "Data Manager"));
    roles.add(role(5, 95, "ecommerce-manager", "E-commerce Manager"));
    roles.add(role(6, 100, "admin", "System Administrator"));
    return roles;
  }

  private static User savedUser() {
    User user = new User();
    user.setId(9L);
    user.setEmail("new-user@example.com");
    return user;
  }

  @Test
  void communityManagerCannotGrantRoleAboveOwnLevelViaNewUserForm() throws Exception {
    setRoles(widgetContext, COMMUNITY_MANAGER);
    addQueryParameter(widgetContext, "firstName", "New");
    addQueryParameter(widgetContext, "lastName", "User");
    addQueryParameter(widgetContext, "email", "new-user@example.com");
    addQueryParameter(widgetContext, "roleId1", "1"); // content-editor (70) -- allowed
    addQueryParameter(widgetContext, "roleId4", "4"); // data-manager (93) -- must be refused
    addQueryParameter(widgetContext, "roleId5", "5"); // ecommerce-manager (95) -- must be refused
    addQueryParameter(widgetContext, "roleId6", "6"); // admin (100) -- must be refused

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      saveCmd.when(() -> SaveUserCommand.saveUser(any())).thenReturn(savedUser());
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(savedUser());

      new UsersListWidget().post(widgetContext);

      saveCmd.verify(() -> SaveUserCommand.saveUser(captor.capture()));
      Set<String> codes = new HashSet<>(captor.getValue().getRoleList().stream().map(Role::getCode).toList());
      Assertions.assertEquals(Set.of("content-editor"), codes,
          "only the role at/below the editor's level may be granted -- everything above it must be silently dropped");
    }
  }

  @Test
  void communityManagerCanCreateUserWithRolesAtOrBelowOwnLevel() throws Exception {
    setRoles(widgetContext, COMMUNITY_MANAGER);
    addQueryParameter(widgetContext, "firstName", "New");
    addQueryParameter(widgetContext, "lastName", "User");
    addQueryParameter(widgetContext, "email", "new-user@example.com");
    addQueryParameter(widgetContext, "roleId1", "1"); // content-editor (70)
    addQueryParameter(widgetContext, "roleId3", "3"); // community-manager (90) -- editor's own level

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      saveCmd.when(() -> SaveUserCommand.saveUser(any())).thenReturn(savedUser());
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(savedUser());

      new UsersListWidget().post(widgetContext);

      saveCmd.verify(() -> SaveUserCommand.saveUser(captor.capture()));
      Set<String> codes = new HashSet<>(captor.getValue().getRoleList().stream().map(Role::getCode).toList());
      Assertions.assertEquals(Set.of("content-editor", "community-manager"), codes,
          "both requested roles are at/below the editor's own level and must both be granted, with nothing extra");
    }
  }

  @Test
  void adminCanGrantAdminViaNewUserForm() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "firstName", "New");
    addQueryParameter(widgetContext, "lastName", "Admin");
    addQueryParameter(widgetContext, "email", "new-admin@example.com");
    addQueryParameter(widgetContext, "roleId6", "6"); // admin

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      saveCmd.when(() -> SaveUserCommand.saveUser(any())).thenReturn(savedUser());
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(savedUser());

      new UsersListWidget().post(widgetContext);

      saveCmd.verify(() -> SaveUserCommand.saveUser(captor.capture()));
      Set<String> codes = new HashSet<>(captor.getValue().getRoleList().stream().map(Role::getCode).toList());
      Assertions.assertEquals(Set.of("admin"), codes, "an admin must still be able to grant admin to a new user");
    }
  }

  @Test
  void executeExposesActingRoleLevelToTheNewUserFormForCommunityManager() {
    setRoles(widgetContext, COMMUNITY_MANAGER);
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      userRepo.when(() -> UserRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      new UsersListWidget().execute(widgetContext);

      // users-list.jsp hides a role checkbox when role.level > actingRoleLevel -- a community-manager
      // (level 90) must see content-editor/content-manager/community-manager but not data-manager (93)
      // and above.
      Assertions.assertEquals(90, widgetContext.getRequest().getAttribute("actingRoleLevel"),
          "the New User form must only be able to offer roles at/below the community-manager's own level");
    }
  }

  @Test
  void executeExposesActingRoleLevelToTheNewUserFormForAdmin() {
    setRoles(widgetContext, ADMIN);
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      userRepo.when(() -> UserRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      new UsersListWidget().execute(widgetContext);

      Assertions.assertEquals(100, widgetContext.getRequest().getAttribute("actingRoleLevel"),
          "an admin must still be offered every role, including admin itself");
    }
  }

  @Test
  void unrecognizedActingRoleGrantsNothingViaNewUserForm() throws Exception {
    // A caller can pass post()'s hasRole("admin")||hasRole("community-manager") gate yet still fail
    // closed here if their session role code isn't in the authoritative RoleRepository.findAll() list
    // (e.g. a stale/renamed role) -- highestRoleLevel() then returns 0, and every real role has a
    // level above that, so nothing should be grantable. Model that by having RoleRepository.findAll()
    // no longer carry "community-manager" (renamed/removed), while the session's cached role claim
    // still says "community-manager" -- enough to pass post()'s own hasRole() gate, which checks the
    // session directly rather than the authoritative list.
    setRoles(widgetContext, COMMUNITY_MANAGER);
    List<Role> rolesWithoutCommunityManager = new ArrayList<>(allRoles());
    rolesWithoutCommunityManager.removeIf(r -> "community-manager".equals(r.getCode()));
    addQueryParameter(widgetContext, "firstName", "New");
    addQueryParameter(widgetContext, "lastName", "User");
    addQueryParameter(widgetContext, "email", "new-user@example.com");
    addQueryParameter(widgetContext, "roleId1", "1"); // content-editor (70) -- must still be refused

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(rolesWithoutCommunityManager);
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      saveCmd.when(() -> SaveUserCommand.saveUser(any())).thenReturn(savedUser());
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(savedUser());

      new UsersListWidget().post(widgetContext);

      saveCmd.verify(() -> SaveUserCommand.saveUser(captor.capture()));
      Assertions.assertTrue(captor.getValue().getRoleList().isEmpty(),
          "a session whose role code isn't in the authoritative role list must fail closed and grant nothing");
    }
  }
}
