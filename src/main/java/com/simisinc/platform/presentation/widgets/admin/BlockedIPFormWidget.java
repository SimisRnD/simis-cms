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
import com.simisinc.platform.application.cms.SaveBlockedIPCommand;
import com.simisinc.platform.domain.model.BlockedIP;
import com.simisinc.platform.infrastructure.persistence.BlockedIPRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;

import java.lang.reflect.InvocationTargetException;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/25/2020 10:10 AM
 */
public class BlockedIPFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/blocked-ip-list-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Form bean
    if (context.getRequestObject() != null) {
      context.getRequest().setAttribute("blockedIPList", context.getRequestObject());
    } else {
      int blockedIPListId = context.getParameterAsInt("blockedIPListId");
      BlockedIP blockedIP = BlockedIPRepository.findById(blockedIPListId);
      context.getRequest().setAttribute("blockedIPList", blockedIP);
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields
    BlockedIP blockedIPBean = new BlockedIP();
    BeanUtils.populate(blockedIPBean, context.getParameterMap());

    // Don't add your own IP
    if (blockedIPBean.getIpAddress().equals(context.getRequest().getRemoteAddr())) {
      context.setErrorMessage("Cannot add your own IP");
      return context;
    }

    // Skip duplicates
    if (BlockedIPRepository.findByIpAddress(blockedIPBean.getIpAddress()) != null) {
      context.setWarningMessage("IP already exists");
      return context;
    }

    // Save the record
    BlockedIP blockedIP = null;
    try {
      blockedIP = SaveBlockedIPCommand.save(blockedIPBean);
      if (blockedIP == null) {
        throw new AppException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | AppException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(blockedIPBean);
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Record was saved");
    return context;
  }
}
