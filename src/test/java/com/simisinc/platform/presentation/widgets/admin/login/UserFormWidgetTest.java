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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.register.SaveUserCommand;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

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

  /** An existing admin account (level 100) with a settled sign-in identity -- the target of the
   *  identity-field escalation tests below. */
  private static User adminTarget() {
    User user = new User();
    user.setId(5L);
    user.setEmail("admin@example.com");
    user.setUsername("admin@example.com");
    List<Role> held = new ArrayList<>();
    held.add(role(4, 100, "admin", "System Administrator"));
    user.setRoleList(held);
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
  void executeExposesActingRoleLevelForCommunityManager() {
    // user-form.jsp hides a role checkbox when role.level > actingRoleLevel (and not already held),
    // and renders it checked+disabled when it is already held -- mirroring users-list.jsp's New User
    // form. A community-manager (level 90) must not be offered admin (100).
    setRoles(widgetContext, COMMUNITY_MANAGER);
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());

      new UserFormWidget().execute(widgetContext);

      Assertions.assertEquals(90, widgetContext.getRequest().getAttribute("actingRoleLevel"),
          "the edit-user form must only be able to offer roles at/below the community-manager's own level");
    }
  }

  @Test
  void executeExposesActingRoleLevelForAdmin() {
    setRoles(widgetContext, ADMIN);
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());

      new UserFormWidget().execute(widgetContext);

      Assertions.assertEquals(100, widgetContext.getRequest().getAttribute("actingRoleLevel"),
          "an admin must still be offered every role, including admin itself");
    }
  }

  // --- break-glass toggle ---

  /** A saved user with the given roles and break-glass state, as SaveUserCommand would return it. */
  private User savedUser(long id, boolean breakGlass, String... roleCodes) {
    User user = new User();
    user.setId(id);
    user.setEmail("target@example.com");
    user.setBreakGlass(breakGlass);
    List<Role> roles = new ArrayList<>();
    for (String code : roleCodes) {
      roles.add(new Role(code, code));
    }
    user.setRoleList(roles);
    return user;
  }

  @Test
  void breakGlassIsSetOnAnAdminWhenTheToggleIsChecked() throws Exception {
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "id", "42");
    addQueryParameter(widgetContext, "breakGlassAccount", "true");
    User saved = savedUser(42L, false, ADMIN);

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      saveCmd.when(() -> SaveUserCommand.saveUser(any(User.class))).thenReturn(saved);
      userRepo.when(() -> UserRepository.updateBreakGlass(eq(saved), eq(true))).thenReturn(saved);

      new UserFormWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.updateBreakGlass(eq(saved), eq(true)));
    }
  }

  /**
   * The security case. The form does not render the toggle for a non-admin, but the parameter can
   * still be sent -- and BeanUtils.populate would happily set it on the bean. It must not reach the
   * database: break-glass exempts an account from the MFA enrollment redirect, and there is no
   * reason for a non-administrator to hold that exemption.
   */
  @Test
  void aNonAdminNeverGetsBreakGlassEvenWhenTheParameterIsSent() throws Exception {
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "id", "42");
    addQueryParameter(widgetContext, "breakGlassAccount", "true");
    User saved = savedUser(42L, false, CONTENT_MANAGER);

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      saveCmd.when(() -> SaveUserCommand.saveUser(any(User.class))).thenReturn(saved);

      new UserFormWidget().post(widgetContext);

      // Not verifyNoInteractions: the post path legitimately reaches UserRepository through
      // LoadUserCommand for the role-escalation guard. What must never happen is the write.
      userRepo.verify(() -> UserRepository.updateBreakGlass(any(User.class), anyBoolean()), never());
    }
  }

  @Test
  void clearingTheLastBreakGlassAccountWarnsButStillClearsIt() throws Exception {
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "id", "42");
    // no breakGlassAccount parameter: an unchecked checkbox submits nothing
    User saved = savedUser(42L, true, ADMIN);

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      saveCmd.when(() -> SaveUserCommand.saveUser(any(User.class))).thenReturn(saved);
      userRepo.when(UserRepository::countBreakGlassAccounts).thenReturn(1L);
      userRepo.when(() -> UserRepository.updateBreakGlass(eq(saved), eq(false))).thenReturn(saved);

      new UserFormWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.updateBreakGlass(eq(saved), eq(false)));
    }
    Assertions.assertNotNull(widgetContext.getWarningMessage(),
        "clearing the last one must warn -- an MFA policy could then strand every administrator");
    Assertions.assertTrue(widgetContext.getWarningMessage().contains("no break-glass account remains"));
  }

  @Test
  void clearingOneOfSeveralDoesNotWarn() throws Exception {
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "id", "42");
    User saved = savedUser(42L, true, ADMIN);

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      saveCmd.when(() -> SaveUserCommand.saveUser(any(User.class))).thenReturn(saved);
      userRepo.when(UserRepository::countBreakGlassAccounts).thenReturn(3L);
      userRepo.when(() -> UserRepository.updateBreakGlass(eq(saved), eq(false))).thenReturn(saved);

      new UserFormWidget().post(widgetContext);
    }
    Assertions.assertNull(widgetContext.getWarningMessage());
  }

  @Test
  void anUnchangedToggleDoesNotWriteAtAll() throws Exception {
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "id", "42");
    addQueryParameter(widgetContext, "breakGlassAccount", "true");
    User saved = savedUser(42L, true, ADMIN); // already true

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      saveCmd.when(() -> SaveUserCommand.saveUser(any(User.class))).thenReturn(saved);

      new UserFormWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.updateBreakGlass(any(User.class), anyBoolean()), never());
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
    Assertions.assertNotNull(widgetContext.getRedirect(),
        "the prompt must come back on a URL that still identifies the record being edited");
    Assertions.assertTrue(widgetContext.getRedirect().contains("userId=-1"),
        "the New User form must return to itself, not to an edit of some other record");
  }

  @Test
  void postWithoutStepUpKeepsTheRecordBeingEdited() throws Exception {
    // Regression: the step-up prompt used to re-render the form without carrying the submitted bean,
    // and the redirect dropped the userId. execute() then fell back to new User(), so the prompt came
    // back as an empty form with id="-1" -- the editor's selections were gone and the next submit
    // would have created a user instead of updating this one. The two-step prompt could therefore
    // never be completed for an existing user, which made every role/group change impossible.
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "id", "42");
    addQueryParameter(widgetContext, "groupId2", "2");

    Group allEmployees = new Group("All Employees", "all-employees");
    allEmployees.setId(2L);
    User target = new User();
    target.setId(42L);

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(List.of(allEmployees));
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);

      new UserFormWidget().post(widgetContext);

      saveCmd.verifyNoInteractions();
    }

    Assertions.assertEquals("true", widgetContext.getSharedRequestValue("stepUpRequired"));
    Assertions.assertNotNull(widgetContext.getRedirect(),
        "the prompt must come back on a URL that still identifies the record being edited");
    Assertions.assertTrue(widgetContext.getRedirect().contains("userId=42"),
        "the prompt must return to the same user, otherwise the next submit becomes a create");

    User redisplayed = (User) widgetContext.getRequestObject();
    Assertions.assertNotNull(redisplayed, "the submitted record must travel with the prompt");
    Assertions.assertEquals(42L, redisplayed.getId().longValue(),
        "user-form.jsp renders the hidden id from this bean; losing it turns the next submit into a create");
    List<String> names = redisplayed.getGroupList().stream().map(Group::getName).toList();
    Assertions.assertTrue(names.contains("All Employees"),
        "the selections being confirmed must survive the prompt, or the editor has to re-enter them");
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

  @Test
  void communityManagerCannotChangeTheEmailOfAnAccountThatOutranksThem() throws Exception {
    // The takeover this closes: User.email is where the password reset link is delivered, so
    // repointing an admin's address and then triggering a reset hands the link to the new address on
    // an account that still holds admin.
    //
    // Step-up is deliberately NOT granted here. The refusal must land before the re-authentication
    // prompt -- there is nothing behind a credential prompt for a save that is refused either way --
    // so this also pins that ordering.
    setRoles(widgetContext, COMMUNITY_MANAGER);
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "email", "attacker@example.net");

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(adminTarget());

      WidgetContext result = new UserFormWidget().post(widgetContext);

      saveCmd.verify(() -> SaveUserCommand.saveUser(any()), never());
      Assertions.assertEquals(
          "You cannot change the sign-in email or username of an account with a higher role level than your own",
          result.getErrorMessage());
      Assertions.assertNull(result.getSharedRequestValue("stepUpRequired"),
          "the refusal must come before the step-up prompt, not after it");
    }
  }

  @Test
  void communityManagerCannotChangeTheUsernameOfAnAccountThatOutranksThem() throws Exception {
    // The username field is hidden on the form and round-trips the current value, so a different one
    // is a crafted parameter -- but it is the sign-in identifier, so it is guarded alongside email.
    setRoles(widgetContext, COMMUNITY_MANAGER);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "email", "admin@example.com");     // unchanged
    addQueryParameter(widgetContext, "username", "attacker");           // crafted

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(adminTarget());

      WidgetContext result = new UserFormWidget().post(widgetContext);

      saveCmd.verify(() -> SaveUserCommand.saveUser(any()), never());
      Assertions.assertNotNull(result.getErrorMessage());
    }
  }

  @Test
  void communityManagerCanEditOtherFieldsOnAnAccountThatOutranksThem() throws Exception {
    // The point of refusing only on change rather than refusing the whole action: correcting a typo
    // in an admin's name is a legitimate edit and must keep working. If this test ever starts
    // failing, the guard has become a blanket block.
    setRoles(widgetContext, COMMUNITY_MANAGER);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "email", "admin@example.com");     // unchanged
    addQueryParameter(widgetContext, "username", "admin@example.com");  // unchanged
    addQueryParameter(widgetContext, "firstName", "Corrected");

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(adminTarget());
      saveCmd.when(() -> SaveUserCommand.saveUser(any())).thenReturn(savedUser());

      WidgetContext result = new UserFormWidget().post(widgetContext);

      saveCmd.verify(() -> SaveUserCommand.saveUser(captor.capture()));
      Assertions.assertEquals("Corrected", captor.getValue().getFirstName());
      Assertions.assertNull(result.getErrorMessage());
    }
  }

  @Test
  void aCaseOnlyEmailChangeOnAnOutrankingAccountIsRefused() throws Exception {
    // Pins the fail-closed comparison: rather than reasoning about which mail providers treat a
    // local part case-insensitively, a case-only difference counts as a change and is refused.
    setRoles(widgetContext, COMMUNITY_MANAGER);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "email", "Admin@example.com");

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(adminTarget());

      new UserFormWidget().post(widgetContext);

      saveCmd.verify(() -> SaveUserCommand.saveUser(any()), never());
    }
  }

  @Test
  void adminCanChangeTheEmailOfAnotherAdmin() throws Exception {
    // At or below the actor's own level -- not "outranks", so the ordinary edit path stays open.
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "email", "new-address@example.com");

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(new ArrayList<>());
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(adminTarget());
      saveCmd.when(() -> SaveUserCommand.saveUser(any())).thenReturn(savedUser());

      new UserFormWidget().post(widgetContext);

      saveCmd.verify(() -> SaveUserCommand.saveUser(captor.capture()));
      Assertions.assertEquals("new-address@example.com", captor.getValue().getEmail());
    }
  }

  @Test
  void postCannotGrantAllGuestsGroupEvenIfSubmitted() throws Exception {
    // "All Guests" has no checkbox on the edit form (see users-list.jsp's New User modal, "not a
    // logged in user group"), but the server must refuse it even if a request forges the parameter --
    // the fix can't rely on the checkbox being hidden client-side.
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "id", "-1");
    Group allGuests = new Group("All Guests", "all-guests");
    allGuests.setId(9L);
    addQueryParameter(widgetContext, "groupId9", "9");

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(List.of(allGuests));
      saveCmd.when(() -> SaveUserCommand.saveUser(any())).thenReturn(savedUser());

      new UserFormWidget().post(widgetContext);

      saveCmd.verify(() -> SaveUserCommand.saveUser(captor.capture()));
      List<String> names = captor.getValue().getGroupList().stream().map(Group::getName).toList();
      Assertions.assertFalse(names.contains("All Guests"), "a new membership in 'All Guests' must never be granted through this form");
    }
  }

  @Test
  void editingExistingUserPreservesAllGuestsMembershipItAlreadyHeld() throws Exception {
    // A user can already be a member of "All Guests" from a path that isn't gated the same way
    // (e.g. CSV import's Groups column, or an OAuth group claim). Since the checkbox is hidden,
    // an unrelated save must not silently drop that pre-existing membership.
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "id", "5"); // editing an existing user; groupId9 left unchecked

    Group allGuests = new Group("All Guests", "all-guests");
    allGuests.setId(9L);
    User target = new User();
    target.setId(5L);
    List<Group> held = new ArrayList<>();
    held.add(allGuests);
    target.setGroupList(held);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      groupRepo.when(GroupRepository::findAll).thenReturn(List.of(allGuests));
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      saveCmd.when(() -> SaveUserCommand.saveUser(any())).thenReturn(savedUser());

      new UserFormWidget().post(widgetContext);

      saveCmd.verify(() -> SaveUserCommand.saveUser(captor.capture()));
      List<String> names = captor.getValue().getGroupList().stream().map(Group::getName).toList();
      Assertions.assertTrue(names.contains("All Guests"), "existing 'All Guests' membership must survive an unrelated save, not be silently stripped");
    }
  }
}
