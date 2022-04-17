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
import com.simisinc.platform.application.UserPasswordCommand;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.routines.EmailValidator;

import javax.security.auth.login.AccountException;
import java.util.ArrayList;
import java.util.List;

/**
 * Methods for user registration
 *
 * @author matt rajkowski
 * @created 4/8/18 9:36 PM
 */
public class RegisterUserCommand {

  private static Log LOG = LogFactory.getLog(RegisterUserCommand.class);

  public static User registerUser(User userBean) throws DataException, AccountException {

    // Validate the required fields
    if (StringUtils.isBlank(userBean.getFirstName()) ||
        StringUtils.isBlank(userBean.getLastName()) ||
        StringUtils.isBlank(userBean.getEmail()) ||
        StringUtils.isBlank(userBean.getPassword())) {
      throw new DataException("Please check the fields and try again");
    }

    if (userBean.getPassword().trim().length() < 6) {
      throw new DataException("Passwords must be at least 6 characters");
    }

    EmailValidator emailValidator = EmailValidator.getInstance(false);
    if (!emailValidator.isValid(userBean.getEmail())) {
      throw new DataException("Check the email address and try again");
    }

    // See if a user exists with this email
    if (UserRepository.findByUsername(userBean.getEmail()) != null) {
      throw new AccountException("Registration could not be completed. There is an account with this email address already.");
    }

    // Hash the password
    String hash = UserPasswordCommand.hash(userBean.getPassword());

    // Transform the fields and store...
    User user = new User();
    user.setFirstName(userBean.getFirstName());
    user.setLastName(userBean.getLastName());
    user.setOrganization(userBean.getOrganization());
    user.setNickname(userBean.getNickname());
    user.setEmail(userBean.getEmail());
    user.setUsername(userBean.getEmail());
    user.setPassword(hash);
    // Assign the default group
    Group defaultGroup = GroupRepository.findByName("All Users");
    if (defaultGroup != null) {
      List<Group> userGroupList = new ArrayList<>();
      userGroupList.add(defaultGroup);
      user.setGroupList(userGroupList);
    }
    return UserRepository.add(user);
  }

}
