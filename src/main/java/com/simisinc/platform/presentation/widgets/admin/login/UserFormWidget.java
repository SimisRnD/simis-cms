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
import com.simisinc.platform.infrastructure.persistence.UserRepository;
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
      if (user == null) {
        // No userId, or it didn't match an existing record -- treat this as the New User form
        user = new User();
      }
    }

    // Set the request items
    context.getRequest().setAttribute("user", user);
    context.setPageTitle(user.getFullName());

    // Shows any roles -- the JSP only offers/enables roles the editor is allowed to grant or revoke
    // (see highestRoleLevel() below; matches the same request attribute UsersListWidget sets).
    List<Role> roleList = RoleRepository.findAll();
    context.getRequest().setAttribute("roleList", roleList);
    context.getRequest().setAttribute("actingRoleLevel",
        highestRoleLevel(context.getUserSession(), roleList != null ? roleList : new ArrayList<>()));

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

    // Populate the groups -- "All Guests" has no checkbox on this form (see users-list.jsp's New
    // User modal, "not a logged in user group"); if the target already belongs to it, e.g. via
    // CSV/OAuth group provisioning, preserve that rather than letting an unrelated save drop it.
    List<Group> groupList = GroupRepository.findAll();
    if (groupList != null) {
      boolean targetHasAllGuests = targetAlreadyHasAllGuests(userBean.getId());
      List<Group> userGroupList = new ArrayList<>();
      for (Group group : groupList) {
        if ("All Guests".equals(group.getName())) {
          if (targetHasAllGuests) {
            userGroupList.add(group);
          }
          continue;
        }
        String groupValue = context.getParameter("groupId" + group.getId());
        if (groupValue != null && groupValue.equals(String.valueOf(group.getId()))) {
          userGroupList.add(group);
        }
      }
      userBean.setGroupList(userGroupList);
    }

    // An existing id means an edit; a new record is a create
    boolean isUpdate = userBean.getId() > -1;

    // An editor may not repoint the sign-in identity of an account that outranks them. The role list
    // above is filtered rather than refused because the form submits every checkbox on every save, so
    // an unrelated edit would otherwise strip a role the editor never meant to touch -- preserving is
    // protection against an accident. Email is different: it is a free-text field, so a changed value
    // was deliberately typed, and silently restoring it would report "User was saved" while discarding
    // what the editor entered. This refuses instead, and only when the value would actually change, so
    // correcting a typo in an admin's name (or timezone, or department) still works.
    //
    // Why this is an escalation and not a cosmetic edit: User.email is where the password reset link
    // is delivered (site-workflows.yml resolves to-user from the account id at send time), and
    // SaveUserCommand re-syncs username -- the sign-in identifier -- from the email whenever the two
    // previously matched. /admin/modify-user is reachable by community-manager and by a users:manage
    // capability-only grantee with no legacy role at all.
    //
    // Checked BEFORE the step-up prompt below, unlike UserDetailsWidget's resetPassword/resetMfa which
    // step up first: those prompt from a dispatch table that cannot know the target yet, whereas the
    // target is already in hand here, and demanding a credential for a save that is going to be
    // refused either way is a prompt with nothing behind it. Nothing is disclosed by ordering it this
    // way -- the acting user can already read the target's roles on /admin/users.
    if (isUpdate) {
      User existing = LoadUserCommand.loadUser(userBean.getId());
      if (existing != null && UserDetailsWidget.targetOutranksActor(context, existing)
          && identityFieldsChanged(existing, userBean)) {
        LOG.warn("Blocked sign-in identity change: user " + context.getUserId()
            + " attempted to change the email/username of user " + existing.getId());
        context.setErrorMessage(
            "You cannot change the sign-in email or username of an account with a higher role level than your own");
        context.setRequestObject(userBean);
        context.setRedirect("/admin/modify-user?userId=" + userBean.getId());
        return context;
      }
    }

    // Require a recent step-up before saving role or group changes. This runs after the bean is
    // populated so the prompt can redisplay what was submitted -- see redisplayForStepUp().
    if (!isStepUpSatisfied(context, userBean)) {
      return context;
    }

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

    // Break-glass, applied after the save because it cannot travel through it -- see
    // applyBreakGlass. Only reached once the step-up above is satisfied.
    applyBreakGlass(context, user);

    // Record the change with the effective roles and groups
    AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT, eventType, AuditEventCommand.SUCCESS,
        "user", String.valueOf(user.getId()), user.getEmail(), AuditEventCommand.describeRolesAndGroups(user));

    // Determine the page to return to
    context.setSuccessMessage("User was saved");
    context.setRedirect("/admin/user-details?userId=" + user.getId());
    return context;

  }

  /**
   * Applies the break-glass checkbox, if it changed.
   *
   * <p>
   * Deliberately not routed through {@code SaveUserCommand}: that command copies an explicit
   * allowlist of fields onto a fresh {@link User}, and {@code break_glass} is in neither that list
   * nor the repository's insert/update column set. Only {@code updateBreakGlass} writes it. That is
   * why {@code BeanUtils.populate} setting {@code breakGlass} from a crafted parameter has never
   * reached the database, and this method keeps it that way -- the flag moves only on an explicit,
   * admin-scoped, step-up-gated call.
   * </p>
   *
   * <p>
   * Offered for admin accounts only, checked against the SAVED roles rather than the submitted
   * form: the role list is filtered for escalation above, so what the editor asked for and what the
   * account ends up holding are not always the same thing.
   * </p>
   */
  private void applyBreakGlass(WidgetContext context, User user) {
    if (!user.hasRole("admin")) {
      // The form does not render the toggle for a non-admin, so a value here was not offered.
      return;
    }
    boolean requested = "true".equals(context.getParameter("breakGlassAccount"));
    if (requested == user.getBreakGlass()) {
      return;
    }

    if (!requested && UserRepository.countBreakGlassAccounts() <= 1) {
      // Allowed, not refused: refusing would leave no way to clear the flag without marking another
      // account first, and the warning is the part that actually helps. Said before the write so the
      // message is accurate whether or not the update then succeeds.
      context.setWarningMessage("Break-glass was cleared on " + user.getEmail()
          + ", and no break-glass account remains. If MFA enforcement is turned on for a role every"
          + " administrator holds, there is now no account exempt from the enrollment redirect.");
    }

    if (UserRepository.updateBreakGlass(user, requested) == null) {
      LOG.error("Could not update break_glass for user " + user.getId());
      context.setWarningMessage("The account was saved, but the break-glass setting could not be changed.");
      return;
    }
    AuditEventCommand.record(context, AuditEventCommand.USER_MANAGEMENT,
        requested ? "user.break-glass.set" : "user.break-glass.cleared", AuditEventCommand.SUCCESS,
        "user", String.valueOf(user.getId()), user.getEmail(), null);
  }

  /**
   * Whether the acting user has satisfied step-up authentication for this save. When they have not,
   * the form is redisplayed with the prompt and this returns false, leaving the caller to return the
   * context untouched -- nothing is written.
   */
  private boolean isStepUpSatisfied(WidgetContext context, User userBean) {
    if (StepUpAuthCommand.isValid(context.getUserSession())) {
      return true;
    }
    String stepUpCredential = context.getParameter("stepUpCredential");
    if (StringUtils.isNotBlank(stepUpCredential)) {
      User actingUser = LoadUserCommand.loadUser(context.getUserId());
      if (StepUpAuthCommand.verify(context.getUserSession(), actingUser, stepUpCredential)) {
        return true;
      }
      context.setErrorMessage("Re-authentication failed. Enter your password or authenticator code.");
    }
    redisplayForStepUp(context, userBean);
    return false;
  }

  /**
   * Redisplay the form with the step-up prompt, carrying the submitted record back to the JSP.
   *
   * <p>The bean has to travel with the redisplay. user-form.jsp renders the hidden id from the "user"
   * request attribute, and UserFormWidget#execute falls back to <code>new User()</code> when neither a
   * request object nor a resolvable userId parameter is present. Without both of these the prompt came
   * back as an empty form with id="-1", which discarded the editor's selections and turned the very
   * next submit into a create -- so the two-step prompt could never be completed for an existing user.
   */
  private void redisplayForStepUp(WidgetContext context, User userBean) {
    context.addSharedRequestValue("stepUpRequired", "true");
    context.setRequestObject(userBean);
    context.setRedirect("/admin/modify-user?userId=" + userBean.getId());
  }

  /**
   * The highest role level the acting user holds, found by matching their session role codes against
   * the authoritative role list (which carries the levels). Returns 0 when nothing matches, which
   * fails closed -- no role above 0 can then be granted. Package-private so UsersListWidget's New User
   * flow can enforce the same rule when creating a user, instead of duplicating the logic.
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
   * Whether this save would change the target's sign-in identity -- the email the password reset link
   * is delivered to, or the username it is signed in with.
   *
   * <p>Deliberately does not reproduce {@code SaveUserCommand}'s email/username sync rules. The only
   * two ways either value can move are a changed email (which the sync then carries into the username)
   * or an explicitly submitted, different username, and both are caught here directly -- duplicating
   * the sync logic would just give it a second copy to drift from.
   *
   * <p>Comparison is trimmed but case-sensitive, so a case-only email change is treated as a change
   * and refused. That fails closed rather than reasoning about which providers treat a local part
   * case-insensitively.
   */
  private static boolean identityFieldsChanged(User existing, User submitted) {
    if (!sameValue(existing.getEmail(), submitted.getEmail())) {
      return true;
    }
    // user-form.jsp posts the username as a hidden field carrying the current value, and
    // SaveUserCommand defaults a blank one from the email, so only an explicit different value is a
    // change the editor asked for.
    return StringUtils.isNotBlank(submitted.getUsername())
        && !sameValue(existing.getUsername(), submitted.getUsername());
  }

  /** Null-safe, trimmed, case-sensitive equality -- a blank stored value and a blank submitted one
   *  are the same value, and blanking a populated field counts as a change. */
  private static boolean sameValue(String stored, String submitted) {
    return StringUtils.trimToEmpty(stored).equals(StringUtils.trimToEmpty(submitted));
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

  /**
   * True when the target user already belongs to "All Guests" -- a group this form has no
   * checkbox for (see users-list.jsp's New User modal, "not a logged in user group"). Existing
   * membership is preserved rather than dropped by an unrelated save.
   */
  private static boolean targetAlreadyHasAllGuests(long userId) {
    if (userId < 0) {
      return false;
    }
    User existing = LoadUserCommand.loadUser(userId);
    if (existing == null || existing.getGroupList() == null) {
      return false;
    }
    for (Group group : existing.getGroupList()) {
      if ("All Guests".equals(group.getName())) {
        return true;
      }
    }
    return false;
  }
}
