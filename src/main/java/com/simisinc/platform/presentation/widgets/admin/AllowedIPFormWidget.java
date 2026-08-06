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

import com.simisinc.platform.application.AppException;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.SaveAllowedIPCommand;
import com.simisinc.platform.domain.model.AllowedIP;
import com.simisinc.platform.infrastructure.persistence.AllowedIPRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;

import java.lang.reflect.InvocationTargetException;

/**
 * The "Add to List" sidebar form for the IP allow list
 *
 * @author elizabeth houser
 */
public class AllowedIPFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908894L;

  static String JSP = "/admin/allowed-ip-list-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Form bean
    if (context.getRequestObject() != null) {
      context.getRequest().setAttribute("allowedIPList", context.getRequestObject());
    } else {
      int allowedIPListId = context.getParameterAsInt("allowedIPListId");
      AllowedIP allowedIP = AllowedIPRepository.findById(allowedIPListId);
      context.getRequest().setAttribute("allowedIPList", allowedIP);
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields
    AllowedIP allowedIPBean = new AllowedIP();
    BeanUtils.populate(allowedIPBean, context.getParameterMap());

    // Skip duplicates
    if (AllowedIPRepository.findByIpAddress(allowedIPBean.getIpAddress()) != null) {
      context.setWarningMessage("IP already exists");
      return context;
    }

    // Save the record
    AllowedIP allowedIP = null;
    try {
      allowedIP = SaveAllowedIPCommand.save(allowedIPBean);
      if (allowedIP == null) {
        throw new AppException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | AppException e) {
      AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "allowed_ip.add", AuditEventCommand.FAILURE,
          "allowed_ip", null, allowedIPBean.getIpAddress(), e.getMessage());
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(allowedIPBean);
      return context;
    }
    AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "allowed_ip.add", AuditEventCommand.SUCCESS,
        "allowed_ip", String.valueOf(allowedIP.getId()), allowedIP.getIpAddress(), allowedIP.getReason());

    // Determine the page to return to
    context.setSuccessMessage("Record was saved");
    // Surface the cross-list shadowing warning SaveAllowedIPCommand computed during save() -- an
    // Allowed entry always wins over a Blocked one (BlockedIPListCommand.passesCheck), so an
    // admin adding an Allowed IP that overlaps an existing Blocked entry needs to know their new
    // entry just silently neutralized that block, not discover it later.
    String conflictWarning = SaveAllowedIPCommand.getLastConflictWarning();
    if (conflictWarning != null) {
      context.setWarningMessage(conflictWarning);
    }
    return context;
  }
}
