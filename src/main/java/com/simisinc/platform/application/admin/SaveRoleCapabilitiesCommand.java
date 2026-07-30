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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.domain.model.Role;
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

  /**
   * The single capability that gates access to this very page (RoleCapabilitiesFormWidget/
   * RoleCapabilitiesListWidget both require it). If it ever reached zero role-holders, nobody
   * could use this page to undo the mistake - a hard, unrecoverable lockout, not just an
   * inconvenience. Guarded specifically, not generalized to every capability: revoking any
   * *other* capability down to zero holders (e.g. retiring a role's access to a sunset feature)
   * is a legitimate admin decision this command should not block.
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

    // Validate the full set of revocations before applying any of them, so a refused change
    // never leaves some of this save's other changes half-applied.
    for (Capability capability : toRevoke) {
      if (ADMIN_MANAGE_CAPABILITY.equals(capability.getCode())) {
        long remainingHolders = RoleCapabilityRepository.countRolesGrantedCapability(capability.getId());
        if (remainingHolders <= 1) {
          // targetLabel = role.getCode() so this shows up under the same role.getCode()-filtered
          // History link the role-capabilities list page uses for every other event on this role.
          AuditEventCommand.record(context, AuditEventCommand.AUTHORIZATION, "role_capability.revoke",
              AuditEventCommand.FAILURE, "role_capability", capability.getCode(), role.getCode(),
              "Refused: " + role.getCode() + " is the only role with this capability");
          throw new DataException("Cannot revoke \"" + capability.getCode() + "\" from " + role.getTitle() +
              " - it's the only role that currently has it, and removing it would leave nobody able to use this " +
              "page to grant it back. Grant it to another role first if you really want to remove it here.");
        }
      }
    }

    for (Capability capability : toGrant) {
      RoleCapabilityRepository.grant(role.getId(), capability.getId());
      AuditEventCommand.record(context, AuditEventCommand.AUTHORIZATION, "role_capability.grant",
          AuditEventCommand.SUCCESS, "role_capability", capability.getCode(), role.getCode(), reason);
    }
    for (Capability capability : toRevoke) {
      RoleCapabilityRepository.revoke(role.getId(), capability.getId());
      AuditEventCommand.record(context, AuditEventCommand.AUTHORIZATION, "role_capability.revoke",
          AuditEventCommand.SUCCESS, "role_capability", capability.getCode(), role.getCode(), reason);
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
