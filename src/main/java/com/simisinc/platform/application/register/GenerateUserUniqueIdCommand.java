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

import com.simisinc.platform.application.cms.MakeContentUniqueIdCommand;
import com.simisinc.platform.application.login.DeleteUserCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;

/**
 * Generates a plain text string - a uniqueId for URLs and referencing
 *
 * @author matt rajkowski
 * @created 1/10/22 8:57 PM
 */
public class GenerateUserUniqueIdCommand {

  public static String generateUniqueId(User previousUser, User user) {

    // The account that holds deleted accounts' content keeps its ID whatever it is called. Deletes
    // find it by that ID, so renaming it here would leave the next delete to create a second holding
    // account, splitting the content between them and leaving this one unrecognized.
    if (DeleteUserCommand.isHoldingAccount(previousUser)) {
      return previousUser.getUniqueId();
    }

    // Use an existing uniqueId
    if (previousUser != null && previousUser.getUniqueId() != null) {
      // See if the name changed
      if (previousUser.getFullName().equals(user.getFullName())) {
        return previousUser.getUniqueId();
      }
    }

    // Create a new uniqueId
    String value = MakeContentUniqueIdCommand.parseToValidValue(user.getFullName());

    // Find the next available unique instance
    int count = 1;
    String uniqueId = value;
    while (isReserved(uniqueId) || UserRepository.findByUniqueId(uniqueId) != null) {
      ++count;
      uniqueId = value + "-" + count;
    }
    return uniqueId;
  }

  /**
   * IDs kept for the system's own accounts. Someone named "Deleted Accounts" must not be given the
   * ID of the account that holds deleted accounts' content, even before that account exists.
   */
  private static boolean isReserved(String uniqueId) {
    return DeleteUserCommand.HOLDING_ACCOUNT_UNIQUE_ID.equals(uniqueId);
  }
}
