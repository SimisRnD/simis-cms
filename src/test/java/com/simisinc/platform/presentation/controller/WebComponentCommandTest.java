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

import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author matt rajkowski
 * @created 4/10/2022 9:24 AM
 */
class WebComponentCommandTest {
  @Test
  void userHasRoleAndGroupTest() {

    // Related user information
    List<Role> roleList = new ArrayList<>();
    Role administratorRole = new Role("System Administrator", "admin");
    roleList.add(administratorRole);

    // Related user information
    List<Group> groupList = new ArrayList<>();
    Group testGroup = new Group("Testers", "testers");
    groupList.add(testGroup);

    // User information
    User user = new User();
    user.setId(1L);
    user.setRoleList(roleList);
    user.setGroupList(groupList);

    // Log the user in
    UserSession userSession = new UserSession();
    userSession.login(user);

    // Component information (no additional roles/groups required)
    List<String> roles = new ArrayList<>();
    List<String> groups = new ArrayList<>();
    Assertions.assertTrue(WebComponentCommand.allowsUser(roles, groups, userSession));

    // Any of these: admin AND any of these: testers
    roles.add("admin");
    groups.add("testers");
    Assertions.assertTrue(WebComponentCommand.allowsUser(roles, groups, userSession));
  }

  @Test
  void userHasRoleTest() {

    // Related user information
    List<Role> roleList = new ArrayList<>();
    Role administratorRole = new Role("System Administrator", "admin");
    roleList.add(administratorRole);

    // Related user information
    List<Group> groupList = new ArrayList<>();

    // User information
    User user = new User();
    user.setId(1L);
    user.setRoleList(roleList);
    user.setGroupList(groupList);

    // Log the user in
    UserSession userSession = new UserSession();
    userSession.login(user);

    // Component information (no additional roles/groups required)
    List<String> roles = new ArrayList<>();
    List<String> groups = new ArrayList<>();
    Assertions.assertTrue(WebComponentCommand.allowsUser(roles, groups, userSession));

    // Any of these: admin, users
    roles.add("admin");
    roles.add("users");
    Assertions.assertTrue(WebComponentCommand.allowsUser(roles, groups, userSession));
  }

  @Test
  void userHasGroupTest() {

    // Related user information
    List<Role> roleList = new ArrayList<>();

    // Related user information
    List<Group> groupList = new ArrayList<>();
    Group testGroup = new Group("Testers", "testers");
    groupList.add(testGroup);

    // User information
    User user = new User();
    user.setId(1L);
    user.setRoleList(roleList);
    user.setGroupList(groupList);

    // Log the user in
    UserSession userSession = new UserSession();
    userSession.login(user);

    // Component information (no additional roles/groups required)
    List<String> roles = new ArrayList<>();
    List<String> groups = new ArrayList<>();
    Assertions.assertTrue(WebComponentCommand.allowsUser(roles, groups, userSession));

    // Any of these: testers
    groups.add("testers");
    Assertions.assertTrue(WebComponentCommand.allowsUser(roles, groups, userSession));

    // Any of these: testers, researchers
    groups.add("researchers");
    Assertions.assertTrue(WebComponentCommand.allowsUser(roles, groups, userSession));
  }

  @Test
  void userHasRoleAndDoesNotHaveGroupTest() {

    // Related user information
    List<Role> roleList = new ArrayList<>();
    Role administratorRole = new Role("System Administrator", "admin");
    roleList.add(administratorRole);

    // Related user information
    List<Group> groupList = new ArrayList<>();

    // User information
    User user = new User();
    user.setId(1L);
    user.setRoleList(roleList);
    user.setGroupList(groupList);

    // Log the user in
    UserSession userSession = new UserSession();
    userSession.login(user);

    // Component information (no additional roles/groups required)
    List<String> roles = new ArrayList<>();
    List<String> groups = new ArrayList<>();

    // Any of these: admin AND any of these: testers
    roles.add("admin");
    groups.add("testers");
    Assertions.assertFalse(WebComponentCommand.allowsUser(roles, groups, userSession));

    // Any of these: admin, users AND any of these: researchers
    roles.add("users");
    groups.add("researchers");
    Assertions.assertFalse(WebComponentCommand.allowsUser(roles, groups, userSession));
  }

