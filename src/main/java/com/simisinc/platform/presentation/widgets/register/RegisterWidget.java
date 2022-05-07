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

package com.simisinc.platform.presentation.widgets.register;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.CaptchaCommand;
import com.simisinc.platform.application.register.RegisterUserCommand;
import com.simisinc.platform.domain.events.cms.UserSignedUpEvent;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import javax.security.auth.login.AccountException;
import java.lang.reflect.InvocationTargetException;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/6/18 9:26 PM
 */
public class RegisterWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/register/register-form.jsp";
  static String NO_REGISTRATIONS_JSP = "/register/register-form-turned-off.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // See if registrations are allowed
    if (!context.hasRole("admin") &&
        !"true".equals(LoadSitePropertyCommand.loadByName("site.registrations"))) {
      context.setJsp(NO_REGISTRATIONS_JSP);
      return context;
    }

    // Preferences
    context.getRequest().setAttribute("showLegalLinks", context.getPreferences().getOrDefault("showLegalLinks", "false"));

    // Determine the captcha service
    boolean useCaptcha = "true".equals(context.getPreferences().getOrDefault("useCaptcha", "false"));
    if (useCaptcha) {
      context.getRequest().setAttribute("useCaptcha", "true");
      context.getRequest().setAttribute("googleSiteKey", LoadSitePropertyCommand.loadByName("captcha.google.sitekey"));
    }

    // Form bean
    if (context.getRequestObject() != null) {
      context.getRequest().setAttribute("user", context.getRequestObject());
    }

    // Show the form
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Don't accept multiple form posts
    context.getUserSession().renewFormToken();

    // See if registrations are allowed
    if (!context.hasRole("admin") &&
        !"true".equals(LoadSitePropertyCommand.loadByName("site.registrations"))) {
      return null;
    }

    // Populate the fields
    boolean isValid = true;
    User userBean = new User();
    BeanUtils.populate(userBean, context.getParameterMap());
    String password = context.getParameter("password");
    String password2 = context.getParameter("password2");

    // Validate the captcha
    boolean useCaptcha = "true".equals(context.getPreferences().getOrDefault("useCaptcha", "false"));
    if (useCaptcha) {
      boolean captchaSuccess = CaptchaCommand.validateRequest(context);
      if (!captchaSuccess) {
        isValid = false;
        context.setWarningMessage("The captcha could not be validated");
      }
    }

    // Check the form values
    if (!StringUtils.equals(password, password2)) {
      isValid = false;
      context.setWarningMessage("The password fields did not match, please try again");
    }

    if (!isValid) {
      context.setRequestObject(userBean);
      return context;
    }

    // Save the new user
    User user = null;
    try {
      user = RegisterUserCommand.registerUser(userBean);
      if (user == null) {
        throw new AccountException("Your information could not be saved due to a system issue. Please try again.");
      }
    } catch (DataException | AccountException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(userBean);
      return context;
    }

    // Trigger events
    WorkflowManager.triggerWorkflowForEvent(new UserSignedUpEvent(user));

    // Redirect
    context.setRedirect("/validation-sent");
    return context;
  }
}
