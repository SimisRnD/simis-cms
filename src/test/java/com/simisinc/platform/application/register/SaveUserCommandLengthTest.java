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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.User;

/**
 * users.first_name, last_name, title and organization are VARCHAR(100) -- the narrowest human-typed
 * columns reached from an admin form (issue #1740). 100 characters is well within what someone can
 * put in a job title or an organization name, particularly when pasting.
 *
 * @author SimIS Inc.
 */
class SaveUserCommandLengthTest {

  private static User user() {
    User bean = new User();
    bean.setFirstName("Ada");
    bean.setLastName("Lovelace");
    bean.setEmail("ada@example.com");
    return bean;
  }

  @Test
  void anOverLongFirstNameIsRefusedWithTheLimitInTheMessage() {
    User bean = user();
    bean.setFirstName("x".repeat(101));

    DataException exception = assertThrows(DataException.class, () -> SaveUserCommand.saveUser(bean));

    assertTrue(exception.getMessage().contains("A first name can be up to 100 characters"),
        exception.getMessage());
  }

  @Test
  void anOverLongJobTitleIsRefused() {
    // optional, and the one most likely to run long in practice
    User bean = user();
    bean.setTitle("x".repeat(101));

    DataException exception = assertThrows(DataException.class, () -> SaveUserCommand.saveUser(bean));

    assertTrue(exception.getMessage().contains("A title can be up to 100 characters"),
        exception.getMessage());
  }

  @Test
  void anOverLongOrganizationIsRefused() {
    User bean = user();
    bean.setOrganization("x".repeat(101));

    DataException exception = assertThrows(DataException.class, () -> SaveUserCommand.saveUser(bean));

    assertTrue(exception.getMessage().contains("An organization can be up to 100 characters"),
        exception.getMessage());
  }

  @Test
  void aNameExactlyAtTheLimitIsNotRefusedForLength() {
    User bean = user();
    bean.setFirstName("x".repeat(100));

    try {
      SaveUserCommand.saveUser(bean);
    } catch (Exception e) {
      assertTrue(e.getMessage() == null || !e.getMessage().contains("can be up to"),
          "a name at exactly the limit must not be refused for length: " + e.getMessage());
    }
  }

  @Test
  void anAbsentOptionalFieldIsNotReportedAsTooLong() {
    // title and organization are optional; leaving them empty must not trip a length check
    User bean = user();

    try {
      SaveUserCommand.saveUser(bean);
    } catch (Exception e) {
      assertTrue(e.getMessage() == null || !e.getMessage().contains("can be up to"), e.getMessage());
    }
  }
}
