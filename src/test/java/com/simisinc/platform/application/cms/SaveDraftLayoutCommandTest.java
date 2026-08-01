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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;

/**
 * Verifies {@link SaveDraftLayoutCommand} against the DOM shape a default (no {@code cssClass})
 * page section actually renders.
 *
 * <p>
 * {@code buildLayoutJson()} in platform-editor.js used to assume every section's columns are its
 * direct DOM children. layout-body-renderer.jspf only renders that way for
 * grid/platform-no-margin/admin sections; a default section on a normal page nests columns two
 * levels deeper, so the client reported an empty {@code columns} array for a section that still
 * had real columns, and this command persisted that empty array verbatim. These tests reproduce
 * that JSON shape directly against the command -- the same input the buggy client used to send --
 * to prove the section's columns/widgets now survive the round trip instead of being wiped.
 * </p>
 *
 * @author elizabeth houser
 */
class SaveDraftLayoutCommandTest {

  // One section with two columns, each holding a widget -- the shape a real default (no
  // cssClass) section has once it's been authored, regardless of how deep the JSP nests it.
  private static final String TWO_COLUMN_SECTION_XML =
      "<page>\n" +
      "  <section>\n" +
      "    <column class=\"medium-6 cell\">\n" +
      "      <widget name=\"content\">\n" +
      "        <uniqueId>left</uniqueId>\n" +
      "      </widget>\n" +
      "    </column>\n" +
      "    <column class=\"medium-6 cell\">\n" +
      "      <widget name=\"content\">\n" +
      "        <uniqueId>right</uniqueId>\n" +
      "      </widget>\n" +
      "    </column>\n" +
      "  </section>\n" +
      "</page>";

  // A section with no columns at all -- a legitimately empty section, not a data-loss case.
  private static final String EMPTY_SECTION_XML =
      "<page>\n" +
      "  <section class=\"empty\">\n" +
      "  </section>\n" +
      "</page>";

  private static WebPage pageWithXml(String xml) {
    WebPage p = new WebPage();
    p.setId(99);
    p.setLink("/test-page");
    p.setPageXml(xml);
    return p;
  }