  @Test
  void userDoesNotHaveRoleAndHasGroupTest() {

    // Related user information
    List<Role> roleList = new ArrayList<>();

    // Related user information
    List<Group> groupList = new ArrayList<>();
    Group testGroup = new Group("Testers", "testers");
    groupList.add(testGroup);

    // User information
    User user = new User();
    user.setId(1L);
    user.setRoleList(roleList);
    user.setGroupList(groupList);

    // Log the user in
    UserSession userSession = new UserSession();
    userSession.login(user);

    // Component information (no additional roles/groups required)
    List<String> roles = new ArrayList<>();
    List<String> groups = new ArrayList<>();

    // Any of these: admin AND any of these: testers
    roles.add("admin");
    groups.add("testers");
    Assertions.assertFalse(WebComponentCommand.allowsUser(roles, groups, userSession));

    // Any of these: admin AND any of these: testers, researchers
    groups.add("researchers");
    Assertions.assertFalse(WebComponentCommand.allowsUser(roles, groups, userSession));

    // Any of these: admin, users AND any of these: testers, researchers
    roles.add("users");
    Assertions.assertTrue(WebComponentCommand.allowsUser(roles, groups, userSession));
  }

  @Test
  void userDoesNotHaveRoleAndGroupTest() {
    // Related user information
    List<Role> roleList = new ArrayList<>();
    List<Group> groupList = new ArrayList<>();

    // User information
    User user = new User();
    user.setId(1L);
    user.setRoleList(roleList);
    user.setGroupList(groupList);

    // Log the user in
    UserSession userSession = new UserSession();
    userSession.login(user);

    // Component access information (no additional roles/groups required)
    List<String> roles = new ArrayList<>();
    List<String> groups = new ArrayList<>();
    Assertions.assertTrue(WebComponentCommand.allowsUser(roles, groups, userSession));

    // Any of these: admin AND any of these: testers
    roles.add("admin");
    groups.add("testers");
    Assertions.assertFalse(WebComponentCommand.allowsUser(roles, groups, userSession));
  }

  @Test
  void userDoesNotHaveRoleTest() {
    // Related user information
    List<Role> roleList = new ArrayList<>();
    List<Group> groupList = new ArrayList<>();

    // User information
    User user = new User();
    user.setId(1L);
    user.setRoleList(roleList);
    user.setGroupList(groupList);

    // Log the user in
    UserSession userSession = new UserSession();
    userSession.login(user);

    // Component access information (no additional roles/groups required)
    List<String> roles = new ArrayList<>();
    roles.add("admin");
    List<String> groups = new ArrayList<>();

    // Any of these: admin
    Assertions.assertFalse(WebComponentCommand.allowsUser(roles, groups, userSession));

    // Any of these: admin, users
    roles.add("users");
    Assertions.assertTrue(WebComponentCommand.allowsUser(roles, groups, userSession));
  }

  @Test
  void userDoesNotHaveGroupTest() {
    // Related user information
    List<Role> roleList = new ArrayList<>();
    List<Group> groupList = new ArrayList<>();

    // User information
    User user = new User();
    user.setId(1L);
    user.setRoleList(roleList);
    user.setGroupList(groupList);

    // Log the user in
    UserSession userSession = new UserSession();
    userSession.login(user);

    // Component access information (no additional roles/groups required)
    List<String> roles = new ArrayList<>();
    List<String> groups = new ArrayList<>();
    groups.add("testers");

    // Any of these: testers
    Assertions.assertFalse(WebComponentCommand.allowsUser(roles, groups, userSession));

    // Any of these: testers, researchers
    groups.add("researchers");
    Assertions.assertFalse(WebComponentCommand.allowsUser(roles, groups, userSession));
  }

  @Test
  void guestHasRoleTest() {
    // Not a user session
    UserSession userSession = new UserSession();

    // Component information (no additional roles/groups required)
    List<String> roles = new ArrayList<>();
    List<String> groups = new ArrayList<>();
    Assertions.assertTrue(WebComponentCommand.allowsUser(roles, groups, userSession));

    // Component access information (roles and groups required)
    roles.add("guest");

    // Any of these: admin AND any of these: testers
    Assertions.assertTrue(WebComponentCommand.allowsUser(roles, groups, userSession));
  }


  @Test
  void guestDoesNotHaveRoleAndGroupTest() {
    // Not a user session
    UserSession userSession = new UserSession();

    // Component information (no additional roles/groups required)
    List<String> roles = new ArrayList<>();
    List<String> groups = new ArrayList<>();
    Assertions.assertTrue(WebComponentCommand.allowsUser(roles, groups, userSession));

    // Component access information (roles and groups required)
    roles.add("admin");
    groups.add("testers");

    // Any of these: admin AND any of these: testers
    Assertions.assertFalse(WebComponentCommand.allowsUser(roles, groups, userSession));
  }

