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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.persistence.CapabilityRepository;
import com.simisinc.platform.infrastructure.persistence.RoleCapabilityRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Grants/revokes capabilities for a role (issue #704), recording an audit event per change with
 * the admin's stated reason. The only runtime mutation path for role_capabilities - everything
 * the #701 walking skeleton seeded was a one-time migration INSERT.
 *
 * @author elizabeth houser
 */
public class SaveRoleCapabilitiesCommand {

  private static Log LOG = LogFactory.getLog(SaveRoleCapabilitiesCommand.class);

  /**
   * The single capability that gates access to this very page (RoleCapabilitiesFormWidget/
   * RoleCapabilitiesListWidget both require it). If it ever reached zero *effective* holders -
   * zero distinct users covered by either a role that grants it or their own direct grant of it -
   * nobody could use this page (or SaveCapabilityGrantCommand's per-user grant UI) to undo the
   * mistake - a hard, unrecoverable lockout, not just an inconvenience. Guarded specifically, not
   * generalized to every capability: revoking any *other* capability down to zero holders (e.g.
   * retiring a role's access to a sunset feature) is a legitimate admin decision this command
   * should not block.
   */
  private static final String ADMIN_MANAGE_CAPABILITY = "admin:manage";

  public static void save(WidgetContext context, Role role, Set<String> submittedCapabilityCodes, String reason)
      throws DataException {
    if (StringUtils.isBlank(reason)) {
      throw new DataException("A reason is required when changing role permissions");
    }

    List<Capability> allCapabilities = CapabilityRepository.findAll();
    if (allCapabilities == null) {
      return;
    }
    List<Capability> currentlyGranted = CapabilityRepository.findAllByRoleId(role.getId());
    Set<String> currentCodes = toCodeSet(currentlyGranted);

    List<Capability> toGrant = new ArrayList<>();
    List<Capability> toRevoke = new ArrayList<>();
    for (Capability capability : allCapabilities) {
      boolean isCurrentlyGranted = currentCodes.contains(capability.getCode());
      boolean shouldBeGranted = submittedCapabilityCodes.contains(capability.getCode());
      if (isCurrentlyGranted && !shouldBeGranted) {
        toRevoke.add(capability);
      } else if (!isCurrentlyGranted && shouldBeGranted) {
        toGrant.add(capability);
      }
    }

    // The admin:manage revoke, if present, is validated and applied first, as a single transaction
    // serialized by RoleCapabilityRepository's advisory lock - see
    // revokeAdminManageRoleCapabilityGuarded. It runs before any other grant/revoke in this save so
    // a refused revoke still leaves every other change untouched, the same as the old "validate
    // everything before applying anything" ordering did. At most one capability in toRevoke can be
    // admin:manage.
    for (Capability capability : toRevoke) {
      if (ADMIN_MANAGE_CAPABILITY.equals(capability.getCode())) {
        revokeAdminManageRoleCapabilityGuarded(context, role, capability, reason);
        break;
      }
    }

    for (Capability capability : toGrant) {
      boolean wasGranted = RoleCapabilityRepository.grant(role.getId(), capability.getId());
      if (wasGranted) {
        AuditEventCommand.record(context, AuditEventCommand.AUTHORIZATION, "role_capability.grant",
            AuditEventCommand.SUCCESS, "role_capability", capability.getCode(), role.getCode(), reason);
      }
    }
    for (Capability capability : toRevoke) {
      if (ADMIN_MANAGE_CAPABILITY.equals(capability.getCode())) {
        continue; // already validated and applied above, inside the guard's own transaction
      }
      boolean wasRevoked = RoleCapabilityRepository.revoke(role.getId(), capability.getId());
      if (wasRevoked) {
        AuditEventCommand.record(context, AuditEventCommand.AUTHORIZATION, "role_capability.revoke",
            AuditEventCommand.SUCCESS, "role_capability", capability.getCode(), role.getCode(), reason);
      }
    }
  }

  /**
   * Validates and applies the admin:manage revoke for {@code role} as a single transaction,
   * serialized by RoleCapabilityRepository's admin:manage guard lock, so the check-then-revoke is
   * atomic with respect to every other concurrent admin:manage revoke - whether another role here
   * or a direct grant via SaveCapabilityGrantCommand. Without this, two concurrent revokes could
   * each count the other's still-live holder, both pass the guard, and both commit, leaving zero
   * effective holders. Throws DataException, and records the matching FAILURE audit event, without
   * revoking anything, if this would leave zero effective holders.
   */
  private static void revokeAdminManageRoleCapabilityGuarded(WidgetContext context, Role role,
      Capability capability, String reason) throws DataException {
    Connection connection = null;
    boolean priorAutoCommit = true;
    boolean safeToRevoke;
    boolean wasRevoked = false;
    try {
      connection = DB.getConnection();
      priorAutoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      RoleCapabilityRepository.acquireAdminManageGuardLock(connection);

      // Effective holders after hypothetically removing *this role's* contribution - not the
      // pre-revoke total. A user covered by another role that also grants admin:manage, or by
      // their own active direct grant, is still fine without this role, so they must not count
      // against the "would this leave zero holders" check.
      long remainingHoldersAfterRevoke = RoleCapabilityRepository.countDistinctUsersHoldingCapability(
          connection, capability.getId(), role.getId(), -1);
      safeToRevoke = remainingHoldersAfterRevoke > 0;
      if (safeToRevoke) {
        wasRevoked = RoleCapabilityRepository.revoke(connection, role.getId(), capability.getId());
      }
      connection.commit();
    } catch (SQLException se) {
      rollbackQuietly(connection);
      LOG.error("Guarded admin:manage revoke failed for role " + role.getCode() + ": " + se.getMessage(), se);
      throw new DataException("Could not revoke \"" + capability.getCode() + "\" from " + role.getTitle() +
          " - a database error occurred while checking whether this would be safe");
    } finally {
      closeQuietly(connection, priorAutoCommit);
    }

    if (!safeToRevoke) {
      // targetLabel = role.getCode() so this shows up under the same role.getCode()-filtered
      // History link the role-capabilities list page uses for every other event on this role.
      AuditEventCommand.record(context, AuditEventCommand.AUTHORIZATION, "role_capability.revoke",
          AuditEventCommand.FAILURE, "role_capability", capability.getCode(), role.getCode(),
          "Refused: revoking from " + role.getCode() + " would leave no user holding this capability, " +
              "via any role or direct grant");
      throw new DataException("Cannot revoke \"" + capability.getCode() + "\" from " + role.getTitle() +
          " - no one would be left holding it, via any role or direct grant, and nobody could use this " +
          "page to grant it back. Grant it to another role or user first if you really want to remove it here.");
    }
    if (wasRevoked) {
      AuditEventCommand.record(context, AuditEventCommand.AUTHORIZATION, "role_capability.revoke",
          AuditEventCommand.SUCCESS, "role_capability", capability.getCode(), role.getCode(), reason);
    }
  }

  private static void rollbackQuietly(Connection connection) {
    if (connection != null) {
      try {
        connection.rollback();
      } catch (SQLException se) {
        LOG.error("Guarded admin:manage revoke rollback failed: " + se.getMessage());
      }
    }
  }

  private static void closeQuietly(Connection connection, boolean priorAutoCommit) {
    if (connection == null) {
      return;
    }
    try {
      connection.setAutoCommit(priorAutoCommit);
    } catch (SQLException se) {
      LOG.debug("Could not restore autoCommit on the pooled connection");
    }
    try {
      connection.close();
    } catch (SQLException se) {
      LOG.debug("Could not close the pooled connection");
    }
  }

  private static Set<String> toCodeSet(List<Capability> capabilities) {
    Set<String> codes = new HashSet<>();
    if (capabilities != null) {
      for (Capability capability : capabilities) {
        codes.add(capability.getCode());
      }
    }
    return codes;
  }
}
