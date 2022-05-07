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

package com.simisinc.platform.presentation.widgets.admin.login;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.register.SaveUserCommand;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

import org.apache.commons.beanutils.BeanUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/19/18 1:15 PM
 */
public class UserFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/user-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Form bean
    User user;
    if (context.getRequestObject() != null) {
      user = (User) context.getRequestObject();
    } else {
      long userId = context.getParameterAsLong("userId");
      user = LoadUserCommand.loadUser(userId);
    }

    // Set the request items
    context.getRequest().setAttribute("user", user);
    context.setPageTitle(user.getFullName());

    // Shows any roles
    List<Role> roleList = RoleRepository.findAll();
    context.getRequest().setAttribute("roleList", roleList);

    // Show any groups
    List<Group> groupList = GroupRepository.findAll();
    context.getRequest().setAttribute("groupList", groupList);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }


  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields
    User userBean = new User();
    BeanUtils.populate(userBean, context.getParameterMap());
    userBean.setModifiedBy(context.getUserId());

    // Populate the roles
    List<Role> roleList = RoleRepository.findAll();
    if (roleList != null) {
      List<Role> userRoleList = new ArrayList<>();
      for (Role role : roleList) {
        String roleValue = context.getParameter("roleId" + role.getId());
        if (roleValue != null && roleValue.equals(String.valueOf(role.getId()))) {
          LOG.debug("Adding user to role: " + role.getCode());
          userRoleList.add(role);
        }
      }
      userBean.setRoleList(userRoleList);
    }

    // Populate the groups
    List<Group> groupList = GroupRepository.findAll();
    if (groupList != null) {
      List<Group> userGroupList = new ArrayList<>();
      for (Group group : groupList) {
        String groupValue = context.getParameter("groupId" + group.getId());
        if (groupValue != null && groupValue.equals(String.valueOf(group.getId()))) {
          userGroupList.add(group);
        }
      }
      userBean.setGroupList(userGroupList);
    }

    // Save the user
    User user = null;
    try {
      user = SaveUserCommand.saveUser(userBean);
      if (user == null) {
        throw new DataException("The information could not be saved due to a system error. Please try again.");
      }
    } catch (Exception e) {
      LOG.error("User record error: " + e.getMessage(), e);
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(userBean);
      context.setRedirect("/admin/modify-user?userId=" + userBean.getId());
      //context.addSharedRequestValue("returnPage", UrlCommand.getValidReturnPage(context.getParameter("returnPage")));
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("User was saved");
    context.setRedirect("/admin/user-details?userId=" + user.getId());
    return context;

  }
}
