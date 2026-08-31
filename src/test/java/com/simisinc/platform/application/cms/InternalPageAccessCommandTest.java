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

package com.simisinc.platform.application.cms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.login.MfaEnforcementCommand;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.presentation.controller.UserSession;

/**
 * Tests the internal-page gate (issue #1688).
 *
 * <p>
 * The headline test is {@link #anInternalPageIsRefusedToAnAnonymousVisitorOnceAGroupIsNamed()}: before
 * this command existed, ticking "Internal" restricted nobody, so that assertion is the one that fails
 * without the change. Everything else guards a way the gate could go wrong in the other direction --
 * denying people it should not, or denying them irreversibly.
 * </p>
 *
 * <p>
 * Two tests assert that <em>no site property is read at all</em>. That is a real constraint rather than
 * an optimization: the command is called on every page request, and a property read reaches
 * {@code DataSource.getDataSource()}, which is null in a POJO test. Reordering the checks would both
 * put a database round-trip on the hot path for every non-internal page and break any test that
 * constructs this command's collaborators without a datasource.
 * </p>
 *
 * @author Elizabeth Houser
 * @created 8/31/2026 9:00 AM
 */
class InternalPageAccessCommandTest {

  private static final String STAFF_GROUP = "all-employees";

  private WebPage internalPage(String link) {
    WebPage webPage = new WebPage();
    webPage.setLink(link);
    webPage.setInternal(true);
    return webPage;
  }

  /** A logged-in session holding exactly the given role codes and no groups. */
  private UserSession sessionWithRoles(String... roleCodes) {
    List<Role> roles = new ArrayList<>();
    for (String code : roleCodes) {
      roles.add(new Role(code, code));
    }
    User user = new User();
    user.setId(1L);
    user.setRoleList(roles);
    user.setGroupList(new ArrayList<>());
    UserSession session = new UserSession();
    session.login(user);
    return session;
  }

  /** A logged-in session holding exactly the given group uniqueIds and no roles. */
  private UserSession sessionWithGroups(String... groupUniqueIds) {
    List<Group> groups = new ArrayList<>();
    for (String uniqueId : groupUniqueIds) {
      Group group = new Group();
      group.setUniqueId(uniqueId);
      group.setName(uniqueId);
      groups.add(group);
    }
    User user = new User();
    user.setId(2L);
    user.setRoleList(new ArrayList<>());
    user.setGroupList(groups);
    UserSession session = new UserSession();
    session.login(user);
    return session;
  }

  private UserSession guestSession() {
    return new UserSession();
  }

  /** Stubs the gate's own property; leaves the MFA enrollment URL unstubbed (null), which matches no link. */
  private MockedStatic<LoadSitePropertyCommand> internalGroupIs(String value) {
    MockedStatic<LoadSitePropertyCommand> m = mockStatic(LoadSitePropertyCommand.class);
    m.when(() -> LoadSitePropertyCommand.loadByName(InternalPageAccessCommand.PROPERTY_INTERNAL_PAGE_GROUP))
        .thenReturn(value);
    return m;
  }

  // --- the defect this exists to fix ---

  @Test
  void anInternalPageIsRefusedToAnAnonymousVisitorOnceAGroupIsNamed() {
    try (MockedStatic<LoadSitePropertyCommand> property = internalGroupIs(STAFF_GROUP)) {
      assertTrue(InternalPageAccessCommand.isBlocked(internalPage("/employee-handbook"), guestSession()),
          "an internal page must not be served to an anonymous visitor once a staff group is configured");
    }
  }

  @Test
  void aMemberOfTheNamedGroupIsLetThrough() {
    try (MockedStatic<LoadSitePropertyCommand> property = internalGroupIs(STAFF_GROUP)) {
      assertFalse(InternalPageAccessCommand.isBlocked(internalPage("/employee-handbook"),
          sessionWithGroups(STAFF_GROUP)));
    }
  }

