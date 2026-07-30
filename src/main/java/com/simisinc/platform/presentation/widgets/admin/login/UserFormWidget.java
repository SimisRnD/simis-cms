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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.login.StepUpAuthCommand;
import com.simisinc.platform.application.register.SaveUserCommand;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.UserSession;
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

    // Require a recent step-up before saving role or group changes
    String stepUpCredential = context.getParameter("stepUpCredential");
    if (!StepUpAuthCommand.isValid(context.getUserSession())) {
      if (StringUtils.isBlank(stepUpCredential)) {
        context.addSharedRequestValue("stepUpRequired", "true");
        context.setJsp(JSP);
        return context;
      }
      User actingUser = LoadUserCommand.loadUser(context.getUserId());
      if (!StepUpAuthCommand.verify(context.getUserSession(), actingUser, stepUpCredential)) {
        context.setErrorMessage("Re-authentication failed. Enter your password or authenticator code.");
        context.addSharedRequestValue("stepUpRequired", "true");
        context.setJsp(JSP);
        return context;
      }
    }

    // Populate the fields
    User userBean = new User();
    BeanUtils.populate(userBean, context.getParameterMap());
    userBean.setModifiedBy(context.getUserId());

    // Populate the roles -- but an editor may only assign roles at or below their own highest role
    // level. A role above that level is honored only when the target already holds it (preserve, never
    // escalate): this stops a lower-privileged editor (e.g. a community-manager, level 90) from
    // granting admin (level 100) or another higher role through this form, and equally from stripping
    // one it does not control. Group delegation is unranked and deferred to the deny-by-default work (#299).
    List<Role> roleList = RoleRepository.findAll();
    if (roleList != null) {
      int actingLevel = highestRoleLevel(context.getUserSession(), roleList);
      Set<String> retainedHigherRoleCodes = higherRolesTargetAlreadyHolds(userBean.getId(), roleList, actingLevel);
      List<Role> userRoleList = new ArrayList<>();
      for (Role role : roleList) {
        String roleValue = context.getParameter("roleId" + role.getId());
        boolean requested = roleValue != null && roleValue.equals(String.valueOf(role.getId()));
        if (role.getLevel() <= actingLevel) {
          // At or below the editor's level: the editor controls it.
          if (requested) {
            LOG.debug("Adding user to role: " + role.getCode());
            userRoleList.add(role);
          }
        } else if (retainedHigherRoleCodes.contains(role.getCode())) {
          // Above the editor's level but already held by the target: preserve, do not let the editor revoke it.
          userRoleList.add(role);
        } else if (requested) {
          LOG.warn("Blocked role escalation: user " + context.getUserId() + " (level " + actingLevel
              + ") attempted to grant '" + role.getCode() + "' (level " + role.getLevel() + ")");
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

    // An existing id means an edit; a new record is a create
    boolean isUpdate = userBean.getId() > -1;
    String eventType = isUpdate ? "user.update" : "user.create";

    // Save the user
    User user = null;
    try {
      user = SaveUserCommand.saveUser(userBean);
      if (user == null) {
        throw new DataException("The information could not be saved due to a system error. Please try again.");
      }
    } catch (Exception e) {
      LOG.error("User record error: " + e.getMessage(), e);
      AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, eventType, AuditEventCommand.FAILURE,
          "user", String.valueOf(userBean.getId()), userBean.getEmail(), e.getMessage());
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(userBean);
      context.setRedirect("/admin/modify-user?userId=" + userBean.getId());
      //context.addSharedRequestValue("returnPage", UrlCommand.getValidReturnPage(context.getParameter("returnPage")));
      return context;
    }

    // Record the change with the effective roles and groups
    AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, eventType, AuditEventCommand.SUCCESS,
        "user", String.valueOf(user.getId()), user.getEmail(), AuditEventCommand.describeRolesAndGroups(user));

    // Determine the page to return to
    context.setSuccessMessage("User was saved");
    context.setRedirect("/admin/user-details?userId=" + user.getId());
    return context;

  }

  /**
   * The highest role level the acting user holds, found by matching their session role codes against
   * the authoritative role list (which carries the levels). Returns 0 when nothing matches, which
   * fails closed -- no role above 0 can then be granted.
   *
   * Package-private so UsersListWidget's bulk role-assignment actions can reuse the identical
   * escalation-level logic instead of duplicating it.
   */
  static int highestRoleLevel(UserSession userSession, List<Role> allRoles) {
    int max = 0;
    if (userSession == null) {
      return max;
    }
    for (Role role : allRoles) {
      if (userSession.hasRole(role.getCode()) && role.getLevel() > max) {
        max = role.getLevel();
      }
    }
    return max;
  }

  /**
   * The codes of roles the target user already holds whose level is above the editor's -- the roles
   * the editor may neither grant nor revoke. Empty for a new user.
   */
  private static Set<String> higherRolesTargetAlreadyHolds(long userId, List<Role> allRoles, int actingLevel) {
    Set<String> codes = new HashSet<>();
    if (userId < 0) {
      return codes;
    }
    User existing = LoadUserCommand.loadUser(userId);
    if (existing == null || existing.getRoleList() == null) {
      return codes;
    }
    for (Role held : existing.getRoleList()) {
      for (Role authoritative : allRoles) {
        if (authoritative.getCode().equals(held.getCode()) && authoritative.getLevel() > actingLevel) {
          codes.add(authoritative.getCode());
        }
      }
    }
    return codes;
  }
}
