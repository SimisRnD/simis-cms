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

package com.simisinc.platform.presentation.widgets.userProfile;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.login.UserMfaCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Lets a signed-in user manage their own two-factor authentication: turn it on by enrolling an authenticator app
 * (scan the QR or type the key, then confirm a code) and turn it off again. The cryptography and persistence live in
 * UserMfaCommand; this widget is the self-service screen around it.
 *
 * @author SimIS Inc.
 * @created 2026-07-17
 */
public class MyMfaSettingsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908894L;

  static String JSP = "/userProfile/my-mfa-settings.jsp";

  public WidgetContext execute(WidgetContext context) {
    // Require a logged in user
    if (!context.getUserSession().isLoggedIn()) {
      return context;
    }
    User user = UserRepository.findByUserId(context.getUserId());
    if (user == null) {
      LOG.warn("Could not find current user record");
      return context;
    }
    showView(context, user);
    return context;
  }

  public WidgetContext post(WidgetContext context) {
    // Require a logged in user
    if (!context.getUserSession().isLoggedIn()) {
      return context;
    }
    User user = UserRepository.findByUserId(context.getUserId());
    if (user == null) {
      LOG.warn("Could not find current user record");
      return context;
    }

    String action = context.getParameter("action");

    if ("start".equals(action)) {
      // Generate and store a pending secret, then re-display so the user can add it and confirm a code
      UserMfaCommand.startEnrollment(user);
      context.setRedirect(context.getUri());
      return context;
    }

    if ("confirm".equals(action)) {
      if (UserMfaCommand.confirmEnrollment(user, context.getParameter("code"))) {
        context.setSuccessMessage("Two-factor authentication is now on.");
        context.setRedirect(context.getUri());
        return context;
      }
      // Wrong code: keep the pending enrollment on screen with an error
      context.setErrorMessage("That code didn't match. Check your authenticator app and try again.");
      showView(context, user);
      return context;
    }

    if ("cancel".equals(action)) {
      // Abandon a pending enrollment -- clears the stored secret
      UserMfaCommand.disable(user);
      context.setRedirect(context.getUri());
      return context;
    }

    if ("disable".equals(action)) {
      UserMfaCommand.disable(user);
      context.setSuccessMessage("Two-factor authentication has been turned off.");
      context.setRedirect(context.getUri());
      return context;
    }

    // Unknown action -- just re-render the current state
    showView(context, user);
    return context;
  }

  private void showView(WidgetContext context, User user) {
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    boolean enabled = user.getMfaEnabled();
    context.getRequest().setAttribute("mfaEnabled", enabled ? "true" : "false");
    // A stored-but-not-yet-enabled secret means an enrollment is in progress
    boolean enrolling = !enabled && StringUtils.isNotBlank(user.getMfaSecret());
    context.getRequest().setAttribute("mfaEnrolling", enrolling ? "true" : "false");
    if (enrolling) {
      context.getRequest().setAttribute("mfaSecret", user.getMfaSecret());
      context.getRequest().setAttribute("otpauthUri", UserMfaCommand.buildEnrollmentUri(user));
    }
    context.setJsp(JSP);
  }
}