  @Test
  void aLoggedInUserOutsideTheNamedGroupIsStillRefused() {
    try (MockedStatic<LoadSitePropertyCommand> property = internalGroupIs(STAFF_GROUP)) {
      assertTrue(InternalPageAccessCommand.isBlocked(internalPage("/employee-handbook"),
          sessionWithGroups("newsletter-subscribers")),
          "being signed in is not the same as being staff");
    }
  }

  // --- the off switch, and staying recoverable ---

  @Test
  void aBlankPropertyLeavesInternalAsTheLabelItHasAlwaysBeen() {
    try (MockedStatic<LoadSitePropertyCommand> property = internalGroupIs("")) {
      assertFalse(InternalPageAccessCommand.isBlocked(internalPage("/employee-handbook"), guestSession()),
          "blank is the shipped default, so upgrading must not restrict a single page");
    }
  }

  @Test
  void aGroupThatNoLongerExistsFailsClosedForEveryoneOutsideTheEditorTier() {
    try (MockedStatic<LoadSitePropertyCommand> property = internalGroupIs("deleted-group")) {
      assertTrue(InternalPageAccessCommand.isBlocked(internalPage("/employee-handbook"), guestSession()));
    }
  }

  @Test
  void theEditorTierGetsThroughEvenWhenTheNamedGroupIsBroken() {
    // This is what keeps a typo recoverable: whoever can fix the setting can still reach the pages.
    for (String role : new String[] { "admin", "content-manager", "content-editor" }) {
      try (MockedStatic<LoadSitePropertyCommand> property = internalGroupIs("deleted-group")) {
        assertFalse(InternalPageAccessCommand.isBlocked(internalPage("/employee-handbook"), sessionWithRoles(role)),
            role + " must never be locked out of an internal page");
      }
    }
  }

  // --- lockout exemptions ---

  @Test
  void theMfaEnrollmentPageIsNeverGated() {
    try (MockedStatic<LoadSitePropertyCommand> property = internalGroupIs(STAFF_GROUP)) {
      property.when(() -> LoadSitePropertyCommand.loadByName(MfaEnforcementCommand.PROPERTY_ENROLLMENT_URL,
          MfaEnforcementCommand.DEFAULT_ENROLLMENT_URL)).thenReturn("/my-page");
      assertFalse(InternalPageAccessCommand.isBlocked(internalPage("/my-page"), guestSession()),
          "a user required to enrol in MFA would otherwise have nowhere to go");
    }
  }

  @Test
  void aRowShadowingAShippedFileLayoutIsNeverGated() {
    try (MockedStatic<LoadSitePropertyCommand> property = internalGroupIs(STAFF_GROUP);
        MockedStatic<WebPageXmlLayoutCommand> layout = mockStatic(WebPageXmlLayoutCommand.class)) {
      layout.when(() -> WebPageXmlLayoutCommand.containsPage("/login")).thenReturn(true);
      assertFalse(InternalPageAccessCommand.isBlocked(internalPage("/login"), guestSession()),
          "a web_pages row does not supply /login's markup, so it must not supply its gate either");
    }
  }

  // --- ordering: the cheap exits must come before any property read ---

  @Test
  void aNullPageIsNotBlockedAndReadsNoProperty() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      assertFalse(InternalPageAccessCommand.isBlocked(null, guestSession()));
      property.verifyNoInteractions();
    }
  }

  @Test
  void anOrdinaryPageIsNotBlockedAndReadsNoProperty() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      WebPage publicPage = new WebPage();
      publicPage.setLink("/about-us");
      assertFalse(InternalPageAccessCommand.isBlocked(publicPage, guestSession()));
      property.verifyNoInteractions();
    }
  }

  @Test
  void aNullSessionIsTreatedAsAnonymous() {
    try (MockedStatic<LoadSitePropertyCommand> property = internalGroupIs(STAFF_GROUP)) {
      assertTrue(InternalPageAccessCommand.isBlocked(internalPage("/employee-handbook"), null));
    }
  }
}
