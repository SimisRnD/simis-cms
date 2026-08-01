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

import com.simisinc.platform.application.cms.EditorPermissionCommand;
import com.simisinc.platform.domain.model.Role;

/**
 * Regression test for issue #818: a user holding only the {@code content-editor} role had no
 * working page-content draft entry point because the {@code /content-editor} page layout
 * (cms-layout.xml) never included {@code content-editor} in its {@code role=} allow-list, despite
 * {@link EditorPermissionCommand#canEditContent} already granting that role edit rights
 * everywhere else in the app (the builder-vs-editor split, see {@link EditorPermissionCommand}).
 *
 * <p>This reads the real layout file (same approach as {@link AdminLayoutAccessGateTest}) rather
 * than hard-coding the expected role list, so it fails if the fix is ever reverted or the
 * attribute is mistyped, and mirrors {@code XMLPageLoader}'s own role="..." parsing (comma
 * separated, trimmed) so the test doesn't silently drift from how the value is actually consumed.
 *
 * @author elizabeth houser
 */
class ContentEditorPageAccessTest {

  private static final File CMS_LAYOUT = new File("src/main/webapp/WEB-INF/web-layouts/page/cms-layout.xml");

  @Test
  void contentEditorPageRoleAttributeIncludesContentEditorRole() throws Exception {
    assertTrue(CMS_LAYOUT.isFile(),
        "cms-layout.xml not found (run from the project root): " + CMS_LAYOUT.getAbsolutePath());

    List<String> roles = parseContentEditorPageRoles();

    assertTrue(roles.contains("content-editor"),
        "/content-editor page must allow the content-editor role (issue #818): " + roles);
    // Guard the fix's scope: admin/content-manager access must not have been dropped.
    assertTrue(roles.contains("admin"), "must still allow admin: " + roles);
    assertTrue(roles.contains("content-manager"), "must still allow content-manager: " + roles);
  }

  @Test
  void contentEditorOnlyUserIsAllowedByThePageGate() throws Exception {
    Page page = new Page("/content-editor", null, null);
    page.setRoles(parseContentEditorPageRoles());

    UserSession contentEditorSession = sessionWithRoles("content-editor");
    assertTrue(WebComponentCommand.allowsUser(page, contentEditorSession),
        "a content-editor-only user must be able to reach /content-editor (issue #818)");
  }

  @Test
  void unrelatedRoleIsStillDeniedByThePageGate() throws Exception {
    Page page = new Page("/content-editor", null, null);
    page.setRoles(parseContentEditorPageRoles());

    UserSession otherSession = sessionWithRoles("data-manager");
    assertFalse(WebComponentCommand.allowsUser(page, otherSession),
        "an unrelated role must still be denied /content-editor");
  }

  @Test
  void contentEditorOnlyUserStillCannotBuildLayout() {
    // Acceptance criteria (#818): reaching the content-draft entry point must not carry the
    // separate, higher layout/structural-mutation tier (canBuildLayout, issue #701/#733).
    UserSession contentEditorSession = sessionWithRoles("content-editor");
    assertFalse(EditorPermissionCommand.canBuildLayout(contentEditorSession),
        "content-editor must not gain canBuildLayout access via the /content-editor page gate");
  }

  private static List<String> parseContentEditorPageRoles() throws Exception {
    Document document = parse(CMS_LAYOUT);
    NodeList pages = document.getElementsByTagName("page");
    for (int i = 0; i < pages.getLength(); i++) {
      Element page = (Element) pages.item(i);
      if ("/content-editor".equals(page.getAttribute("name"))) {
        // Mirrors XMLPageLoader's own role="..." parsing (comma-separated, trimmed).
        String aRoles = page.getAttribute("role");
        return Stream.of(aRoles.split(","))
            .map(String::trim)
            .collect(toList());
      }
    }
    throw new AssertionError(
        "could not find the /content-editor <page> element in " + CMS_LAYOUT.getAbsolutePath());
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
