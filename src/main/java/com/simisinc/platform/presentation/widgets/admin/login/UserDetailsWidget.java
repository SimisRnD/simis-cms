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

package com.simisinc.platform.presentation.widgets.admin.login;

import org.apache.commons.lang3.StringUtils;

import java.sql.Timestamp;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.login.StepUpAuthCommand;
import com.simisinc.platform.application.login.UnsuspendAccountCommand;
import com.simisinc.platform.application.login.UserMfaCommand;
import com.simisinc.platform.application.login.UserMfaRecoveryCodeCommand;
import com.simisinc.platform.domain.events.cms.UnsuspendRequestedEvent;
import com.simisinc.platform.domain.events.cms.UserAccountRestoredEvent;
import com.simisinc.platform.domain.events.cms.UserPasswordResetEvent;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.UnsuspendRequest;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.login.UnsuspendRequestRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.UserSession;
import com.simisinc.platform.presentation.controller.WidgetContext;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/19/18 1:15 PM
 */
public class UserDetailsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/user-details.jsp";
  static String INVALID_USER_JSP = "/admin/user-invalid.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Load the user
    User user = LoadUserCommand.loadUser(context.getParameterAsLong("userId"));
    if (user == null) {
      context.setJsp(INVALID_USER_JSP);
      return context;
    }
    context.getRequest().setAttribute("user", user);
    context.setPageTitle(user.getFullName());

    // Shows any roles
    List<Role> roleList = RoleRepository.findAll();
    context.getRequest().setAttribute("roleList", roleList);

    // Show any groups
    List<Group> groupList = GroupRepository.findAll();
    context.getRequest().setAttribute("groupList", groupList);

    // Show Last Login Record
    context.getRequest().setAttribute("userLogin", user.getLastLogin());

    // Password-age warning tier (#492): "warning" past the configurable threshold, "critical" at
    // 2x it (not separately configurable), "ok" otherwise. A never-tracked password (existing
    // account predating this column) is treated as maximally stale, not silently skipped.
    int maxAgeDays = UserRepository.resolvePasswordMaxAgeDays(LoadSitePropertyCommand.loadByName("security.password.maxAgeDays"));
    context.getRequest().setAttribute("passwordAgeSeverity", passwordAgeSeverity(user.getLastPasswordChangedAt(), maxAgeDays));

    // Maker-checker unsuspend (#492 Phase 3): an elevated-role account can't be reactivated by one
    // admin acting alone -- the JSP uses these to decide whether "Restore" is a direct action or
    // opens the request-a-review modal, and whether to show a pending request's status/controls.
    context.getRequest().setAttribute("isElevatedTarget", UnsuspendAccountCommand.requiresApproval(user));
    context.getRequest().setAttribute("pendingUnsuspendRequest", UnsuspendRequestRepository.findPendingByTargetUserId(user.getId()));
    context.getRequest().setAttribute("currentUserId", context.getUserId());

    // #1836: whether a setup/reset link is currently outstanding for this account. Until now the
    // page showed only "Not Validated", identically whether a live link existed or none did, so an
    // admin had no way to tell "they never got a link" from "a link is sitting in their inbox" --
    // and reissuing was the only way to find out, which destroys the outstanding link (see
    // resetPassword below). buildRecord already loads both fields on every user load.
    context.getRequest().setAttribute("accountLinkState", accountLinkState(user));

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  static final String LINK_NONE = "none";
  static final String LINK_OUTSTANDING = "outstanding";
  static final String LINK_EXPIRED = "expired";

  /**
   * Classify the account's outstanding validation/reset link for display (#1836).
   *
   * <p>Display only. Whether a token actually grants access is decided solely by
   * {@link UserRepository#findByAccountToken(String)}, which enforces expiry in SQL against the
   * database clock; this compares against the JVM clock, so around the expiry instant the two can
   * disagree by the clock skew between them. That is acceptable for choosing a label and must not
   * be relied on for an access decision.
   *
   * <p>A null expiry counts as outstanding, matching findByAccountToken's own "IS NULL" arm.
   */
  static String accountLinkState(User user) {
    if (user == null || StringUtils.isBlank(user.getAccountToken())) {
      return LINK_NONE;
    }
    Timestamp expires = user.getAccountTokenExpires();
    if (expires != null && expires.getTime() <= System.currentTimeMillis()) {
      return LINK_EXPIRED;
    }
    return LINK_OUTSTANDING;
  }

  private static String passwordAgeSeverity(Timestamp lastChanged, int maxAgeDays) {
    if (lastChanged == null) {
      return "critical";
    }
    long ageDays = (System.currentTimeMillis() - lastChanged.getTime()) / 86_400_000L;
    if (ageDays > (long) maxAgeDays * 2) {
      return "critical";
    }
    if (ageDays > maxAgeDays) {
      return "warning";
    }
    return "ok";
  }

  public WidgetContext post(WidgetContext context) {
    long userId = context.getParameterAsLong("userId");
    User user = LoadUserCommand.loadUser(userId);
    if (user == null) {
      context.setErrorMessage("The user record was not found");
      context.setJsp(INVALID_USER_JSP);
      return context;
    }
    String action = context.getParameter("action");
    if ("resetPassword".equals(action)) {
      String stepUpCredential = context.getParameter("stepUpCredential");
      if (!StepUpAuthCommand.isValid(context.getUserSession())) {
        if (StringUtils.isBlank(stepUpCredential)) {
          context.addSharedRequestValue("stepUpRequired", "true");
          renderDetailsPage(context, user);
          return context;
        }
        User actingUser = LoadUserCommand.loadUser(context.getUserId());
        if (!StepUpAuthCommand.verify(context.getUserSession(), actingUser, stepUpCredential)) {
          context.setErrorMessage("Re-authentication failed. Enter your password or authenticator code.");
          context.addSharedRequestValue("stepUpRequired", "true");
          renderDetailsPage(context, user);
          return context;
        }
      }
      context.setRedirect("/admin/user-details?userId=" + userId);
      return resetPassword(context, user);
    }
    if ("resetMfa".equals(action)) {
      // Clearing another user's MFA enrollment requires step-up, same bar as Reset Password --
      // and, matching resetPassword's own comment above, is intentionally kept OUT of action()'s
      // dispatch table so a plain GET/action request can never reach it.
      String stepUpCredential = context.getParameter("stepUpCredential");
      if (!StepUpAuthCommand.isValid(context.getUserSession())) {
        if (StringUtils.isBlank(stepUpCredential)) {
          context.addSharedRequestValue("stepUpRequired", "true");
          renderDetailsPage(context, user);
          return context;
        }
        User actingUser = LoadUserCommand.loadUser(context.getUserId());
        if (!StepUpAuthCommand.verify(context.getUserSession(), actingUser, stepUpCredential)) {
          context.setErrorMessage("Re-authentication failed. Enter your password or authenticator code.");
          context.addSharedRequestValue("stepUpRequired", "true");
          renderDetailsPage(context, user);
          return context;
        }
      }
      context.setRedirect("/admin/user-details?userId=" + userId);
      return resetMfa(context, user);
    }
    if ("approveUnsuspend".equals(action)) {
      // Approving an unsuspend request requires step-up, same bar as Reset Password/Assign Roles --
      // and, matching resetPassword's own comment above, is intentionally kept OUT of action()'s
      // dispatch table so a plain GET/action request can never reach it.
      String stepUpCredential = context.getParameter("stepUpCredential");
      if (!StepUpAuthCommand.isValid(context.getUserSession())) {
        if (StringUtils.isBlank(stepUpCredential)) {
          context.addSharedRequestValue("stepUpRequired", "true");
          renderDetailsPage(context, user);
          return context;
        }
        User actingUser = LoadUserCommand.loadUser(context.getUserId());
        if (!StepUpAuthCommand.verify(context.getUserSession(), actingUser, stepUpCredential)) {
          context.setErrorMessage("Re-authentication failed. Enter your password or authenticator code.");
          context.addSharedRequestValue("stepUpRequired", "true");
          renderDetailsPage(context, user);
          return context;
        }
      }
      context.setRedirect("/admin/user-details?userId=" + userId);
      return approveUnsuspend(context);
    }
    if ("suspendAccount".equals(action) || "restoreAccount".equals(action)
        || "deleteAccount".equals(action) || "unlockAccount".equals(action) || "denyUnsuspend".equals(action)) {
      // The user-details menu submits these via POST (issue #358 moved state-changing
      // admin actions off GET query strings), so they arrive here rather than in
      // action() below. Dispatch through the same table action() uses for a GET caller.
      return action(context);
    }
    context.setRedirect("/admin/user-details?userId=" + userId);
    return context;
  }

  public WidgetContext action(WidgetContext context) {
    // Find the user record
    long userId = context.getParameterAsLong("userId");
    User user = LoadUserCommand.loadUser(userId);
    if (user == null) {
      context.setErrorMessage("The user record was not found");
      return context;
    }
    // Execute the action
    // Note: resetPassword and resetMfa are intentionally NOT handled here -- both require step-up
    // re-authentication (see post()) and must only be reachable through that gated path.
    context.setRedirect("/admin/user-details?userId=" + userId);
    String action = context.getParameter("action");
    if ("suspendAccount".equals(action)) {
      return suspendAccount(context, user);
    } else if ("restoreAccount".equals(action)) {
      return restoreAccount(context, user);
    } else if ("deleteAccount".equals(action)) {
      return deleteAccount(context, user);
    } else if ("unlockAccount".equals(action)) {
      return unlockAccount(context, user);
    } else if ("denyUnsuspend".equals(action)) {
      return denyUnsuspend(context);
    }
    return context;
  }

  /**
   * Re-render the details page from a POST branch (the step-up re-authentication prompts).
   *
   * <p>These paths never run {@link #execute(WidgetContext)}, so every attribute the JSP reads has
   * to be set here as well. #1836's accountLinkState in particular: the JSP declares it via
   * jsp:useBean, so an unset attribute becomes an empty string rather than null, and the Setup Link
   * row would render and report a link as outstanding on an account that has none.
   */
  private void renderDetailsPage(WidgetContext context, User user) {
    context.getRequest().setAttribute("user", user);
    context.getRequest().setAttribute("accountLinkState", accountLinkState(user));
    context.setJsp(JSP);
  }

  private WidgetContext resetPassword(WidgetContext context, User user) {
    // Capture the target before the token replaces the reference
    String targetId = String.valueOf(user.getId());
    String targetLabel = user.getEmail();
    // #1836: read the outstanding link's state BEFORE minting a new one. createAccountToken
    // overwrites the single account_token column, so issuing a new link silently stops the
    // previously emailed one from resolving. Admins were told only "instructions have been sent"
    // and reasonably kept resending to help someone mid-click, destroying the very link that
    // person was using.
    boolean replacedLiveLink = LINK_OUTSTANDING.equals(accountLinkState(user));
    // Create an account token and send email
    user = UserRepository.createAccountToken(user);

    // Record the admin-initiated password reset of another user
    AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.password.reset",
        user != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
        "user", targetId, targetLabel, null);

    // The token write did not take (see UserRepository#createAccountToken returning null), so there is
    // no token to email -- report the failure the audit record just captured instead of dereferencing
    // the null reference below. Uses targetLabel, captured before the call, for the address.
    if (user == null) {
      context.setErrorMessage("The password could not be reset for: " + targetLabel);
      return context;
    }

    // Trigger events
    WorkflowManager.triggerWorkflowForEvent(new UserPasswordResetEvent(user, context.getUserSession().getUser()));

    String successMessage = "Password reset instructions have been sent to: " + user.getEmail();
    if (replacedLiveLink) {
      successMessage += ". Any link sent to them earlier has stopped working -- they must use this newest email";
    }
    context.setSuccessMessage(successMessage);
    return context;
  }

  private WidgetContext resetMfa(WidgetContext context, User user) {
    // Not one that outranks the acting admin -- see targetOutranksActor()
    if (targetOutranksActor(context, user)) {
      context.setErrorMessage("You cannot reset MFA for an account with a higher role level than your own");
      return context;
    }
    // Capture the target before disable/clear alter its in-memory state
    String targetId = String.valueOf(user.getId());
    String targetLabel = user.getEmail();

    // Clear the second factor and any unused recovery codes -- reuses the same commands the
    // self-service MyMfaSettingsWidget "disable" action calls on the user's own account.
    boolean disabled = UserMfaCommand.disable(user);
    UserMfaRecoveryCodeCommand.clear(user);

    // Record the admin-initiated MFA reset of another user, matching deleteAccount()'s pattern of
    // reflecting the actual DB-write outcome rather than assuming success.
    if (disabled) {
      AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.mfa.reset",
          AuditEventCommand.SUCCESS, "user", targetId, targetLabel, null);
      context.setSuccessMessage("MFA has been reset for: " + targetLabel + ". They must re-enroll from scratch.");
    } else {
      AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.mfa.reset",
          AuditEventCommand.FAILURE, "user", targetId, targetLabel, "MFA disable write failed");
      context.setErrorMessage("MFA reset failed for: " + targetLabel);
    }
    return context;
  }

  private WidgetContext suspendAccount(WidgetContext context, User user) {
    // Suspend the account (but not own self)
    if (context.getUserId() == user.getId()) {
      context.setErrorMessage("You cannot suspend your own account");
      return context;
    }
    // Nor one that outranks the acting admin -- see targetOutranksActor()
    if (targetOutranksActor(context, user)) {
      context.setErrorMessage("You cannot suspend an account with a higher role level than your own");
      return context;
    }
    String reason = context.getParameter("reason");
    User result = UserRepository.suspendAccount(user, reason);
    AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.disable",
        result != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
        "user", String.valueOf(user.getId()), user.getEmail(), reason);
    context.setSuccessMessage("Account suspended");
    return context;
  }

  private WidgetContext restoreAccount(WidgetContext context, User user) {
    // Restore the account (but not one that outranks the acting admin)
    if (targetOutranksActor(context, user)) {
      context.setErrorMessage("You cannot restore an account with a higher role level than your own");
      return context;
    }
    // Elevated-role accounts (#492 Phase 3) can't be reactivated by one admin acting alone --
    // UnsuspendAccountCommand decides whether this is a direct restore (unchanged behavior for a
    // non-elevated target) or files a request for a second admin to review.
    String reason = context.getParameter("reason");
    User actingAdmin = context.getUserSession() != null ? context.getUserSession().getUser() : null;
    try {
      UnsuspendAccountCommand.Outcome outcome = UnsuspendAccountCommand.requestOrRestore(user, actingAdmin, reason);
      switch (outcome) {
        case RESTORED:
          AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.enable",
              AuditEventCommand.SUCCESS, "user", String.valueOf(user.getId()), user.getEmail(), null);
          context.setSuccessMessage("Account restored");
          break;
        case REQUESTED:
          AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.unsuspend.requested",
              AuditEventCommand.SUCCESS, "user", String.valueOf(user.getId()), user.getEmail(), reason);
          WorkflowManager.triggerWorkflowForEvent(new UnsuspendRequestedEvent(user, actingAdmin, reason));
          context.setSuccessMessage("This account requires a second administrator's review to unsuspend. "
              + "A request was created and eligible admins have been notified.");
          break;
        case ALREADY_PENDING:
          context.setWarningMessage("An unsuspend request is already pending for this account.");
          break;
        case NOT_SUSPENDED:
        default:
          context.setWarningMessage("This account is not currently suspended.");
          break;
      }
    } catch (DataException e) {
      AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.enable",
          AuditEventCommand.FAILURE, "user", String.valueOf(user.getId()), user.getEmail(), e.getMessage());
      context.setErrorMessage(e.getMessage());
    }
    return context;
  }

  private WidgetContext approveUnsuspend(WidgetContext context) {
    long requestId = context.getParameterAsLong("requestId");
    try {
      UnsuspendRequest request = UnsuspendAccountCommand.approve(requestId, context.getUserId());
      String targetId = String.valueOf(request.getTargetUserId());
      AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.unsuspend.approved",
          AuditEventCommand.SUCCESS, "user", targetId, request.getTargetEmail(),
          "requestedBy=" + request.getRequestedByEmail() + "; reason=" + request.getReason());
      AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.password.invalidated",
          AuditEventCommand.SUCCESS, "user", targetId, request.getTargetEmail(), null);
      AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.enable",
          AuditEventCommand.SUCCESS, "user", targetId, request.getTargetEmail(), "via unsuspend approval");

      User target = LoadUserCommand.loadUser(request.getTargetUserId());
      User approvedBy = context.getUserSession() != null ? context.getUserSession().getUser() : null;
      WorkflowManager.triggerWorkflowForEvent(new UserAccountRestoredEvent(target, approvedBy));

      context.setSuccessMessage("Account restored. " + request.getTargetEmail()
          + " must set a new password before they can sign in again.");
    } catch (DataException e) {
      AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.unsuspend.approved",
          AuditEventCommand.FAILURE, "user", null, null, e.getMessage());
      context.setErrorMessage(e.getMessage());
    }
    return context;
  }

  private WidgetContext denyUnsuspend(WidgetContext context) {
    long requestId = context.getParameterAsLong("requestId");
    String denialReason = context.getParameter("denialReason");
    try {
      UnsuspendRequest request = UnsuspendAccountCommand.deny(requestId, context.getUserId(), denialReason);
      AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.unsuspend.denied",
          AuditEventCommand.SUCCESS, "user", String.valueOf(request.getTargetUserId()), request.getTargetEmail(),
          "requestedBy=" + request.getRequestedByEmail() + "; denialReason=" + denialReason);
      context.setSuccessMessage("The unsuspend request was denied");
    } catch (DataException e) {
      AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.unsuspend.denied",
          AuditEventCommand.FAILURE, "user", null, null, e.getMessage());
      context.setErrorMessage(e.getMessage());
    }
    return context;
  }

  /**
   * True when the target account's highest role level exceeds the acting user's highest role level --
   * mirrors UserFormWidget's role-grant escalation guard so a lower-privileged admin (e.g.
   * community-manager, level 90, who reaches this page via admin-layout.xml's
   * role="admin,community-manager") cannot suspend, restore, or delete an account that outranks them
   * (e.g. admin, level 100). Both /admin/users and /admin/user-details are open to community-manager
   * -- and, as of the users:manage capability, to any user holding only that capability with no
   * legacy role at all -- and this is the only check standing between that access and acting on an
   * admin account.
   * <p>
   * Public (not private): UsersListWidget.bulkSuspendAction() and MfaEnrolledRolesWidget's
   * bulk MFA reset both re-check this identical rule per affected account so neither bulk path can
   * reach an account the single-account suspendAccount()/resetMfa() below would refuse to touch.
   */
  public static boolean targetOutranksActor(WidgetContext context, User user) {
    List<Role> allRoles = RoleRepository.findAll();
    int actingLevel = highestRoleLevel(context.getUserSession(), allRoles);
    int targetLevel = highestRoleLevel(user.getRoleList());
    return targetLevel > actingLevel;
  }

  private static int highestRoleLevel(UserSession userSession, List<Role> allRoles) {
    int max = 0;
    if (userSession == null || allRoles == null) {
      return max;
    }
    for (Role role : allRoles) {
      if (userSession.hasRole(role.getCode()) && role.getLevel() > max) {
        max = role.getLevel();
      }
    }
    return max;
  }

  private static int highestRoleLevel(List<Role> roleList) {
    int max = 0;
    if (roleList == null) {
      return max;
    }
    for (Role role : roleList) {
      if (role.getLevel() > max) {
        max = role.getLevel();
      }
    }
    return max;
  }

  private WidgetContext deleteAccount(WidgetContext context, User user) {
    // Attempt to delete the account (but not own self)
    if (context.getUserId() == user.getId()) {
      context.setErrorMessage("You cannot delete your own account");
      return context;
    }
    // Nor one that outranks the acting admin -- see targetOutranksActor(). Without this, any user
    // who can reach this page (including a users:manage capability-only grantee with no admin or
    // community-manager role) could permanently delete any other account, including an admin's.
    if (targetOutranksActor(context, user)) {
      context.setErrorMessage("You cannot delete an account with a higher role level than your own");
      return context;
    }
    // Capture identity before the record is removed
    String targetId = String.valueOf(user.getId());
    String targetLabel = user.getEmail();
    try {
      if (UserRepository.remove(user)) {
        AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.delete",
            AuditEventCommand.SUCCESS, "user", targetId, targetLabel, null);
        context.setSuccessMessage("Account deleted");
      } else {
        AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.delete",
            AuditEventCommand.FAILURE, "user", targetId, targetLabel, "referenced in other tables");
        context.setErrorMessage("Account not deleted - this user is referenced in other database tables");
      }
    } catch (Exception e) {
      AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.delete",
          AuditEventCommand.FAILURE, "user", targetId, targetLabel, e.getMessage());
      context.setErrorMessage("The account could not be deleted: " + e.getMessage());
    }
    return context;
  }

  private WidgetContext unlockAccount(WidgetContext context, User user) {
    // Clear the failed-attempt counter and lockout timestamp so the user can sign in again (#295, AC-7).
    // The clear is idempotent, so the outcome is recorded as a success even if the lock had just expired.
    UserRepository.resetLockout(user.getId());
    AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.unlock",
        AuditEventCommand.SUCCESS, "user", String.valueOf(user.getId()), user.getEmail(), null);
    context.setSuccessMessage("Account unlocked");
    return context;
  }

}
