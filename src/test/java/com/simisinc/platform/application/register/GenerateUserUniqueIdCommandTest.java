/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.login.DeleteUserCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;

/**
 * A user's unique ID is made from their name. The one the deleted-accounts holding account uses
 * must never be given to a person, or a person could end up with every deleted account's content.
 *
 * @author SimIS Inc.
 */
class GenerateUserUniqueIdCommandTest {

  private static User named(String firstName, String lastName) {
    User user = new User();
    user.setFirstName(firstName);
    user.setLastName(lastName);
    return user;
  }

  @Test
  void aNameIsTurnedIntoItsId() {
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      userRepo.when(() -> UserRepository.findByUniqueId(anyString())).thenReturn(null);

      assertEquals("jane-doe", GenerateUserUniqueIdCommand.generateUniqueId(null, named("Jane", "Doe")));
    }
  }

  @Test
  void someoneNamedDeletedAccountsDoesNotGetTheReservedId() {
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      // Even before the holding account exists, when nothing in the database has the ID yet
      userRepo.when(() -> UserRepository.findByUniqueId(anyString())).thenReturn(null);

      String uniqueId = GenerateUserUniqueIdCommand.generateUniqueId(null, named("Deleted", "Accounts"));

      assertEquals(DeleteUserCommand.HOLDING_ACCOUNT_UNIQUE_ID + "-2", uniqueId);
    }
  }
}
