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

import javax.security.auth.login.AccountException;

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

  private static List<Role> rolesWithLevel(int level, String... codes) {
    List<Role> list = new ArrayList<>();
    for (String code : codes) {
      Role role = new Role(code, code); // Role(title, code)
      role.setLevel(level);
      list.add(role);
    }
    return list;
  }

  /** An existing stored account at a given role level, whose username is in sync with its email. */
  private static User rankedUser(long id, int level, String roleCode, String email) {
    User user = new User();
    user.setId(id);
    user.setRoleList(rolesWithLevel(level, roleCode));
    user.setEmail(email);
    user.setUsername(email);
    return user;
  }

  /** A well-formed edit of an existing ranked account, submitting the given identity fields. */
  private static User identityEditBean(long targetId, long editorId, int targetLevel, String targetRoleCode,
      String email, String username) {
    User bean = new User();
    bean.setId(targetId);
    bean.setModifiedBy(editorId);
    bean.setFirstName("Test");
    bean.setLastName("User");
    bean.setEmail(email);
    bean.setUsername(username);
    bean.setRoleList(rolesWithLevel(targetLevel, targetRoleCode));
    bean.setGroupList(new ArrayList<>());
    return bean;
  }

  /** As runSaveUser, but through the provider-managed entry point (isSystemUser = true). */
  private static User runSaveUserAsSystem(User existing, User bean) throws Exception {
    try (MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
         MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
         MockedStatic<GenerateUserUniqueIdCommand> genId = mockStatic(GenerateUserUniqueIdCommand.class)) {
      loadUser.when(() -> LoadUserCommand.loadUser(bean.getId())).thenReturn(existing);
      genId.when(() -> GenerateUserUniqueIdCommand.generateUniqueId(any(), any())).thenReturn("uniqueid");
      userRepo.when(() -> UserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      return SaveUserCommand.saveUser(bean, true);
    }
  }

  @Test
  void lowerRankedEditorCannotChangeTheEmailOfAnAccountThatOutranksThem() {
    // Repointing an admin's email is a complete account takeover on its own: /forgot-password is a
    // public page that mails the reset link to whatever address the account carries, so no
    // admin-side reset guard is involved in the chain at all.
    User editor = rankedUser(EDITOR_ID, 90, "community-manager", "cm@example.com");
    User existing = rankedUser(TARGET_ID, 100, "admin", "admin@example.com");
    User bean = identityEditBean(TARGET_ID, EDITOR_ID, 100, "admin", "attacker@example.com", "admin@example.com");

    DataException e = Assertions.assertThrows(DataException.class, () -> runSaveUser(editor, existing, bean));
    Assertions.assertEquals(
        "You cannot change the email address of an account with a higher role level than your own",
        e.getMessage());
  }

  @Test
  void lowerRankedEditorCannotChangeTheUsernameOfAccountThatOutranksThem() {
    // Separate from the email case: username is what AuthenticateLoginCommand resolves a sign-in
    // against, so changing it alone locks the admin out of the identifier they know.
    User editor = rankedUser(EDITOR_ID, 90, "community-manager", "cm@example.com");
    User existing = rankedUser(TARGET_ID, 100, "admin", "admin@example.com");
    User bean = identityEditBean(TARGET_ID, EDITOR_ID, 100, "admin", "admin@example.com", "attacker");

    DataException e = Assertions.assertThrows(DataException.class, () -> runSaveUser(editor, existing, bean));
    Assertions.assertEquals(
        "You cannot change the username of an account with a higher role level than your own",
        e.getMessage());
  }

  @Test
  void lowerRankedEditorCanStillEditOtherFieldsOfAnAccountThatOutranksThem() throws Exception {
    // The guard is scoped to a *change* of email/username, not to the record. A community-manager
    // correcting a typo in an admin's name re-submits the stored identity unchanged and must still
    // succeed -- a blanket refusal would have broken this.
    User editor = rankedUser(EDITOR_ID, 90, "community-manager", "cm@example.com");
    User existing = rankedUser(TARGET_ID, 100, "admin", "admin@example.com");
    User bean = identityEditBean(TARGET_ID, EDITOR_ID, 100, "admin", "admin@example.com", "admin@example.com");
    bean.setFirstName("Corrected");

    User saved = runSaveUser(editor, existing, bean);

    Assertions.assertEquals("Corrected", saved.getFirstName());
    Assertions.assertEquals("admin@example.com", saved.getEmail());
  }

  @Test
  void aCaseOnlyDifferenceInTheEmailIsNotAnIdentityChange() throws Exception {
    // The comparison is deliberately case-insensitive, because the platform already resolves
    // identity that way: UserRepository.findByUsername matches on LOWER(username) and
    // findByEmailAddress on LOWER(email). Re-casing an address therefore moves neither who can sign
    // in nor where a reset link is delivered, so refusing it would raise a security error against an
    // edit that changes nothing. This pins that -- without it, "hardening" the comparison to be
    // case-sensitive would look like a safe tightening and break a legitimate normalisation.
    User editor = rankedUser(EDITOR_ID, 90, "community-manager", "cm@example.com");
    User existing = rankedUser(TARGET_ID, 100, "admin", "admin@example.com");
    User bean = identityEditBean(TARGET_ID, EDITOR_ID, 100, "admin", "Admin@Example.com", "Admin@Example.com");

    User saved = runSaveUser(editor, existing, bean);

    Assertions.assertEquals("Admin@Example.com", saved.getEmail());
  }

  @Test
  void anEmailThatDiffersBeyondCaseIsStillRefused() throws Exception {
    // The guard against the guard above: loosening a comparison is exactly the kind of change that
    // quietly goes too far. A different address in mixed case is a real identity change and must
    // stay refused -- only the casing of the same address is exempt.
    User editor = rankedUser(EDITOR_ID, 90, "community-manager", "cm@example.com");
    User existing = rankedUser(TARGET_ID, 100, "admin", "admin@example.com");
    User bean = identityEditBean(TARGET_ID, EDITOR_ID, 100, "admin", "Admin@Example.NET", "Admin@Example.NET");

    DataException e = Assertions.assertThrows(DataException.class, () -> runSaveUser(editor, existing, bean));
    Assertions.assertEquals(
        "You cannot change the email address of an account with a higher role level than your own",
        e.getMessage());
  }

  @Test
  void editorAtTheSameLevelCanChangeTheIdentityFields() throws Exception {
    // "Outranks" is strictly greater, matching targetOutranksActor(): one admin may still edit
    // another admin's email, which is how a legitimate address change gets made at all.
    User editor = rankedUser(EDITOR_ID, 100, "admin", "admin1@example.com");
    User existing = rankedUser(TARGET_ID, 100, "admin", "admin2@example.com");
    User bean = identityEditBean(TARGET_ID, EDITOR_ID, 100, "admin", "newaddress@example.com", "newaddress@example.com");

    User saved = runSaveUser(editor, existing, bean);

    Assertions.assertEquals("newaddress@example.com", saved.getEmail());
  }

  @Test
  void providerManagedSaveIsNotSubjectToTheIdentityGuard() throws Exception {
    // CSV import and OAuth provisioning save with isSystemUser = true and no acting user, and
    // legitimately rewrite an address from the provider. The guard must not break those.
    User existing = rankedUser(TARGET_ID, 100, "admin", "admin@example.com");
    User bean = identityEditBean(TARGET_ID, EDITOR_ID, 100, "admin", "moved@example.com", "moved@example.com");

    User saved = runSaveUserAsSystem(existing, bean);

    Assertions.assertEquals("moved@example.com", saved.getEmail());
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

  @Test
  void editingUserEmailToOneUsedByADifferentAccountIsRejected() {
    // Editing user (TARGET_ID) attempts to change their email to one already
    // registered to a different account (id 99).
    // Note: the editing user's username is deliberately different from the
    // email being claimed, so a check that queries the username column
    // instead of the email column would miss this collision entirely.
    User editor = userWithRoles(EDITOR_ID, "admin");
    User existing = userWithRoles(TARGET_ID, "users");
    User bean = editBeanRequesting(TARGET_ID, EDITOR_ID, "users");
    bean.setUsername("editing-user");
    bean.setEmail("taken@example.com");

    User otherAccountWithEmail = userWithRoles(99L, "users");
    otherAccountWithEmail.setUsername("other-user");
    otherAccountWithEmail.setEmail("taken@example.com");

    try (MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
         MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
         MockedStatic<GenerateUserUniqueIdCommand> genId = mockStatic(GenerateUserUniqueIdCommand.class)) {
      loadUser.when(() -> LoadUserCommand.loadUser(bean.getModifiedBy())).thenReturn(editor);
      loadUser.when(() -> LoadUserCommand.loadUser(bean.getId())).thenReturn(existing);
      genId.when(() -> GenerateUserUniqueIdCommand.generateUniqueId(any(), any())).thenReturn("uniqueid");
      userRepo.when(() -> UserRepository.findByEmailAddress("taken@example.com")).thenReturn(otherAccountWithEmail);
      userRepo.when(() -> UserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      AccountException ex = Assertions.assertThrows(AccountException.class,
          () -> SaveUserCommand.saveUser(bean));
      Assertions.assertTrue(ex.getMessage().toLowerCase().contains("account with this email address already"),
          "expected a clear duplicate-email message, got: " + ex.getMessage());
    }
  }
}
