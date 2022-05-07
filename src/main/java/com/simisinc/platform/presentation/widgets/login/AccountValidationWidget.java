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

package com.simisinc.platform.presentation.widgets.login;

import com.simisinc.platform.application.UserPasswordCommand;
import com.simisinc.platform.domain.events.cms.UserRegisteredEvent;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 6/21/18 5:15 PM
 */
public class AccountValidationWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/login/account-validated.jsp";
  static String NOT_FOUND_JSP = "/login/account-confirmation-not-found.jsp";
  static String FINISHED_JSP = "/login/account-confirmation-finished.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    String status = context.getParameter("status");
    if ("complete".equals(status)) {
      context.setJsp(FINISHED_JSP);
      return context;
    }

    // Check for an account token
    String confirmation = context.getParameter("confirmation");
    if (StringUtils.isBlank(confirmation)) {
      LOG.warn("No account token was found!");
      return null;
    }

    // Match the user
    User user = UserRepository.findByAccountToken(confirmation);
    if (user == null) {
      LOG.warn("No user was found for token!");
      context.setJsp(NOT_FOUND_JSP);
      return context;
    }

    // User needs to change their password to login
    if ("new".equals(user.getPassword()) || StringUtils.isNotBlank(user.getAccountToken())) {
      context.getRequest().setAttribute("doPassword", "true");
      context.getRequest().setAttribute("confirmation", confirmation);
    } else {
      // Make an update
      if (user.getValidated() == null) {
        UserRepository.updateValidated(user);
        LOG.debug("User finished registering... " + user.getEmail());
        // Trigger Events
        WorkflowManager.triggerWorkflowForEvent(new UserRegisteredEvent(user, context.getRequest().getRemoteAddr()));
      }
    }

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    // Don't accept multiple form posts
    context.getUserSession().renewFormToken();

    // Check for an account token
    String confirmation = context.getParameter("confirmation");
    if (StringUtils.isBlank(confirmation)) {
      LOG.warn("No account token was found!");
      return null;
    }

    // Match the user
    User user = UserRepository.findByAccountToken(confirmation);
    if (user == null) {
      LOG.warn("No user was found for token!");
      context.setJsp(NOT_FOUND_JSP);
      return context;
    }

    // User needs to change their password to login, or they requested to
    if ("new".equals(user.getPassword()) || StringUtils.isNotBlank(user.getAccountToken())) {
      String password = context.getParameter("password");
      String password2 = context.getParameter("password2");
      if (!StringUtils.equals(password, password2)) {
        context.setWarningMessage("The password fields did not match, please try again");
        context.setRedirect("/validate-account?confirmation=" + confirmation);
        return context;
      }
      if (password.trim().length() < 8) {
        context.setWarningMessage("Passwords must be at least 8 characters");
        context.setRedirect("/validate-account?confirmation=" + confirmation);
        return context;
      }

      // Hash the password
      user.setPassword(UserPasswordCommand.hash(password));
      UserRepository.updatePassword(user);

      // Make an update
      if (user.getValidated() == null) {
        UserRepository.updateValidated(user);
        LOG.debug("User was validated... " + user.getEmail());
        // Trigger Events
        WorkflowManager.triggerWorkflowForEvent(new UserRegisteredEvent(user, context.getRequest().getRemoteAddr()));
      }
    }

    context.setRedirect("/validate-account?status=complete");
    return context;
  }
}
