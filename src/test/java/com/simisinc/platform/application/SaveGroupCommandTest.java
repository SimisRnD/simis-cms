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

package com.simisinc.platform.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;

/**
 * groups.name is VARCHAR(100), which issue #1740 identified as the length limit most reachable by
 * ordinary use -- 100 characters is a plausible descriptive group name. Before these checks the
 * value reached Postgres, the write was refused, and the admin was told the system had failed and
 * to try again, with nothing naming the field or the limit.
 *
 * @author SimIS Inc.
 */
class SaveGroupCommandTest {

  private static Group groupBean(String name) {
    Group bean = new Group();
    bean.setName(name);
    return bean;
  }

  @Test
  void anOverLongNameIsRefusedWithTheLimitInTheMessage() {
    Group bean = groupBean("x".repeat(101));

    try (MockedStatic<GroupRepository> groupRepository = mockStatic(GroupRepository.class)) {
      DataException exception = assertThrows(DataException.class, () -> SaveGroupCommand.saveGroup(bean));

      assertTrue(exception.getMessage().contains("A name can be up to 100 characters"),
          "the message must name the limit, not say the system failed: " + exception.getMessage());
      groupRepository.verify(() -> GroupRepository.save(any()), never());
    }
  }

  @Test
  void aNameExactlyAtTheLimitIsAccepted() throws DataException {
    // the column holds 100, so 100 must save -- an off-by-one here refuses a legitimate group name
    String atLimit = "x".repeat(100);
    Group bean = groupBean(atLimit);

    try (MockedStatic<GroupRepository> groupRepository = mockStatic(GroupRepository.class)) {
      groupRepository.when(() -> GroupRepository.findByName(atLimit)).thenReturn(null);
      groupRepository.when(() -> GroupRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      Group saved = SaveGroupCommand.saveGroup(bean);

      assertEquals(atLimit, saved.getName());
    }
  }

  @Test
  void trailingWhitespaceDoesNotPushANameOverTheLimit() throws DataException {
    // GroupRepository trims before writing, so 100 characters plus spaces still fits
    String atLimit = "x".repeat(100);
    Group bean = groupBean(atLimit + "   ");

    try (MockedStatic<GroupRepository> groupRepository = mockStatic(GroupRepository.class)) {
      groupRepository.when(() -> GroupRepository.findByName(any())).thenReturn(null);
      groupRepository.when(() -> GroupRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      SaveGroupCommand.saveGroup(bean);

      groupRepository.verify(() -> GroupRepository.save(any()));
    }
  }

  @Test
  void aBlankNameStillReportsMissingRatherThanTooLong() {
    // the length check is chained onto the blank check, so an empty form reports what is actually
    // wrong with it
    Group bean = groupBean("");

    try (MockedStatic<GroupRepository> groupRepository = mockStatic(GroupRepository.class)) {
      DataException exception = assertThrows(DataException.class, () -> SaveGroupCommand.saveGroup(bean));

      assertTrue(exception.getMessage().contains("A name is required"), exception.getMessage());
      assertTrue(!exception.getMessage().contains("can be up to"), exception.getMessage());
      groupRepository.verify(() -> GroupRepository.save(any()), never());
    }
  }
}
