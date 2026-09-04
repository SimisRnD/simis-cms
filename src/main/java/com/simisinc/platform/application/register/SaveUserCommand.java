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

package com.simisinc.platform.application.register;

import static com.simisinc.platform.application.register.GenerateUserUniqueIdCommand.generateUniqueId;

import java.util.List;

import javax.security.auth.login.AccountException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sanctionco.jmail.JMail;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.FieldLengthCommand;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;

/**
 * Validates and saves a user object
 *
 * @author matt rajkowski
 * @created 4/8/18 9:36 PM
 */
public class SaveUserCommand {

  // These are the narrowest human-typed columns reached from an admin form: 100 characters is well
  // within what someone can put in a name or job title, particularly when pasting (issue #1740).
  // This command throws on the first problem rather than accumulating, so these follow that shape.
  // @column users.first_name
  private static final int MAX_FIRST_NAME_LENGTH = 100;
  // @column users.last_name
  private static final int MAX_LAST_NAME_LENGTH = 100;
  // @column users.title
  private static final int MAX_TITLE_LENGTH = 100;
  // @column users.organization
  private static final int MAX_ORGANIZATION_LENGTH = 100;
  // @column users.email
  private static final int MAX_EMAIL_LENGTH = 255;


  private static Log LOG = LogFactory.getLog(SaveUserCommand.class);

  public static final String allowedChars = "1234567890abcdefghijklmnopqrstuvwxyz";

  public static User saveUser(User userBean) throws DataException, AccountException {
    return saveUser(userBean, false);
  }

