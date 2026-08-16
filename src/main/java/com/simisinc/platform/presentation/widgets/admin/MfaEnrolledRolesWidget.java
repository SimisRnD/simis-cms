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

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.login.StepUpAuthCommand;
import com.simisinc.platform.application.login.UserMfaCommand;
import com.simisinc.platform.application.login.UserMfaRecoveryCodeCommand;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.UserSpecification;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.widgets.admin.login.UserDetailsWidget;

/**
 * Shows the roles that already have at least one MFA-enrolled member, on the MFA Enforcement
 * Settings page -- the page's own help text warns that enabling enforcement for a role with no
 * enrolled member locks out every member of it, so this gives the admin that answer before they
 * flip the switch instead of finding out after.
 * <p>
 * Also offers a bulk "Remove MFA" action per role, clearing every enrolled member's second factor
 * and recovery codes in one step -- the role-level counterpart to the per-user Reset MFA action on
 * /admin/user-details, for an admin who wants to walk a role back out of enrollment (e.g. before
 * retiring it) without opening each member's page individually.
 *
 * @author SimIS
 * @created 8/16/2026
 */
public class MfaEnrolledRolesWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/mfa-enrolled-roles.jsp";

  public WidgetContext execute(WidgetContext context) {

    if (!context.hasRole("admin")) {
      return context;
    }

    context.getRequest().setAttribute("roleList", RoleRepository.findAllWithMfaEnrolledMember());

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    context.setRedirect("/admin/mfa-properties");

    if (!context.hasRole("admin")) {
      return context;
    }
    if (!"removeMfaFromRole".equals(context.getParameter("action"))) {
      return context;
    }

    Role role = RoleRepository.findById((int) context.getParameterAsLong("roleId"));
    if (role == null) {
      context.setErrorMessage("The role was not found");
      return context;
    }

    // Clearing MFA for another account requires step-up, same bar as the per-user Reset MFA action
    // on /admin/user-details -- and, matching that action's own precedent, kept reachable only
    // through this POST handler, never a plain GET/action request.
    if (!StepUpAuthCommand.isValid(context.getUserSession())) {
      String stepUpCredential = context.getParameter("stepUpCredential");
      User actingUser = StringUtils.isNotBlank(stepUpCredential) ? LoadUserCommand.loadUser(context.getUserId()) : null;
      if (actingUser == null || !StepUpAuthCommand.verify(context.getUserSession(), actingUser, stepUpCredential)) {
        context.setErrorMessage("Re-authentication failed. Enter your password or authenticator code.");
        return context;
      }
    }

    UserSpecification specification = new UserSpecification();
    specification.setRoleId(role.getId());
    specification.setIsMfaEnabled(true);
    List<User> members = UserRepository.findAll(specification, null);
    if (members == null || members.isEmpty()) {
      context.setErrorMessage("No MFA-enrolled members were found for " + role.getTitle());
      return context;
    }

    int succeeded = 0;
    int skippedOutranked = 0;
    int failed = 0;
    for (User user : members) {
      // Same guardrail as the per-user action. /admin/mfa-properties is admin-only today, so this
      // never actually trips against the four built-in roles -- it only matters if a site ever
      // configures a custom role above admin's level, the same case UserDetailsWidget already
      // guards against for its own admin-reachable actions.
      if (UserDetailsWidget.targetOutranksActor(context, user)) {
        ++skippedOutranked;
        continue;
      }
      boolean disabled = UserMfaCommand.disable(user);
      UserMfaRecoveryCodeCommand.clear(user);
      String targetId = String.valueOf(user.getId());
      if (disabled) {
        ++succeeded;
        AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.mfa.reset",
            AuditEventCommand.SUCCESS, "user", targetId, user.getEmail(), "(bulk, role=" + role.getCode() + ")");
      } else {
        ++failed;
        AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.mfa.reset",
            AuditEventCommand.FAILURE, "user", targetId, user.getEmail(),
            "MFA disable write failed (bulk, role=" + role.getCode() + ")");
      }
    }
    AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "role.mfa.bulk_reset",
        succeeded > 0 ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
        "role", String.valueOf(role.getId()), role.getTitle(),
        "reset=" + succeeded + "; skippedOutranked=" + skippedOutranked + "; failed=" + failed);

    setResultMessage(context, role, succeeded, members.size(), skippedOutranked, failed);
    return context;
  }

  private void setResultMessage(WidgetContext context, Role role, int succeeded, int totalMembers,
      int skippedOutranked, int failed) {
    StringBuilder sb = new StringBuilder();
    sb.append("MFA was reset for ").append(succeeded).append(" of ").append(totalMembers)
        .append(" enrolled member").append(totalMembers == 1 ? "" : "s").append(" of ").append(role.getTitle())
        .append(". They must re-enroll from scratch.");
    if (skippedOutranked > 0) {
      sb.append(" Skipped (higher role level than yours): ").append(skippedOutranked).append(".");
    }
    if (failed > 0) {
      sb.append(" Failed: ").append(failed).append(".");
    }
    if (succeeded == 0) {
      context.setErrorMessage(sb.toString());
    } else if (succeeded < totalMembers) {
      context.setWarningMessage(sb.toString());
    } else {
      context.setSuccessMessage(sb.toString());
    }
  }
}
