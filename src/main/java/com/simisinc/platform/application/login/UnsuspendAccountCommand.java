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

package com.simisinc.platform.application.login;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.UnsuspendRequest;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.login.UnsuspendRequestRepository;

/**
 * The single enforcement point for unsuspending an account (issue #492 Phase 3). A target holding
 * a role at or above the community-manager level cannot be reactivated by one administrator acting
 * alone: {@link #requestOrRestore} files a request instead of restoring directly, and a SECOND
 * administrator (never the requester) must {@link #approve} it before the account comes back.
 * Approval also invalidates the account's current password immediately -- it stays unable to log
 * in until the account holder completes a forced password reset via the emailed token link.
 *
 * <p>A non-elevated target is restored exactly as before (byte-for-byte the same
 * {@link UserRepository#restoreAccount} call) -- zero new friction for the common case.
 *
 * <p>This is a pure domain command with no {@code WidgetContext} dependency, matching
 * {@code ContentReviewCommand}'s style; callers (widgets) are responsible for audit logging and
 * triggering notification workflows off the {@link Outcome} / returned {@link UnsuspendRequest}.
 *
 * @author SimIS Inc.
 */
public class UnsuspendAccountCommand {

  private static Log LOG = LogFactory.getLog(UnsuspendAccountCommand.class);

  /** Fallback used only if the community-manager role is somehow missing from the seed data. */
  private static final int FALLBACK_ELEVATED_ROLE_LEVEL = 90;

  /**
   * Distinct from the existing "new" sentinel {@code SaveUserCommand} uses for a brand-new invite,
   * so the two states are never confused. Verified safe against this codebase's actual Argon2
   * implementation: a non-null malformed hash makes {@code verify()} return false, never throw --
   * the only unsafe value for the NOT NULL {@code users.password} column is {@code null} itself.
   */
  private static final String PASSWORD_INVALIDATED_SENTINEL = "!unsuspend-pending-reverification!";

  public enum Outcome {
    RESTORED, REQUESTED, ALREADY_PENDING, NOT_SUSPENDED
  }

  private UnsuspendAccountCommand() {
  }

  public static boolean requiresApproval(User target) {
    if (target == null) {
      return false;
    }
    return RoleLevelCommand.highestRoleLevel(target.getRoleList()) >= elevatedRoleThreshold();
  }

  /**
   * The single call both {@code UserDetailsWidget.restoreAccount()} and
   * {@code UsersListWidget.bulkUnsuspendAction()} route through instead of calling
   * {@code UserRepository.restoreAccount()} directly.
   */
  public static Outcome requestOrRestore(User target, User actingAdmin, String reason) throws DataException {
    if (target == null || actingAdmin == null) {
      throw new DataException("A required account was not found");
    }
    // Defensive only: a suspended account cannot hold an authenticated session in this codebase
    // (WebRequestFilter re-verifies isEnabled() on every request), so this is structurally
    // unreachable today -- kept as cheap insurance against that behavior changing later.
    if (actingAdmin.getId() == target.getId()) {
      throw new DataException("You cannot request the unsuspension of your own account");
    }
    if (target.isEnabled()) {
      return Outcome.NOT_SUSPENDED;
    }
    if (!requiresApproval(target)) {
      User result = UserRepository.restoreAccount(target);
      if (result == null) {
        throw new DataException("The account could not be restored");
      }
      return Outcome.RESTORED;
    }
    if (UnsuspendRequestRepository.findPendingByTargetUserId(target.getId()) != null) {
      return Outcome.ALREADY_PENDING;
    }
    if (StringUtils.isBlank(reason)) {
      throw new DataException("A reason is required to request an unsuspend for this account");
    }
    UnsuspendRequest request = new UnsuspendRequest();
    request.setTargetUserId(target.getId());
    request.setTargetEmail(target.getEmail());
    request.setTargetRoleSnapshot(describeRoles(target.getRoleList()));
    request.setRequestedBy(actingAdmin.getId());
    request.setRequestedByEmail(actingAdmin.getEmail());
    request.setReason(reason);
    if (UnsuspendRequestRepository.add(request) == null) {
      throw new DataException("The unsuspend request could not be created");
    }
    return Outcome.REQUESTED;
  }

