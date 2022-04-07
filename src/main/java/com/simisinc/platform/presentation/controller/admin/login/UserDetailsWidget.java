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

package com.simisinc.platform.presentation.controller.admin.login;

import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.domain.events.cms.UserPasswordResetEvent;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/19/18 1:15 PM
 */
public class UserDetailsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/user-details.jsp";
  static String INVALID_USER_JSP = "/admin/user-invalid.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Load the user
    User user = LoadUserCommand.loadUser(context.getParameterAsLong("userId"));
    if (user == null) {
      context.setJsp(INVALID_USER_JSP);
      return context;
    }
    context.getRequest().setAttribute("user", user);
    context.setPageTitle(user.getFullName());

    // Shows any roles
    List<Role> roleList = RoleRepository.findAll();
    context.getRequest().setAttribute("roleList", roleList);

    // Show any groups
    List<Group> groupList = GroupRepository.findAll();
    context.getRequest().setAttribute("groupList", groupList);

    // Show Last Login Record
    context.getRequest().setAttribute("userLogin", user.getLastLogin());

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext action(WidgetContext context) {
    // Find the user record
    long userId = context.getParameterAsLong("userId");
    User user = LoadUserCommand.loadUser(userId);
    if (user == null) {
      context.setErrorMessage("The user record was not found");
      return context;
    }
    // Execute the action
    context.setRedirect("/admin/user-details?userId=" + userId);
    String action = context.getParameter("action");
    if ("resetPassword".equals(action)) {
      return resetPassword(context, user);
    } else if ("suspendAccount".equals(action)) {
      return suspendAccount(context, user);
    } else if ("restoreAccount".equals(action)) {
      return restoreAccount(context, user);
    } else if ("deleteAccount".equals(action)) {
      return deleteAccount(context, user);
    }
    return context;
  }

  private WidgetContext resetPassword(WidgetContext context, User user) {
    // Create an account token and send email
    user = UserRepository.createAccountToken(user);

    // Trigger events
    WorkflowManager.triggerWorkflowForEvent(new UserPasswordResetEvent(user, context.getUserSession().getUser()));

    context.setSuccessMessage("Password reset instructions have been sent to: " + user.getEmail());
    return context;
  }

  private WidgetContext suspendAccount(WidgetContext context, User user) {
    // Suspend the account (but not own self)
    if (context.getUserId() == user.getId()) {
      context.setErrorMessage("You cannot suspend your own account");
      return context;
    }
    UserRepository.suspendAccount(user);
    context.setSuccessMessage("Account suspended");
    return context;
  }

  private WidgetContext restoreAccount(WidgetContext context, User user) {
    // Restore the account
    UserRepository.restoreAccount(user);
    context.setSuccessMessage("Account restored");
    return context;
  }

  private WidgetContext deleteAccount(WidgetContext context, User user) {
    // Attempt to delete the account (but not own self)
    if (context.getUserId() == user.getId()) {
      context.setErrorMessage("You cannot delete your own account");
      return context;
    }
    try {
      if (UserRepository.remove(user)) {
        context.setSuccessMessage("Account deleted");
      } else {
        context.setErrorMessage("Account not deleted - this user is referenced in other database tables");
      }
    } catch (Exception e) {
      context.setErrorMessage("The account could not be deleted: " + e.getMessage());
    }
    return context;
  }

}
