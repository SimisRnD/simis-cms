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

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.SaveCapabilityGrantCommand;
import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.domain.model.CapabilityGrant;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.CapabilityGrantRepository;
import com.simisinc.platform.infrastructure.persistence.CapabilityRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Lists and manages one user's direct capability grants (issue #702) - the minimal grant/revoke
 * UI for temporary/expiring permissions, the per-user counterpart to RoleCapabilitiesForm/
 * ListWidget's per-role UI (#704). No risk badges, search, or bulk operations - that richer UX
 * layer is #703's job.
 *
 * @author elizabeth houser
 */
public class CapabilityGrantsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908897L;

  static String JSP = "/admin/capability-grants.jsp";

  public WidgetContext execute(WidgetContext context) {
    if (!context.hasPermission("admin:manage")) {
      LOG.warn("No access to capability grants");
      return null;
    }

    User user = loadUser(context);
    if (user == null) {
      return null;
    }

    List<Capability> allCapabilities = CapabilityRepository.findAll();
    List<CapabilityGrant> grantList = CapabilityGrantRepository.findAllByUserId(user.getId());

    context.getRequest().setAttribute("targetUser", user);
    context.getRequest().setAttribute("capabilityList", allCapabilities);
    context.getRequest().setAttribute("grantList", grantList);

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {
    if (!context.hasPermission("admin:manage")) {
      LOG.warn("No access to save capability grants");
      return context;
    }

    User user = loadUser(context);
    if (user == null) {
      return context;
    }

    context.setRedirect("/admin/capability-grants?userId=" + user.getId());

    String command = context.getParameter("command");
    try {
      if ("add".equals(command)) {
        add(context, user);
      } else if ("revoke".equals(command)) {
        revoke(context, user);
      }
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
    }

    return context;
  }

  private void add(WidgetContext context, User user) throws DataException {
    long capabilityId = context.getParameterAsLong("capabilityId", -1);
    Capability capability = findCapability(capabilityId);
    if (capability == null) {
      throw new DataException("Capability was not found");
    }
    String reason = context.getParameter("reason");
    Timestamp expiresAt = parseExpiresAt(context.getParameter("expiresAt"));

    SaveCapabilityGrantCommand.grant(context, user, capability, context.getUserId(), reason, expiresAt);
    context.setSuccessMessage("Granted \"" + capability.getCode() + "\" to " + user.getUsername());
  }

  private void revoke(WidgetContext context, User user) throws DataException {
    long capabilityGrantId = context.getParameterAsLong("capabilityGrantId", -1);
    CapabilityGrant capabilityGrant = CapabilityGrantRepository.findById(capabilityGrantId);
    if (capabilityGrant == null || !capabilityGrant.getUserId().equals(user.getId())) {
      throw new DataException("Capability grant was not found for this user");
    }
    String reason = context.getParameter("reason");

    SaveCapabilityGrantCommand.revoke(context, capabilityGrant, user, reason);
    context.setSuccessMessage("Revoked \"" + capabilityGrant.getCapabilityCode() + "\" from " + user.getUsername());
  }

  private Capability findCapability(long capabilityId) {
    List<Capability> allCapabilities = CapabilityRepository.findAll();
    if (allCapabilities == null) {
      return null;
    }
    for (Capability capability : allCapabilities) {
      if (capability.getId() == capabilityId) {
        return capability;
      }
    }
    return null;
  }

  /** Parses a yyyy-MM-dd date input as expiring at the start of the following day (inclusive of the
   *  selected day); null when blank/invalid, meaning a permanent grant. */
  private Timestamp parseExpiresAt(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    try {
      LocalDate date = LocalDate.parse(value.trim()).plusDays(1);
      return Timestamp.valueOf(date.atStartOfDay());
    } catch (Exception e) {
      return null;
    }
  }

  private User loadUser(WidgetContext context) {
    long userId = context.getParameterAsLong("userId", -1);
    User user = UserRepository.findByUserId(userId);
    if (user == null) {
      context.setErrorMessage("User was not found");
    }
    return user;
  }
}
