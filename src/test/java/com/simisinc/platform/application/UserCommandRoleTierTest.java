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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/**
 * Role badges on /admin/users and /admin/user-details are coloured by privilege level, so that
 * "who holds elevated access" is answerable at a glance rather than by reading every badge.
 *
 * <p>These pin the banding against the seeded lookup_role levels, and pin the one colour the
 * mapping must never return.
 *
 * @author SimIS Inc.
 */
class UserCommandRoleTierTest {

  @Test
  void eachSeededRoleLevelGetsItsTierColour() {
    // The levels seeded by NEW_10000: an admin has to be distinguishable from everything below it.
    assertEquals("warning", UserCommand.roleTierClass(100), "admin");
    assertEquals("primary", UserCommand.roleTierClass(95), "ecommerce-manager");
    assertEquals("primary", UserCommand.roleTierClass(93), "data-manager");
    assertEquals("primary", UserCommand.roleTierClass(90), "community-manager");
    assertEquals("success", UserCommand.roleTierClass(80), "content-manager");
    assertEquals("secondary", UserCommand.roleTierClass(70), "content-editor");
  }

  @Test
  void bandsRatherThanEnumeratesSoAnAddedRoleStillGetsAColour() {
    // A site can add a role at a level the seed never used; it must land in a band rather than
    // fall through to no colour at all.
    assertEquals("success", UserCommand.roleTierClass(85));
    assertEquals("primary", UserCommand.roleTierClass(99));
    assertEquals("secondary", UserCommand.roleTierClass(10));
    assertEquals("secondary", UserCommand.roleTierClass(0));
    // Above the seeded ceiling still reads as the top tier rather than wrapping to the bottom
    assertEquals("warning", UserCommand.roleTierClass(120));
  }

  @Test
  void neverReturnsAlert() {
    // Two reasons, both deliberate. The break-glass badge sits alongside these and owns red -- a
    // role sharing it would blunt the one badge that must stand out. And Foundation's .label.alert
    // is #cc4b37 on #fefefe, which is 4.4981:1: just under the 4.5:1 AA floor for small text, so
    // routing a role onto it would introduce an accessibility regression on an admin screen.
    for (int level = 0; level <= 120; level++) {
      assertNotEquals("alert", UserCommand.roleTierClass(level), "level " + level);
    }
  }
}
