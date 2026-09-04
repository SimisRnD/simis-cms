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
import com.simisinc.platform.application.login.UserMfaCommand;
import com.simisinc.platform.application.login.UserMfaRecoveryCodeCommand;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
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
 * communityManagerCannotSuspendAccountThatOutranksThem / communityManagerCannotRestoreAccountThatOutranksThem /
 * communityManagerCannotDeleteAccountThatOutranksThem guard a shared gap: suspendAccount(), restoreAccount(), and
 * deleteAccount() only ever checked "is this my own account" -- none checked the target's role level against the
 * acting admin's, even though both /admin/users and /admin/user-details are reachable by community-manager
 * (level 90, admin-layout.xml) and, as of the users:manage capability, by a user holding only that capability
 * with no legacy role at all -- one level below admin (level 100). Without this guard, either could suspend,
 * restore, or permanently delete an admin account outright. Mirrors the escalation guard UserFormWidget already
 * applies to role grants (see UserFormWidgetTest). deleteAccount() was the last of the three still missing it.
 *
 * resetMfaWithoutStepUpDoesNotResetAndShowsReAuthPanel / resetMfaRefusesWhenTargetOutranksActor /
 * resetMfaViaPostCallsCommandsAndAudits cover the admin "Reset MFA" lockout-recovery action: it requires a fresh
 * step-up re-authentication exactly like Reset Password, refuses when the target outranks the acting admin exactly
 * like Suspend/Restore, and on success clears the target's MFA secret/enabled flag and recovery codes by reusing
 * the same UserMfaCommand/UserMfaRecoveryCodeCommand calls the self-service "disable" action already makes on the
 * user's own account (see MyMfaSettingsWidgetTest).
 *
 * resetPasswordViaPostReportsFailureWhenTheTokenWriteFails pins the null return of
 * UserRepository#createAccountToken. The audit line already recorded that outcome as FAILURE, but the two
 * statements after it passed the null reference into UserPasswordResetEvent and then called user.getEmail()
 * unconditionally, so a failed token write threw a NullPointerException at the admin instead of a message
 * saying the reset did not happen. resetPasswordViaPostSendsInstructionsWhenTheTokenWriteSucceeds keeps the
 * success path honest alongside it.
 *
 * stepUpReRenderStillSetsAccountLinkState guards a trap the #1836 change itself introduced:
 * post()'s step-up prompts re-render user-details.jsp WITHOUT running execute(), and the JSP
 * declares accountLinkState through jsp:useBean -- so an unset attribute resolves to "" rather
 * than null, and a "not none" test would have rendered "Outstanding" for an account holding no
 * link at all. Every path that renders that JSP must set it.
 *
 * accountLinkStateClassifiesOutstandingExpiredAndNone / resetPasswordWarnsWhenItReplacedAnOutstandingLink /
 * resetPasswordStaysQuietWhenNoLinkWasOutstanding cover #1836. An account holds exactly one
 * account_token, so createAccountToken overwrites whatever was there -- issuing a reset silently
 * stops the previously emailed link resolving. The page reported only "instructions have been
 * sent", so an admin helping someone mid-activation would reasonably keep resending and destroy
 * the very link that person was clicking. These pin the classification the page renders and the
 * warning the admin now gets, and pin that the warning stays off when nothing was replaced -- a
 * warning on every reset would be noise and would train admins to ignore it.
 *
 * suspendAccountViaPostRecordsFailureWhenTheSuspendWriteFails pins the null return of
 * UserRepository#suspendAccount. Unlike the resetPassword case above, nothing threw -- user is never
 * reassigned -- so the failure was silent: the audit line already recorded FAILURE, but the success
 * message was set unconditionally, leaving the admin told "Account suspended" for an account that is
 * still enabled. suspendAccountViaPostCallsRepositoryAndAudits keeps the success path honest alongside it.
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
  void actionResetMfaIsNotHandledByTheGetActionPath() throws Exception {
    // MFA reset requires step-up re-authentication (see post()), same bar as Reset Password. The
    // GET/action() path must never reset MFA directly -- that would bypass step-up entirely,
    // reachable via a plain GET request carrying the same parameters the UI's POST form uses.
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "resetMfa");

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserMfaCommand> mfa = mockStatic(UserMfaCommand.class);
        MockedStatic<UserMfaRecoveryCodeCommand> recovery = mockStatic(UserMfaRecoveryCodeCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(lockedUser());

      new UserDetailsWidget().action(widgetContext);

      mfa.verify(() -> UserMfaCommand.disable(any()), never());
      recovery.verify(() -> UserMfaRecoveryCodeCommand.clear(any()), never());
      audit.verifyNoInteractions();
    }
  }

  @Test
  void suspendAccountViaPostCallsRepositoryAndAudits() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "suspendAccount");
    addQueryParameter(widgetContext, "reason", "Reported phishing attempt from this account");

    // An admin (level 100) acting on another admin (level 100): equal level is not "outranks" and
    // must still be permitted.
    User target = adminUser();

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      userRepo.when(() -> UserRepository.suspendAccount(target, "Reported phishing attempt from this account"))
          .thenReturn(target);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.suspendAccount(target, "Reported phishing attempt from this account"), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT), eq("user.disable"),
          eq(AuditEventCommand.SUCCESS), eq("user"), eq("5"), eq("active@example.com"),
          eq("Reported phishing attempt from this account")), times(1));
      Assertions.assertEquals("Account suspended", result.getSuccessMessage());
    }
  }

  @Test
  void suspendAccountViaPostRecordsFailureWhenTheSuspendWriteFails() throws Exception {
    // UserRepository.suspendAccount() returns null when its DB update does not take (it logs
    // "suspendAccount failed!") -- suspendAccount() must reflect that instead of unconditionally
    // reporting "Account suspended", matching deleteAccount()'s if/else pattern. Nothing here
    // threw before the fix: user is never reassigned, so the admin simply saw a success message
    // for an account that is still enabled, while the audit record said FAILURE.
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "suspendAccount");
    addQueryParameter(widgetContext, "reason", "Reported phishing attempt from this account");

    User target = adminUser();

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      userRepo.when(() -> UserRepository.suspendAccount(eq(target), any())).thenReturn(null);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT), eq("user.disable"),
          eq(AuditEventCommand.FAILURE), eq("user"), eq("5"), eq("active@example.com"),
          eq("Reported phishing attempt from this account")), times(1));
      Assertions.assertNull(result.getSuccessMessage());
      Assertions.assertNotNull(result.getErrorMessage());
    }
  }

  @Test
  void restoreAccountViaPostCallsRepositoryAndAudits() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "restoreAccount");

    // Below the maker-checker elevated-role threshold (community-manager, level 90) -- this test
    // is exercising the direct-restore path, not the #492 approval-request path (see
    // UserDetailsWidgetUnsuspendTest for that).
    User target = activeUser();
    List<Role> held = new ArrayList<>();
    held.add(role(2, 80, "content-manager", "Content Manager"));
    target.setRoleList(held);
    target.setEnabled(false);

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(5L)).thenReturn(target);
      // restoreAccount() resolves the acting admin via UserSession.getUser(), a separate
      // LoadUserCommand.loadUser(1L) call (see WidgetBase#login) -- must not collapse onto the
      // target stub above, or UnsuspendAccountCommand's self-check ("you cannot request the
      // unsuspension of your own account") wrongly fires since actingAdmin.getId() would equal
      // target.getId().
      User actingAdmin = new User();
      actingAdmin.setId(1L);
      actingAdmin.setEmail("admin@example.com");
      loadCmd.when(() -> LoadUserCommand.loadUser(1L)).thenReturn(actingAdmin);
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

      userRepo.verify(() -> UserRepository.suspendAccount(any(), any()), never());
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
      userRepo.when(() -> UserRepository.suspendAccount(eq(target), any())).thenReturn(target);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.suspendAccount(eq(target), any()), times(1));
      Assertions.assertEquals("Account suspended", result.getSuccessMessage());
    }
  }

  @Test
  void deleteAccountViaPostCallsRepositoryAndAudits() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "deleteAccount");

    // An admin (level 100) acting on a non-elevated target -- not "outranks".
    User target = activeUser();

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      userRepo.when(() -> UserRepository.remove(target)).thenReturn(true);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.remove(target), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT), eq("user.delete"),
          eq(AuditEventCommand.SUCCESS), eq("user"), eq("5"), eq("active@example.com"), any()), times(1));
      Assertions.assertEquals("Account deleted", result.getSuccessMessage());
    }
  }

  @Test
  void communityManagerCannotDeleteAccountThatOutranksThem() throws Exception {
    // Without this guard, any user who can reach this page -- including a community-manager, or,
    // as of the users:manage capability, a user holding only that capability with no role at all --
    // could permanently delete an admin account, since deleteAccount()'s only other check is
    // "not yourself".
    setRoles(widgetContext, COMMUNITY_MANAGER);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "deleteAccount");

    // The target holds admin (level 100), above the acting community-manager (level 90).
    User target = adminUser();

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.remove(any()), never());
      audit.verifyNoInteractions();
      Assertions.assertEquals("You cannot delete an account with a higher role level than your own",
          result.getErrorMessage());
    }
  }

  @Test
  void communityManagerCanDeleteAccountAtOrBelowTheirOwnLevel() throws Exception {
    setRoles(widgetContext, COMMUNITY_MANAGER);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "deleteAccount");

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
      userRepo.when(() -> UserRepository.remove(target)).thenReturn(true);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.remove(target), times(1));
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

      userRepo.verify(() -> UserRepository.suspendAccount(any(), any()), never());
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
      loadCmd.when(() -> LoadUserCommand.loadUser(5L)).thenReturn(target);
      // See the identical comment in restoreAccountViaPostCallsRepositoryAndAudits above.
      User actingAdmin = new User();
      actingAdmin.setId(1L);
      actingAdmin.setEmail("admin@example.com");
      loadCmd.when(() -> LoadUserCommand.loadUser(1L)).thenReturn(actingAdmin);
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

  @Test
  void resetMfaWithoutStepUpDoesNotResetAndShowsReAuthPanel() throws Exception {
    // No stepUpCredential provided, and the session has no valid step-up token
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "resetMfa");

    User target = activeUser();
    target.setMfaEnabled(true);

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserMfaCommand> mfa = mockStatic(UserMfaCommand.class);
        MockedStatic<UserMfaRecoveryCodeCommand> recovery = mockStatic(UserMfaRecoveryCodeCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      mfa.verify(() -> UserMfaCommand.disable(any()), never());
      recovery.verify(() -> UserMfaRecoveryCodeCommand.clear(any()), never());
      audit.verifyNoInteractions();
      Assertions.assertEquals("true", result.getSharedRequestValue("stepUpRequired"));
      Assertions.assertEquals(UserDetailsWidget.JSP, result.getJsp());
      Assertions.assertNull(result.getRedirect());
    }
  }

  @Test
  void resetMfaRefusesWhenTargetOutranksActor() throws Exception {
    // The target holds admin (level 100), above the acting community-manager (level 90).
    setRoles(widgetContext, COMMUNITY_MANAGER);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "resetMfa");

    User target = adminUser();
    target.setMfaEnabled(true);

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<UserMfaCommand> mfa = mockStatic(UserMfaCommand.class);
        MockedStatic<UserMfaRecoveryCodeCommand> recovery = mockStatic(UserMfaRecoveryCodeCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      mfa.verify(() -> UserMfaCommand.disable(any()), never());
      recovery.verify(() -> UserMfaRecoveryCodeCommand.clear(any()), never());
      audit.verifyNoInteractions();
      Assertions.assertEquals("You cannot reset MFA for an account with a higher role level than your own",
          result.getErrorMessage());
    }
  }

  @Test
  void resetMfaViaPostCallsCommandsAndAudits() throws Exception {
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "resetMfa");

    // Below the acting admin's level -- not "outranks", the reset must proceed.
    User target = activeUser();
    target.setMfaEnabled(true);
    target.setMfaSecret("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    List<Role> held = new ArrayList<>();
    held.add(role(2, 80, "content-manager", "Content Manager"));
    target.setRoleList(held);

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<UserMfaCommand> mfa = mockStatic(UserMfaCommand.class);
        MockedStatic<UserMfaRecoveryCodeCommand> recovery = mockStatic(UserMfaRecoveryCodeCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      mfa.when(() -> UserMfaCommand.disable(target)).thenReturn(true);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      // The second factor and recovery codes are cleared through the same commands the
      // self-service MyMfaSettingsWidget "disable" action already calls on the user's own account.
      mfa.verify(() -> UserMfaCommand.disable(target), times(1));
      recovery.verify(() -> UserMfaRecoveryCodeCommand.clear(target), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT), eq("user.mfa.reset"),
          eq(AuditEventCommand.SUCCESS), eq("user"), eq("5"), eq("active@example.com"), any()), times(1));
      Assertions.assertNotNull(result.getSuccessMessage());
      Assertions.assertEquals("/admin/user-details?userId=5", result.getRedirect());
    }
  }

  @Test
  void resetMfaViaPostRecordsFailureWhenTheMfaDisableWriteFails() throws Exception {
    // UserMfaCommand.disable() returning false means UserRepository.disableMfa()'s DB write did not
    // take (see UserRepository#disableMfa returning null on failure) -- resetMfa() must reflect that
    // instead of unconditionally reporting success, matching deleteAccount()'s if/else pattern.
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "resetMfa");

    User target = activeUser();
    target.setMfaEnabled(true);
    target.setMfaSecret("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    List<Role> held = new ArrayList<>();
    held.add(role(2, 80, "content-manager", "Content Manager"));
    target.setRoleList(held);

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<UserMfaCommand> mfa = mockStatic(UserMfaCommand.class);
        MockedStatic<UserMfaRecoveryCodeCommand> recovery = mockStatic(UserMfaRecoveryCodeCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      mfa.when(() -> UserMfaCommand.disable(target)).thenReturn(false);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT), eq("user.mfa.reset"),
          eq(AuditEventCommand.FAILURE), eq("user"), eq("5"), eq("active@example.com"), any()), times(1));
      Assertions.assertNull(result.getSuccessMessage());
      Assertions.assertNotNull(result.getErrorMessage());
    }
  }

  @Test
  void resetPasswordViaPostReportsFailureWhenTheTokenWriteFails() throws Exception {
    // UserRepository.createAccountToken() returns null when its DB update does not take (it logs
    // "createAccountToken failed!"). The audit line already anticipated that by recording FAILURE, but the
    // statements after it dereferenced the same null reference -- the admin got a NullPointerException
    // rather than a message explaining the reset did not happen. No token was written, so no reset email
    // may be triggered either.
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "resetPassword");

    User target = activeUser();

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      userRepo.when(() -> UserRepository.createAccountToken(target)).thenReturn(null);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT),
          eq("user.password.reset"), eq(AuditEventCommand.FAILURE), eq("user"), eq("5"),
          eq("active@example.com"), any()), times(1));
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), never());
      Assertions.assertNull(result.getSuccessMessage());
      Assertions.assertNotNull(result.getErrorMessage());
      // The address comes from targetLabel, captured before the call, not from the null reference
      Assertions.assertTrue(result.getErrorMessage().contains("active@example.com"));
    }
  }

  @Test
  void resetPasswordViaPostSendsInstructionsWhenTheTokenWriteSucceeds() throws Exception {
    // The guard above must not change the success path: a token that writes still audits SUCCESS,
    // triggers the reset event, and reports the address the instructions went to.
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "resetPassword");

    User target = activeUser();

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      userRepo.when(() -> UserRepository.createAccountToken(target)).thenReturn(target);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.USER_MANAGEMENT),
          eq("user.password.reset"), eq(AuditEventCommand.SUCCESS), eq("user"), eq("5"),
          eq("active@example.com"), any()), times(1));
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), times(1));
      Assertions.assertNull(result.getErrorMessage());
      Assertions.assertNotNull(result.getSuccessMessage());
      Assertions.assertTrue(result.getSuccessMessage().contains("active@example.com"));
    }
  }

  @Test
  void accountLinkStateClassifiesOutstandingExpiredAndNone() {
    User none = activeUser();
    none.setAccountToken(null);
    Assertions.assertEquals(UserDetailsWidget.LINK_NONE, UserDetailsWidget.accountLinkState(none));
    Assertions.assertEquals(UserDetailsWidget.LINK_NONE, UserDetailsWidget.accountLinkState(null));

    User outstanding = activeUser();
    outstanding.setAccountToken("a-token");
    outstanding.setAccountTokenExpires(new Timestamp(System.currentTimeMillis() + 3_600_000L));
    Assertions.assertEquals(UserDetailsWidget.LINK_OUTSTANDING, UserDetailsWidget.accountLinkState(outstanding));

    User expired = activeUser();
    expired.setAccountToken("a-token");
    expired.setAccountTokenExpires(new Timestamp(System.currentTimeMillis() - 1_000L));
    Assertions.assertEquals(UserDetailsWidget.LINK_EXPIRED, UserDetailsWidget.accountLinkState(expired));

    // A null expiry counts as outstanding, matching findByAccountToken's own "IS NULL" arm -- such
    // a token still opens the password form, so the page must not imply no link exists.
    User noExpiry = activeUser();
    noExpiry.setAccountToken("a-token");
    noExpiry.setAccountTokenExpires(null);
    Assertions.assertEquals(UserDetailsWidget.LINK_OUTSTANDING, UserDetailsWidget.accountLinkState(noExpiry));
  }

  @Test
  void resetPasswordWarnsWhenItReplacedAnOutstandingLink() throws Exception {
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "resetPassword");

    User target = activeUser();
    target.setAccountToken("still-valid");
    target.setAccountTokenExpires(new Timestamp(System.currentTimeMillis() + 3_600_000L));

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      userRepo.when(() -> UserRepository.createAccountToken(target)).thenReturn(target);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      userRepo.verify(() -> UserRepository.createAccountToken(target), times(1));
      Assertions.assertTrue(result.getSuccessMessage().contains("stopped working"),
          "an admin who just invalidated a live link must be told so: " + result.getSuccessMessage());
    }
  }

  @Test
  void resetPasswordStaysQuietWhenNoLinkWasOutstanding() throws Exception {
    setRoles(widgetContext, ADMIN);
    grantStepUp(widgetContext);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "resetPassword");

    User target = activeUser();
    target.setAccountToken(null);

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);
      roleRepo.when(RoleRepository::findAll).thenReturn(allRoles());
      userRepo.when(() -> UserRepository.createAccountToken(target)).thenReturn(target);

      WidgetContext result = new UserDetailsWidget().post(widgetContext);

      Assertions.assertFalse(result.getSuccessMessage().contains("stopped working"),
          "nothing was replaced, so the warning must not fire: " + result.getSuccessMessage());
    }
  }

  @Test
  void stepUpReRenderStillSetsAccountLinkState() throws Exception {
    // No step-up granted and no credential supplied: post() re-renders the page itself rather
    // than delegating to execute(), which is where the attribute is normally set.
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "userId", "5");
    addQueryParameter(widgetContext, "action", "resetPassword");

    User target = activeUser();
    target.setAccountToken(null);

    try (MockedStatic<LoadUserCommand> loadCmd = mockStatic(LoadUserCommand.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadCmd.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(target);

      new UserDetailsWidget().post(widgetContext);

      // No token was minted -- the step-up prompt is shown instead.
      userRepo.verify(() -> UserRepository.createAccountToken(any()), never());
      Assertions.assertEquals(UserDetailsWidget.LINK_NONE,
          widgetContext.getRequest().getAttribute("accountLinkState"),
          "the re-render must state the link state explicitly; an unset attribute becomes \"\" "
              + "under jsp:useBean and would render as an outstanding link");
    }
  }
}
