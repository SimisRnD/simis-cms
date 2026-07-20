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

package com.simisinc.platform.presentation.controller;

import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the audit detail formatting used by the Phase 2 admin audit events.
 *
 * @author SimIS Inc.
 */
class AuditEventCommandTest {

  private Role role(String code) {
    Role role = new Role();
    role.setCode(code);
    return role;
  }

  private Group group(String name) {
    Group group = new Group();
    group.setName(name);
    return group;
  }

  @Test
  void describesRolesAndGroupsAsCodesAndNames() {
    User user = new User();
    List<Role> roles = new ArrayList<>();
    roles.add(role("admin"));
    roles.add(role("content-manager"));
    user.setRoleList(roles);
    List<Group> groups = new ArrayList<>();
    groups.add(group("All Users"));
    user.setGroupList(groups);

    assertEquals("roles=[admin,content-manager]; groups=[All Users]",
        AuditEventCommand.describeRolesAndGroups(user));
  }

  @Test
  void describesEmptyRolesAndGroups() {
    User user = new User();
    user.setRoleList(new ArrayList<>());
    user.setGroupList(new ArrayList<>());

    assertEquals("roles=[]; groups=[]", AuditEventCommand.describeRolesAndGroups(user));
  }

  @Test
  void isNullSafeForAUserWithNoLists() {
    // A user whose role/group lists were never populated must not throw
    User user = new User();

    assertEquals("roles=[]; groups=[]", AuditEventCommand.describeRolesAndGroups(user));
  }
}
