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

import org.apache.commons.lang3.StringUtils;

import com.sanctionco.jmail.JMail;
import com.simisinc.platform.application.IpAddressCommand;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.domain.events.cms.UserPasswordResetEvent;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

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

  /**
   * The single answer every reachable outcome of a well-formed request gives back: username not
   * found, username found and mailed, and username found but carrying an address that cannot be
   * mailed. It is deliberately one constant rather than three identical literals -- the enumeration
   * defence is the responses being indistinguishable, so they must not be able to drift apart.
   */
  static final String GENERIC_RESPONSE_MESSAGE =
      "If the email you specified exists in our system, we've sent a password reset link to it.";

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

    // Rate limiting.
    // The per-ip bucket must be keyed to the address this reset is actually being driven from, not
    // the one the session happened to start at (issue #1791). execute() above already reads the
    // request, and every other isIpAllowedRightNow caller in the platform does too -- they all share
    // one bucket namespace, so keying it two ways from inside the same widget is what breaks it.
    String ipAddress = IpAddressCommand.forAction(context.getRequest(), context.getUserSession().getIpAddress());
    if (!RateLimitCommand.isUsernameAllowedRightNow(username, false)) {
      context.setWarningMessage(RateLimitCommand.INVALID_ATTEMPTS);
      return context;
    }
    if (!RateLimitCommand.isIpAllowedRightNow(ipAddress, false)) {
      context.setWarningMessage(RateLimitCommand.INVALID_ATTEMPTS);
      return context;
    }

    // Locate the user
    User user = LoadUserCommand.loadUser(username);
    if (user == null) {
      if (!RateLimitCommand.isIpAllowedRightNow(ipAddress, true)) {
        context.setWarningMessage(RateLimitCommand.INVALID_ATTEMPTS);
        return context;
      }
      // Always show the same success message whether the username exists or not, to prevent enumeration.
      context.setSuccessMessage(GENERIC_RESPONSE_MESSAGE);
      context.setJsp(SUCCESS_JSP);
      return context;
    }

    // Record rate limiting
    // Limit the number of attempts per username (system(s) attempting the same username)
    // Limit the number of attempts per ip (a system attempting multiple users)
    RateLimitCommand.isUsernameAllowedRightNow(username, true);
    RateLimitCommand.isIpAllowedRightNow(ipAddress, true);

    // Make sure the user has a valid email address. Nothing can be sent, but the answer has to be
    // the same one every other outcome gives: this branch is only reachable when the username DOES
    // resolve to an account, so a distinct "Check the username and try again" on the form told an
    // unauthenticated caller that the account exists -- the one hole in this page's otherwise
    // careful enumeration defence, which is why the not-found branch above is worded the way it is.
    //
    // The account holder is not worse off: they could not have been mailed either way. The signal
    // moves to the log, which is now the only place this surfaces, so it names the account rather
    // than reporting that some user somewhere has an unusable address.
    if (!JMail.isValid(user.getEmail())) {
      LOG.warn("No password reset sent: user " + user.getId() + " has an unusable email address");
      context.setSuccessMessage(GENERIC_RESPONSE_MESSAGE);
      context.setJsp(SUCCESS_JSP);
      return context;
    }

    // Create an account token and send email
    user = UserRepository.createAccountToken(user);

    // Record the self-service request (#492) -- distinct event type from the admin-initiated
    // "user.password.reset" so the audit trail shows who actually asked, not just that a reset
    // happened. The actor resolves to unauthenticated/anonymous since nobody is signed in yet.
    AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.password.reset.requested",
        AuditEventCommand.SUCCESS, "user", String.valueOf(user.getId()), user.getEmail(), "self-service");

    // Trigger events
    WorkflowManager.triggerWorkflowForEvent(new UserPasswordResetEvent(user, null));

    context.setSuccessMessage(GENERIC_RESPONSE_MESSAGE);
    context.setJsp(SUCCESS_JSP);
    return context;
  }
}
