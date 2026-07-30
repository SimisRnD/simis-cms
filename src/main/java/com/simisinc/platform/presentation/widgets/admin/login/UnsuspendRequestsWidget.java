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

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.login.StepUpAuthCommand;
import com.simisinc.platform.application.login.UnsuspendAccountCommand;
import com.simisinc.platform.domain.events.cms.UserAccountRestoredEvent;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.UnsuspendRequest;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.login.UnsuspendRequestRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * The approving surface for maker-checker unsuspend requests (issue #492 Phase 3) -- the first
 * "pending items to review" queue page in this admin UI. Approve/Deny here call the exact same
 * {@link UnsuspendAccountCommand#approve}/{@link UnsuspendAccountCommand#deny} a pending request's
 * controls on the target's own /admin/user-details page call -- this widget is a second entry point
 * onto the one shared enforcement point, not a second place the actual mutation logic lives.
 *
 * @author SimIS Inc.
 */
public class UnsuspendRequestsWidget extends GenericWidget {

  static final long serialVersionUID = 1L;

  static String JSP = "/admin/unsuspend-requests-list.jsp";

  public WidgetContext execute(WidgetContext context) {
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "20"));
    int page = context.getParameterAsInt("page", 1);
    DataConstraints constraints = new DataConstraints(page, limit);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    String statusFilter = context.getParameter("statusFilter", UnsuspendRequest.STATUS_PENDING);
    context.getRequest().setAttribute("statusFilter", statusFilter);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING_URI, "&statusFilter=" + statusFilter);

    List<UnsuspendRequest> requestList = UnsuspendRequestRepository.findAll(statusFilter, constraints);
    context.getRequest().setAttribute("requestList", requestList);

    // Viewer's own highest role level -- lets the JSP hint (never enforce) whether Approve should
    // be offered for a given row; the level guard is always re-checked server-side regardless.
    context.getRequest().setAttribute("currentUserId", context.getUserId());
    context.getRequest().setAttribute("currentUserLevel", highestRoleLevel(context.getUserId()));

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {
    if (!(context.hasRole("admin") || context.hasRole("community-manager"))) {
      return context;
    }
    context.getUserSession().renewFormToken();

    String command = context.getParameter("command");
    context.setRedirect("/admin/unsuspend-requests");
    if ("approve".equals(command)) {
      return approveAction(context);
    }
    if ("deny".equals(command)) {
      return denyAction(context);
    }
    return context;
  }

  private WidgetContext approveAction(WidgetContext context) {
    String stepUpCredential = context.getParameter("stepUpCredential");
    if (!StepUpAuthCommand.isValid(context.getUserSession())) {
      if (StringUtils.isBlank(stepUpCredential)) {
        context.setErrorMessage("Re-authentication is required for this action. Enter your password or authenticator code.");
        return context;
      }
      User actingUser = LoadUserCommand.loadUser(context.getUserId());
      if (!StepUpAuthCommand.verify(context.getUserSession(), actingUser, stepUpCredential)) {
        context.setErrorMessage("Re-authentication failed. Enter your password or authenticator code.");
        return context;
      }
    }

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

  private WidgetContext denyAction(WidgetContext context) {
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

  private static int highestRoleLevel(long userId) {
    List<Role> roleList = RoleRepository.findAllByUserId(userId);
    int max = 0;
    if (roleList != null) {
      for (Role role : roleList) {
        if (role.getLevel() > max) {
          max = role.getLevel();
        }
      }
    }
    return max;
  }
}
