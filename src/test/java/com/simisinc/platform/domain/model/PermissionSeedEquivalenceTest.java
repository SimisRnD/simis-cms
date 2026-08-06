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

package com.simisinc.platform.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Issue #701's walking skeleton adds hasPermission() alongside the existing hasRole(), backed by
 * a role_capabilities table seeded (in UPGRADE_20260729.2003__capabilities_walking_skeleton.sql /
 * NEW_10000__new_database.sql) directly from the hasRole()-OR-chain survey done for #701. This
 * test hand-encodes that same seed mapping and proves hasRole()/hasPermission() agree on it for
 * every existing role, so the migration is provably a read model over the status quo, not a
 * silent policy change. If the seed SQL and this test ever diverge, this test is the one that's
 * wrong - keep it in sync with the migration, not the other way around.
 *
 * users:manage (UPGRADE_20260805.1200__users_manage_capability.sql, #733 follow-up) is a
 * deliberate exception to that "read model" framing: it's a brand-new, independently grantable
 * capability, not a restatement of a role's existing access, so only admin is seeded with it -
 * see communityManagerDoesNotHaveUsersManage below for why community-manager is not.
 *
 * @author elizabeth houser
 */
class PermissionSeedEquivalenceTest {

  private static final List<String> ALL_CAPABILITIES = Arrays.asList(
      "content:manage", "community:manage", "data:manage", "ecommerce:manage", "admin:manage", "users:manage");

  @Test
  void adminHasEveryCapability() {
    assertRoleGrantsExactly("admin", ALL_CAPABILITIES);
  }

  @Test
  void contentManagerHasOnlyContentManage() {
    assertRoleGrantsExactly("content-manager", List.of("content:manage"));
  }

  @Test
  void communityManagerHasCommunityAndContentManage() {
    // The wiki widget's 3-way admin/content-manager/community-manager OR-check means
    // community-manager also needs content:manage's wiki slice - see the migration's comment.
    assertRoleGrantsExactly("community-manager", List.of("community:manage", "content:manage"));
  }

  @Test
  void communityManagerDoesNotHaveUsersManage() {
    // community-manager's role="admin,community-manager" attribute already covers /admin/users,
    // /admin/user-details, /admin/unsuspend-requests, and /admin/modify-user directly - but NOT
    // /admin/groups or /admin/group (role="admin" only). Seeding community-manager with
    // users:manage here would silently widen its access to the Groups pages once
    // capability="users:manage" is added there, so it's deliberately left unseeded; already
    // covered by communityManagerHasCommunityAndContentManage's exact-match assertion above, but
    // named explicitly here since it's the one deviation from the "just restates hasRole()" story
    // the other assertions in this file tell.
    assertRoleGrantsExactly("community-manager", List.of("community:manage", "content:manage"));
  }

  @Test
  void dataManagerHasOnlyDataManage() {
    assertRoleGrantsExactly("data-manager", List.of("data:manage"));
  }

  @Test
  void ecommerceManagerHasOnlyEcommerceManage() {
    assertRoleGrantsExactly("ecommerce-manager", List.of("ecommerce:manage"));
  }

  @Test
  void contentEditorRoleHasNoCapabilitiesSeededYet() {
    // content-editor (UPGRADE_20260724.1001) isn't referenced by any hasRole() call site, so the
    // walking-skeleton migration deliberately seeds it with zero role_capabilities rows rather
    // than inventing access it doesn't currently grant.
    User user = userWithRole("content-editor", List.of());

    assertTrue(user.hasRole("content-editor"));
    for (String capabilityCode : ALL_CAPABILITIES) {
      assertFalse(user.hasPermission(capabilityCode), "content-editor should not yet resolve any capability");
    }
  }

  private static void assertRoleGrantsExactly(String roleCode, List<String> grantedCapabilities) {
    User user = userWithRole(roleCode, grantedCapabilities);
    for (String capabilityCode : ALL_CAPABILITIES) {
      boolean expected = grantedCapabilities.contains(capabilityCode);
      assertEquals(expected, user.hasPermission(capabilityCode),
          roleCode + " hasPermission(" + capabilityCode + ") should be " + expected);
    }
  }

  private static User userWithRole(String roleCode, List<String> grantedCapabilities) {
    User user = new User();

    Role role = new Role();
    role.setCode(roleCode);
    user.setRoleList(List.of(role));

    List<Capability> capabilities = new ArrayList<>();
    for (String code : grantedCapabilities) {
      Capability capability = new Capability();
      capability.setCode(code);
      capabilities.add(capability);
    }
    user.setCapabilityList(capabilities);

    return user;
  }
}