  /**
   * Approves a pending request: restores the account and invalidates its current password in the
   * same operation, leaving it unable to log in until the account holder completes a forced reset.
   * Re-validates everything server-side from a freshly-loaded approver and target, never trusting
   * anything about either from a prior step -- separation of duties (a different admin than the
   * requester) plus an escalation guard (the approver's own highest role level must be at least the
   * target's, closing a two-lower-privileged-admins-collude gap).
   */
  public static UnsuspendRequest approve(long requestId, long approvingAdminId) throws DataException {
    UnsuspendRequest request = requirePending(requestId);
    User approvingAdmin = LoadUserCommand.loadUser(approvingAdminId);
    if (approvingAdmin == null) {
      throw new DataException("The approving administrator's account was not found");
    }
    requireApproverIsNotRequester(request, approvingAdmin.getId());
    User target = LoadUserCommand.loadUser(request.getTargetUserId());
    if (target == null) {
      throw new DataException("The account for this request was not found");
    }
    int approverLevel = RoleLevelCommand.highestRoleLevel(approvingAdmin.getRoleList());
    int targetLevel = RoleLevelCommand.highestRoleLevel(target.getRoleList());
    if (approverLevel < targetLevel) {
      LOG.warn("Blocked unsuspend approval: user " + approvingAdmin.getId() + " (level " + approverLevel
          + ") attempted to approve unsuspending user " + target.getId() + " (level " + targetLevel + ")");
      throw new DataException("You cannot approve unsuspending an account with a role above your own level");
    }

    // Atomic claim FIRST -- if another admin already decided this request, this affects zero rows
    // and the whole approval is refused before any account mutation happens.
    if (!UnsuspendRequestRepository.claimForApproval(requestId, approvingAdmin.getId(), approvingAdmin.getEmail())) {
      throw new DataException("This request has already been decided");
    }

    // Fail-safe order: invalidate the old password, THEN mint the reset token, THEN restore. A
    // crash mid-sequence leaves the account still suspended, never enabled-with-a-working-old-password.
    target.setPassword(PASSWORD_INVALIDATED_SENTINEL);
    if (UserRepository.updatePassword(target) == null) {
      throw new DataException("The account's password could not be invalidated");
    }
    if (UserRepository.createAccountToken(target) == null) {
      throw new DataException("A password reset token could not be created");
    }
    if (UserRepository.restoreAccount(target) == null) {
      throw new DataException("The account could not be restored");
    }

    request.setStatus(UnsuspendRequest.STATUS_APPROVED);
    request.setDecidedBy(approvingAdmin.getId());
    request.setDecidedByEmail(approvingAdmin.getEmail());
    return request;
  }

  /** No step-up, no role-level check -- any eligible admin/community-manager who isn't the requester may deny. */
  public static UnsuspendRequest deny(long requestId, long decidingAdminId, String denialReason) throws DataException {
    if (StringUtils.isBlank(denialReason)) {
      throw new DataException("A reason is required to deny this request");
    }
    UnsuspendRequest request = requirePending(requestId);
    User decidingAdmin = LoadUserCommand.loadUser(decidingAdminId);
    if (decidingAdmin == null) {
      throw new DataException("The deciding administrator's account was not found");
    }
    requireApproverIsNotRequester(request, decidingAdmin.getId());

    if (!UnsuspendRequestRepository.claimForDenial(requestId, decidingAdmin.getId(), decidingAdmin.getEmail(),
        denialReason)) {
      throw new DataException("This request has already been decided");
    }

    request.setStatus(UnsuspendRequest.STATUS_DENIED);
    request.setDecidedBy(decidingAdmin.getId());
    request.setDecidedByEmail(decidingAdmin.getEmail());
    request.setDecisionReason(denialReason);
    return request;
  }

  private static UnsuspendRequest requirePending(long requestId) throws DataException {
    UnsuspendRequest request = UnsuspendRequestRepository.findById(requestId);
    if (request == null || !request.isPending()) {
      throw new DataException("This request has already been decided or no longer exists");
    }
    return request;
  }

  private static void requireApproverIsNotRequester(UnsuspendRequest request, long deciderId) throws DataException {
    if (deciderId <= 0) {
      throw new DataException("A valid administrator is required");
    }
    if (deciderId == request.getRequestedBy()) {
      throw new DataException(
          "The reviewer must be different from the administrator who requested this (separation of duties)");
    }
  }

  private static int elevatedRoleThreshold() {
    Role communityManagerRole = RoleRepository.findByCode("community-manager");
    if (communityManagerRole == null) {
      LOG.error("The community-manager role was not found; falling back to level " + FALLBACK_ELEVATED_ROLE_LEVEL);
      return FALLBACK_ELEVATED_ROLE_LEVEL;
    }
    return communityManagerRole.getLevel();
  }

  private static String describeRoles(List<Role> roleList) {
    if (roleList == null || roleList.isEmpty()) {
      return "(none)";
    }
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Role role : roleList) {
      if (!first) {
        sb.append(",");
      }
      sb.append(role.getCode());
      first = false;
    }
    return sb.toString();
  }
}
