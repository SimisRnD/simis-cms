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

package com.simisinc.platform.presentation.controller;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.simisinc.platform.domain.model.Role;

/**
 * Regression test: a same-day tightening of {@code /admin/apps} and {@code /admin/app{?appId}}
 * from {@code role="admin,data-manager"} down to {@code role="admin"} was silently reverted by a
 * later merge -- the role attribute went back to including {@code data-manager} while the page's
 * own "About Apps" callout text kept claiming "access is limited to admins". An App's Client ID
 * is not secret, but the OAuth2 client-credential flow it enables reaches the full access of
 * whatever role the authenticating user already has (see the in-page API docs), so this list
 * should stay admin-only.
 *
 * <p>Reads the real layout file (same approach as {@link AdminLayoutAccessGateTest} and
 * {@link ContentEditorPageAccessTest}) rather than hard-coding the expected role list, so it fails
 * if the fix is ever reverted again or the attribute is mistyped, and mirrors
 * {@code XMLPageLoader}'s own role="..." parsing (comma separated, trimmed) so the test doesn't
 * silently drift from how the value is actually consumed.
 *
 * @author elizabeth houser
 */
class AppsPageAccessTest {

  private static final File ADMIN_LAYOUT = new File("src/main/webapp/WEB-INF/web-layouts/page/admin-layout.xml");

  @Test
  void appsListPageIsAdminOnly() throws Exception {
    List<String> roles = parsePageRoles("/admin/apps");
    assertTrue(roles.contains("admin"), "/admin/apps must still allow admin: " + roles);
    assertFalse(roles.contains("data-manager"), "/admin/apps must not allow data-manager: " + roles);
  }

  @Test
  void editAppPageIsAdminOnly() throws Exception {
    List<String> roles = parsePageRoles("/admin/app{?appId}");
    assertTrue(roles.contains("admin"), "/admin/app must still allow admin: " + roles);
    assertFalse(roles.contains("data-manager"), "/admin/app must not allow data-manager: " + roles);
  }

  @Test
  void dataManagerOnlyUserIsDeniedTheAppsListPageGate() throws Exception {
    Page page = new Page("/admin/apps", null, null);
    page.setRoles(parsePageRoles("/admin/apps"));

    UserSession dataManagerSession = sessionWithRoles("data-manager");
    assertFalse(WebComponentCommand.allowsUser(page, dataManagerSession),
        "a data-manager-only user must not be able to reach /admin/apps");
  }

  @Test
  void adminUserIsAllowedTheAppsListPageGate() throws Exception {
    Page page = new Page("/admin/apps", null, null);
    page.setRoles(parsePageRoles("/admin/apps"));

    UserSession adminSession = sessionWithRoles("admin");
    assertTrue(WebComponentCommand.allowsUser(page, adminSession),
        "an admin user must be able to reach /admin/apps");
  }

  private static List<String> parsePageRoles(String pageName) throws Exception {
    assertTrue(ADMIN_LAYOUT.isFile(),
        "admin-layout.xml not found (run from the project root): " + ADMIN_LAYOUT.getAbsolutePath());

    Document document = parse(ADMIN_LAYOUT);
    NodeList pages = document.getElementsByTagName("page");
    for (int i = 0; i < pages.getLength(); i++) {
      Element page = (Element) pages.item(i);
      if (pageName.equals(page.getAttribute("name"))) {
        // Mirrors XMLPageLoader's own role="..." parsing (comma-separated, trimmed).
        String aRoles = page.getAttribute("role");
        return Stream.of(aRoles.split(","))
            .map(String::trim)
            .collect(toList());
      }
    }
    throw new AssertionError(
        "could not find the " + pageName + " <page> element in " + ADMIN_LAYOUT.getAbsolutePath());
  }

  private static Document parse(File file) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    DocumentBuilder builder = factory.newDocumentBuilder();
    return builder.parse(file);
  }

  private static UserSession sessionWithRoles(String... codes) {
    UserSession userSession = new UserSession();
    List<Role> roles = new ArrayList<>();
    for (String code : codes) {
      roles.add(new Role(code, code));
    }
    userSession.setRoleList(roles);
    return userSession;
  }
}
