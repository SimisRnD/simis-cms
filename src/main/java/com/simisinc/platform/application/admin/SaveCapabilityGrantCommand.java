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

package com.simisinc.platform.application.admin;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.domain.model.CapabilityGrant;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.CapabilityGrantRepository;
import com.simisinc.platform.infrastructure.persistence.RoleCapabilityRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Grants/revokes direct, individually-trackable capabilities to a user (issue #702) - the
 * per-user counterpart to SaveRoleCapabilitiesCommand's per-role grants (#704). Expiration of a
 * granted-with-an-expiresAt grant is enforced by CapabilityGrantExpirationJob's scheduled sweep,
 * not here.
 *
 * @author elizabeth houser
 */
public class SaveCapabilityGrantCommand {

  /**
   * Same capability, same self-lockout rationale, as SaveRoleCapabilitiesCommand's
   * ADMIN_MANAGE_CAPABILITY - see that constant's javadoc. Revoking a *direct* grant of it needs
   * the identical guard: a capability-only administrator with no role granting admin:manage is
   * exactly the pattern this feature exists to support, so their direct grant can just as easily
   * be the last thing standing between the system and a hard lockout as a role's grant can.
   */
  private static final String ADMIN_MANAGE_CAPABILITY = "admin:manage";

  public static CapabilityGrant grant(WidgetContext context, User targetUser, Capability capability,
      long grantedByUserId, String reason, java.sql.Timestamp expiresAt) throws DataException {
    if (StringUtils.isBlank(reason)) {
      throw new DataException("A reason is required when granting a capability");
    }

    // A friendlier refusal than letting the DB's unique-active-grant index surface a raw
    // constraint violation - revoke (or wait for expiry) before granting the same one again.
    List<CapabilityGrant> activeGrantList = CapabilityGrantRepository.findActiveByUserId(targetUser.getId());
    if (activeGrantList != null) {
      for (CapabilityGrant existing : activeGrantList) {
        if (existing.getCapabilityId().equals(capability.getId())) {
          throw new DataException(
              targetUser.getUsername() + " already has an active grant of \"" + capability.getCode() +
                  "\" - revoke it first if you want to change its reason or expiration");
        }
      }
    }

    CapabilityGrant record = new CapabilityGrant();
    record.setUserId(targetUser.getId());
    record.setCapabilityId(capability.getId());
    record.setGrantedBy(grantedByUserId);
    record.setReason(reason);
    record.setExpiresAt(expiresAt);
    CapabilityGrant savedRecord = CapabilityGrantRepository.add(record);
    if (savedRecord == null) {
      throw new DataException("The capability grant could not be saved");
    }

    // targetLabel = targetUser.getUsername() so a per-user History link (targetLabel-filtered,
    // matching the role_capability convention where targetLabel = role code) surfaces every
    // direct-grant change made for this user.
    AuditEventCommand.record(context, AuditEventCommand.AUTHORIZATION, "capability_grant.grant",
        AuditEventCommand.SUCCESS, "capability_grant", capability.getCode(), targetUser.getUsername(), reason);
    return savedRecord;
  }

  /**
   * capabilityGrant must have been loaded through CapabilityGrantRepository (findById/
   * findAllByUserId/etc) so its capabilityCode is already populated for the audit event.
   */
  public static void revoke(WidgetContext context, CapabilityGrant capabilityGrant, User targetUser, String reason)
      throws DataException {
    if (StringUtils.isBlank(reason)) {
      throw new DataException("A reason is required when revoking a capability grant");
    }

    if (ADMIN_MANAGE_CAPABILITY.equals(capabilityGrant.getCapabilityCode())) {
      // Effective holders after hypothetically removing *this grant's* contribution - a user
      // covered by a role that also grants admin:manage is still fine without this direct grant,
      // so they must not count against the "would this leave zero holders" check.
      long remainingHoldersAfterRevoke = RoleCapabilityRepository.countDistinctUsersHoldingCapability(
          capabilityGrant.getCapabilityId(), -1, capabilityGrant.getId());
      if (remainingHoldersAfterRevoke == 0) {
        AuditEventCommand.record(context, AuditEventCommand.AUTHORIZATION, "capability_grant.revoke",
            AuditEventCommand.FAILURE, "capability_grant", capabilityGrant.getCapabilityCode(),
            targetUser.getUsername(), "Refused: revoking " + targetUser.getUsername() +
                "'s direct grant would leave no user holding this capability, via any role or direct grant");
        throw new DataException("Cannot revoke \"" + capabilityGrant.getCapabilityCode() + "\" from " +
            targetUser.getUsername() + " - no one would be left holding it, via any role or direct grant, " +
            "and nobody could use this page to grant it back. Grant it to a role or another user first if " +
            "you really want to remove it here.");
      }
    }

    boolean wasRevoked = CapabilityGrantRepository.revoke(capabilityGrant.getId());
    if (!wasRevoked) {
      throw new DataException("The capability grant could not be revoked");
    }
    AuditEventCommand.record(context, AuditEventCommand.AUTHORIZATION, "capability_grant.revoke",
        AuditEventCommand.SUCCESS, "capability_grant", capabilityGrant.getCapabilityCode(),
        targetUser.getUsername(), reason);
  }
}
