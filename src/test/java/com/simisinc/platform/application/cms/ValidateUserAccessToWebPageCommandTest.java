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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.presentation.controller.Column;
import com.simisinc.platform.presentation.controller.Page;
import com.simisinc.platform.presentation.controller.Section;
import com.simisinc.platform.presentation.controller.UserSession;
import com.simisinc.platform.presentation.controller.Widget;

import static org.mockito.Mockito.mockStatic;

/**
 * Exercises the page-level access gate.
 *
 * <p>
 * The role/group primitive this gate leans on ({@code WebComponentCommand.allowsUser}) is covered by
 * WebComponentCommandTest. These tests cover what {@code hasAccess} adds on top of it: the privileged-role
 * bypass, and the checks that a page is loadable, published (not draft), non-empty, and reachable. The
 * data-loading boundary ({@code LoadWebPageCommand}, {@code WebPageXmlLayoutCommand}) is mocked so the real
 * authorization logic runs against controlled page structures.
 * </p>
 *
 * @author Elizabeth Houser
 * @created 7/22/2026 4:15 PM
 */
class ValidateUserAccessToWebPageCommandTest {

  private static final String LINK = "/some-page";

  /** A logged-in session whose user holds exactly the given role codes. */
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

  /** A not-logged-in (guest) session. */
  private UserSession guestSession() {
    return new UserSession();
  }

  private WebPage publishedPage(String xml) {
    WebPage webPage = new WebPage();
    webPage.setDraft(false);
    webPage.setPageXml(xml);
    return webPage;
  }

  /** A single-widget page structure with the given roles required at the page level; everything else public. */
  private Page pageStructure(List<String> pageRoles) {
    Widget widget = new Widget("content");
    Column column = new Column();
    column.setWidgets(List.of(widget));
    Section section = new Section();
    section.setColumns(List.of(column));
    Page page = new Page();
    page.setRoles(pageRoles);
    page.setSections(List.of(section));
    return page;
  }

  // --- privileged bypass: returns before any page is loaded, so no mocking is needed ---

  @Test
  void administratorAlwaysHasAccess() {
    Assertions.assertTrue(ValidateUserAccessToWebPageCommand.hasAccess(LINK, sessionWithRoles("admin")));
  }

  @Test
  void contentManagerAlwaysHasAccess() {
    Assertions.assertTrue(ValidateUserAccessToWebPageCommand.hasAccess(LINK, sessionWithRoles("content-manager")));
  }

  // --- non-privileged users: the page-loading and structural gates ---

  @Test
  void deniedWhenPageDoesNotExist() {
    try (MockedStatic<LoadWebPageCommand> load = mockStatic(LoadWebPageCommand.class)) {
      load.when(() -> LoadWebPageCommand.loadByLink(LINK)).thenReturn(null);
      Assertions.assertFalse(ValidateUserAccessToWebPageCommand.hasAccess(LINK, guestSession()));
    }
  }

  @Test
  void deniedWhenPageIsDraft() {
    // Security property: an unpublished draft must never be served to a non-privileged user.
    WebPage draft = publishedPage("<page/>");
    draft.setDraft(true);
    try (MockedStatic<LoadWebPageCommand> load = mockStatic(LoadWebPageCommand.class)) {
      load.when(() -> LoadWebPageCommand.loadByLink(LINK)).thenReturn(draft);
      Assertions.assertFalse(ValidateUserAccessToWebPageCommand.hasAccess(LINK, guestSession()));
    }
  }

  @Test
  void deniedWhenPageXmlIsBlank() {
    try (MockedStatic<LoadWebPageCommand> load = mockStatic(LoadWebPageCommand.class)) {
      load.when(() -> LoadWebPageCommand.loadByLink(LINK)).thenReturn(publishedPage("   "));
      Assertions.assertFalse(ValidateUserAccessToWebPageCommand.hasAccess(LINK, guestSession()));
    }
  }

  @Test
  void deniedWhenPageStructureCannotBeBuilt() {
    WebPage webPage = publishedPage("<page/>");
    try (MockedStatic<LoadWebPageCommand> load = mockStatic(LoadWebPageCommand.class);
         MockedStatic<WebPageXmlLayoutCommand> layout = mockStatic(WebPageXmlLayoutCommand.class)) {
      load.when(() -> LoadWebPageCommand.loadByLink(LINK)).thenReturn(webPage);
      layout.when(() -> WebPageXmlLayoutCommand.retrievePageForRequest(webPage, LINK)).thenReturn(null);
      Assertions.assertFalse(ValidateUserAccessToWebPageCommand.hasAccess(LINK, guestSession()));
    }
  }

  @Test
  void grantedForAPublishedPublicPage() {
    WebPage webPage = publishedPage("<page/>");
    Page structure = pageStructure(new ArrayList<>()); // no roles required -> public
    try (MockedStatic<LoadWebPageCommand> load = mockStatic(LoadWebPageCommand.class);
         MockedStatic<WebPageXmlLayoutCommand> layout = mockStatic(WebPageXmlLayoutCommand.class)) {
      load.when(() -> LoadWebPageCommand.loadByLink(LINK)).thenReturn(webPage);
      layout.when(() -> WebPageXmlLayoutCommand.retrievePageForRequest(webPage, LINK)).thenReturn(structure);
      Assertions.assertTrue(ValidateUserAccessToWebPageCommand.hasAccess(LINK, guestSession()));
    }
  }

  @Test
  void deniedWhenPageRequiresLoginAndUserIsGuest() {
    // Security property: a page restricted to logged-in users ("users" role) is not reachable by a guest.
    WebPage webPage = publishedPage("<page/>");
    Page structure = pageStructure(List.of("users"));
    try (MockedStatic<LoadWebPageCommand> load = mockStatic(LoadWebPageCommand.class);
         MockedStatic<WebPageXmlLayoutCommand> layout = mockStatic(WebPageXmlLayoutCommand.class)) {
      load.when(() -> LoadWebPageCommand.loadByLink(LINK)).thenReturn(webPage);
      layout.when(() -> WebPageXmlLayoutCommand.retrievePageForRequest(webPage, LINK)).thenReturn(structure);
      Assertions.assertFalse(ValidateUserAccessToWebPageCommand.hasAccess(LINK, guestSession()));
    }
  }

  @Test
  void grantedWhenLoginRestrictedPageIsViewedByALoggedInUser() {
    // The same "users"-restricted page IS reachable once the viewer is logged in.
    WebPage webPage = publishedPage("<page/>");
    Page structure = pageStructure(List.of("users"));
    try (MockedStatic<LoadWebPageCommand> load = mockStatic(LoadWebPageCommand.class);
         MockedStatic<WebPageXmlLayoutCommand> layout = mockStatic(WebPageXmlLayoutCommand.class)) {
      load.when(() -> LoadWebPageCommand.loadByLink(LINK)).thenReturn(webPage);
      layout.when(() -> WebPageXmlLayoutCommand.retrievePageForRequest(webPage, LINK)).thenReturn(structure);
      Assertions.assertTrue(ValidateUserAccessToWebPageCommand.hasAccess(LINK, sessionWithRoles("some-role")));
    }
  }
}
