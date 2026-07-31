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
import java.util.List;

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
import com.simisinc.platform.presentation.controller.AuditEventCommand;

/**
 * The user-edit form is reachable by both admin and community-manager (admin-layout.xml). Without a
 * check, the role checkboxes let a lower-privileged editor grant themselves or anyone else a higher
 * role -- e.g. a community-manager (level 90) granting admin (level 100), a full privilege escalation.
 * Most of these verify the editor can only set roles at or below their own level, admins are
 * unaffected, and a higher role the target already holds is neither grantable nor strippable by a
 * lower editor. One covers a separate regression: execute() with no userId (the New User form) must
 * not pass LoadUserCommand's not-found null straight to the JSP.
 *
 * @author Elizabeth Houser
 */
class UserFormWidgetTest extends WidgetBase {

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

  private static User savedUser() {
    User user = new User();
    user.setId(5L);
    user.setEmail("saved@example.com");
    return user;
  }

  @Test
  void executeWithoutUserIdBuildsBlankUserForNewUserForm() {
    // GET /admin/modify-user with no userId (and no requestObject from a prior post()) is the New
    // User form. LoadUserCommand.loadUser(-1) intentionally returns null for a not-found id -- the
    // widget must not pass that null straight to the JSP.
    setRoles(widgetContext, ADMIN);
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());

      Assertions.assertDoesNotThrow(() -> new UserFormWidget().execute(widgetContext));

      User user = (User) widgetContext.getRequest().getAttribute("user");
      Assertions.assertNotNull(user, "a blank User must be set so the New User form can render");
      Assertions.assertEquals(-1L, user.getId().longValue(), "a blank User must keep the 'new record' id sentinel");
    }
  }

  @Test
  void postWithoutStepUpShowsReAuthPanel() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "id", "-1");
    addQueryParameter(widgetContext, "roleId4", "4");
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      new UserFormWidget().post(widgetContext);
    }
    Assertions.assertEquals("true", widgetContext.getSharedRequestValue("stepUpRequired"));
    Assertions.assertNull(widgetContext.getRedirect());
  }

  @Test
  void communityManagerCannotGrantAdminButKeepsAllowedRoles() throws Exception {
    setRoles(widgetContext, COMMUNITY_MANAGER);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "id", "-1");        // create
    addQueryParameter(widgetContext, "roleId1", "1");    // content-editor (70) -- allowed
    addQueryParameter(widgetContext, "roleId4", "4");    // admin (100) -- must be refused

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      saveCmd.when(() -> SaveUserCommand.saveUser(any())).thenReturn(savedUser());

      new UserFormWidget().post(widgetContext);

      saveCmd.verify(() -> SaveUserCommand.saveUser(captor.capture()));
      List<String> codes = captor.getValue().getRoleList().stream().map(Role::getCode).toList();
      Assertions.assertTrue(codes.contains("content-editor"), "a role at/below the editor's level must be granted");
      Assertions.assertFalse(codes.contains("admin"), "a community-manager must NOT be able to grant admin");
    }
  }

  @Test
  void adminCanGrantAdmin() throws Exception {
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "id", "-1");
    addQueryParameter(widgetContext, "roleId4", "4");    // admin

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      saveCmd.when(() -> SaveUserCommand.saveUser(any())).thenReturn(savedUser());

      new UserFormWidget().post(widgetContext);

      saveCmd.verify(() -> SaveUserCommand.saveUser(captor.capture()));
      List<String> codes = captor.getValue().getRoleList().stream().map(Role::getCode).toList();
      Assertions.assertTrue(codes.contains("admin"), "an admin must still be able to grant admin");
    }
  }

  @Test
  void communityManagerCannotStripHigherRoleTheTargetAlreadyHolds() throws Exception {
    setRoles(widgetContext, COMMUNITY_MANAGER);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "id", "5");         // editing an existing user
    addQueryParameter(widgetContext, "roleId1", "1");    // submits content-editor; admin left unchecked

    User target = new User();
    target.setId(5L);
    List<Role> held = new ArrayList<>();
    held.add(role(4, 100, "admin", "System Administrator"));
    target.setRoleList(held);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      saveCmd.when(() -> SaveUserCommand.saveUser(any())).thenReturn(savedUser());

      new UserFormWidget().post(widgetContext);

      saveCmd.verify(() -> SaveUserCommand.saveUser(captor.capture()));
      List<String> codes = captor.getValue().getRoleList().stream().map(Role::getCode).toList();
      Assertions.assertTrue(codes.contains("admin"), "a higher role the target already holds must be preserved, not stripped");
      Assertions.assertTrue(codes.contains("content-editor"));
    }
  }
}
