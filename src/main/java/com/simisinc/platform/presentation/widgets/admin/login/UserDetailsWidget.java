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
    int maxAgeDays = UserRepository.resolvePasswordMaxAgeDays(LoadSitePropertyCommand.loadByName("password.maxAgeDays"));
    context.getRequest().setAttribute("passwordAgeSeverity", passwordAgeSeverity(user.getLastPasswordChangedAt(), maxAgeDays));

    // Maker-checker unsuspend (#492 Phase 3): an elevated-role account can't be reactivated by one
    // admin acting alone -- the JSP uses these to decide whether "Restore" is a direct action or
    // opens the request-a-review modal, and whether to show a pending request's status/controls.
    context.getRequest().setAttribute("isElevatedTarget", UnsuspendAccountCommand.requiresApproval(user));
    context.getRequest().setAttribute("pendingUnsuspendRequest", UnsuspendRequestRepository.findPendingByTargetUserId(user.getId()));
    context.getRequest().setAttribute("currentUserId", context.getUserId());

    // Show the editor
    context.setJsp(JSP);
    return context;
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
          context.getRequest().setAttribute("user", user);
          context.setJsp(JSP);
          return context;
        }
        User actingUser = LoadUserCommand.loadUser(context.getUserId());
        if (!StepUpAuthCommand.verify(context.getUserSession(), actingUser, stepUpCredential)) {
          context.setErrorMessage("Re-authentication failed. Enter your password or authenticator code.");
          context.addSharedRequestValue("stepUpRequired", "true");
          context.getRequest().setAttribute("user", user);
          context.setJsp(JSP);
          return context;
        }
      }
      context.setRedirect("/admin/user-details?userId=" + userId);
      return resetPassword(context, user);
    }
    if ("approveUnsuspend".equals(action)) {
      // Approving an unsuspend request requires step-up, same bar as Reset Password/Assign Roles --
      // and, matching resetPassword's own comment above, is intentionally kept OUT of action()'s
      // dispatch table so a plain GET/action request can never reach it.
      String stepUpCredential = context.getParameter("stepUpCredential");
      if (!StepUpAuthCommand.isValid(context.getUserSession())) {
        if (StringUtils.isBlank(stepUpCredential)) {
          context.addSharedRequestValue("stepUpRequired", "true");
          context.getRequest().setAttribute("user", user);
          context.setJsp(JSP);
          return context;
        }
        User actingUser = LoadUserCommand.loadUser(context.getUserId());
        if (!StepUpAuthCommand.verify(context.getUserSession(), actingUser, stepUpCredential)) {
          context.setErrorMessage("Re-authentication failed. Enter your password or authenticator code.");
          context.addSharedRequestValue("stepUpRequired", "true");
          context.getRequest().setAttribute("user", user);
          context.setJsp(JSP);
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
    // Note: resetPassword is intentionally NOT handled here -- it requires step-up
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

  private WidgetContext resetPassword(WidgetContext context, User user) {
    // Capture the target before the token replaces the reference
    String targetId = String.valueOf(user.getId());
    String targetLabel = user.getEmail();
    // Create an account token and send email
    user = UserRepository.createAccountToken(user);

    // Record the admin-initiated password reset of another user
    AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.password.reset",
        user != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
        "user", targetId, targetLabel, null);

    // Trigger events
    WorkflowManager.triggerWorkflowForEvent(new UserPasswordResetEvent(user, context.getUserSession().getUser()));

    context.setSuccessMessage("Password reset instructions have been sent to: " + user.getEmail());
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
    User result = UserRepository.restoreAccount(user);
    AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.enable",
        result != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
        "user", String.valueOf(user.getId()), user.getEmail(), null);
    context.setSuccessMessage("Account restored");
    return context;
  }

  /**
   * True when the target account's highest role level exceeds the acting user's highest role level --
   * mirrors UserFormWidget's role-grant escalation guard so a lower-privileged admin (e.g.
   * community-manager, level 90, who reaches this page via admin-layout.xml's
   * role="admin,community-manager") cannot suspend or restore an account that outranks them (e.g.
   * admin, level 100). Both /admin/users and /admin/user-details are open to community-manager, and
   * this is the only check standing between that role and acting on an admin account.
   */
  private static boolean targetOutranksActor(WidgetContext context, User user) {
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
