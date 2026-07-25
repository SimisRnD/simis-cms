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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Deny-by-default gate for the admin surface (issue #299).
 *
 * <p>{@link WebComponentCommand#allowsUser} is open-by-default: a page that declares no roles is
 * public. That is correct for the public site, but it means a NEW admin page ships silently public if
 * someone forgets to gate it. Rather than flip the runtime default (which would deny the whole public
 * site), this fails the build if any page in an admin-* layout omits a {@code role}, so the admin
 * surface stays deny-by-default by construction. It asserts an invariant over the layout definitions;
 * it does not change runtime behavior.
 *
 * @author elizabeth houser
 */
class AdminLayoutAccessGateTest {

  private static final File LAYOUT_DIR = new File("src/main/webapp/WEB-INF/web-layouts/page");

  @Test
  void everyAdminLayoutPageDeclaresARole() throws Exception {
    assertTrue(LAYOUT_DIR.isDirectory(),
        "layout directory not found (run from the project root): " + LAYOUT_DIR.getAbsolutePath());

    File[] adminLayouts = LAYOUT_DIR.listFiles((dir, name) -> name.startsWith("admin") && name.endsWith(".xml"));
    assertTrue(adminLayouts != null && adminLayouts.length > 0,
        "no admin-* layout files found in " + LAYOUT_DIR.getAbsolutePath());

    List<String> ungated = new ArrayList<>();
    int pagesScanned = 0;
    for (File layout : adminLayouts) {
      Document document = parse(layout);
      NodeList pages = document.getElementsByTagName("page");
      for (int i = 0; i < pages.getLength(); i++) {
        pagesScanned++;
        Element page = (Element) pages.item(i);
        if (page.getAttribute("role").trim().isEmpty()) {
          ungated.add(layout.getName() + " -> page name=\"" + page.getAttribute("name") + "\"");
        }
      }
    }

    // A page with no role= is public via allowsUser's open-by-default; on the admin surface that is a
    // silently-exposed admin page. Gate it (role="admin", or the least-privileged role that fits).
    assertTrue(ungated.isEmpty(),
        "Admin-layout pages must declare a role= (deny-by-default, #299). Ungated pages: " + ungated);
    // Guard against a path/parser change silently turning this into a no-op that always passes.
    assertTrue(pagesScanned > 0, "scanned zero <page> elements in the admin layouts -- check the parser/path");
  }

  private static Document parse(File file) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    DocumentBuilder builder = factory.newDocumentBuilder();
    return builder.parse(file);
  }
}
