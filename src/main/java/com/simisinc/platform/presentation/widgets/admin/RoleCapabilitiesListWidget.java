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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.infrastructure.persistence.CapabilityRepository;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Read-only summary of every role's currently granted capabilities (issue #704's minimal grant/
 * revoke UI - the richer UX layer described in #703 is separate, later work).
 *
 * @author elizabeth houser
 */
public class RoleCapabilitiesListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908895L;

  static String JSP = "/admin/role-capabilities-list.jsp";

  public WidgetContext execute(WidgetContext context) {
    if (!context.hasPermission("admin:manage")) {
      LOG.warn("No access to role capabilities list");
      return null;
    }

    List<Role> roleList = RoleRepository.findAll();
    if (roleList == null) {
      roleList = new ArrayList<>();
    }

    Map<Integer, List<Capability>> capabilitiesByRoleId = new LinkedHashMap<>();
    for (Role role : roleList) {
      List<Capability> granted = CapabilityRepository.findAllByRoleId(role.getId());
      capabilitiesByRoleId.put(role.getId(), granted != null ? granted : new ArrayList<>());
    }

    context.getRequest().setAttribute("roleList", roleList);
    context.getRequest().setAttribute("capabilitiesByRoleId", capabilitiesByRoleId);

    context.setJsp(JSP);
    return context;
  }
}
