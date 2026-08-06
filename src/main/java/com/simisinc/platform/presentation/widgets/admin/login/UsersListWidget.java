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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.admin.ProcessUserCSVFileCommand;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.login.StepUpAuthCommand;
import com.simisinc.platform.application.login.UnsuspendAccountCommand;
import com.simisinc.platform.application.register.SaveUserCommand;
import com.simisinc.platform.domain.events.cms.UnsuspendRequestedEvent;
import com.simisinc.platform.domain.events.cms.UserInvitedEvent;
import com.simisinc.platform.domain.events.cms.UserPasswordResetEvent;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.UserLogin;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.UserSpecification;
import com.simisinc.platform.infrastructure.persistence.login.UnsuspendRequestRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserLoginRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.UserSession;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import javax.security.auth.login.AccountException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/24/18 10:06 AM
 */
public class UsersListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/users-list.jsp";

  static final String STATUS_FILTER_ANY = "any";
  static final String STATUS_FILTER_ACTIVE = User.STATUS_ACTIVE;
  static final String STATUS_FILTER_SUSPENDED = User.STATUS_SUSPENDED;
  static final String STATUS_FILTER_LOCKED = User.STATUS_LOCKED;
  static final String STATUS_FILTER_INACTIVE = User.STATUS_INACTIVE;

  static final String MFA_FILTER_ANY = "any";
  static final String MFA_FILTER_ENABLED = "enabled";
  static final String MFA_FILTER_DISABLED = "disabled";

  // A crafted POST is the only thing this bounds -- normal usage never approaches it, since
  // selection is scoped to the current page (default page size 20). An id list over this cap is
  // rejected outright, never silently truncated, so a bulk action can never touch a different set
  // of accounts than the one the admin reviewed in the confirmation modal.
  static final int MAX_BULK_SELECTION = 100;

  public WidgetContext execute(WidgetContext context) {

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "20"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Determine the sorting
//    String sortByValue = context.getParameter("sortBy", "date");
//    String sortOrderValue = context.getParameter("sortOrder", "newest");
//    context.getRequest().setAttribute(RequestConstants.RECORD_SORT_BY, sortByValue);
//    context.getRequest().setAttribute(RequestConstants.RECORD_SORT_ORDER, sortOrderValue);

    // Determine the search
    String query = context.getParameter("query");
    context.getRequest().setAttribute(RequestConstants.RECORD_QUERY, query);

    // Determine the filters
    String statusFilterValue = context.getParameter("statusFilter", STATUS_FILTER_ANY);
    String statusFilter = STATUS_FILTER_ANY;
    if (STATUS_FILTER_ACTIVE.equals(statusFilterValue) || STATUS_FILTER_SUSPENDED.equals(statusFilterValue)
        || STATUS_FILTER_LOCKED.equals(statusFilterValue) || STATUS_FILTER_INACTIVE.equals(statusFilterValue)) {
      statusFilter = statusFilterValue;
    }
    context.getRequest().setAttribute("statusFilter", statusFilter);

    String mfaFilterValue = context.getParameter("mfaFilter", MFA_FILTER_ANY);
    String mfaFilter = (MFA_FILTER_ENABLED.equals(mfaFilterValue) || MFA_FILTER_DISABLED.equals(mfaFilterValue))
        ? mfaFilterValue : MFA_FILTER_ANY;
    context.getRequest().setAttribute("mfaFilter", mfaFilter);

    // "1" is the only supported value today (a simple on/off toggle); the threshold itself comes
    // from the configurable password.maxAgeDays site property, not a request parameter.
    boolean agingPasswordFilter = "1".equals(context.getParameter("agingPasswordFilter"));
    context.getRequest().setAttribute("agingPasswordFilter", agingPasswordFilter ? "1" : "");

    // Configure the paging uri
    String pagingUri = "";
    if (StringUtils.isNotBlank(query)) {
      pagingUri = pagingUri + "&query=" + UrlCommand.encodeUri(query);
    }
    if (StringUtils.isNotBlank(statusFilter)) {
      pagingUri = pagingUri + "&statusFilter=" + UrlCommand.encodeUri(statusFilter);
    }
    if (StringUtils.isNotBlank(mfaFilter)) {
      pagingUri = pagingUri + "&mfaFilter=" + UrlCommand.encodeUri(mfaFilter);
    }
    if (agingPasswordFilter) {
      pagingUri = pagingUri + "&agingPasswordFilter=1";
    }
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING_URI, pagingUri);

    // Determine criteria
    UserSpecification specification = new UserSpecification();
    if (StringUtils.isNotBlank(query)) {
      specification.setMatchesName(query);
    }
    // Each status bucket is the same compound condition User.getAccountStatus() derives from, so
    // the filter always matches what the badge actually shows.
    if (STATUS_FILTER_ACTIVE.equals(statusFilter)) {
      specification.setIsEnabled(true);
      specification.setIsLocked(false);
      specification.setIsVerified(true);
    } else if (STATUS_FILTER_SUSPENDED.equals(statusFilter)) {
      specification.setIsEnabled(false);
    } else if (STATUS_FILTER_LOCKED.equals(statusFilter)) {
      specification.setIsEnabled(true);
      specification.setIsLocked(true);
    } else if (STATUS_FILTER_INACTIVE.equals(statusFilter)) {
      specification.setIsEnabled(true);
      specification.setIsLocked(false);
      specification.setIsVerified(false);
    }
    if (MFA_FILTER_ENABLED.equals(mfaFilter)) {
      specification.setIsMfaEnabled(true);
    } else if (MFA_FILTER_DISABLED.equals(mfaFilter)) {
      specification.setIsMfaEnabled(false);
    }
    if (agingPasswordFilter) {
      int maxAgeDays = UserRepository.resolvePasswordMaxAgeDays(LoadSitePropertyCommand.loadByName("password.maxAgeDays"));
      specification.setPasswordOlderThanDays(maxAgeDays);
    }

    // Load the users, then batch-load their roles and last-login in one query each rather than
    // one query per row (a page of 20 users previously issued 41 round trips: 1 + 20 + 20).
    List<User> userList = UserRepository.findAll(specification, constraints);
    if (!userList.isEmpty()) {
      List<Long> userIds = new ArrayList<>();
      for (User user : userList) {
        userIds.add(user.getId());
      }
      Map<Long, List<Role>> roleListByUserId = RoleRepository.findAllByUserIds(userIds);
      Map<Long, UserLogin> lastLoginByUserId = UserLoginRepository.queryLastLogins(userIds);
      for (User user : userList) {
        user.setRoleList(roleListByUserId.get(user.getId()));
        user.setLastLogin(lastLoginByUserId.get(user.getId()));
      }
    }
    context.getRequest().setAttribute("userList", userList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Set some form values -- the New User form only offers roles the editor is allowed to grant
    List<Role> roleList = RoleRepository.findAll();
    context.getRequest().setAttribute("roleList", roleList);
    context.getRequest().setAttribute("actingRoleLevel",
        UserFormWidget.highestRoleLevel(context.getUserSession(), roleList != null ? roleList : new ArrayList<>()));

    // Set some form values
    List<Group> groupList = GroupRepository.findAll();
    context.getRequest().setAttribute("groupList", groupList);

    // #492 Phase 3: a lightweight discoverability aid for the maker-checker unsuspend queue --
    // a cheap COUNT, not a full nav-badge system.
    context.getRequest().setAttribute("pendingUnsuspendRequestCount", UnsuspendRequestRepository.countPending());

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {
    // Permission is required
    if (!(context.hasRole("admin") || context.hasRole("community-manager"))) {
      return context;
    }

    // Don't accept multiple form posts
    context.getUserSession().renewFormToken();

    // Determine the action
    String command = context.getParameter("command");

    // Upload file command
    if ("uploadCSVFile".equals(command)) {
      return uploadCSVFileAction(context);
    }

    // Bulk actions, selected from /admin/users' checkbox + action-bar UI
    if ("bulkSuspend".equals(command)) {
      return bulkSuspendAction(context);
    }
    if ("bulkUnsuspend".equals(command)) {
      return bulkUnsuspendAction(context);
    }
    if ("bulkResetPassword".equals(command)) {
      return bulkResetPasswordAction(context);
    }
    if ("bulkAssignRoles".equals(command)) {
      return bulkAssignRolesAction(context);
    }

    // Default to adding a user
    return addUserAction(context);
  }

  private WidgetContext uploadCSVFileAction(WidgetContext context) {
    LOG.info("User is uploading a user file...");
    try {
      int userCount = ProcessUserCSVFileCommand.processCSV(context);
      context.setSuccessMessage(userCount + " user" + (userCount != 1 ? "s" : "") + " added");
    } catch (Exception e) {
      context.setErrorMessage(e.getMessage());
    }
    // Determine the page to return to
    context.setRedirect("/admin/users");
    return context;
  }

  private WidgetContext addUserAction(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields
    User userBean = new User();
    BeanUtils.populate(userBean, context.getParameterMap());
    // This action only ever creates a new user -- the New User form never renders an id field, but
    // populate() maps ANY request parameter matching a bean property, so a crafted "id" parameter would
    // otherwise be mass-assigned here and route SaveUserCommand.saveUser() into overwriting an existing
    // account (by id) instead of creating one. Force create semantics regardless of what was submitted.
    userBean.setId(-1L);
    userBean.setCreatedBy(context.getUserId());
    userBean.setModifiedBy(context.getUserId());

    // Default the username to the email
    if (StringUtils.isBlank(userBean.getUsername())) {
      userBean.setUsername(userBean.getEmail());
    }

    // Populate the roles -- an editor may only grant roles at or below their own highest role level,
    // the same rule UserFormWidget.post() enforces when editing an existing user (see its
    // highestRoleLevel() for details). This is a new user, so there is no prior role to preserve.
    List<Role> roleList = RoleRepository.findAll();
    if (roleList != null) {
      int actingLevel = UserFormWidget.highestRoleLevel(context.getUserSession(), roleList);
      List<Role> userRoleList = new ArrayList<>();
      for (Role role : roleList) {
        String roleValue = context.getParameter("roleId" + role.getId());
        if (roleValue == null || !roleValue.equals(String.valueOf(role.getId()))) {
          continue;
        }
        if (role.getLevel() <= actingLevel) {
          LOG.debug("Adding user to role: " + role.getCode());
          userRoleList.add(role);
        } else {
          LOG.warn("Blocked role escalation: user " + context.getUserId() + " (level " + actingLevel
              + ") attempted to grant '" + role.getCode() + "' (level " + role.getLevel() + ") to a new user");
        }
      }
      userBean.setRoleList(userRoleList);
    }

    // Populate the user groups
    List<Group> groupList = GroupRepository.findAll();
    if (groupList != null) {
      List<Group> userGroupList = new ArrayList<>();
      for (Group group : groupList) {
        // Always add the user to "All Users"
        if (group.getName().equals("All Users")) {
          userGroupList.add(group);
          continue;
        }
        // Add if the Checkbox was selected
        String groupValue = context.getParameter("groupId" + group.getId());
        if (groupValue != null && groupValue.equals(String.valueOf(group.getId()))) {
          LOG.debug("Adding user to group: " + group.getName());
          userGroupList.add(group);
        }
      }
      userBean.setGroupList(userGroupList);
    }

    // Save the user
    User user = null;
    try {
      user = SaveUserCommand.saveUser(userBean);
      if (user == null) {
        throw new DataException("The information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | AccountException e) {
      LOG.error("Save user error", e);
      AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.create", AuditEventCommand.FAILURE,
          "user", String.valueOf(userBean.getId()), userBean.getEmail(), e.getMessage());
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(userBean);
      return context;
    }

    // Record the new account with its initial roles and groups
    AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.create", AuditEventCommand.SUCCESS,
        "user", String.valueOf(user.getId()), user.getEmail(), AuditEventCommand.describeRolesAndGroups(user));

    // Trigger events
    User invitedBy = LoadUserCommand.loadUser(user.getCreatedBy());
    WorkflowManager.triggerWorkflowForEvent(new UserInvitedEvent(user, invitedBy));

    // Determine the page to return to
    context.setSuccessMessage("User was added, and an email invitation was sent with further instructions");
    context.setRedirect("/admin/users");
    return context;
  }

  private WidgetContext bulkSuspendAction(WidgetContext context) {
    List<Long> userIds = resolveSelectedUserIds(context);
    if (userIds == null) {
      return rejectBulkSelection(context);
    }
    if (userIds.isEmpty()) {
      return rejectEmptySelection(context);
    }
    String reason = context.getParameter("reason");

    BulkActor actor = new BulkActor(context);
    int succeeded = 0;
    int skippedSelf = 0;
    int notFound = 0;
    int failed = 0;
    for (Long userId : userIds) {
      // Re-checked here regardless of what the client's checkbox UI shows -- the guard must live
      // in this loop, since UserRepository.suspendAccount() itself has no self-suspend guard.
      if (context.getUserId() == userId) {
        ++skippedSelf;
        continue;
      }
      User user = LoadUserCommand.loadUser(userId);
      if (user == null) {
        ++notFound;
        continue;
      }
      User result = UserRepository.suspendAccount(user, reason);
      String outcome = result != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (result != null) {
        ++succeeded;
      } else {
        ++failed;
      }
      SaveAuditEventCommand.recordAdminEvent(AuditEventCommand.USER_MANAGEMENT, "user.disable", outcome,
          actor.userId, actor.username, actor.ip, actor.sessionId,
          "user", String.valueOf(user.getId()), user.getEmail(), reason + " (bulk)");
    }
    SaveAuditEventCommand.recordAdminEvent(AuditEventCommand.USER_MANAGEMENT, "user.bulk_disable",
        succeeded > 0 ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
        actor.userId, actor.username, actor.ip, actor.sessionId, "user", null, null,
        "suspended=" + succeeded + "; skippedSelf=" + skippedSelf + "; notFound=" + notFound
            + "; failed=" + failed + "; reason=" + reason);

    setBulkResultMessage(context, "suspended", succeeded, 0, userIds.size(), skippedSelf, notFound, failed);
    context.setRedirect("/admin/users");
    return context;
  }

  private WidgetContext bulkUnsuspendAction(WidgetContext context) {
    List<Long> userIds = resolveSelectedUserIds(context);
    if (userIds == null) {
      return rejectBulkSelection(context);
    }
    if (userIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    // Elevated-role accounts (#492 Phase 3) route through the same maker-checker gate the
    // single-user restoreAccount() action uses -- UnsuspendAccountCommand is the one shared
    // enforcement point, so bulk can never bypass what the single-user path requires.
    String reason = context.getParameter("reason");
    User actingAdmin = context.getUserSession() != null ? context.getUserSession().getUser() : null;

    BulkActor actor = new BulkActor(context);
    int succeeded = 0;
    int requested = 0;
    int alreadyPendingOrNotSuspended = 0;
    int reasonRequired = 0;
    int notFound = 0;
    int failed = 0;
    for (Long userId : userIds) {
      User user = LoadUserCommand.loadUser(userId);
      if (user == null) {
        ++notFound;
        continue;
      }
      if (UnsuspendAccountCommand.requiresApproval(user) && StringUtils.isBlank(reason)) {
        ++reasonRequired;
        continue;
      }
      try {
        UnsuspendAccountCommand.Outcome outcome = UnsuspendAccountCommand.requestOrRestore(user, actingAdmin, reason);
        if (outcome == UnsuspendAccountCommand.Outcome.RESTORED) {
          ++succeeded;
          SaveAuditEventCommand.recordAdminEvent(AuditEventCommand.USER_MANAGEMENT, "user.enable",
              AuditEventCommand.SUCCESS, actor.userId, actor.username, actor.ip, actor.sessionId,
              "user", String.valueOf(user.getId()), user.getEmail(), "(bulk)");
        } else if (outcome == UnsuspendAccountCommand.Outcome.REQUESTED) {
          ++requested;
          SaveAuditEventCommand.recordAdminEvent(AuditEventCommand.USER_MANAGEMENT, "user.unsuspend.requested",
              AuditEventCommand.SUCCESS, actor.userId, actor.username, actor.ip, actor.sessionId,
              "user", String.valueOf(user.getId()), user.getEmail(), reason + " (bulk)");
          WorkflowManager.triggerWorkflowForEvent(new UnsuspendRequestedEvent(user, actingAdmin, reason));
        } else {
          // ALREADY_PENDING or NOT_SUSPENDED -- a no-op, not a failure
          ++alreadyPendingOrNotSuspended;
        }
      } catch (DataException e) {
        ++failed;
        SaveAuditEventCommand.recordAdminEvent(AuditEventCommand.USER_MANAGEMENT, "user.enable",
            AuditEventCommand.FAILURE, actor.userId, actor.username, actor.ip, actor.sessionId,
            "user", String.valueOf(user.getId()), user.getEmail(), e.getMessage() + " (bulk)");
      }
    }
    SaveAuditEventCommand.recordAdminEvent(AuditEventCommand.USER_MANAGEMENT, "user.bulk_enable",
        (succeeded + requested) > 0 ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
        actor.userId, actor.username, actor.ip, actor.sessionId, "user", null, null,
        "restored=" + succeeded + "; requested=" + requested + "; alreadyPendingOrNotSuspended="
            + alreadyPendingOrNotSuspended + "; reasonRequired=" + reasonRequired + "; notFound=" + notFound
            + "; failed=" + failed);

    setBulkUnsuspendResultMessage(context, succeeded, requested, alreadyPendingOrNotSuspended, reasonRequired,
        userIds.size(), notFound, failed);
    context.setRedirect("/admin/users");
    return context;
  }

  /**
   * Bulk unsuspend has a richer outcome space than the other 3 bulk actions (a target can be
   * restored directly OR filed as a pending request), so it builds its own aggregate message
   * rather than forcing that shape into {@link #setBulkResultMessage}.
   */
  private void setBulkUnsuspendResultMessage(WidgetContext context, int succeeded, int requested,
      int alreadyPendingOrNotSuspended, int reasonRequired, int totalSelected, int notFound, int failed) {
    StringBuilder sb = new StringBuilder();
    sb.append(succeeded).append(" of ").append(totalSelected).append(" selected account")
        .append(totalSelected == 1 ? "" : "s").append(" restored.");
    if (requested > 0) {
      sb.append(" ").append(requested).append(" require").append(requested == 1 ? "s" : "")
          .append(" a second administrator's review and ").append(requested == 1 ? "was" : "were")
          .append(" submitted for approval.");
    }
    if (alreadyPendingOrNotSuspended > 0) {
      sb.append(" Already pending review or not suspended: ").append(alreadyPendingOrNotSuspended).append(".");
    }
    if (reasonRequired > 0) {
      sb.append(" Needs a reason (elevated account): ").append(reasonRequired).append(".");
    }
    if (notFound > 0) {
      sb.append(" Not found: ").append(notFound).append(".");
    }
    if (failed > 0) {
      sb.append(" Failed: ").append(failed).append(".");
    }
    boolean allAccountedFor = (succeeded + requested) == totalSelected;
    if (succeeded == 0 && requested == 0) {
      context.setErrorMessage(sb.toString());
    } else if (!allAccountedFor) {
      context.setWarningMessage(sb.toString());
    } else {
      context.setSuccessMessage(sb.toString());
    }
  }

  private WidgetContext bulkResetPasswordAction(WidgetContext context) {
    if (!requireStepUp(context)) {
      return context;
    }
    List<Long> userIds = resolveSelectedUserIds(context);
    if (userIds == null) {
      return rejectBulkSelection(context);
    }
    if (userIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    BulkActor actor = new BulkActor(context);
    User actingUser = context.getUserSession() != null ? context.getUserSession().getUser() : null;
    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    for (Long userId : userIds) {
      User user = LoadUserCommand.loadUser(userId);
      if (user == null) {
        ++notFound;
        continue;
      }
      User result = UserRepository.createAccountToken(user);
      String outcome = result != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (result != null) {
        ++succeeded;
        WorkflowManager.triggerWorkflowForEvent(new UserPasswordResetEvent(result, actingUser));
      } else {
        ++failed;
      }
      SaveAuditEventCommand.recordAdminEvent(AuditEventCommand.USER_MANAGEMENT, "user.password.reset", outcome,
          actor.userId, actor.username, actor.ip, actor.sessionId,
          "user", String.valueOf(user.getId()), user.getEmail(), "(bulk)");
    }
    SaveAuditEventCommand.recordAdminEvent(AuditEventCommand.USER_MANAGEMENT, "user.bulk_password_reset",
        succeeded > 0 ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
        actor.userId, actor.username, actor.ip, actor.sessionId, "user", null, null,
        "reset=" + succeeded + "; notFound=" + notFound + "; failed=" + failed);

    setBulkResultMessage(context, "sent a password reset email", succeeded, 0, userIds.size(), 0, notFound, failed);
    context.setRedirect("/admin/users");
    return context;
  }

  private WidgetContext bulkAssignRolesAction(WidgetContext context) {
    if (!requireStepUp(context)) {
      return context;
    }

    Role role = RoleRepository.findById((int) context.getParameterAsLong("roleId"));
    if (role == null) {
      context.setErrorMessage("The selected role was not found");
      context.setRedirect("/admin/users");
      return context;
    }
    // The requested role's level is always resolved server-side and compared against the actor's
    // own highest role level -- reusing UserFormWidget's exact escalation-level logic -- and the
    // WHOLE batch is rejected up front if it's above that level, never silently downgraded and
    // never applied to some accounts but not others.
    int actingLevel = UserFormWidget.highestRoleLevel(context.getUserSession(), RoleRepository.findAll());
    if (role.getLevel() > actingLevel) {
      LOG.warn("Blocked bulk role escalation: user " + context.getUserId() + " (level " + actingLevel
          + ") attempted to bulk-grant '" + role.getCode() + "' (level " + role.getLevel() + ")");
      context.setErrorMessage("You cannot grant a role above your own level");
      context.setRedirect("/admin/users");
      return context;
    }

    List<Long> userIds = resolveSelectedUserIds(context);
    if (userIds == null) {
      return rejectBulkSelection(context);
    }
    if (userIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    BulkActor actor = new BulkActor(context);
    int succeeded = 0;
    int alreadyHadRole = 0;
    int notFound = 0;
    int failed = 0;
    for (Long userId : userIds) {
      User user = LoadUserCommand.loadUser(userId);
      if (user == null) {
        ++notFound;
        continue;
      }
      if (user.hasRole(role.getCode())) {
        // Additive-only: a target that already holds the role is a no-op success, not a failure.
        ++alreadyHadRole;
        continue;
      }
      // Add the role to the target's REAL current role list (never a thin bean) -- additive-only,
      // so this can never strip a role the admin wasn't even thinking about, and structurally
      // cannot trigger SaveUserCommand.saveUser()'s self-admin-removal guard since nothing is
      // ever removed.
      List<Role> userRoleList = new ArrayList<>(user.getRoleList() != null ? user.getRoleList() : new ArrayList<>());
      userRoleList.add(role);
      user.setRoleList(userRoleList);
      user.setModifiedBy(context.getUserId());
      User result = null;
      try {
        result = SaveUserCommand.saveUser(user);
      } catch (DataException | AccountException e) {
        LOG.error("Bulk role assignment error for user " + userId + ": " + e.getMessage(), e);
      }
      String outcome = result != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (result != null) {
        ++succeeded;
      } else {
        ++failed;
      }
      SaveAuditEventCommand.recordAdminEvent(AuditEventCommand.USER_MANAGEMENT, "user.update", outcome,
          actor.userId, actor.username, actor.ip, actor.sessionId,
          "user", String.valueOf(user.getId()), user.getEmail(),
          AuditEventCommand.describeRolesAndGroups(result != null ? result : user) + " (bulk)");
    }
    SaveAuditEventCommand.recordAdminEvent(AuditEventCommand.USER_MANAGEMENT, "user.bulk_role_assign",
        succeeded > 0 ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
        actor.userId, actor.username, actor.ip, actor.sessionId, "user", null, null,
        "role=" + role.getCode() + "; granted=" + succeeded + "; alreadyHadRole=" + alreadyHadRole
            + "; notFound=" + notFound + "; failed=" + failed);

    setBulkResultMessage(context, "granted the " + role.getTitle() + " role", succeeded, alreadyHadRole,
        userIds.size(), 0, notFound, failed);
    context.setRedirect("/admin/users");
    return context;
  }

  /**
   * Requires a recent step-up re-authentication before a bulk action proceeds, checked once for
   * the whole batch (the 5-minute validity window is session-scoped, not per-target). Unlike the
   * single-user forms, a failure here rejects the whole request rather than re-rendering the same
   * page with the prior selection preserved -- reconstructing "which modal was open, with which
   * accounts checked" on a list page is materially more state to carry than this rare failure path
   * is worth; the admin re-selects and retries.
   */
  private boolean requireStepUp(WidgetContext context) {
    if (StepUpAuthCommand.isValid(context.getUserSession())) {
      return true;
    }
    String stepUpCredential = context.getParameter("stepUpCredential");
    if (StringUtils.isNotBlank(stepUpCredential)) {
      User actingUser = LoadUserCommand.loadUser(context.getUserId());
      if (StepUpAuthCommand.verify(context.getUserSession(), actingUser, stepUpCredential)) {
        return true;
      }
      context.setErrorMessage("Re-authentication failed. Enter your password or authenticator code.");
    } else {
      context.setErrorMessage("Re-authentication is required for this action. Enter your password or authenticator code.");
    }
    context.setRedirect("/admin/users");
    return false;
  }

  /**
   * Parses and dedupes the selected user ids from the repeated {@code userId} hidden inputs the
   * bulk modals inject, silently dropping any non-numeric entry (a tampered value is not a
   * batch-ending error). Returns {@code null} when the list exceeds {@link #MAX_BULK_SELECTION} --
   * the whole request is then rejected rather than silently truncated, since truncation could apply
   * the action to a different subset of accounts than the one the admin reviewed and confirmed.
   */
  private List<Long> resolveSelectedUserIds(WidgetContext context) {
    String[] rawIds = context.getParameterMap().get("userId");
    List<Long> ids = new ArrayList<>();
    if (rawIds != null) {
      for (String rawId : rawIds) {
        try {
          long id = Long.parseLong(rawId.trim());
          if (!ids.contains(id)) {
            ids.add(id);
          }
        } catch (NumberFormatException e) {
          // Dropped, not treated as a batch-ending error
        }
      }
    }
    if (ids.size() > MAX_BULK_SELECTION) {
      LOG.warn("Bulk user action rejected: " + ids.size() + " ids exceeds MAX_BULK_SELECTION ("
          + MAX_BULK_SELECTION + ")");
      return null;
    }
    return ids;
  }

  private WidgetContext rejectBulkSelection(WidgetContext context) {
    context.setErrorMessage("Too many accounts were selected (maximum " + MAX_BULK_SELECTION
        + "). Select fewer accounts and try again.");
    context.setRedirect("/admin/users");
    return context;
  }

  private WidgetContext rejectEmptySelection(WidgetContext context) {
    context.setErrorMessage("No accounts were selected");
    context.setRedirect("/admin/users");
    return context;
  }

  /**
   * Sets the single aggregate result message every other action on this page already relies on
   * (page_messages.jspf renders exactly one of success/warning/error). Full per-account detail of
   * which accounts failed, and why, is not reconstructable from this string by design -- it lives
   * in the audit log instead, where every account gets its own event regardless of outcome.
   */
  private void setBulkResultMessage(WidgetContext context, String verb, int succeeded, int alreadyDone,
      int totalSelected, int skippedSelf, int notFound, int failed) {
    StringBuilder sb = new StringBuilder();
    sb.append(succeeded).append(" of ").append(totalSelected).append(" selected account")
        .append(totalSelected == 1 ? "" : "s").append(" ").append(verb).append(".");
    if (alreadyDone > 0) {
      sb.append(" Already had it: ").append(alreadyDone).append(".");
    }
    if (skippedSelf > 0) {
      sb.append(" Skipped: your own account.");
    }
    if (notFound > 0) {
      sb.append(" Not found: ").append(notFound).append(".");
    }
    if (failed > 0) {
      sb.append(" Failed: ").append(failed).append(".");
    }
    boolean allAccountedFor = (succeeded + alreadyDone) == totalSelected;
    if (succeeded == 0 && alreadyDone == 0) {
      context.setErrorMessage(sb.toString());
    } else if (!allAccountedFor) {
      context.setWarningMessage(sb.toString());
    } else {
      context.setSuccessMessage(sb.toString());
    }
  }

  /**
   * The acting admin's audit identity, resolved once per bulk request so the per-account audit
   * calls inside the loop don't re-derive it on every iteration -- mirrors the pattern
   * ProcessUserCSVFileCommand already established for this exact widget's CSV-import bulk path.
   */
  private static final class BulkActor {
    final long userId;
    final String username;
    final String ip;
    final String sessionId;

    BulkActor(WidgetContext context) {
      this.userId = context.getUserId();
      String resolvedUsername = null;
      String resolvedIp = null;
      String resolvedSessionId = null;
      UserSession userSession = context.getUserSession();
      if (userSession != null) {
        resolvedSessionId = userSession.getSessionId();
        resolvedIp = userSession.getIpAddress();
        if (userSession.getUserId() > -1L && userSession.getUser() != null) {
          resolvedUsername = userSession.getUser().getEmail();
        }
      }
      if (context.getRequest() != null && context.getRequest().getRemoteAddr() != null) {
        resolvedIp = context.getRequest().getRemoteAddr();
      }
      this.username = resolvedUsername;
      this.ip = resolvedIp;
      this.sessionId = resolvedSessionId;
    }
  }
}
