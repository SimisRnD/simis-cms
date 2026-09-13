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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jakarta.servlet.ServletContext;

/**
 * The platform's account and utility pages must stay out of search indexes (issue #2021).
 *
 * <p>Measured against www.simisinc.com before the fix, seven of these answered 200 to an anonymous
 * visitor with no {@code X-Robots-Tag} at all. A site audit flagged {@code /login} and
 * {@code /forgot-password} because those were the two it could reach by following links --
 * {@code /login} carries an inbound link from the site-wide header, giving it more internal
 * inlinks than any real content page -- but the rest were equally indexable and simply had not
 * been linked or guessed yet.
 *
 * <p>These load the real {@code cms-layout.xml} through the real {@link XMLPageLoader} rather than
 * re-parsing the attribute, so the assertions cover the layout and the parser together and cannot
 * drift from how the value is actually consumed. Same approach as
 * {@link ContentEditorPageAccessTest} and {@link AdminLayoutAccessGateTest}.
 *
 * @author elizabeth houser
 */
class AccountPageNoindexTest {

  private static final File CMS_LAYOUT = new File("src/main/webapp/WEB-INF/web-layouts/page/cms-layout.xml");

  /** Forms for existing users, actions, and one-time transactional states. */
  private static final List<String> MUST_BE_NOINDEX = List.of(
      "/login", "/logout", "/forgot-password", "/validation-sent", "/confirm-subscription", "/unsubscribe");

  private static Map<String, Page> pages;

  @BeforeAll
  static void loadTheRealLayout() throws Exception {
    assertTrue(CMS_LAYOUT.isFile(),
        "cms-layout.xml not found (run from the project root): " + CMS_LAYOUT.getAbsolutePath());
    pages = loadLayout(CMS_LAYOUT);
  }

  private static Map<String, Page> loadLayout(File layout) throws Exception {
    // XMLPageLoader reaches its files through the ServletContext; point that at the file on disk.
    ServletContext context = mock(ServletContext.class);
    when(context.getResource(anyString())).thenReturn(layout.toURI().toURL());
    when(context.getResourceAsStream(anyString())).thenAnswer(invocation -> new FileInputStream(layout));

    Map<String, Page> loaded = new HashMap<>();
    XMLPageLoader loader = new XMLPageLoader(loaded);
    loader.addFile(layout.getName());
    loader.load(context);
    return loaded;
  }

  @Test
  void theAccountAndUtilityPagesAreMarkedNoindex() {
    for (String pageName : MUST_BE_NOINDEX) {
      Page page = pages.get(pageName);
      assertNotNull(page, pageName + " is missing from cms-layout.xml");
      assertTrue(page.isNoindex(),
          pageName + " must carry noindex=\"true\": a search result pointing at it is never what "
              + "the searcher wanted (issue #2021)");
    }
  }

  /**
   * {@code /register} is deliberately left indexable. Unlike the others it is a real destination
   * rather than a form for existing users or a transient state, and a membership or community site
   * may well want it discoverable. Asserted so that flipping it becomes a deliberate decision with
   * a failing test attached, rather than a quiet edit.
   */
  @Test
  void theRegisterPageIsDeliberatelyLeftIndexable() {
    Page register = pages.get("/register");
    assertNotNull(register, "/register is missing from cms-layout.xml");
    assertFalse(register.isNoindex(),
        "/register is intentionally indexable -- if that changed on purpose, update this test and "
            + "say why in the commit");
  }

  @Test
  void anOrdinaryPageIsIndexableByDefault() {
    // Guard the blast radius: the attribute must opt individual pages out, not flip the default.
    long noindexCount = pages.values().stream().filter(Page::isNoindex).count();
    assertEquals(MUST_BE_NOINDEX.size(), noindexCount,
        "exactly the " + MUST_BE_NOINDEX.size() + " listed pages should be noindex in cms-layout.xml");
  }

  @Test
  void onlyTheLiteralTrueTurnsIndexingOff(@TempDir Path tempDir) throws Exception {
    // A misspelled or negated value must fail toward the status quo -- leaving a page indexable --
    // rather than silently dropping it out of search.
    File layout = tempDir.resolve("noindex-values-layout.xml").toFile();
    Files.writeString(layout.toPath(), "<?xml version=\"1.0\" ?>\n"
        + "<layout>\n"
        + "  <page name=\"/absent\" title=\"Absent\"/>\n"
        + "  <page name=\"/true\" title=\"True\" noindex=\"true\"/>\n"
        + "  <page name=\"/mixed-case\" title=\"Mixed\" noindex=\"TRUE\"/>\n"
        + "  <page name=\"/padded\" title=\"Padded\" noindex=\" true \"/>\n"
        + "  <page name=\"/false\" title=\"False\" noindex=\"false\"/>\n"
        + "  <page name=\"/yes\" title=\"Yes\" noindex=\"yes\"/>\n"
        + "  <page name=\"/empty\" title=\"Empty\" noindex=\"\"/>\n"
        + "</layout>\n", StandardCharsets.UTF_8);

    Map<String, Page> parsed = loadLayout(layout);

    assertTrue(parsed.get("/true").isNoindex(), "noindex=\"true\"");
    assertTrue(parsed.get("/mixed-case").isNoindex(), "noindex=\"TRUE\" is the same value");
    assertTrue(parsed.get("/padded").isNoindex(), "surrounding whitespace is trimmed");

    assertFalse(parsed.get("/absent").isNoindex(), "no attribute leaves the page indexable");
    assertFalse(parsed.get("/false").isNoindex(), "noindex=\"false\" leaves the page indexable");
    assertFalse(parsed.get("/yes").isNoindex(), "only \"true\" counts, so a typo fails safe");
    assertFalse(parsed.get("/empty").isNoindex(), "an empty value leaves the page indexable");
  }
}
