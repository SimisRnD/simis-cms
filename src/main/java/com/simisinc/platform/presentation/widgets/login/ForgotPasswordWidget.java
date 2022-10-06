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

import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.domain.events.cms.UserPasswordResetEvent;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;

/**
 * Forgot Password widget
 *
 * @author matt rajkowski
 * @created 5/2/18 10:27 AM
 */
public class ForgotPasswordWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/login/forgot-password-form.jsp";
  static String SUCCESS_JSP = "/login/forgot-password-success.jsp";
  static String RATE_LIMITED_JSP = "/cms/error-rate-limited.jsp";

  public WidgetContext execute(WidgetContext context) {
    
    // No need to show widget when rate limiting is triggered
    if (!RateLimitCommand.isIpAllowedRightNow(context.getRequest().getRemoteAddr(), false)) {
      context.setJsp(RATE_LIMITED_JSP);
      return context;
    }

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    // Don't accept multiple form posts
    context.getUserSession().renewFormToken();

    // Populate the fields
    String username = context.getParameter("username");

    // Validate the required fields
    if (StringUtils.isBlank(username)) {
      context.setWarningMessage("Field is required");
      return context;
    }

    // Rate limiting
    if (!RateLimitCommand.isUsernameAllowedRightNow(username, false)) {
      context.setWarningMessage(RateLimitCommand.INVALID_ATTEMPTS);
      return context;
    }
    if (!RateLimitCommand.isIpAllowedRightNow(context.getUserSession().getIpAddress(), false)) {
      context.setWarningMessage(RateLimitCommand.INVALID_ATTEMPTS);
      return context;
    }

    // Locate the user
    User user = LoadUserCommand.loadUser(username);
    if (user == null) {
      if (!RateLimitCommand.isIpAllowedRightNow(context.getUserSession().getIpAddress(), true)) {
        context.setWarningMessage(RateLimitCommand.INVALID_ATTEMPTS);
      } else {
        context.setWarningMessage("Check the username and try again");
      }
      return context;
    }

    // Record rate limiting
    // Limit the number of attempts per username (system(s) attempting the same username)
    // Limit the number of attempts per ip (a system attempting multiple users)
    RateLimitCommand.isUsernameAllowedRightNow(username, true);
    RateLimitCommand.isIpAllowedRightNow(context.getUserSession().getIpAddress(), true);

    // Make sure the user has a valid email address
    EmailValidator emailValidator = EmailValidator.getInstance(false);
    if (!emailValidator.isValid(user.getEmail())) {
      LOG.warn("This user does not have a valid email to send to");
      context.setWarningMessage("Check the username and try again");
      return context;
    }

    // Create an account token and send email
    user = UserRepository.createAccountToken(user);

    // Trigger events
    WorkflowManager.triggerWorkflowForEvent(new UserPasswordResetEvent(user, null));

    context.setSuccessMessage("If the email you specified exists in our system, we've sent a password reset link to it.");
    context.setJsp(SUCCESS_JSP);
    return context;
  }
}
