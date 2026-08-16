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

package com.simisinc.platform.presentation.widgets.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.login.UserMfaCommand;
import com.simisinc.platform.application.login.UserMfaRecoveryCodeCommand;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.UserSpecification;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.admin.login.UserDetailsWidget;

/**
 * Covers the bulk "Remove MFA" action on the "Roles already MFA-enrolled" widget -- the role-level
 * counterpart to {@code UserDetailsWidget}'s per-user Reset MFA action (see
 * {@code UserDetailsWidgetTest}), reusing the same step-up gate and
 * {@link UserDetailsWidget#targetOutranksActor} guardrail so a lower-privileged admin can't use the
 * bulk path to reach an account the per-user action would refuse to touch.
 */
class MfaEnrolledRolesWidgetTest extends WidgetBase {

  private static Role role(int id, int level, String code, String title) {
    Role role = new Role(title, code);
    role.setId(id);
    role.setLevel(level);
    return role;
  }

  private static List<Role> allRoles() {
    List<Role> roles = new ArrayList<>();
    roles.add(role(2, 80, "content-manager", "Content Manager"));
    roles.add(role(3, 90, "community-manager", "Community Manager"));
    roles.add(role(4, 100, "admin", "System Administrator"));
    return roles;
  }

  private static User enrolledUser(long id, String email) {
    User user = new User();
    user.setId(id);
    user.setEmail(email);
    user.setMfaEnabled(true);
    List<Role> held = new ArrayList<>();
    held.add(role(2, 80, "content-manager", "Content Manager"));
    user.setRoleList(held);
    return user;
  }

