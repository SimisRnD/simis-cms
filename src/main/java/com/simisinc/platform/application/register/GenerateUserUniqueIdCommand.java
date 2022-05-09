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
    while (UserRepository.findByUniqueId(uniqueId) != null) {
      ++count;
      uniqueId = value + "-" + count;
    }
    return uniqueId;
  }
}
