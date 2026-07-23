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

package com.simisinc.platform.application.register;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;

/**
 * Exercises the privilege-assignment guard in SaveUserCommand.saveUser: who may grant or
 * remove the Admin role. This is a security-critical authorization path that had no test
 * coverage. The cases below pin down that a non-admin editor can neither grant nor strip
 * Admin, that an admin can do both (except removing it from their own account), and that
 * the "maintain Admin" path keeps the real role rather than corrupting the role list.
 *
 * @author Liz Houser
 * @created 7/23/2026
 */
class SaveUserCommandTest {

  private static final long EDITOR_ID = 2L;
  private static final long TARGET_ID = 5L;

  private static List<Role> roles(String... codes) {
    List<Role> list = new ArrayList<>();
    for (String code : codes) {
      list.add(new Role(code, code)); // Role(title, code)
    }
    return list;
  }

  private static User userWithRoles(long id, String... roleCodes) {
    User user = new User();
    user.setId(id);
    user.setRoleList(roles(roleCodes));
    return user;
  }

  /** A well-formed edit of an existing user (id > -1) requesting the given roles. */
  private static User editBeanRequesting(long targetId, long editorId, String... requestedRoleCodes) {
    User bean = new User();
    bean.setId(targetId);
    bean.setModifiedBy(editorId);
    bean.setFirstName("Test");
    bean.setLastName("User");
    bean.setEmail("test@example.com");
    bean.setUsername("test@example.com");
    bean.setRoleList(roles(requestedRoleCodes));
    bean.setGroupList(new ArrayList<>());
    return bean;
  }

  /** Runs saveUser with the collaborators stubbed; UserRepository.save echoes its argument. */
  private static User runSaveUser(User editor, User existing, User bean) throws Exception {
    try (MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
         MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
         MockedStatic<GenerateUserUniqueIdCommand> genId = mockStatic(GenerateUserUniqueIdCommand.class)) {
      loadUser.when(() -> LoadUserCommand.loadUser(bean.getModifiedBy())).thenReturn(editor);
      loadUser.when(() -> LoadUserCommand.loadUser(bean.getId())).thenReturn(existing);
      genId.when(() -> GenerateUserUniqueIdCommand.generateUniqueId(any(), any())).thenReturn("uniqueid");
      userRepo.when(() -> UserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      return SaveUserCommand.saveUser(bean);
    }
  }

  @Test
  void nonAdminCannotGrantAdminRole() throws Exception {
    User editor = userWithRoles(EDITOR_ID, "users");
    User existing = userWithRoles(TARGET_ID, "users");
    User bean = editBeanRequesting(TARGET_ID, EDITOR_ID, "users", "admin"); // trying to escalate

    User saved = runSaveUser(editor, existing, bean);

    Assertions.assertFalse(saved.hasRole("admin"), "a non-admin must not be able to grant Admin");
    Assertions.assertTrue(saved.hasRole("users"));
  }

  @Test
  void nonAdminCannotRemoveAdminFromAnotherUser() throws Exception {
    User editor = userWithRoles(EDITOR_ID, "users");
    User existing = userWithRoles(TARGET_ID, "admin", "users"); // target currently has Admin
    User bean = editBeanRequesting(TARGET_ID, EDITOR_ID, "users"); // omits admin -> would remove it

    User saved = runSaveUser(editor, existing, bean);

    // The guard must MAINTAIN the existing Admin role -- with the real role object, not a
    // null placeholder (a null in the list corrupts every later hasRole/role iteration).
    Assertions.assertTrue(saved.hasRole("admin"), "a non-admin must not be able to remove Admin");
    Assertions.assertFalse(saved.getRoleList().contains(null), "the role list must not contain a null role");
  }

  @Test
  void adminCanGrantAdminRole() throws Exception {
    User editor = userWithRoles(EDITOR_ID, "admin");
    User existing = userWithRoles(TARGET_ID, "users");
    User bean = editBeanRequesting(TARGET_ID, EDITOR_ID, "users", "admin");

    User saved = runSaveUser(editor, existing, bean);

    Assertions.assertTrue(saved.hasRole("admin"), "an admin may grant Admin");
  }

  @Test
  void adminCanRemoveAdminFromAnotherUser() throws Exception {
    User editor = userWithRoles(EDITOR_ID, "admin");
    User existing = userWithRoles(TARGET_ID, "admin", "users");
    User bean = editBeanRequesting(TARGET_ID, EDITOR_ID, "users"); // admin de-escalates another user

    User saved = runSaveUser(editor, existing, bean);

    Assertions.assertFalse(saved.hasRole("admin"), "an admin may remove Admin from another user");
  }

  @Test
  void userCannotRemoveAdminFromOwnAccount() {
    // Self-edit: the editor and the target are the same admin account.
    User self = userWithRoles(EDITOR_ID, "admin");
    User bean = editBeanRequesting(EDITOR_ID, EDITOR_ID, "users"); // removing own Admin

    DataException ex = Assertions.assertThrows(DataException.class,
        () -> runSaveUser(self, self, bean));
    Assertions.assertTrue(ex.getMessage().toLowerCase().contains("admin role from your own account"));
  }
}
