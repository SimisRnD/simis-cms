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
import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Reachability test for the new {@code /admin/feature-flags} page (issue #410).
 *
 * <p>Reads the real admin-layout.xml (same approach as {@link AdminLayoutAccessGateTest} and
 * {@link ContentEditorPageAccessTest}) so this fails if the page entry is ever removed, retargeted
 * to the wrong widget/prefix, or its role gate is loosened -- rather than only exercising the
 * generic {@code sitePropertiesEditor} widget mechanism in isolation (already covered by
 * {@code SitePropertiesEditorWidgetTest}).
 *
 * @author elizabeth houser
 */
class FeatureFlagsAdminPageTest {

  private static final File ADMIN_LAYOUT = new File("src/main/webapp/WEB-INF/web-layouts/page/admin-layout.xml");

  @Test
  void featureFlagsPageExistsWithTheAdminRoleAndTheFeaturesPrefix() throws Exception {
    assertTrue(ADMIN_LAYOUT.isFile(),
        "admin-layout.xml not found (run from the project root): " + ADMIN_LAYOUT.getAbsolutePath());

    Element page = findPage("/admin/feature-flags");

    assertEquals("admin", page.getAttribute("role").trim(),
        "/admin/feature-flags must be gated to the admin role, matching every other *-properties page");

    NodeList widgets = page.getElementsByTagName("widget");
    boolean foundSitePropertiesEditor = false;
    for (int i = 0; i < widgets.getLength(); i++) {
      Element widget = (Element) widgets.item(i);
      if ("sitePropertiesEditor".equals(widget.getAttribute("name"))) {
        foundSitePropertiesEditor = true;
        NodeList prefixNodes = widget.getElementsByTagName("prefix");
        assertTrue(prefixNodes.getLength() > 0, "sitePropertiesEditor widget must declare a <prefix>");
        assertEquals("features", prefixNodes.item(0).getTextContent().trim());
      }
    }
    assertTrue(foundSitePropertiesEditor,
        "/admin/feature-flags must use the sitePropertiesEditor widget, the same generic mechanism "
            + "every other /admin/*-properties settings page uses");
  }

  @Test
  void anAdminUserIsAllowedByThePageGate() throws Exception {
    Page page = new Page("/admin/feature-flags", null, null);
    page.setRoles(parseRoles(findPage("/admin/feature-flags")));

    UserSession adminSession = sessionWithRoles("admin");
    assertTrue(WebComponentCommand.allowsUser(page, adminSession),
        "an admin user must be able to reach /admin/feature-flags");
  }

  @Test
  void aNonAdminUserIsDeniedByThePageGate() throws Exception {
    Page page = new Page("/admin/feature-flags", null, null);
    page.setRoles(parseRoles(findPage("/admin/feature-flags")));

    UserSession contentManagerSession = sessionWithRoles("content-manager");
    assertFalse(WebComponentCommand.allowsUser(page, contentManagerSession),
        "a non-admin role must not be able to reach /admin/feature-flags");
  }

  private static Element findPage(String name) throws Exception {
    Document document = parse(ADMIN_LAYOUT);
    NodeList pages = document.getElementsByTagName("page");
    for (int i = 0; i < pages.getLength(); i++) {
      Element page = (Element) pages.item(i);
      if (name.equals(page.getAttribute("name"))) {
        return page;
      }
    }
    throw new AssertionError("could not find the " + name + " <page> element in " + ADMIN_LAYOUT.getAbsolutePath());
  }

  private static List<String> parseRoles(Element page) {
    // Mirrors XMLPageLoader's own role="..." parsing (comma-separated, trimmed).
    String aRoles = page.getAttribute("role");
    return Stream.of(aRoles.split(","))
        .map(String::trim)
        .collect(toList());
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
