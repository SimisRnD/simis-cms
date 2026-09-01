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

package com.simisinc.platform.presentation.widgets.admin;

import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.presentation.widgets.admin.SettingsHubWidget.SettingsEntry;
import com.simisinc.platform.presentation.widgets.admin.SettingsHubWidget.SettingsGroup;

/**
 * The settings hub (issue #1765), and the two things about it that can silently rot: an entry that
 * points nowhere, and the hub drifting out of step with the menu it replaced.
 *
 * @author SimIS Inc.
 */
class SettingsHubWidgetTest extends WidgetBase {

  private static final Path LAYOUT = Paths.get("src/main/webapp/WEB-INF/web-layouts/page/admin-layout.xml");
  private static final Path ECOMMERCE_LAYOUT = Paths
      .get("src/main/webapp/WEB-INF/web-layouts/page/admin-ecommerce-layout.xml");
  private static final Path MAIN_JSP = Paths.get("src/main/webapp/WEB-INF/jsp/main.jsp");

  private static List<SettingsEntry> allEntries() {
    List<SettingsEntry> all = new ArrayList<>();
    for (SettingsGroup group : SettingsHubWidget.SETTINGS_GROUPS) {
      all.addAll(group.getEntryList());
    }
    return all;
  }

  private static String read(Path path) throws IOException {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  @SuppressWarnings("unchecked")
  private List<SettingsGroup> renderedGroups() {
    return (List<SettingsGroup>) widgetContext.getRequest().getAttribute("settingsGroupList");
  }

  private void execute(Map<String, String> ecommerce, Map<String, String> elearning) {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadAsMap("ecommerce")).thenReturn(ecommerce);
      property.when(() -> LoadSitePropertyCommand.loadAsMap("elearning")).thenReturn(elearning);
      new SettingsHubWidget().execute(widgetContext);
    }
  }

  private static Map<String, String> map(String key, String value) {
    Map<String, String> result = new HashMap<>();
    if (value != null) {
      result.put(key, value);
    }
    return result;
  }

  // --- the entries themselves -------------------------------------------------------------

  @Test
  void everyEntryPointsAtAPageThatExists() throws IOException {
    // The failure this prevents is a card that 404s. A settings page renamed or moved leaves the
    // hub pointing at nothing, and nothing else would notice.
    String layouts = read(LAYOUT) + read(ECOMMERCE_LAYOUT);
    List<String> missing = new ArrayList<>();
    for (SettingsEntry entry : allEntries()) {
      if (!layouts.contains("<page name=\"" + entry.getLink() + "\"")) {
        missing.add(entry.getLabel() + " -> " + entry.getLink());
      }
    }
    Assertions.assertEquals(List.of(), missing, "hub entries with no page declaration: " + missing);
  }

  @Test
  void everyEntryTargetsAnAdminOnlyPage() throws IOException {
    // The hub is role="admin". A card pointing at a page with a wider or narrower gate is the bug
    // found in issue #1764: a link shown to someone who is then denied when they click it.
    String layouts = read(LAYOUT) + read(ECOMMERCE_LAYOUT);
    List<String> wrong = new ArrayList<>();
    for (SettingsEntry entry : allEntries()) {
      Matcher matcher = Pattern.compile("<page name=\"" + Pattern.quote(entry.getLink()) + "\"([^>]*)>")
          .matcher(layouts);
      Assertions.assertTrue(matcher.find(), "no page declaration for " + entry.getLink());
      String attributes = matcher.group(1);
      if (!attributes.contains("role=\"admin\"")) {
        wrong.add(entry.getLink() + attributes);
      }
    }
    Assertions.assertEquals(List.of(), wrong, "hub entries not gated role=\"admin\": " + wrong);
  }

  @Test
  void everyEntryHasADescription() {
    // A card without one is just a menu row that takes more space -- the page would then be worse
    // than what it replaced.
    for (SettingsEntry entry : allEntries()) {
      Assertions.assertNotNull(entry.getDescription(), entry.getLabel());
      Assertions.assertFalse(entry.getDescription().isBlank(), entry.getLabel());
    }
  }

  @Test
  void noDestinationIsListedTwice() {
    List<String> links = new ArrayList<>();
    for (SettingsEntry entry : allEntries()) {
      Assertions.assertFalse(links.contains(entry.getLink()), "listed twice: " + entry.getLink());
      links.add(entry.getLink());
    }
  }

  @Test
  void theMenuNoLongerDuplicatesWhatTheHubLists() throws IOException {
    // The point of the change: the rows moved. Theme and Site Settings are the deliberate
    // exceptions, kept in the menu as the daily-use pair.
    String mainJsp = read(MAIN_JSP);
    int settingsStart = mainJsp.indexOf("section-title\">Settings");
    int settingsEnd = mainJsp.indexOf("</ul>", settingsStart);
    String menuSection = mainJsp.substring(settingsStart, settingsEnd);
    List<String> stillInMenu = new ArrayList<>();
    for (SettingsEntry entry : allEntries()) {
      if (menuSection.contains("\"${ctx}" + entry.getLink() + "\"")
          && !"/admin/theme-properties".equals(entry.getLink())
          && !"/admin/site-properties".equals(entry.getLink())) {
        stillInMenu.add(entry.getLink());
      }
    }
    Assertions.assertEquals(List.of(), stillInMenu,
        "these moved to the hub but are still in the menu: " + stillInMenu);
    Assertions.assertTrue(menuSection.contains("/admin/settings"), "the menu must link to the hub");
  }

  // --- module state -----------------------------------------------------------------------

  @Test
  void aModuleThatIsOffIsStillListed() {
    // The reason this page unblocks the rest of issue #1763: e-learning and BI settings pages each
    // render their own enabled checkbox, so hiding them when the module is off leaves no route back
    // to switch it on. Marked, never hidden.
    execute(map("ecommerce.enabled", "false"), map("elearning.enabled", "false"));

    List<SettingsEntry> entries = new ArrayList<>();
    for (SettingsGroup group : renderedGroups()) {
      entries.addAll(group.getEntryList());
    }
    Assertions.assertEquals(allEntries().size(), entries.size(),
        "a switched-off module must still be listed, only marked");
    for (SettingsEntry entry : entries) {
      if (entry.getBelongsToModule()) {
        Assertions.assertFalse(entry.getModuleEnabled(), entry.getLabel() + " should read as off");
      }
    }
  }

  @Test
  void anEnabledModuleIsNotMarkedOff() {
    execute(map("ecommerce.enabled", "true"), map("elearning.enabled", "true"));
    for (SettingsGroup group : renderedGroups()) {
      for (SettingsEntry entry : group.getEntryList()) {
        Assertions.assertTrue(entry.getModuleEnabled(), entry.getLabel());
      }
    }
  }

  @Test
  void theModuleTestsMatchTheMenusOwnGates() {
    // Byte-for-byte the same semantics as main.jsp (issue #1763), so a card cannot read "on" while
    // the menu behaves as "off". They differ from each other on purpose: e-commerce fails closed to
    // stay identical to the section gate it must agree with; e-learning fails open because it has
    // no section gate to match.
    Map<String, String> absent = new HashMap<>();
    Assertions.assertFalse(SettingsHubWidget.isModuleEnabled("ecommerce.enabled", absent, absent),
        "e-commerce must fail closed when the property row is missing");
    Assertions.assertTrue(SettingsHubWidget.isModuleEnabled("elearning.enabled", absent, absent),
        "e-learning must fail open when the property row is missing");
    Assertions.assertTrue(SettingsHubWidget.isModuleEnabled(null, absent, absent),
        "an entry with no module always shows");
  }

  @Test
  void theSharedGroupListIsNotMutatedByARequest() {
    // SETTINGS_GROUPS is static and shared across every request; one site's module state must not
    // become every site's.
    execute(map("ecommerce.enabled", "false"), map("elearning.enabled", "false"));
    for (SettingsEntry entry : allEntries()) {
      Assertions.assertTrue(entry.getModuleEnabled(),
          "the shared template was mutated by a request: " + entry.getLabel());
    }
  }

  @Test
  void theJspIsSelected() {
    execute(map("ecommerce.enabled", "true"), map("elearning.enabled", "true"));
    Assertions.assertEquals(SettingsHubWidget.JSP, widgetContext.getJsp());
  }
}