  @Test
  void rejectsEmptyColumnsWhenSectionHadColumns() {
    // This is exactly what the buggy buildLayoutJson() sent for a default section: the section
    // index is correct, but its columns array is empty even though the section has two.
    WebPage page = pageWithXml(TWO_COLUMN_SECTION_XML);
    String buggyLayoutJson = "{\"sections\":[{\"s\":0,\"columns\":[]}]}";

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class)) {
      DataException ex = assertThrows(DataException.class,
          () -> SaveDraftLayoutCommand.saveDraftLayout(page, buggyLayoutJson, 42L),
          "a section reporting zero columns when it actually has some must be rejected");
      assertTrue(ex.getMessage().contains("data loss"), "the rejection reason should be clear: " + ex.getMessage());

      repo.verify(() -> WebPageRepository.save(any(WebPage.class)), never());
    }
    assertTrue(page.getDraftPageXml() == null || page.getDraftPageXml().isEmpty(),
        "the in-memory page must not be mutated either -- the caller reports this failure back to the editor");
  }

  @Test
  void allowsGenuinelyEmptySectionToStayEmpty() throws DataException {
    // A section that never had any columns is not a data-loss case -- must not be blocked.
    WebPage page = pageWithXml(EMPTY_SECTION_XML);
    String layoutJson = "{\"sections\":[{\"s\":0,\"columns\":[]}]}";

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
         MockedStatic<WebPageXmlLayoutCommand> cmd = mockStatic(WebPageXmlLayoutCommand.class)) {
      repo.when(() -> WebPageRepository.save(any(WebPage.class))).thenAnswer(i -> i.getArgument(0));

      SaveDraftLayoutCommand.saveDraftLayout(page, layoutJson, 42L);

      repo.verify(() -> WebPageRepository.save(any(WebPage.class)));
    }
    assertTrue(page.getDraftPageXml().contains("class=\"empty\""));
  }

  @Test
  void validReorderRoundTripsColumnsAndWidgets() throws DataException {
    // A real drag reorder: same two columns, swapped, widgets intact -- must persist correctly
    // once the column count matches what's actually there.
    WebPage page = pageWithXml(TWO_COLUMN_SECTION_XML);
    String reorderJson = "{\"sections\":[{\"s\":0,\"columns\":["
        + "{\"c\":1,\"widgets\":[0]},"
        + "{\"c\":0,\"widgets\":[0]}"
        + "]}]}";

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
         MockedStatic<WebPageXmlLayoutCommand> cmd = mockStatic(WebPageXmlLayoutCommand.class)) {
      repo.when(() -> WebPageRepository.save(any(WebPage.class))).thenAnswer(i -> i.getArgument(0));

      SaveDraftLayoutCommand.saveDraftLayout(page, reorderJson, 42L);
    }

    String result = page.getDraftPageXml();
    assertTrue(result.contains("<uniqueId>left</uniqueId>"), "left widget should survive the reorder");
    assertTrue(result.contains("<uniqueId>right</uniqueId>"), "right widget should survive the reorder");
    // The originally-second column (uniqueId "right") should now come first.
    assertTrue(result.indexOf("right") < result.indexOf("left"), "columns should be swapped");
  }

  @Test
  void columnsKeyAbsentLeavesSectionUntouched() throws DataException {
    WebPage page = pageWithXml(TWO_COLUMN_SECTION_XML);
    // No "columns" key at all for section 0 -- distinct from an empty array; must be a no-op.
    String layoutJson = "{\"sections\":[{\"s\":0}]}";

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
         MockedStatic<WebPageXmlLayoutCommand> cmd = mockStatic(WebPageXmlLayoutCommand.class)) {
      repo.when(() -> WebPageRepository.save(any(WebPage.class))).thenAnswer(i -> i.getArgument(0));

      SaveDraftLayoutCommand.saveDraftLayout(page, layoutJson, 42L);
    }

    String result = page.getDraftPageXml();
    assertTrue(result.contains("<uniqueId>left</uniqueId>") && result.contains("<uniqueId>right</uniqueId>"),
        "both columns/widgets should be preserved when the columns key is absent");
  }

  // ── modifiedBy / persistence-failure propagation ─────────────────────────
  // A draft-layout reorder must record who made it and must not report success when the
  // underlying save silently fails (e.g. a stale modified_by value tripping the
  // web_pages_modified_by_fkey foreign key). See SaveDraftLayoutCommandIntegrationTest for the
  // same two properties proven against a real database instead of a mock.

  @Test
  void saveDraftLayoutSetsModifiedByBeforeSaving() throws DataException {
    WebPage page = pageWithXml(EMPTY_SECTION_XML);
    String layoutJson = "{\"sections\":[{\"s\":0,\"columns\":[]}]}";

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
         MockedStatic<WebPageXmlLayoutCommand> cmd = mockStatic(WebPageXmlLayoutCommand.class)) {
      repo.when(() -> WebPageRepository.save(any(WebPage.class))).thenAnswer(i -> i.getArgument(0));
      cmd.when(() -> WebPageXmlLayoutCommand.removeCustomPage(anyString())).thenAnswer(i -> null);

      SaveDraftLayoutCommand.saveDraftLayout(page, layoutJson, 42L);

      repo.verify(() -> WebPageRepository.save(argThat(p -> p.getModifiedBy() == 42L)));
    }
  }

  @Test
  void saveDraftLayoutThrowsWhenSaveFails() {
    WebPage page = pageWithXml(EMPTY_SECTION_XML);
    String layoutJson = "{\"sections\":[{\"s\":0,\"columns\":[]}]}";

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
         MockedStatic<WebPageXmlLayoutCommand> cmd = mockStatic(WebPageXmlLayoutCommand.class)) {
      // A null return simulates WebPageRepository.update() failing (e.g. the FK violation logged
      // by DB.update() when modified_by isn't a real user id) -- saveDraftLayout() must not treat
      // that as success.
      repo.when(() -> WebPageRepository.save(any(WebPage.class))).thenAnswer(i -> null);
      cmd.when(() -> WebPageXmlLayoutCommand.removeCustomPage(anyString())).thenAnswer(i -> null);

      assertThrows(DataException.class,
          () -> SaveDraftLayoutCommand.saveDraftLayout(page, layoutJson, 42L),
          "a null return from WebPageRepository.save() means persistence failed and must not be swallowed");

      cmd.verify(() -> WebPageXmlLayoutCommand.removeCustomPage(anyString()), never());
    }
  }
}
