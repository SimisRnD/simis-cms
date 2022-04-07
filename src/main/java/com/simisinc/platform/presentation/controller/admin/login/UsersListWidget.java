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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.admin.ProcessUserCSVFileCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.register.SaveUserCommand;
import com.simisinc.platform.domain.events.cms.UserInvitedEvent;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.UserSpecification;
import com.simisinc.platform.infrastructure.persistence.login.UserLoginRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import javax.security.auth.login.AccountException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/24/18 10:06 AM
 */
public class UsersListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/users-list.jsp";

  static final String STATUS_FILTER_ANY = "any";
  static final String STATUS_FILTER_ACTIVE = "active";
  static final String STATUS_FILTER_INACTIVE = "inactive";

  public WidgetContext execute(WidgetContext context) {

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "20"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Determine the sorting
//    String sortByValue = context.getParameter("sortBy", "date");
//    String sortOrderValue = context.getParameter("sortOrder", "newest");
//    context.getRequest().setAttribute(RequestConstants.RECORD_SORT_BY, sortByValue);
//    context.getRequest().setAttribute(RequestConstants.RECORD_SORT_ORDER, sortOrderValue);

    // Determine the search
    String query = context.getParameter("query");
    context.getRequest().setAttribute(RequestConstants.RECORD_QUERY, query);

    // Determine the filters
    String statusFilterValue = context.getParameter("statusFilter", STATUS_FILTER_ANY);
    String statusFilter = STATUS_FILTER_ANY;
    if (StringUtils.isNotBlank(statusFilterValue)) {
      if (STATUS_FILTER_ACTIVE.equals(statusFilterValue)) {
        statusFilter = STATUS_FILTER_ACTIVE;
      } else if (STATUS_FILTER_INACTIVE.equals(statusFilterValue)) {
        statusFilter = STATUS_FILTER_INACTIVE;
      }
    }
    context.getRequest().setAttribute("statusFilter", statusFilter);

    // Configure the paging uri
    String pagingUri = "";
    if (StringUtils.isNotBlank(query)) {
      pagingUri = pagingUri + "&query=" + UrlCommand.encodeUri(query);
    }
    if (StringUtils.isNotBlank(statusFilter)) {
      pagingUri = pagingUri + "&statusFilter=" + UrlCommand.encodeUri(statusFilter);
    }
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING_URI, pagingUri);

    // Determine criteria
    UserSpecification specification = new UserSpecification();
    if (StringUtils.isNotBlank(query)) {
      specification.setMatchesName(query);
    }
    if (!STATUS_FILTER_ANY.equals(statusFilter)) {
      if (STATUS_FILTER_ACTIVE.equals(statusFilter)) {
        specification.setIsEnabled(true);
      } else if (STATUS_FILTER_INACTIVE.equals(statusFilter)) {
        specification.setIsEnabled(false);
      }
    }

    // Load the users
    List<User> userList = UserRepository.findAll(specification, constraints);
    for (User user : userList) {
      user.setRoleList(RoleRepository.findAllByUserId(user.getId()));
      user.setLastLogin(UserLoginRepository.queryLastLogin(user.getId()));
    }
    context.getRequest().setAttribute("userList", userList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Set some form values
    List<Role> roleList = RoleRepository.findAll();
    context.getRequest().setAttribute("roleList", roleList);

    // Set some form values
    List<Group> groupList = GroupRepository.findAll();
    context.getRequest().setAttribute("groupList", groupList);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {
    // Permission is required
    if (!(context.hasRole("admin") || context.hasRole("community-manager"))) {
      return context;
    }

    // Don't accept multiple form posts
    context.getUserSession().renewFormToken();

    // Determine the action
    String command = context.getParameter("command");

    // Upload file command
    if ("uploadCSVFile".equals(command)) {
      return uploadCSVFileAction(context);
    }

    // Default to adding a user
    return addUserAction(context);
  }

  private WidgetContext uploadCSVFileAction(WidgetContext context) {
    LOG.info("User is uploading a user file...");
    try {
      int userCount = ProcessUserCSVFileCommand.processCSV(context);
      context.setSuccessMessage(userCount + " user" + (userCount != 1 ? "s" : "") + " added");
    } catch (Exception e) {
      context.setErrorMessage(e.getMessage());
    }
    // Determine the page to return to
    context.setRedirect("/admin/users");
    return context;
  }

  private WidgetContext addUserAction(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields
    User userBean = new User();
    BeanUtils.populate(userBean, context.getParameterMap());
    userBean.setCreatedBy(context.getUserId());
    userBean.setModifiedBy(context.getUserId());

    // Default the username to the email
    if (StringUtils.isBlank(userBean.getUsername())) {
      userBean.setUsername(userBean.getEmail());
    }

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

    // Populate the user groups
    List<Group> groupList = GroupRepository.findAll();
    if (groupList != null) {
      List<Group> userGroupList = new ArrayList<>();
      for (Group group : groupList) {
        // Always add the user to "All Users"
        if (group.getName().equals("All Users")) {
          userGroupList.add(group);
          continue;
        }
        // Add if the Checkbox was selected
        String groupValue = context.getParameter("groupId" + group.getId());
        if (groupValue != null && groupValue.equals(String.valueOf(group.getId()))) {
          LOG.debug("Adding user to group: " + group.getName());
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
    } catch (DataException | AccountException e) {
      LOG.error("Save user error", e);
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(userBean);
      return context;
    }

    // Trigger events
    User invitedBy = LoadUserCommand.loadUser(user.getCreatedBy());
    WorkflowManager.triggerWorkflowForEvent(new UserInvitedEvent(user, invitedBy));

    // Determine the page to return to
    context.setSuccessMessage("User was added, and an email invitation was sent with further instructions");
    context.setRedirect("/admin/users");
    return context;
  }
}