  public static User saveUser(User userBean, boolean isSystemUser) throws DataException, AccountException {

    // Validate the required fields
    if (StringUtils.isBlank(userBean.getFirstName()) ||
        StringUtils.isBlank(userBean.getLastName()) ||
        StringUtils.isBlank(userBean.getEmail())) {
      throw new DataException("Please check the fields and try again");
    }
    if (FieldLengthCommand.exceedsLimit(userBean.getFirstName(), MAX_FIRST_NAME_LENGTH)) {
      throw new DataException(FieldLengthCommand.tooLongMessage("A first name", MAX_FIRST_NAME_LENGTH));
    }
    if (FieldLengthCommand.exceedsLimit(userBean.getLastName(), MAX_LAST_NAME_LENGTH)) {
      throw new DataException(FieldLengthCommand.tooLongMessage("A last name", MAX_LAST_NAME_LENGTH));
    }
    if (FieldLengthCommand.exceedsLimit(userBean.getTitle(), MAX_TITLE_LENGTH)) {
      throw new DataException(FieldLengthCommand.tooLongMessage("A title", MAX_TITLE_LENGTH));
    }
    if (FieldLengthCommand.exceedsLimit(userBean.getOrganization(), MAX_ORGANIZATION_LENGTH)) {
      throw new DataException(FieldLengthCommand.tooLongMessage("An organization", MAX_ORGANIZATION_LENGTH));
    }
    if (FieldLengthCommand.exceedsLimit(userBean.getEmail(), MAX_EMAIL_LENGTH)) {
      throw new DataException(FieldLengthCommand.tooLongMessage("An email address", MAX_EMAIL_LENGTH));
    }

    if (!userBean.getEmail().equals(userBean.getUsername())) {
      if (!JMail.isValid(userBean.getEmail())) {
        throw new DataException("Check the email address and try again");
      }
    }

    // Determine the user saving the record
    User userMakingChange = null;
    if (!isSystemUser) {
      userMakingChange = LoadUserCommand.loadUser(userBean.getModifiedBy());
      if (userMakingChange == null) {
        throw new DataException("Could not find the user making the change");
      }
    }

    // Transform the fields and store...
    User user;
    if (userBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      user = LoadUserCommand.loadUser(userBean.getId());
      if (user == null) {
        throw new DataException("The existing record could not be found");
      }
      user.setModifiedBy(userBean.getModifiedBy());
      // See if a different user already has this email
      User userWithEmail = UserRepository.findByEmailAddress(userBean.getEmail());
      if (userWithEmail != null && userWithEmail.getId().longValue() != userBean.getId().longValue()) {
        throw new AccountException("Information could not be saved. There is an account with this email address already.");
      }
      // Validate (skip if managed by provider)
      if (!isSystemUser) {
        if (user.getId() == userBean.getModifiedBy()) {
          if (user.hasRole("admin") && !userBean.hasRole("admin")) {
            LOG.debug("prevented removing the Admin role");
            throw new DataException("You cannot remove the Admin role from your own account");
          }
        }
        // An editor may not repoint the identity of an account that outranks them. Unlike the other
        // profile fields below, email and username are the account's credentials-adjacent identity:
        // username is what AuthenticateLoginCommand resolves a sign-in against, and email is where
        // every password-reset link is delivered -- including the public /forgot-password flow, which
        // asks nothing about who is requesting it. Repointing an admin's email and then using that
        // public flow is a complete account takeover that no admin-side reset guard can observe, so
        // this has to be refused at the write rather than at any one reset path.
        //
        // Scoped deliberately to a *change* of those two fields: an unrelated save re-submits the
        // stored values unchanged and must still succeed, so a lower-ranked editor can go on
        // correcting a name, title or department on such a record.
        if (userMakingChange != null
            && highestRoleLevel(user.getRoleList()) > highestRoleLevel(userMakingChange.getRoleList())) {
          int actorLevel = highestRoleLevel(userMakingChange.getRoleList());
          int targetLevel = highestRoleLevel(user.getRoleList());
          if (!StringUtils.equalsIgnoreCase(user.getEmail(), userBean.getEmail())) {
            LOG.warn("Blocked identity change: user " + userMakingChange.getId() + " (level " + actorLevel
                + ") attempted to change the email of user " + user.getId() + " (level " + targetLevel + ")");
            throw new DataException(
                "You cannot change the email address of an account with a higher role level than your own");
          }
          if (StringUtils.isNotBlank(userBean.getUsername())
              && !StringUtils.equalsIgnoreCase(user.getUsername(), userBean.getUsername())) {
            LOG.warn("Blocked identity change: user " + userMakingChange.getId() + " (level " + actorLevel
                + ") attempted to change the username of user " + user.getId() + " (level " + targetLevel + ")");
            throw new DataException(
                "You cannot change the username of an account with a higher role level than your own");
          }
        }
      }
    } else {
      LOG.debug("Saving a new record... ");
      user = new User();
      // See if a user exists with this email
      if (UserRepository.findByUsername(userBean.getEmail()) != null) {
        throw new AccountException("Information could not be saved. There is an account with this email address already.");
      }
      user.setPassword("new");
      user.setCreatedBy(userBean.getCreatedBy());
      user.setCreated(userBean.getCreated());
    }

    // Verify the allowed roles
    LOG.debug("Verifying the allowed roles...");
    if (userMakingChange != null && !userMakingChange.hasRole("admin")) {
      // Maintain the admin permission because the record already has it. Take the role from
      // the stored user (which has it) -- userBean does not (the condition above requires
      // !userBean.hasRole("admin")), so userBean.getRole("admin") would be null and would add
      // a null into the role list, dropping the very permission this branch means to keep.
      if (user.hasRole("admin") && !userBean.hasRole("admin")) {
        userBean.getRoleList().add(user.getRole("admin"));
      } else if (!user.hasRole("admin") && userBean.hasRole("admin")) {
        // Don't allow it to be added if it wasn't there
        userBean.removeRole("admin");
      }
    }

    // @note set the uniqueId before setting the first and last name
    user.setUniqueId(generateUniqueId(user, userBean));
    user.setFirstName(userBean.getFirstName());
    user.setLastName(userBean.getLastName());
    user.setOrganization(userBean.getOrganization());
    user.setNickname(userBean.getNickname());
    // Determine if the username and email should be in-sync
    if (StringUtils.isNotBlank(user.getUsername()) && StringUtils.isNotBlank(user.getEmail()) &&
        user.getUsername().equals(user.getEmail())) {
      user.setEmail(userBean.getEmail());
      user.setUsername(userBean.getEmail());
    } else {
      // Keep the username and email separate
      user.setEmail(userBean.getEmail());
      if (StringUtils.isNotBlank(userBean.getUsername())) {
        user.setUsername(userBean.getUsername());
      } else {
        user.setUsername(userBean.getEmail());
      }
    }
    user.setTitle(userBean.getTitle());
    user.setDepartment(userBean.getDepartment());
    user.setCity(userBean.getCity());
    user.setState(userBean.getState());
    user.setCountry(userBean.getCountry());
    user.setPostalCode(userBean.getPostalCode());
    user.setLatitude(userBean.getLatitude());
    user.setLongitude(userBean.getLongitude());
    user.setRoleList(userBean.getRoleList());
    user.setGroupList(userBean.getGroupList());
    user.setTimeZone(userBean.getTimeZone());
    return UserRepository.save(user);
  }


  /**
   * The highest role level in the given list, or 0 when the account holds no roles.
   * <p>
   * Mirrors the identical rule in UserDetailsWidget.targetOutranksActor(), UnsuspendAccountCommand
   * and UserFormWidget -- a lower-privileged editor cannot act on an account that outranks them.
   * Duplicated rather than shared because those live in the presentation and login packages;
   * consolidating the copies is worth doing, but not inside a security fix.
   */
  private static int highestRoleLevel(List<Role> roleList) {
    int max = 0;
    if (roleList == null) {
      return max;
    }
    for (Role role : roleList) {
      if (role.getLevel() > max) {
        max = role.getLevel();
      }
    }
    return max;
  }
}