  @Test
  void postRequiresStepUpAndDoesNotResetWithoutIt() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "action", "removeMfaFromRole");
    addQueryParameter(widgetContext, "roleId", "2");

    Role target = role(2, 80, "content-manager", "Content Manager");

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<UserMfaCommand> mfa = mockStatic(UserMfaCommand.class);
        MockedStatic<UserMfaRecoveryCodeCommand> recovery = mockStatic(UserMfaRecoveryCodeCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      roleRepo.when(() -> RoleRepository.findById(2)).thenReturn(target);

      WidgetContext result = new MfaEnrolledRolesWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.findAll(any(), any()), never());
      mfa.verify(() -> UserMfaCommand.disable(any()), never());
      recovery.verify(() -> UserMfaRecoveryCodeCommand.clear(any()), never());
      audit.verifyNoInteractions();
      Assertions.assertNotNull(result.getErrorMessage());
      Assertions.assertEquals("/admin/mfa-properties", result.getRedirect());
    }
  }

  @Test
  void postRemovesMfaForEveryEnrolledMemberOfTheRole() {
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "action", "removeMfaFromRole");
    addQueryParameter(widgetContext, "roleId", "2");

    Role target = role(2, 80, "content-manager", "Content Manager");
    User first = enrolledUser(5L, "first@example.com");
    User second = enrolledUser(6L, "second@example.com");

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<UserMfaCommand> mfa = mockStatic(UserMfaCommand.class);
        MockedStatic<UserMfaRecoveryCodeCommand> recovery = mockStatic(UserMfaRecoveryCodeCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      roleRepo.when(() -> RoleRepository.findById(2)).thenReturn(target);
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      userRepo.when(() -> UserRepository.findAll(any(UserSpecification.class), any())).thenReturn(List.of(first, second));
      mfa.when(() -> UserMfaCommand.disable(any())).thenReturn(true);

      WidgetContext result = new MfaEnrolledRolesWidget().post(widgetContext);

      mfa.verify(() -> UserMfaCommand.disable(first), times(1));
      mfa.verify(() -> UserMfaCommand.disable(second), times(1));
      recovery.verify(() -> UserMfaRecoveryCodeCommand.clear(first), times(1));
      recovery.verify(() -> UserMfaRecoveryCodeCommand.clear(second), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT), eq("user.mfa.reset"),
          eq(AuditEventCommand.SUCCESS), eq("user"), eq("5"), eq("first@example.com"), any()), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT), eq("user.mfa.reset"),
          eq(AuditEventCommand.SUCCESS), eq("user"), eq("6"), eq("second@example.com"), any()), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT), eq("role.mfa.bulk_reset"),
          eq(AuditEventCommand.SUCCESS), eq("role"), eq("2"), eq("Content Manager"), any()), times(1));
      Assertions.assertNotNull(result.getSuccessMessage());
    }
  }

  @Test
  void postSkipsAMemberWhoOutranksTheActingAdmin() {
    // /admin/mfa-properties (and this widget's own hasRole("admin") gate) only ever admits an
    // "admin" actor, already the highest of the four built-in roles -- so exercising the outrank
    // guard here needs a hypothetical role above admin's level, the same way it would if a site
    // ever configured one, rather than a lower-privileged acting role that could never reach this
    // widget's post() in the first place.
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "action", "removeMfaFromRole");
    addQueryParameter(widgetContext, "roleId", "2");

    List<Role> rolesWithSuperAdmin = new ArrayList<>(allRoles());
    rolesWithSuperAdmin.add(role(5, 150, "super-admin", "Super Admin"));

    Role target = role(2, 80, "content-manager", "Content Manager");
    User belowActor = enrolledUser(5L, "below@example.com");
    User outranksActor = enrolledUser(6L, "outranks@example.com");
    List<Role> outrankerRoles = new ArrayList<>();
    outrankerRoles.add(role(5, 150, "super-admin", "Super Admin"));
    outranksActor.setRoleList(outrankerRoles);

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<UserMfaCommand> mfa = mockStatic(UserMfaCommand.class);
        MockedStatic<UserMfaRecoveryCodeCommand> recovery = mockStatic(UserMfaRecoveryCodeCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      roleRepo.when(() -> RoleRepository.findById(2)).thenReturn(target);
      roleRepo.when(RoleRepository::findAll).thenReturn(rolesWithSuperAdmin);
      userRepo.when(() -> UserRepository.findAll(any(UserSpecification.class), any()))
          .thenReturn(List.of(belowActor, outranksActor));
      mfa.when(() -> UserMfaCommand.disable(any())).thenReturn(true);

      WidgetContext result = new MfaEnrolledRolesWidget().post(widgetContext);

      mfa.verify(() -> UserMfaCommand.disable(belowActor), times(1));
      mfa.verify(() -> UserMfaCommand.disable(outranksActor), never());
      recovery.verify(() -> UserMfaRecoveryCodeCommand.clear(outranksActor), never());
      Assertions.assertNotNull(result.getWarningMessage());
    }
  }

  @Test
  void postRequiresAdminRole() {
    setRoles(widgetContext, COMMUNITY_MANAGER);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "action", "removeMfaFromRole");
    addQueryParameter(widgetContext, "roleId", "2");

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      new MfaEnrolledRolesWidget().post(widgetContext);

      roleRepo.verify(() -> RoleRepository.findById(anyInt()), never());
      userRepo.verify(() -> UserRepository.findAll(any(), any()), never());
    }
  }

  @Test
  void postIgnoresAnUnrecognizedAction() {
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "action", "somethingElse");
    addQueryParameter(widgetContext, "roleId", "2");

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class)) {
      WidgetContext result = new MfaEnrolledRolesWidget().post(widgetContext);

      roleRepo.verify(() -> RoleRepository.findById(anyInt()), never());
      Assertions.assertEquals("/admin/mfa-properties", result.getRedirect());
    }
  }

  @Test
  void postReportsWhenTheRoleHasNoEnrolledMembers() {
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "action", "removeMfaFromRole");
    addQueryParameter(widgetContext, "roleId", "2");

    Role target = role(2, 80, "content-manager", "Content Manager");

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      roleRepo.when(() -> RoleRepository.findById(2)).thenReturn(target);
      userRepo.when(() -> UserRepository.findAll(any(UserSpecification.class), any())).thenReturn(List.of());

      WidgetContext result = new MfaEnrolledRolesWidget().post(widgetContext);

      audit.verifyNoInteractions();
      Assertions.assertNotNull(result.getErrorMessage());
    }
  }
}
