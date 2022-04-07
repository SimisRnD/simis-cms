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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.routines.EmailValidator;

import javax.security.auth.login.AccountException;

import static com.simisinc.platform.application.register.GenerateUserUniqueIdCommand.generateUniqueId;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/8/18 9:36 PM
 */
public class SaveUserCommand {

  private static Log LOG = LogFactory.getLog(SaveUserCommand.class);

  public static final String allowedChars = "1234567890abcdefghijklmnopqrstuvwyxz";

  public static User saveUser(User userBean) throws DataException, AccountException {

    // Validate the required fields
    if (StringUtils.isBlank(userBean.getFirstName()) ||
        StringUtils.isBlank(userBean.getLastName()) ||
        StringUtils.isBlank(userBean.getEmail())) {
      throw new DataException("Please check the fields and try again");
    }

    EmailValidator emailValidator = EmailValidator.getInstance(false);
    if (!emailValidator.isValid(userBean.getEmail())) {
      throw new DataException("Check the email address and try again");
    }

    // Determine the user saving the record
    User userMakingChange = LoadUserCommand.loadUser(userBean.getModifiedBy());
    if (userMakingChange == null) {
      throw new DataException("There has been validation error");
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
      // Validate
      if (user.getId() == userBean.getModifiedBy()) {
        if (user.hasRole("admin") && !userBean.hasRole("admin")) {
          throw new DataException("You cannot remove the Admin role from your own account");
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
    if (!userMakingChange.hasRole("admin")) {
      // Maintain the admin permission because the record already has it
      if (user.hasRole("admin") && !userBean.hasRole("admin")) {
        userBean.getRoleList().add(userBean.getRole("admin"));
      } else if (userBean.hasRole("admin")) {
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
    user.setRoleList(userBean.getRoleList());
    user.setGroupList(userBean.getGroupList());
    user.setTimeZone(userBean.getTimeZone());
    return UserRepository.save(user);
  }

}
