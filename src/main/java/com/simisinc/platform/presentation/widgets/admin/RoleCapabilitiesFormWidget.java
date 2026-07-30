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

package com.simisinc.platform.presentation.widgets.admin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.SaveRoleCapabilitiesCommand;
import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.infrastructure.persistence.CapabilityRepository;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Grants/revokes capabilities for one role - the minimal grant/revoke UI for issue #704. No risk
 * badges, search, templates, or bulk operations here - that richer UX layer is #703's job. This
 * exists so #704's audit trail has a real action to produce events from, rather than plumbing
 * with nothing ever calling it.
 *
 * @author elizabeth houser
 */
public class RoleCapabilitiesFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908896L;

  static String JSP = "/admin/role-capabilities-form.jsp";

  public WidgetContext execute(WidgetContext context) {
    if (!context.hasPermission("admin:manage")) {
      LOG.warn("No access to edit role capabilities");
      return null;
    }

    Role role = loadRole(context);
    if (role == null) {
      return null;
    }

    List<Capability> allCapabilities = CapabilityRepository.findAll();
    List<Capability> grantedCapabilities = CapabilityRepository.findAllByRoleId(role.getId());
    Set<String> grantedCodes = new HashSet<>();
    if (grantedCapabilities != null) {
      for (Capability capability : grantedCapabilities) {
        grantedCodes.add(capability.getCode());
      }
    }

    context.getRequest().setAttribute("role", role);
    context.getRequest().setAttribute("capabilityList", allCapabilities != null ? allCapabilities : new ArrayList<>());
    context.getRequest().setAttribute("grantedCodes", grantedCodes);

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {
    if (!context.hasPermission("admin:manage")) {
      LOG.warn("No access to save role capabilities");
      return context;
    }

    Role role = loadRole(context);
    if (role == null) {
      return context;
    }

    context.setRedirect("/admin/role-capabilities");

    String reason = context.getParameter("reason");

    List<Capability> allCapabilities = CapabilityRepository.findAll();
    Set<String> submittedCodes = new HashSet<>();
    if (allCapabilities != null) {
      for (Capability capability : allCapabilities) {
        if (context.getParameter("capability" + capability.getId()) != null) {
          submittedCodes.add(capability.getCode());
        }
      }
    }

    try {
      SaveRoleCapabilitiesCommand.save(context, role, submittedCodes, reason);
      context.setSuccessMessage("Updated permissions for " + role.getTitle());
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
      // Return to the form (not the list) so the admin sees the error against the role they
      // were editing, without losing which role that was.
      context.setRedirect("/admin/role-capabilities-form?roleId=" + role.getId());
    }

    return context;
  }

  private Role loadRole(WidgetContext context) {
    int roleId = context.getParameterAsInt("roleId", -1);
    Role role = RoleRepository.findById(roleId);
    if (role == null) {
      context.setErrorMessage("Role was not found");
    }
    return role;
  }
}