  @Test
  void guestDoesNotHaveRoleTest() {
    // Not a user session
    UserSession userSession = new UserSession();

    // Component information (no additional roles/groups required)
    List<String> roles = new ArrayList<>();
    roles.add("admin");
    List<String> groups = new ArrayList<>();

    // Any of these: admin
    Assertions.assertFalse(WebComponentCommand.allowsUser(roles, groups, userSession));

    // Any of these: admin, users
    roles.add("users");
    Assertions.assertFalse(WebComponentCommand.allowsUser(roles, groups, userSession));
  }

  @Test
  void guestDoesNotHaveGroupTest() {
    // Not a user session
    UserSession userSession = new UserSession();

    // Component access information (roles and groups required)
    List<String> roles = new ArrayList<>();
    List<String> groups = new ArrayList<>();

    // Any of these: testers
    groups.add("testers");
    Assertions.assertFalse(WebComponentCommand.allowsUser(roles, groups, userSession));

    // Any of these: testers, researchers
    groups.add("researchers");
    Assertions.assertFalse(WebComponentCommand.allowsUser(roles, groups, userSession));
  }

  // --- capability= (issue #733: a page-level role="..." gate previously had no hasPermission()
  // path at all, so a user granted a capability directly -- but holding no matching legacy role --
  // was blocked by the page container before the widget's own hasPermission() check ever ran) ---

  private static UserSession sessionWithCapability(String code) {
    Capability capability = new Capability();
    capability.setCode(code);
    User user = new User();
    user.setId(1L);
    user.setRoleList(new ArrayList<>());
    user.setGroupList(new ArrayList<>());
    user.setCapabilityList(List.of(capability));
    UserSession userSession = new UserSession();
    userSession.login(user);
    return userSession;
  }

  @Test
  void userWithCapabilityButNoMatchingRoleIsAllowed() {
    UserSession userSession = sessionWithCapability("admin:manage");

    List<String> roles = List.of("admin");
    List<String> groups = Collections.emptyList();
    List<String> capabilities = List.of("admin:manage");

    Assertions.assertFalse(WebComponentCommand.allowsUser(roles, groups, userSession),
        "the 3-arg overload has no capability path -- confirms this user genuinely lacks the role");
    Assertions.assertTrue(WebComponentCommand.allowsUser(roles, groups, capabilities, userSession, false),
        "a listed capability the user holds must satisfy the check even without a matching role");
  }

  @Test
  void userWithNeitherRoleNorMatchingCapabilityIsDenied() {
    UserSession userSession = sessionWithCapability("some-other:capability");

    List<String> roles = List.of("admin");
    List<String> groups = Collections.emptyList();
    List<String> capabilities = List.of("admin:manage");

    Assertions.assertFalse(WebComponentCommand.allowsUser(roles, groups, capabilities, userSession, false));
  }

  @Test
  void userWithTheRoleIsStillAllowedWhenACapabilityIsAlsoListed() {
    List<Role> roleList = new ArrayList<>();
    roleList.add(new Role("System Administrator", "admin"));
    User user = new User();
    user.setId(1L);
    user.setRoleList(roleList);
    user.setGroupList(new ArrayList<>());
    UserSession userSession = new UserSession();
    userSession.login(user);

    List<String> roles = List.of("admin");
    List<String> groups = Collections.emptyList();
    List<String> capabilities = List.of("admin:manage");

    Assertions.assertTrue(WebComponentCommand.allowsUser(roles, groups, capabilities, userSession, false),
        "adding a capability= list must not break the existing role-only path");
  }

  @Test
  void capabilityOnlyPageDeniesAUserWithoutIt() {
    UserSession userSession = sessionWithCapability("some-other:capability");

    // No role= or group= at all -- capability= alone must still gate access (not open-by-default).
    List<String> roles = Collections.emptyList();
    List<String> groups = Collections.emptyList();
    List<String> capabilities = List.of("admin:manage");

    Assertions.assertFalse(WebComponentCommand.allowsUser(roles, groups, capabilities, userSession, false));
  }

  @Test
  void capabilityOnlyPageAllowsAUserWithIt() {
    UserSession userSession = sessionWithCapability("admin:manage");

    List<String> roles = Collections.emptyList();
    List<String> groups = Collections.emptyList();
    List<String> capabilities = List.of("admin:manage");

    Assertions.assertTrue(WebComponentCommand.allowsUser(roles, groups, capabilities, userSession, false));
  }

  @Test
  void pageOverloadReadsCapabilitiesFromThePageItself() {
    UserSession userSession = sessionWithCapability("admin:manage");

    Page page = new Page("/admin/role-capabilities", null, null);
    page.setRoles(List.of("admin"));
    page.setCapabilities(List.of("admin:manage"));

    Assertions.assertTrue(WebComponentCommand.allowsUser(page, userSession),
        "the Page-specific overload must read page.getCapabilities(), not just page.getRoles()");
  }
}
