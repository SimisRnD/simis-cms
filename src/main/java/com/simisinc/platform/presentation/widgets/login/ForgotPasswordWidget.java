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
      context.setSuccessMessage("If the email you specified exists in our system, we've sent a password reset link to it.");
      context.setJsp(SUCCESS_JSP);
      return context;
    }

    // Record rate limiting
    // Limit the number of attempts per username (system(s) attempting the same username)
    // Limit the number of attempts per ip (a system attempting multiple users)
    RateLimitCommand.isUsernameAllowedRightNow(username, true);
    RateLimitCommand.isIpAllowedRightNow(ipAddress, true);

    // Make sure the user has a valid email address
    if (!JMail.isValid(user.getEmail())) {
      LOG.warn("This user does not have a valid email to send to");
      context.setWarningMessage("Check the username and try again");
      return context;
    }

    // Capture the target before the token write can replace the reference
    String targetId = String.valueOf(user.getId());
    String targetLabel = user.getEmail();

    // Create an account token and send email
    user = UserRepository.createAccountToken(user);

    // Record the self-service request (#492) -- distinct event type from the admin-initiated
    // "user.password.reset" so the audit trail shows who actually asked, not just that a reset
    // happened. The actor resolves to unauthenticated/anonymous since nobody is signed in yet.
    //
    // The outcome reflects what the token write actually did. createAccountToken returns null when
    // its update does not take (it logs "createAccountToken failed!"), and recording SUCCESS there
    // put a reset that never happened into the audit trail -- the same mismatch
    // UserDetailsWidget#resetPassword and #deleteAccount already avoid by auditing the write's
    // return rather than assuming it took.
    AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, "user.password.reset.requested",
        user != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
        "user", targetId, targetLabel, "self-service");

    // Trigger events -- but only when a token was actually written. On the null return there is no
    // token for the reset email to carry, and the event's consumers read the user off it
    // (BuildWebhookPayloadCommand#userSummary, the EmailTask templates), so firing it would send a
    // link-less reset mail and a null-user webhook for a reset that did not happen.
    //
    // Nothing is said to the visitor about any of this, which is where this path deliberately
    // parts from the admin-initiated fix in UserDetailsWidget#resetPassword (#1837). That one shows
    // the admin an error, because an authenticated admin is entitled to know their action failed.
    // Here the caller is anonymous and the response is the enumeration control: every arm of this
    // method -- no such username, token written, token write failed -- has to leave the same
    // message and the same JSP, because a failure response would only ever be reachable for a
    // username that does exist. A generic "please try again" is no safer than a specific one; what
    // leaks is the difference itself, not the wording. So the method falls through to the one exit
    // below and the failure is recorded server-side only, in the audit line above and in
    // createAccountToken's own log.
    if (user != null) {
      WorkflowManager.triggerWorkflowForEvent(new UserPasswordResetEvent(user, null));
    }

    context.setSuccessMessage("If the email you specified exists in our system, we've sent a password reset link to it.");
    context.setJsp(SUCCESS_JSP);
    return context;
  }
}
