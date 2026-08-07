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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.presentation.controller.UserSession;

/**
 * Verifies {@link ValidateApiAccessToWebPageCommand}: the User-to-UserSession bridge and the
 * publishAt/expiresAt scheduling check it layers on top of
 * {@link ValidateUserAccessToWebPageCommand#hasAccess} (issue #412). The underlying
 * draft/role/group/capability logic itself is covered by ValidateUserAccessToWebPageCommandTest;
 * these tests mock that boundary to isolate what this class adds.
 *
 * @author SimIS Inc.
 */
class ValidateApiAccessToWebPageCommandTest {

  private WebPage webPageWithLink(String link) {
    WebPage webPage = new WebPage();
    webPage.setLink(link);
    return webPage;
  }

  private User userWithRoles(String... roleCodes) {
    List<Role> roles = new ArrayList<>();
    for (String code : roleCodes) {
      roles.add(new Role(code, code));
    }
    User user = new User();
    user.setId(1L);
    user.setRoleList(roles);
    return user;
  }

  @Test
  void returnsFalseForANullWebPage() {
    assertFalse(ValidateApiAccessToWebPageCommand.hasAccess(null, null));
  }

  @Test
  void delegatesToTheSharedAccessCheckAndDeniesWhenItDenies() {
    WebPage webPage = webPageWithLink("/gated");
    try (MockedStatic<ValidateUserAccessToWebPageCommand> shared = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      shared.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/gated"), any(UserSession.class)))
          .thenReturn(false);

      assertFalse(ValidateApiAccessToWebPageCommand.hasAccess(webPage, null));
    }
  }

  @Test
  void aGuestNullUserStillProducesAWorkingUserSession() {
    // A null User (never logged in / no bearer token resolved) must not NPE the UserSession
    // bridge -- it should behave like the guest UserSession the JSP path uses.
    WebPage webPage = webPageWithLink("/public-page");
    try (MockedStatic<ValidateUserAccessToWebPageCommand> shared = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      shared.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/public-page"), any(UserSession.class)))
          .thenReturn(true);

      assertTrue(ValidateApiAccessToWebPageCommand.hasAccess(webPage, null));
    }
  }

  @Test
  void returnsFalseForAPageScheduledToPublishInTheFuture() {
    WebPage webPage = webPageWithLink("/upcoming");
    webPage.setPublishAt(new Timestamp(System.currentTimeMillis() + 3_600_000));
    try (MockedStatic<ValidateUserAccessToWebPageCommand> shared = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      shared.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/upcoming"), any(UserSession.class)))
          .thenReturn(true);

      assertFalse(ValidateApiAccessToWebPageCommand.hasAccess(webPage, null));
    }
  }

  @Test
  void returnsFalseForAPageThatHasAlreadyExpired() {
    WebPage webPage = webPageWithLink("/expired");
    webPage.setExpiresAt(new Timestamp(System.currentTimeMillis() - 3_600_000));
    try (MockedStatic<ValidateUserAccessToWebPageCommand> shared = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      shared.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/expired"), any(UserSession.class)))
          .thenReturn(true);

      assertFalse(ValidateApiAccessToWebPageCommand.hasAccess(webPage, null));
    }
  }

  @Test
  void anAdminSeesAScheduledPageDespiteThePublishAtWindow() {
    // Mirrors PageServlet.service(): the publishAt/expiresAt check is skipped entirely for
    // admin/content-manager, same as it is for a real logged-in editor browsing the live site.
    WebPage webPage = webPageWithLink("/upcoming");
    webPage.setPublishAt(new Timestamp(System.currentTimeMillis() + 3_600_000));
    User admin = userWithRoles("admin");
    try (MockedStatic<ValidateUserAccessToWebPageCommand> shared = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      shared.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/upcoming"), any(UserSession.class)))
          .thenReturn(true);

      assertTrue(ValidateApiAccessToWebPageCommand.hasAccess(webPage, admin));
    }
  }

  @Test
  void returnsFalseForAnArchivedPage() {
    // Regression test: this class never checked WebPage.getArchived() at all, unlike
    // PageServlet.isArchivedBlockedFromPublicAccess() which this class's own javadoc claims to
    // mirror -- an archived page's metadata was still served over the REST API.
    WebPage webPage = webPageWithLink("/retired");
    webPage.setArchived(new Timestamp(System.currentTimeMillis()));
    try (MockedStatic<ValidateUserAccessToWebPageCommand> shared = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      shared.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/retired"), any(UserSession.class)))
          .thenReturn(true);

      assertFalse(ValidateApiAccessToWebPageCommand.hasAccess(webPage, null));
    }
  }

  @Test
  void anAdminSeesAnArchivedPageDespiteTheArchivedFlag() {
    // Mirrors PageServlet.isArchivedBlockedFromPublicAccess(): admin/content-manager still need
    // an archived page resolvable so it can be managed.
    WebPage webPage = webPageWithLink("/retired");
    webPage.setArchived(new Timestamp(System.currentTimeMillis()));
    User admin = userWithRoles("admin");
    try (MockedStatic<ValidateUserAccessToWebPageCommand> shared = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      shared.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/retired"), any(UserSession.class)))
          .thenReturn(true);

      assertTrue(ValidateApiAccessToWebPageCommand.hasAccess(webPage, admin));
    }
  }

  @Test
  void returnsTrueForALivePageWithNoScheduleRestriction() {
    WebPage webPage = webPageWithLink("/about");
    try (MockedStatic<ValidateUserAccessToWebPageCommand> shared = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      shared.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/about"), any(UserSession.class)))
          .thenReturn(true);

      assertTrue(ValidateApiAccessToWebPageCommand.hasAccess(webPage, null));
    }
  }
}
