/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

import com.simisinc.platform.application.login.StepUpAuthCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

/**
 * Step-up re-authentication challenge widget (IA-2 / AC-6). Presents a password or TOTP form to
 * re-verify the acting user's identity before a sensitive action is permitted. On success, marks the
 * session and redirects back to the originating page; on failure, re-displays the form with an error.
 */
public class StepUpAuthWidget extends GenericWidget {

  static final long serialVersionUID = -1937504882716093401L;

  static String JSP = "/login/step-up-auth.jsp";

  public WidgetContext execute(WidgetContext context) {
    if (!context.getUserSession().isLoggedIn()) {
      return context;
    }
    context.getRequest().setAttribute("returnUrl", safeReturnUrl(context.getParameter("return")));
    User user = UserRepository.findByUserId(context.getUserId());
    if (user != null) {
      context.getRequest().setAttribute("mfaEnabled", user.getMfaEnabled() ? "true" : "false");
    }
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {
    if (!context.getUserSession().isLoggedIn()) {
      return context;
    }
    String returnUrl = safeReturnUrl(context.getParameter("returnUrl"));
    User user = UserRepository.findByUserId(context.getUserId());
    if (user == null) {
      context.setErrorMessage("Unable to verify identity. Please try again.");
      context.getRequest().setAttribute("returnUrl", returnUrl);
      context.setJsp(JSP);
      return context;
    }
    String password = context.getParameter("password");
    String totpCode = context.getParameter("code");
    if (StepUpAuthCommand.verify(user, password, totpCode)) {
      context.getUserSession().recordStepUp();
      AuditEventCommand.record(context, AuditEventCommand.AUTHORIZATION, "stepup.auth",
          AuditEventCommand.SUCCESS, "user", String.valueOf(user.getId()), user.getEmail(), null);
      LOG.info("Step-up re-auth succeeded for user id " + user.getId());
      context.setRedirect(returnUrl);
      return context;
    }
    AuditEventCommand.record(context, AuditEventCommand.AUTHORIZATION, "stepup.auth",
        AuditEventCommand.FAILURE, "user", String.valueOf(user.getId()), user.getEmail(), null);
    context.setErrorMessage("Verification failed. Please check your password or authenticator code.");
    context.getRequest().setAttribute("returnUrl", returnUrl);
    context.getRequest().setAttribute("mfaEnabled", user.getMfaEnabled() ? "true" : "false");
    context.setJsp(JSP);
    return context;
  }

  private static String safeReturnUrl(String url) {
    if (StringUtils.isBlank(url) || !url.startsWith("/")) {
      return "/";
    }
    return url;
  }
}
